package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.*;
import com.adam.localfts.webserver.exception.InvalidRangeException;
import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.adam.localfts.webserver.common.Constants.CRLF;
import static com.adam.localfts.webserver.common.Constants.DATE_FORMAT_FILE_STANDARD;

@Service
public class FtsService {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;

    private FtsServerIpInfoModel serverIpInfoModel;
    private static final Logger LOGGER = LoggerFactory.getLogger(FtsService.class);

    public void ensureDirectoryExists(String relativePath) {
        Assert.isTrue(relativePath != null && relativePath.startsWith("/"), "非法请求参数");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        File directory = IOUtil.getFile(rootPath + relativePath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "非法的请求路径");
    }

    public FtsPageModel getDirectoryModel(String relativePath, int pageNo, int pageSize) {
        Assert.isTrue(null != relativePath && relativePath.startsWith("/") && pageNo > 0 && pageSize > 0 && pageSize <= 50, "非法请求参数");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualPath = rootPath + relativePath;
        File directory = IOUtil.getFile(actualPath);
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
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT_FILE_STANDARD);
                fileModel.setLastModified(simpleDateFormat.format(new Date(item.lastModified())));
                fileModels.add(fileModel);
            }
        }
        model.setFileList(fileModels);
        return model;
    }

    public void headDownloadFile(String filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        IOUtil.debugPrintSelectedRequestHeaders(request, LOGGER, "headDownloadFile");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFilePath = rootPath + filePath;
        File file = IOUtil.getFile(actualFilePath);
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
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFilePath = rootPath + filePath;
        File file = IOUtil.getFile(actualFilePath);
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
            String rangeOriginalString = isSendCompleteFile ? "" : "[" + httpRangeObject.getOriginalString() + "]";
            if(cause != null) {
                LOGGER.error("异常：{}下载文件【{}】{}时发生异常，原因：{}", rangeMessage, filePath, rangeOriginalString, cause);
            } else {
                LOGGER.error("异常：{}下载文件【{}】{}时发生异常", rangeMessage, filePath, rangeOriginalString, e);
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
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        File directory = IOUtil.getFile(rootPath + dirName);
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

}
