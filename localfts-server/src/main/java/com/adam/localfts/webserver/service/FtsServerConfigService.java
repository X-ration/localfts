package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.Constants;
import com.adam.localfts.webserver.common.FtsServerIpInfoModel;
import com.adam.localfts.webserver.config.server.*;
import com.adam.localfts.webserver.exception.LocalFtsStartupException;
import com.adam.localfts.webserver.util.Assert;
import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Getter
public class FtsServerConfigService {

    @Autowired
    private LocalFtsProperties localFtsProperties;
    private RootPathInfo rootPathInfo;
    private FtsServerIpInfoModel ftsServerIpInfoModel;
    @Value("${server.port}")
    private Integer serverPort;
    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize maxFileSize;
    @Value("${spring.servlet.multipart.max-request-size}")
    private DataSize maxRequestSize;

    private static final Logger LOGGER = LoggerFactory.getLogger(FtsServerConfigService.class);

    public void checkPropertiesAndPostConstruct() {
        //check properties
        checkSystem();
        boolean checkRootPath = checkRootPath(localFtsProperties.getRootPath(), false);
        if(!checkRootPath) {
            String oldRootPath = localFtsProperties.getRootPath();
            String newRootPath = changeRootPathToDefault();
            LOGGER.warn("Root path '{}' does not match rules, changed to default '{}'", oldRootPath, newRootPath);
            checkRootPath = checkRootPath(localFtsProperties.getRootPath(), true);
            Assert.isTrue(checkRootPath, "Check root path failed", LocalFtsStartupException.class);
        }
        boolean checkLogProperties = checkLogProperties(localFtsProperties.getLog(), true);
        Assert.isTrue(checkLogProperties, "Check log properties failed", LocalFtsStartupException.class);
        boolean checkTestLanguage = checkTestLanguageAndDeleteNullKeyValue(localFtsProperties.getTestLanguage(), false, true);

        //post construct
        this.rootPathInfo = new RootPathInfo(localFtsProperties.getRootPath());
        this.ftsServerIpInfoModel = getServerIpInfoModelImpl();
    }

    /**
     * 服务端启动后动态修改根路径
     * @param rootPath
     * @return
     */
    public RootPathInfo changeRootPath(String rootPath) {
        boolean checkRootPath = checkRootPath(rootPath, false);
        if(!checkRootPath) {
            LOGGER.warn("check root path '{}' failed, keeping old config '{}'", rootPath, localFtsProperties.getRootPath());
            return this.rootPathInfo;
        }
        this.rootPathInfo.updateRootPath(rootPath);
        return this.rootPathInfo;
    }

    public LogProperties changeLogFilePath(String logFilePath) {
        boolean checkLogFilePath = checkLogFilePath(logFilePath, false);
        if(!checkLogFilePath) {
            LOGGER.warn("check log file path '{}' failed, keeping old config '{}'", logFilePath, localFtsProperties.getLog().getFilePath());
            return this.localFtsProperties.getLog();
        }
        this.localFtsProperties.getLog().setFilePath(logFilePath);
        return this.localFtsProperties.getLog();
    }

    public LogProperties changeLogRootLevel(LogLevel rootLevel) {
        boolean checkLogRootLevel = checkLogRootLevel(rootLevel, false);
        if(!checkLogRootLevel) {
            LOGGER.warn("check log root level '{}' failed, keeping old config '{}'", rootLevel, localFtsProperties.getLog().getRootLevel());
            return this.localFtsProperties.getLog();
        }
        this.localFtsProperties.getLog().setRootLevel(rootLevel);
        return this.localFtsProperties.getLog();
    }

    @PostConstruct
    public void checkAndPrintServerIpInfo() {
        checkPropertiesAndPostConstruct();
        LOGGER.info(toStringConsole());
    }

    public String toStringConsole() {
        StringBuilder stringBuilder = new StringBuilder("Output server info").append(System.lineSeparator())
                .append("======Server properties======").append(System.lineSeparator())
                .append("[Server port]").append(serverPort).append(System.lineSeparator())
                .append("[Server context path]").append(contextPath).append(System.lineSeparator())
                .append("[Max file size]").append(Util.fileLengthToStringNew(maxFileSize.toBytes())).append(System.lineSeparator())
                .append("[Max request size]").append(Util.fileLengthToStringNew(maxRequestSize.toBytes())).append(System.lineSeparator())
                .append("[Root path]").append(localFtsProperties.getRootPath()).append(System.lineSeparator())
                .append("[Total space]").append(rootPathInfo.getTotalSpace()).append(System.lineSeparator())
                .append("[Usable space]").append(rootPathInfo.getUsableSpace()).append(System.lineSeparator())
                .append("[Free space]").append(rootPathInfo.getFreeSpace()).append(System.lineSeparator())
                .append("[Log file path]").append(localFtsProperties.getLog().getFilePath()).append(System.lineSeparator())
                .append("[Log root level]").append(localFtsProperties.getLog().getRootLevel()).append(System.lineSeparator());
        Map<TestLanguageText, Boolean> testLanguageMap = localFtsProperties.getTestLanguage();
        if(!testLanguageMap.isEmpty()) {
            for(Map.Entry<TestLanguageText, Boolean> entry: testLanguageMap.entrySet()) {
                TestLanguageText testLanguageText = entry.getKey();
                Boolean enabled = entry.getValue();
                if(enabled) {
                    stringBuilder.append("[Test language(").append(testLanguageText.name()).append(")]").append(testLanguageText.getText()).append(System.lineSeparator());
                }
            }
        }
        stringBuilder.append(System.lineSeparator())
                .append("======Server network info======").append(System.lineSeparator())
                .append("[Network interfaces and urls]").append(System.lineSeparator());
        List<String> serverIpList = new LinkedList<>();
        for(int seq=0;seq<ftsServerIpInfoModel.getItems().length;seq++) {
            FtsServerIpInfoModel.IpInfoItem ipInfoItem = ftsServerIpInfoModel.getItems()[seq];
            stringBuilder.append(seq).append(". ").append(ipInfoItem.getDisplayName()).append(",").append(ipInfoItem.getName()).append(",[");
            for(int i=0;i<ipInfoItem.getAddresses().length;i++) {
                stringBuilder.append(ipInfoItem.getAddresses()[i]);
                serverIpList.add(ipInfoItem.getAddresses()[i]);
                if(i!=ipInfoItem.getAddresses().length-1) {
                    stringBuilder.append(",");
                }
            }
//            stringBuilder.append("]").append(System.lineSeparator());
            stringBuilder.append("],[");
            for(int i=0;i<serverIpList.size();i++) {
                String ip = serverIpList.get(i);
                StringBuilder urlStringBuilder = new StringBuilder("http://").append(ip);
                if(serverPort != 80) {
                    urlStringBuilder.append(":").append(serverPort);
                }
                urlStringBuilder.append(contextPath);
                String url = urlStringBuilder.toString();
                stringBuilder.append(url);
                if(i != serverIpList.size() - 1) {
                    stringBuilder.append(", ");
                }
            }
            stringBuilder.append("]").append(System.lineSeparator());
            serverIpList.clear();
        }

        return stringBuilder.toString();
    }

    private void checkSystem() {
        if(!Util.isSystemWindows() && !Util.isSystemLinux() && !Util.isSystemMacOS()) {
            throw new LocalFtsStartupException("Unknown system:" + Util.getOsName());
        }
    }

    private boolean checkTestLanguageAndDeleteNullKeyValue(Map<TestLanguageText, Boolean> testLanguageMap, boolean throwException, boolean deleteNullKeyValue) {
        if(testLanguageMap == null) {
            if(throwException) {
                throw new LocalFtsStartupException("Test language object is null!");
            } else {
                return false;
            }
        }
        boolean returnValue = true;
        for(Map.Entry<TestLanguageText, Boolean> entry: testLanguageMap.entrySet()) {
            if(entry.getKey() == null || entry.getValue() == null) {
                if(throwException) {
                    throw new LocalFtsStartupException("Test language entry key/value is null:" + entry.getKey() + "," + entry.getValue());
                } else {
                    returnValue = false;
                }
            }
        }
        if(deleteNullKeyValue) {
            List<TestLanguageText> nullKeyList = testLanguageMap.entrySet().stream()
                    .filter(entry -> entry.getKey() == null || entry.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            for(TestLanguageText key: nullKeyList) {
                testLanguageMap.remove(key);
            }
        }
        return returnValue;
    }

    private boolean checkLogProperties(LogProperties logProperties, boolean throwException) {
        if(logProperties == null) {
            if(throwException) {
                throw new LocalFtsStartupException("Log properties object is null!");
            } else {
                return false;
            }
        }

        String logFilePath = logProperties.getFilePath();
        boolean checkLogFilePath = checkLogFilePath(logFilePath, throwException);
        if(!checkLogFilePath) {
            return false;
        }

        LogLevel rootLevel = logProperties.getRootLevel();
        boolean checkLogRootLevel = checkLogRootLevel(rootLevel, throwException);
        if(!checkLogRootLevel) {
            return false;
        }

        return true;
    }

    private boolean checkLogFilePath(String logFilePath, boolean throwException) {
        if(logFilePath == null) {
            if(throwException) {
                throw new LocalFtsStartupException("Log file path is null!");
            } else {
                return false;
            }
        }

        boolean isMatch;
        if(Util.isSystemWindows()) {
            isMatch = Constants.PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(logFilePath).matches() || Constants.PATTERN_PATH_WINDOWS_RELATIVE.matcher(logFilePath).matches();
        } else {
            //Linux or MacOS
            isMatch = Constants.PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(logFilePath).matches() || Constants.PATTERN_PATH_LINUX_MACOS_RELATIVE.matcher(logFilePath).matches();
        }
        if(!isMatch) {
            if(throwException) {
                throw new LocalFtsStartupException("Log file path does not match rules:" + logFilePath);
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkLogRootLevel(LogLevel rootLevel, boolean throwException) {
        if(rootLevel == null) {
            if(throwException) {
                throw new LocalFtsStartupException("Log root level is null!");
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查根路径配置
     * @param throwException
     * @return true:校验通过 false:校验不通过
     */
    private boolean checkRootPath(String rootPath, boolean throwException) {
        if(rootPath == null) {
            if(throwException) {
                throw new LocalFtsStartupException("Root path is null!");
            } else {
                return false;
            }
        }

        boolean isMatch;
        if(Util.isSystemWindows()) {
            isMatch = Constants.PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(rootPath).matches();
        } else {
            isMatch = Constants.PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(rootPath).matches();
        }
        if(!isMatch) {
            if(throwException) {
                throw new LocalFtsStartupException("Root path '" + rootPath + "' does not match rules!");
            } else {
                return false;
            }
        }

        File rootPathFile = IOUtil.getFile(rootPath);
        if(!rootPathFile.exists()) {
            if(throwException) {
                throw new LocalFtsStartupException("Root path '" + rootPath + "' does not exist!");
            } else {
                return false;
            }
        }
        if(!rootPathFile.isDirectory()) {
            if(throwException) {
                throw new LocalFtsStartupException("Root path '" + rootPath + "' is not a directory!");
            } else {
                return false;
            }
        }
        return true;
    }

    private String changeRootPathToDefault() {
        String newRootPath;
        if(Util.isSystemWindows()) {
            newRootPath = Constants.ROOT_PATH_DEFAULT_WINDOWS;
        } else {
            newRootPath = Constants.ROOT_PATH_DEFAULT_LINUX_MACOS;
        }
        localFtsProperties.setRootPath(newRootPath);
        return newRootPath;
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
