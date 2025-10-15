package com.adam.localfts.webserver;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.adam.localfts.webserver.Util.CRLF;

@Service
public class FtsService {

    @Value("${localfts.root_path}")
    private String rootPath;
    @Value("${server.port}")
    private String serverPort;
    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Value("${localfts.upload_file_limit}")
    private long uploadFileLimit;
    @Value("${localfts.log.level.root}")
    private String logLevelRoot;
    @Value("${localfts.log.file_path}")
    private String logFilePath;
    @Value("${localfts.test_language.Simplified_Chinese}")
    private boolean testLanguageSC;
    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;
    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize maxRequestSize;

    private FtsServerIpInfoModel serverIpInfoModel;
    private static final Logger LOGGER = LoggerFactory.getLogger(FtsService.class);
    private static final String[] ALLOWED_LOG_LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};
    private static final Pattern PATTERN_PATH_WINDOWS_ABSOLUTE = Pattern.compile("[A-Z]:(\\\\[^\\\\]+)*?");
    private static final Pattern PATTERN_PATH_LINUX_MACOS_ABSOLUTE = Pattern.compile("/|(/[^/]+)+?");
    private static final Pattern PATTERN_PATH_WINDOWS_RELATIVE = Pattern.compile("[^\\\\]+(\\\\[^\\\\]+)*?");
    private static final Pattern PATTERN_PATH_LINUX_MACOS_RELATIVE = Pattern.compile("[^/]+(/[^/]+)*?");

    public void ensureDirectoryExists(String relativePath) {
        Assert.isTrue(relativePath != null && relativePath.startsWith("/"), "非法请求参数");
        File directory = IOUtil.getFileSlashed(rootPath + relativePath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "非法的请求路径");
    }

    public FtsPageModel getDirectoryModel(String relativePath, int pageNo, int pageSize) {
        Assert.isTrue(null != relativePath && relativePath.startsWith("/") && pageNo > 0 && pageSize > 0 && pageSize <= 50, "非法请求参数");
        String actualPath = rootPath + relativePath;
        File directory = IOUtil.getFileSlashed(actualPath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "非法的请求路径");
        FtsPageModel model = new FtsPageModel();
        model.setPath(relativePath);
        model.setCurrentPage(pageNo);
        model.setPageSize(pageSize);
        File[] items = directory.listFiles();
        if(items == null || items.length == 0) {
            model.setCurrentSize(0);
            model.setTotalPage(0);
            model.setTotalSize(0);
            model.setFileList(null);
            return model;
        }
        int totalSize = items.length, totalPage = totalSize / pageSize + 1;
        model.setTotalSize(totalSize);
        model.setTotalPage(totalPage);
        if(pageNo > totalPage) {
            model.setCurrentSize(0);
            return model;
        }

        int actualPageSize = pageNo == totalPage ? (totalSize - pageSize * (pageNo - 1)) : pageSize;
        model.setCurrentSize(actualPageSize);
        //左开右闭区间[lIndex,rIndex)
        int lIndex = pageSize * (pageNo - 1), rIndex = lIndex + actualPageSize;
        List<FtsPageModel.FtsPageFileModel> fileModels = new ArrayList<>(actualPageSize);
        for(int i = 0; i < items.length; i++) {
            if(i >= lIndex && i < rIndex) {
                File item = items[i];
                FtsPageModel.FtsPageFileModel fileModel = model.new FtsPageFileModel();
                boolean isDirectory = item.isDirectory();
                fileModel.setDirectory(isDirectory);
                fileModel.setFileName(item.getName());
                if(isDirectory) {
                    fileModel.setFileSize(0);
                } else {
                    fileModel.setFileSize(item.length());
                }
                fileModel.setFileSizeStr(Util.fileLengthToStringNew(fileModel.getFileSize()));
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(Util.DATE_FORMAT_FILE_STANDARD);
                fileModel.setLastModified(simpleDateFormat.format(new Date(item.lastModified())));
                fileModels.add(fileModel);
            }
        }
        model.setFileList(fileModels);
        return model;
    }

    public void headDownloadFile(String filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        IOUtil.debugPrintSelectedRequestHeaders(request, LOGGER, "headDownloadFile");
        String actualFilePath = rootPath + filePath;
        File file = IOUtil.getFileSlashed(actualFilePath);
        Assert.isTrue(file.exists() && file.isFile() && file.canRead(), "非法的请求路径");
        String fileName = filePath.substring(filePath.lastIndexOf("/")+1);
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment;filename=" + UriUtils.encode(fileName, "UTF-8"));
        response.setContentType("application/octet-stream");
        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("Content-Length", String.valueOf(file.length()));
    }

    public void downloadFile(String filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        long start = System.currentTimeMillis();
        IOUtil.debugPrintSelectedRequestHeaders(request, LOGGER, "downloadFile");
        String actualFilePath = rootPath + filePath;
        File file = IOUtil.getFileSlashed(actualFilePath);
        Assert.isTrue(file.exists() && file.isFile() && file.canRead(), "非法的请求路径:" + actualFilePath);
        String fileName = filePath.substring(filePath.lastIndexOf("/")+1);
        long fileLength = file.length();
        long fileLastModified = file.lastModified();

        boolean isSendCompleteFile = false;
        Long ifRangeHeaderLong = null;
        try {
            ifRangeHeaderLong = IOUtil.getDateHeaderIgnoreCase(request, "If-Range");
            if(ifRangeHeaderLong != null) {
                isSendCompleteFile = fileLastModified / 1000 != ifRangeHeaderLong / 1000;
            }
        } catch (IllegalArgumentException e) {
            //忽略If-Range头
        }
        HttpRangeObject httpRangeObject = null;
        if(!isSendCompleteFile) {
            String rangeHeader = IOUtil.getHeaderIgnoreCase(request, "Range");
            if (rangeHeader != null) {
                try {
                    httpRangeObject = Util.resolveHttpRangeHeader(rangeHeader, fileLength);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("解析Http Range头时出错:{}", e.getMessage());
                    response.setStatus(HttpStatus.BAD_REQUEST.value());
                    return;
                } catch (InvalidRangeException e) {
                    LOGGER.warn("Range头数据不满足条件：{}", e.getMessage());
                    response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                    response.addHeader("Content-Range", "bytes */" + fileLength);
                    return;
                }
            } else {
                isSendCompleteFile = true;
            }
        }
        if(!isSendCompleteFile) {
            LOGGER.info("开始下载文件{}：【{}】[{}][{}]", httpRangeObject.isMultipleRange() ? "(多分段)" : "(分段)",
                    file.getAbsolutePath(), fileLength, httpRangeObject.getOriginalString());
        } else {
            LOGGER.info("开始下载文件：【{}】[{}]", file.getAbsolutePath(), fileLength);
        }

//        String userAgentHeader = IOUtil.getHeaderIgnoreCase(request, "user-agent");

        InputStream inputStream = null;
        OutputStream outputStream = null;
        RandomAccessFile randomAccessFile = null;
        try {
            String encodedFileName = UriUtils.encode(fileName, "UTF-8");
//            if(userAgentHeader != null && userAgentHeader.contains("MSIE")) {
//                encodedFileName = UriUtils.encode(fileName.replaceAll("：", " "), "UTF-8");
//            }
            response.reset();
            //response.setCharacterEncoding("UTF-8");
            response.addHeader("Content-Disposition", "attachment;filename=\"" + encodedFileName + "\";filename*=UTF-8''" + encodedFileName);
            response.addHeader("Accept-Ranges", "bytes");
//            response.setDateHeader("Date", new Date().getTime());
            response.setDateHeader("Last-Modified", file.lastModified());

            if(isSendCompleteFile) {
                response.setStatus(HttpStatus.OK.value());
                response.addHeader("Content-Length", "" + fileLength);
                response.setContentType("application/octet-stream");
                inputStream = new BufferedInputStream(new FileInputStream(file));
                outputStream = new BufferedOutputStream(response.getOutputStream());
                IOUtil.transfer(inputStream, outputStream);
                LOGGER.info("下载文件完成：【{}】[{}],用时{}毫秒", file.getAbsolutePath(), fileLength, (System.currentTimeMillis() - start));
            } else {
                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                if (!httpRangeObject.isMultipleRange()) {
                    response.setContentType("application/octet-stream");

                    HttpRangeObject.Range singleRange = httpRangeObject.get(0);
                    long lowerRange = singleRange.getActualLower(),
                            upperRange = singleRange.getActualUpper();
                    response.addHeader("Content-Range", "bytes " + lowerRange + "-" + upperRange + "/" + fileLength);
                    response.addHeader("Content-Length", String.valueOf(upperRange - lowerRange + 1));
                    randomAccessFile = new RandomAccessFile(file, "r");
                    outputStream = new BufferedOutputStream(response.getOutputStream());
                    IOUtil.transfer(randomAccessFile, outputStream, lowerRange, upperRange, true);

                    LOGGER.info("分段下载文件完成：【{}】[{}][{}],用时{}毫秒", file.getAbsolutePath(), fileLength,
                            httpRangeObject.getOriginalString(), (System.currentTimeMillis() - start));
                } else {
                    String boundary = UUID.randomUUID().toString();
                    response.setContentType("multipart/byteranges; boundary=" + boundary);
                    response.addHeader("Content-Length", String.valueOf(Util.
                            calcMultipleRangeResponseContentLength(httpRangeObject, fileLength, boundary)));
                    randomAccessFile = new RandomAccessFile(file, "r");
                    outputStream = new BufferedOutputStream(response.getOutputStream());
                    for(HttpRangeObject.Range range: httpRangeObject.getRangeList()) {
                        long lowerRange = range.getActualLower(), upperRange = range.getActualUpper();
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("--").append(boundary).append(CRLF)
                                .append("Content-Type: application/octet-stream").append(CRLF)
                                .append("Content-Range: bytes ").append(lowerRange).append("-").append(upperRange).append("/").append(fileLength).append(CRLF)
                                .append(CRLF);
                        outputStream.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
                        IOUtil.transfer(randomAccessFile, outputStream, lowerRange, upperRange, false);
                        outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
                    }
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("--").append(boundary).append("--").append(CRLF);
                    outputStream.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();

                    LOGGER.info("多分段下载文件完成：【{}】[{}][{}],用时{}毫秒", file.getAbsolutePath(), fileLength,
                            httpRangeObject.getOriginalString(), (System.currentTimeMillis() - start));
                }
            }
        } catch (IOException e) {
            String cause = null;
            if(e instanceof ClientAbortException) {
                cause = "远程主机关闭连接";
            }
            String rangeMessage = isSendCompleteFile ? "" : (httpRangeObject.isMultipleRange() ? "(多分段)" : "(分段)");
            if(cause != null) {
                LOGGER.error("{}下载文件【{}】时发生异常，原因：{}", rangeMessage, filePath, cause);
            } else {
                LOGGER.error("{}下载文件【{}】时发生异常", rangeMessage, filePath, e);
            }
        } finally {
            IOUtil.closeStream(inputStream);
            IOUtil.closeStream(outputStream);
            IOUtil.closeRandomAccessFile(randomAccessFile);
        }
    }

    public ReturnObject<Void> uploadFile(String dirName, MultipartFile file) {
        long start = System.currentTimeMillis();
        Assert.isTrue(dirName != null && dirName.startsWith("/") && file != null, "非法请求参数");
        File directory = IOUtil.getFileSlashed(rootPath + dirName);
        ReturnObject<Void> returnObject = new ReturnObject<>();
        if(!directory.exists()) {
            returnObject.setSuccess(false);
            returnObject.setMessage("请求路径不存在");
            return returnObject;
        }
        if(!directory.isDirectory()) {
            returnObject.setSuccess(false);
            returnObject.setMessage("请求路径不是文件夹");
            return returnObject;
        }
        String fileName = file.getOriginalFilename() == null ? "未知文件" : file.getOriginalFilename();
//        if(file.getSize() > uploadFileLimit) {
//            returnObject.setSuccess(false);
//            returnObject.setMessage("待上传的文件大小超过系统限制");
//            return returnObject;
//        }
        if(fileName.contains("\\")) {
            fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
        } else if(fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }

        File actualFile = new File(directory, fileName);
        if(actualFile.exists()) {
            returnObject.setSuccess(false);
            returnObject.setMessage("请求路径下已存在同名文件");
            return returnObject;
        }
        LOGGER.info("开始上传文件{}到路径{}", fileName, dirName);
        try {
            file.transferTo(actualFile);
            returnObject.setSuccess(true);
            returnObject.setMessage(fileName + "上传成功！");
            LOGGER.info("上传文件{}到路径{}成功!耗时{}毫秒", fileName, dirName, (System.currentTimeMillis() - start));
        } catch (IOException e) {
            LOGGER.error("上传文件{}到路径{}时出错", fileName, dirName, e);
            returnObject.setSuccess(false);
            returnObject.setMessage(e.getMessage());
        }
        return returnObject;
    }

    @PostConstruct
    public void checkAndPrintServerIpInfo() {
        checkOptionsAndPrint();
        getServerIpInfoModel();
        printServerIpInfo();
    }

    public FtsServerIpInfoModel getServerIpInfoModel() {
        if(serverIpInfoModel == null) {
            serverIpInfoModel = getServerIpInfoModelImpl();
        }
        return serverIpInfoModel;
    }

    private void checkOptionsAndPrint() {
        com.adam.localfts.webserver.Assert.isTrue(rootPath != null, "Root path is null!", LocalFtsStartupException.class);
        boolean isMatch;
        if(Util.isSystemWindows()) {
            isMatch = PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(rootPath).matches();
        } else if(Util.isSystemLinux() || Util.isSystemMacOS()) {
            isMatch = PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(rootPath).matches();
        } else {
            throw new LocalFtsStartupException("Unknown system:" + Util.getOsName());
        }
        com.adam.localfts.webserver.Assert.isTrue(isMatch, "Invalid root path:" + rootPath, LocalFtsStartupException.class);
        File file = IOUtil.getFileSlashed(rootPath);
        com.adam.localfts.webserver.Assert.isTrue(file.exists() && file.isDirectory(), "Root path\"" + rootPath + "\" does not exist or is not a directory!", LocalFtsStartupException.class);

        com.adam.localfts.webserver.Assert.isTrue(logFilePath != null, "Log file path is null!", LocalFtsStartupException.class);
        if(Util.isSystemWindows()) {
            isMatch = PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(logFilePath).matches() || PATTERN_PATH_WINDOWS_RELATIVE.matcher(logFilePath).matches();
        } else {
            //Linux or MacOS
            isMatch = PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(logFilePath).matches() || PATTERN_PATH_LINUX_MACOS_RELATIVE.matcher(logFilePath).matches();
        }
        com.adam.localfts.webserver.Assert.isTrue(isMatch, "Invalid log file path:" + logFilePath, LocalFtsStartupException.class);

        com.adam.localfts.webserver.Assert.isTrue(logLevelRoot != null, "Root log level is null!", LocalFtsStartupException.class);
        List<String> allowedLogLevelList = Arrays.asList(ALLOWED_LOG_LEVELS);
        com.adam.localfts.webserver.Assert.isTrue(allowedLogLevelList.contains(logLevelRoot), "Invalid root log level:" + logLevelRoot, LocalFtsStartupException.class);

        StringBuilder stringBuilder = new StringBuilder("[Server Options & Info]").append(System.lineSeparator())
                .append("root path=").append(rootPath).append(System.lineSeparator())
                .append("total space=").append(Util.fileLengthToStringNew(file.getTotalSpace())).append(System.lineSeparator())
                .append("usable space=").append(Util.fileLengthToStringNew(file.getUsableSpace())).append(System.lineSeparator())
                .append("free space=").append(Util.fileLengthToStringNew(file.getFreeSpace())).append(System.lineSeparator())
                .append("max file size=").append(Util.fileLengthToStringNew(maxFileSize.toBytes())).append(System.lineSeparator())
                .append("max request size=").append(Util.fileLengthToStringNew(maxRequestSize.toBytes())).append(System.lineSeparator())
                .append("log file path=").append(logFilePath).append(System.lineSeparator())
                .append("log root level=").append(logLevelRoot).append(System.lineSeparator());
        if(testLanguageSC) {
            stringBuilder.append("test language [Simplified Chinese]:").append(TestLanguageText.Simplified_Chinese.getText()).append(System.lineSeparator());
        }

        LOGGER.info(stringBuilder.toString());
    }

    private void printServerIpInfo() {
        StringBuilder stringBuilder = new StringBuilder("[Server Ip Info]").append(System.lineSeparator());
        List<String> serverIpList = new LinkedList<>();
//        for(FtsServerIpInfoModel.IpInfoItem ipInfoItem: serverIpInfoModel.getItems()) {
        for(int seq=0;seq<serverIpInfoModel.getItems().length;seq++) {
            FtsServerIpInfoModel.IpInfoItem ipInfoItem = serverIpInfoModel.getItems()[seq];
            stringBuilder.append(seq).append(". ").append(ipInfoItem.getDisplayName()).append(",").append(ipInfoItem.getName()).append(",[");
            for(int i=0;i<ipInfoItem.getAddresses().length;i++) {
                stringBuilder.append(ipInfoItem.getAddresses()[i]);
                serverIpList.add(ipInfoItem.getAddresses()[i]);
                if(i!=ipInfoItem.getAddresses().length-1) {
                    stringBuilder.append(",");
                }
            }
            stringBuilder.append("]").append(System.lineSeparator());
        }
        stringBuilder.append("[Server Port]").append(serverPort).append(System.lineSeparator());
        stringBuilder.append("[Server Context Path]").append(contextPath).append(System.lineSeparator());
        stringBuilder.append("[All Possible Root Urls]");
        List<String> urlList = serverIpList.stream().map(ip -> "http://" + ip + ":" + serverPort + contextPath).collect(Collectors.toList());
        for(int i=0;i<urlList.size();i++) {
            stringBuilder.append(urlList.get(i)).append(", ");
        }
        if(!urlList.isEmpty()) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        stringBuilder.append(System.lineSeparator());
        LOGGER.info(stringBuilder.toString());
    }

    private FtsServerIpInfoModel getServerIpInfoModelImpl() {
        FtsServerIpInfoModel model = new FtsServerIpInfoModel();
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            List<FtsServerIpInfoModel.IpInfoItem> ipInfoItemList = new LinkedList<>();
            while(networkInterfaces.hasMoreElements()) {
                FtsServerIpInfoModel.IpInfoItem ipInfoItem = null;
                List<String> ipAddressList = null;
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                int addressCount = 0;
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!(inetAddress instanceof Inet4Address) || inetAddress.isLoopbackAddress()) {
                        continue;
                    }
                    if (addressCount++ == 0) {
                        ipInfoItem = model.new IpInfoItem();
                        ipInfoItem.setDisplayName(networkInterface.getDisplayName());
                        ipInfoItem.setName(networkInterface.getName());
                        ipInfoItemList.add(ipInfoItem);
                        ipAddressList = new LinkedList<>();
                    }
                    ipAddressList.add(inetAddress.getHostAddress());
                }
                if(addressCount > 0) {
                    ipInfoItem.setAddresses(ipAddressList.toArray(new String[0]));
                }
            }
            model.setItems(ipInfoItemList.toArray(new FtsServerIpInfoModel.IpInfoItem[0]));
        } catch (SocketException e) {
            System.err.println("Error getting server ips: " + e.getMessage());
            e.printStackTrace();
            model.setItems(new FtsServerIpInfoModel.IpInfoItem[0]);
        }
        return model;
    }

}
