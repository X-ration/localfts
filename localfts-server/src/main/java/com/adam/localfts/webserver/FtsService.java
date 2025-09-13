package com.adam.localfts.webserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

@Service
public class FtsService {

    @Value("${localfts.root_path}")
    private String rootPath;

    private FtsServerIpInfoModel serverIpInfoModel;

    public FtsPageModel getDirectoryModel(String relativePath, int pageNo, int pageSize) {
        Assert.isTrue(null != relativePath && relativePath.startsWith("/") && pageNo > 0 && pageSize > 0 && pageSize <= 50, "Error parameter!");
        String actualPath = rootPath + relativePath;
        File directory = new File(actualPath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "Invalid path!");
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
                fileModels.add(fileModel);
            }
        }
        model.setFileList(fileModels);
        return model;
    }

    public void downloadFile(String fileName, HttpServletResponse response) throws IOException {
        String actualFileName = rootPath + fileName;
        File file = new File(actualFileName);
        Assert.isTrue(file.exists() && file.isFile() && file.canRead(), "Invalid file!");
        System.out.println("开始传输文件：【" + file.getAbsolutePath() + "】");
        long start = System.currentTimeMillis();
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
             OutputStream outputStream = new BufferedOutputStream(response.getOutputStream())
        ) {
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName.substring(fileName.lastIndexOf("/")+1), "UTF-8"));
            response.addHeader("Content-Length", "" + file.length());
            response.setContentType("application/octet-stream");
            byte[] buffer = new byte[1024];
            int readBytes = 0;
            while((readBytes = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readBytes);
            }
            outputStream.flush();
        }
        System.out.println("传输文件完成：【" + file.getAbsolutePath() + "】,用时" + (System.currentTimeMillis() - start) + "毫秒");
    }

    @PostConstruct
    public void initializeAndPrintServerIpInfo() {
        getServerIpInfoModel();
        StringBuilder stringBuilder = new StringBuilder("Server Ip Infos").append(System.lineSeparator());
        for(FtsServerIpInfoModel.IpInfoItem ipInfoItem: serverIpInfoModel.getItems()) {
            stringBuilder.append(ipInfoItem.getDisplayName()).append(",").append(ipInfoItem.getName()).append(",[");
            for(int i=0;i<ipInfoItem.getAddresses().length;i++) {
                stringBuilder.append(ipInfoItem.getAddresses()[i]);
                if(i!=ipInfoItem.getAddresses().length-1) {
                    stringBuilder.append(",");
                }
            }
            stringBuilder.append("]").append(System.lineSeparator());
        }
        System.out.print(stringBuilder);
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
