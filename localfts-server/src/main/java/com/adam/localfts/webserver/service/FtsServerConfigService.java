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
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

@Service
@Getter
public class FtsServerConfigService {

    @Autowired
    private LocalFtsProperties localFtsProperties;
    private RootPathInfo rootPathInfo;
    private FtsServerIpInfoModel ftsServerIpInfoModel;
    @Value("${server.port}")
    private int serverPort;
    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;
    @Value("${spring.servlet.multipart.max-request-size}")
    private String maxRequestSize;

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
        boolean checkZipFolderPath = checkZipFolderPath(localFtsProperties.getZip().getPath(), localFtsProperties.getRootPath(), true);
        Assert.isTrue(checkZipFolderPath, "Check zip folder path failed", LocalFtsStartupException.class);
        boolean checkZipMaxFolderSize = checkZipMaxFolderSize(localFtsProperties.getZip().getMaxFolderSize(), true);
        Assert.isTrue(checkZipMaxFolderSize, "Check zip max folder size failed", LocalFtsStartupException.class);
        Boolean zipDeleteOnExit = localFtsProperties.getZip().getDeleteOnExit();
        if(zipDeleteOnExit == null) {
            localFtsProperties.getZip().setDeleteOnExit(true);
        }
        Boolean zipBackgroundEnabled = localFtsProperties.getZip().getBackgroundEnabled();
        if(zipBackgroundEnabled == null) {
            localFtsProperties.getZip().setBackgroundEnabled(false);
        }

        boolean checkLogProperties = checkLogProperties(localFtsProperties.getLog(), true);
        Assert.isTrue(checkLogProperties, "Check log properties failed", LocalFtsStartupException.class);
        boolean checkTestLanguage = checkTestLanguageAndDeleteNullKeyValue(localFtsProperties.getTestLanguage(), false, true);
        Assert.isTrue(checkTestLanguage, "Check test language failed", LocalFtsStartupException.class);

        //post construct
        this.createZipFolder(localFtsProperties.getZip().getPath(), true);
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

    public Map<TestLanguageText, Boolean> updateTestLanguage(TestLanguageText testLanguageText, Boolean enabled) {
        boolean checkTestLanguageEntry = checkTestLanguageItem(testLanguageText, enabled);
        if(!checkTestLanguageEntry) {
            LOGGER.warn("check test language item '{}:{}' failed", testLanguageText, enabled);
            return this.localFtsProperties.getTestLanguage();
        }
        this.localFtsProperties.getTestLanguage().put(testLanguageText, enabled);
        return this.localFtsProperties.getTestLanguage();
    }

    /**
     * TODO 需要终止进程后由其他进程修改jar包中的文件
     * 由于要求应用以jar方式启动，此方法暂不可用
     * 持久化配置更改到application.yml文件
     */
    @Deprecated
    public void persistConfigChanges() throws IOException {
        URL fileURL = FtsServerConfigService.class.getClassLoader().getResource("application.yml");
        org.springframework.util.Assert.notNull(fileURL, "fileURL is null!");
        String filePath = fileURL.getPath();
        Yaml yaml = new Yaml();
        LOGGER.debug("config file path={}", filePath);

        Map<String, Object> yamlMap;
        File file;
        boolean applicationInJar = false;
        if(filePath.contains(".jar!")) {
            applicationInJar = true;
            String filePathString = filePath;
            if(filePath.startsWith("file:")) {
                filePathString = filePath.substring(5);
            }
            int index = filePathString.indexOf('!');
            String jarFilePath = filePathString.substring(0, index);
            String pathInJar = filePathString.substring(index + 1).replaceAll("!", "");
            LOGGER.debug("jar file path={}, path in jar={}", jarFilePath, pathInJar);
            file = new File(jarFilePath);
            try (JarFile jarFile = new JarFile(file);
                 InputStream inputStream = jarFile.getInputStream(jarFile.getJarEntry(pathInJar))
            ) {
                yamlMap = yaml.load(inputStream);
            }
        } else {
            file = new File(filePath);
            org.springframework.util.Assert.isTrue(file.exists() && file.isFile() && file.canRead() && file.canWrite(), "Invalid file path:" + filePath);
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                yamlMap = yaml.load(fileInputStream);
            }
        }

        org.springframework.util.Assert.isTrue(yamlMap != null && !yamlMap.isEmpty(), "Reading empty yaml map:" + file.getAbsolutePath());
        Map<String, Object> yamlServerMap = (Map<String, Object>) yamlMap.get("server");
        yamlServerMap.put("port", serverPort);
        Map<String, Object> yamlSpringMap = (Map<String, Object>) yamlMap.get("spring");
        Map<String, Object> yamlSpringServletMap = (Map<String, Object>) yamlSpringMap.get("servlet");
        yamlSpringServletMap.put("context-path", contextPath);
        Map<String, Object> yamlSpringServletMultipartMap = (Map<String, Object>) yamlSpringServletMap.get("multipart");
        yamlSpringServletMultipartMap.put("max-file-size", maxFileSize);
        yamlSpringServletMultipartMap.put("max-request-size", maxRequestSize);
        Map<String, Object> yamlLocalftsMap = (Map<String, Object>) yamlMap.get("localfts");
        yamlLocalftsMap.put("root_path", localFtsProperties.getRootPath());
        Map<String, Object> yamlLocalftsLogMap = (Map<String, Object>) yamlLocalftsMap.get("log");
        yamlLocalftsLogMap.put("file_path", localFtsProperties.getLog().getFilePath());
        yamlLocalftsLogMap.put("root_level", localFtsProperties.getLog().getRootLevel().name());
        Map<String, Object> yamlLocalftsTestLanguageMap = (Map<String, Object>) yamlLocalftsMap.get("test_language");
        yamlLocalftsTestLanguageMap.clear();
        for(Map.Entry<TestLanguageText, Boolean> entry: localFtsProperties.getTestLanguage().entrySet()) {
            yamlLocalftsTestLanguageMap.put(entry.getKey().name(), entry.getValue());
        }

        if(applicationInJar) {
            //由于JVM限制，不能向正在运行的jar包中写入文件
        } else {
            try (FileWriter fileWriter = new FileWriter(file)) {
                fileWriter.write(yaml.dumpAsMap(yamlMap));
                fileWriter.flush();
                LOGGER.info("Successfully persisted config changes");
            } catch (IOException e) {
                LOGGER.error("persistConfigChanges error", e);
                throw e;
            }
        }
    }

    @PostConstruct
    public void checkAndPrintServerIpInfo() throws IOException {
        checkPropertiesAndPostConstruct();
        LOGGER.info(toStringConsole());
    }

    public String toStringConsole() {
        StringBuilder stringBuilder = new StringBuilder("Output server info").append(System.lineSeparator())
                .append("======Server properties======").append(System.lineSeparator())
                .append("[Server port]").append(serverPort).append(System.lineSeparator())
                .append("[Server context path]").append(contextPath).append(System.lineSeparator())
                .append("[Max file size]").append(maxFileSize).append(System.lineSeparator())
                .append("[Max request size]").append(maxRequestSize).append(System.lineSeparator())
                .append("[Root path]").append(localFtsProperties.getRootPath()).append(System.lineSeparator())
                .append("[Total space]").append(rootPathInfo.getTotalSpace()).append(System.lineSeparator())
                .append("[Usable space]").append(rootPathInfo.getUsableSpace()).append(System.lineSeparator())
                .append("[Free space]").append(rootPathInfo.getFreeSpace()).append(System.lineSeparator())
                .append("[Log file path]").append(localFtsProperties.getLog().getFilePath()).append(System.lineSeparator())
                .append("[Log root level]").append(localFtsProperties.getLog().getRootLevel()).append(System.lineSeparator())
                .append("[Zip folder path]").append(localFtsProperties.getZip().getPath()).append(System.lineSeparator())
                .append("[Zip max size of compressed folder]").append(localFtsProperties.getZip().getMaxFolderSize()).append(System.lineSeparator())
                .append("[Zip folder delete on exit]").append(localFtsProperties.getZip().getDeleteOnExit()).append(System.lineSeparator())
                .append("[Zip background enabled]").append(localFtsProperties.getZip().getBackgroundEnabled());
                ;
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

    private boolean checkTestLanguageItem(TestLanguageText testLanguageText, Boolean enabled) {
        return testLanguageText != null && enabled != null;
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

    private boolean checkZipMaxFolderSize(String zipMaxFolderSize, boolean throwException) {
        /*if(zipMaxFolderSize == null) {
            if(throwException) {
                throw new LocalFtsStartupException("Zip max folder size is null!");
            } else {
                return false;
            }
        }*/
        //allow null
        if(zipMaxFolderSize == null) {
            return true;
        }
        DataSize dataSize;
        try {
            dataSize = DataSize.parse(zipMaxFolderSize);
        } catch (IllegalArgumentException e) {
            LOGGER.error("解析Zip max folder size配置失败", e);
            if(throwException) {
                throw new LocalFtsStartupException("Error parsing zip max folder size '" + zipMaxFolderSize + "': " + e.getMessage());
            } else {
                return false;
            }
        }
        if(dataSize.toBytes() < 0) {
            if(throwException) {
                throw new LocalFtsStartupException("Zip max folder size '" + dataSize.toBytes() + "' is negative!");
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkZipFolderPath(String zipFolderPath, String rootPath, boolean throwException) {
        if(zipFolderPath == null) {
            if(throwException) {
                throw new LocalFtsStartupException("Zip folder path is null!");
            } else {
                return false;
            }
        }

        if(!zipFolderPath.startsWith(rootPath)) {
            if(throwException) {
                throw new LocalFtsStartupException("Zip folder path outside of root path!");
            } else {
                return false;
            }
        }

        boolean isMatch;
        if(Util.isSystemWindows()) {
            isMatch = Constants.PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(zipFolderPath).matches();
        } else {
            isMatch = Constants.PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(zipFolderPath).matches();
        }
        if(!isMatch) {
            if(throwException) {
                throw new LocalFtsStartupException("Zip folder path '" + zipFolderPath + "' does not match rules!");
            } else {
                return false;
            }
        }

        File zipFolderPathFile = IOUtil.getFile(zipFolderPath);
        if(zipFolderPathFile.exists() && zipFolderPathFile.isFile()) {
            if(throwException) {
                throw new LocalFtsStartupException("Zip folder path '" + zipFolderPath + "' is not a directory!");
            } else {
                return false;
            }
        }

        return true;

    }

    private void createZipFolder(String zipFolderPath, boolean throwException) {
        File zipFolderPathFile = IOUtil.getFile(zipFolderPath);
        if(!zipFolderPathFile.exists()) {
            boolean mkdirs = zipFolderPathFile.mkdirs();
            if(!mkdirs) {
                if(throwException) {
                    throw new LocalFtsStartupException("Create zip folder path '" + zipFolderPath + "' failed!");
                }
            }
        }
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
