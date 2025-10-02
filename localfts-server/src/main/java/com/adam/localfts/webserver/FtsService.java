package com.adam.localfts.webserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

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

    private FtsServerIpInfoModel serverIpInfoModel;
    private static final Logger LOGGER = LoggerFactory.getLogger(FtsService.class);

    public void ensureDirectoryExists(String relativePath) {
        Assert.isTrue(relativePath != null && relativePath.startsWith("/"), "非法请求参数");
        File directory = new File(rootPath + relativePath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "非法的请求路径");
    }

    public FtsPageModel getDirectoryModel(String relativePath, int pageNo, int pageSize) {
        Assert.isTrue(null != relativePath && relativePath.startsWith("/") && pageNo > 0 && pageSize > 0 && pageSize <= 50, "非法请求参数");
        String actualPath = rootPath + relativePath;
        File directory = new File(actualPath);
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
                fileModels.add(fileModel);
            }
        }
        model.setFileList(fileModels);
        return model;
    }

    public void downloadFile(String filePath, HttpServletResponse response) throws IOException {
        String actualFilePath = rootPath + filePath;
        File file = new File(actualFilePath);
        Assert.isTrue(file.exists() && file.isFile() && file.canRead(), "非法的请求路径");
        String fileName = filePath.substring(filePath.lastIndexOf("/")+1);
        LOGGER.info("开始下载文件：【{}】", file.getAbsolutePath());
        long start = System.currentTimeMillis();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
             OutputStream outputStream = new BufferedOutputStream(response.getOutputStream())
        ) {
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.addHeader("Content-Disposition", "attachment;filename=" + UriUtils.encode(fileName, "UTF-8"));
            response.addHeader("Content-Length", "" + file.length());
            response.setContentType("application/octet-stream");
            byte[] buffer = new byte[1024];
            int readBytes = 0;
            while((readBytes = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readBytes);
            }
            outputStream.flush();
        }
        LOGGER.info("下载文件完成：【{}】,用时{}毫秒", file.getAbsolutePath(), (System.currentTimeMillis() - start));
    }

    public ReturnObject<Void> uploadFile(String dirName, MultipartFile file) {
        Assert.isTrue(dirName != null && dirName.startsWith("/") && file != null, "非法请求参数");
        File directory = new File(rootPath + dirName);
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

        File actualFile = new File(directory, fileName);
        if(actualFile.exists()) {
            returnObject.setSuccess(false);
            returnObject.setMessage("请求路径下已存在同名文件");
            return returnObject;
        }
        LOGGER.info("开始上传文件{}到路径{}", fileName, dirName);
        long start = System.currentTimeMillis();
        try {
            file.transferTo(actualFile);
            returnObject.setSuccess(true);
            returnObject.setMessage(fileName + "上传成功！");
            long seconds = (System.currentTimeMillis() - start) / 1000;
            LOGGER.info("上传文件{}到路径{}成功!耗时{}秒", fileName, dirName, seconds);
        } catch (IOException e) {
            LOGGER.error("上传文件{}到路径{}时出错", fileName, dirName, e);
            returnObject.setSuccess(false);
            returnObject.setMessage(e.getMessage());
        }
        return returnObject;
    }

    @PostConstruct
    public void initializeAndPrintServerIpInfo() {
        getServerIpInfoModel();
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

    public FtsServerIpInfoModel getServerIpInfoModel() {
        if(serverIpInfoModel == null) {
            serverIpInfoModel = getServerIpInfoModelImpl();
        }
        return serverIpInfoModel;
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
