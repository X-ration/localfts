package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.Constants;
import com.adam.localfts.webserver.common.FtsServerIpInfoModel;
import com.adam.localfts.webserver.config.localfts.*;
import com.adam.localfts.webserver.exception.LocalFtsCriticalException;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.exception.LocalFtsStartupException;
import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationPid;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

@Service
@Getter
public class FtsServerConfigService implements DisposableBean {

    @Autowired
    private LocalFtsProperties localFtsProperties;
    @Value("${server.port}")
    private int serverPort;
    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;
    @Value("${spring.servlet.multipart.max-request-size}")
    private String maxRequestSize;

    private long pid;
    private RootPathInfo rootPathInfo;
    private FtsServerIpInfoModel ftsServerIpInfoModel;
    private final Stack<File> zipFolderStack = new Stack<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(FtsServerConfigService.class);

    public void checkPropertiesAndPostConstruct() {
        //check properties
        checkSystem();
        boolean checkRootPath = checkRootPath();
        if(!checkRootPath) {
            String oldRootPath = localFtsProperties.getRootPath();
            String newRootPath = changeRootPathToDefault();
            LOGGER.warn("Root path '{}' does not match rules, changed to default '{}'", oldRootPath, newRootPath);
            checkRootPath(LocalFtsStartupException.class);
        }
        checkZip(LocalFtsStartupException.class);
        if(localFtsProperties.getZip().getEnabled() != null && localFtsProperties.getZip().getEnabled()) {
            checkZipFolderPath(true, LocalFtsStartupException.class);
            checkZipMaxFolderSize(LocalFtsStartupException.class);
        }
        if(localFtsProperties.getLog() != null) {
            checkLogProperties(LocalFtsStartupException.class);
        }
        checkUploadProperties(LocalFtsStartupException.class);
        checkPseudoUnloadUaContains(LocalFtsStartupException.class);
        checkSearchProperties(LocalFtsStartupException.class);
        if(localFtsProperties.getTestLanguage() != null) {
            checkTestLanguageAndDeleteNullKeyValue(LocalFtsStartupException.class);
        }

        //post construct
        this.pid = getPid(LocalFtsStartupException.class);
        this.setPropertiesIfNull();
        if(localFtsProperties.getZip().getEnabled()) {
            this.createZipFolder(LocalFtsStartupException.class);
        }
        this.rootPathInfo = new RootPathInfo(localFtsProperties.getRootPath());
        this.ftsServerIpInfoModel = getServerIpInfoModelImpl();
    }

    public long getPid(Class<? extends RuntimeException> exClass) {
        ApplicationPid applicationPid = new ApplicationPid();
        String pidString = applicationPid.toString();
        if(pidString == null) {
            throwException(exClass, "Error getting pid:pidString is null!");
        }
        if(pidString.equals("???")) {
            throwException(exClass, "Cannot get pid!");
        }
        try {
            return Long.parseLong(pidString);
        } catch (NumberFormatException e) {
            LOGGER.error("Error parsing pidString as long:{}", pidString);
            throwException(exClass, "Error parsing pidString as long:" + pidString);
            return -1L;
        }
    }

    /**
     * 服务端启动后动态修改根路径
     * @param rootPath
     * @return
     */
    public RootPathInfo changeRootPath(String rootPath) {
        boolean checkRootPath = checkRootPath(rootPath, LocalFtsRuntimeException.class);
        if(!checkRootPath) {
            LOGGER.warn("check root path '{}' failed, keeping old config '{}'", rootPath, localFtsProperties.getRootPath());
            return this.rootPathInfo;
        }
        this.rootPathInfo.updateRootPath(rootPath);
        return this.rootPathInfo;
    }

    public LogProperties changeLogFilePath(String logFilePath) {
        boolean checkLogFilePath = checkLogFilePath(logFilePath, LocalFtsRuntimeException.class);
        if(!checkLogFilePath) {
            LOGGER.warn("check log file path '{}' failed, keeping old config '{}'", logFilePath, localFtsProperties.getLog().getFilePath());
            return this.localFtsProperties.getLog();
        }
        this.localFtsProperties.getLog().setFilePath(logFilePath);
        return this.localFtsProperties.getLog();
    }

    public LogProperties changeLogRootLevel(LogLevel rootLevel) {
        boolean checkLogRootLevel = checkLogRootLevel(rootLevel, LocalFtsRuntimeException.class);
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
                .append("[Pid]").append(pid).append(System.lineSeparator())
                .append("[Server port]").append(serverPort).append(System.lineSeparator())
                .append("[Server context path]").append(contextPath).append(System.lineSeparator())
                .append("[Max file size]").append(maxFileSize).append(System.lineSeparator())
                .append("[Max request size]").append(maxRequestSize).append(System.lineSeparator())
                .append("[Root path]").append(localFtsProperties.getRootPath()).append(System.lineSeparator())
                .append("[Total space]").append(rootPathInfo.getTotalSpace()).append(System.lineSeparator())
                .append("[Usable space]").append(rootPathInfo.getUsableSpace()).append(System.lineSeparator())
                .append("[Free space]").append(rootPathInfo.getFreeSpace()).append(System.lineSeparator());
        if(localFtsProperties.getLog() != null) {
            stringBuilder.append("[Log file path]").append(localFtsProperties.getLog().getFilePath()).append(System.lineSeparator())
                    .append("[Log root level]").append(localFtsProperties.getLog().getRootLevel()).append(System.lineSeparator());
        }
        stringBuilder.append("[Zip enabled]").append(localFtsProperties.getZip().getEnabled()).append(System.lineSeparator());
        if(localFtsProperties.getZip().getEnabled()) {
            stringBuilder.append("[Zip folder path]").append(localFtsProperties.getZip().getPath()).append(System.lineSeparator())
                    .append("[Zip max size of compressed folder]").append(localFtsProperties.getZip().getMaxFolderSize()).append(System.lineSeparator())
                    .append("[Zip folder delete on exit]").append(localFtsProperties.getZip().getDeleteOnExit()).append(System.lineSeparator())
                    .append("[Zip background enabled]").append(localFtsProperties.getZip().getBackgroundEnabled()).append(System.lineSeparator());
        }
        stringBuilder.append("[Upload directory pseudo user-agent contains]").append(localFtsProperties.getUpload().getDirectory().getPseudoUaContains()).append(System.lineSeparator());
        stringBuilder.append("[Pseudo unload user-agent contains]").append(localFtsProperties.getPseudoUnloadUaContains()).append(System.lineSeparator())
                .append("[Mkdir enabled]").append(localFtsProperties.getMkdir().getEnabled()).append(System.lineSeparator());
        stringBuilder.append("[Search enabled]").append(localFtsProperties.getSearch().getEnabled()).append(System.lineSeparator());
        if(localFtsProperties.getSearch().getEnabled()) {
            stringBuilder.append("[Advanced search enabled]").append(localFtsProperties.getSearch().getAdvancedSearchEnabled()).append(System.lineSeparator())
                    .append("[Search mode]").append(localFtsProperties.getSearch().getMode()).append(System.lineSeparator());
            if(localFtsProperties.getSearch().getMode() == SearchMode.INDEXED) {
                stringBuilder.append("[Search index path]").append(localFtsProperties.getSearch().getIndexPath()).append(System.lineSeparator())
                        .append("[Search index before start]").append(localFtsProperties.getSearch().getIndexBeforeStart()).append(System.lineSeparator())
                        .append("[Search use existing index]").append(localFtsProperties.getSearch().getUseExistingIndex()).append(System.lineSeparator())
                        .append("[Search index file content]").append(localFtsProperties.getSearch().getIndexFileContent()).append(System.lineSeparator());
            }
        }
        Map<TestLanguageText, Boolean> testLanguageMap = localFtsProperties.getTestLanguage();
        if(!CollectionUtils.isEmpty(testLanguageMap)) {
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

    private void setPropertiesIfNull() {
        setZipEnabledIfNull();
        if(localFtsProperties.getZip().getEnabled()) {
            setZipDeleteOnExistIfNull();
            setZipBackgroundEnabledIfNull();
        }
        setMkdirEnabledIfNull();
        setSearchEnabledIfNull();
        if(localFtsProperties.getSearch().getEnabled()) {
            setAdvancedSearchEnabledIfNull();
            if(localFtsProperties.getSearch().getMode() == SearchMode.INDEXED) {
                setIndexBeforeStartIfNull();
                setUseExistingIndexIfNull();
                setIndexFileContentIfNull();
            }
        }
    }

    private void setIndexFileContentIfNull() {
        Boolean indexFileContent = localFtsProperties.getSearch().getIndexFileContent();
        if(indexFileContent == null) {
            localFtsProperties.getSearch().setIndexFileContent(false);
        }
    }

    private void setUseExistingIndexIfNull() {
        Boolean useExistingIndex = localFtsProperties.getSearch().getUseExistingIndex();
        if(useExistingIndex == null) {
            localFtsProperties.getSearch().setUseExistingIndex(false);
        }
    }

    private void setIndexBeforeStartIfNull() {
        Boolean indexBeforeStart = localFtsProperties.getSearch().getIndexBeforeStart();
        if(indexBeforeStart == null) {
            localFtsProperties.getSearch().setIndexBeforeStart(false);
        }
    }

    private void setAdvancedSearchEnabledIfNull() {
        Boolean advancedSearchEnabled = localFtsProperties.getSearch().getAdvancedSearchEnabled();
        if(advancedSearchEnabled == null) {
            localFtsProperties.getSearch().setAdvancedSearchEnabled(false);
        }
    }

    private void setSearchEnabledIfNull() {
        Boolean searchEnabled = localFtsProperties.getSearch().getEnabled();
        if(searchEnabled == null) {
            localFtsProperties.getSearch().setEnabled(false);
        }
    }

    private void setZipEnabledIfNull() {
        Boolean zipEnabled = localFtsProperties.getZip().getEnabled();
        if(zipEnabled == null) {
            localFtsProperties.getZip().setEnabled(false);
        }
    }

    private void setZipDeleteOnExistIfNull() {
        Boolean zipDeleteOnExit = localFtsProperties.getZip().getDeleteOnExit();
        if(zipDeleteOnExit == null) {
            localFtsProperties.getZip().setDeleteOnExit(false);
        }
    }

    private void setZipBackgroundEnabledIfNull() {
        Boolean zipBackgroundEnabled = localFtsProperties.getZip().getBackgroundEnabled();
        if(zipBackgroundEnabled == null) {
            localFtsProperties.getZip().setBackgroundEnabled(false);
        }
    }

    private void setMkdirEnabledIfNull() {
        Boolean mkdirEnabled = localFtsProperties.getMkdir().getEnabled();
        if(mkdirEnabled == null) {
            localFtsProperties.getMkdir().setEnabled(false);
        }
    }

    private void checkSystem() {
        if(!Util.isSystemWindows() && !Util.isSystemLinux() && !Util.isSystemMacOS()) {
            throw new LocalFtsStartupException("Unknown system:" + Util.getOsName());
        }
    }

    private boolean checkTestLanguageItem(TestLanguageText testLanguageText, Boolean enabled) {
        return testLanguageText != null && enabled != null;
    }

    private boolean checkTestLanguageAndDeleteNullKeyValue(Class<? extends RuntimeException> exClass) {
        return checkTestLanguageAndDeleteNullKeyValue(localFtsProperties.getTestLanguage(), exClass);
    }

    private boolean checkTestLanguageAndDeleteNullKeyValue(Map<TestLanguageText, Boolean> testLanguageMap, Class<? extends RuntimeException> exClass) {
        if(testLanguageMap == null) {
            if(exClass != null) {
                throwException(exClass, "Test language object is null!");
            } else {
                return false;
            }
        }
        boolean hasNull = false;
        for(Map.Entry<TestLanguageText, Boolean> entry: testLanguageMap.entrySet()) {
            if(entry.getKey() == null || entry.getValue() == null) {
                hasNull = true;
                break;
            }
        }
        if(hasNull) {
            List<String> nullEntryStringList = testLanguageMap.entrySet().stream()
                    .filter(entry -> entry.getKey() == null || entry.getValue() == null)
                    .map(entry -> "[Entry]key=" + entry.getKey() + ",value=" + entry.getValue())
                    .collect(Collectors.toList());
            LOGGER.warn("Found null key or values {} in testLanguageMap, prepare to remove", nullEntryStringList);
            List<TestLanguageText> nullKeyList = testLanguageMap.entrySet().stream()
                    .filter(entry -> entry.getKey() == null || entry.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            for (TestLanguageText key : nullKeyList) {
                testLanguageMap.remove(key);
            }
        }
        return true;
    }

    private boolean checkPseudoUnloadUaContains(Class<? extends RuntimeException> exClass) {
        return checkPseudoUnloadUaContains(localFtsProperties.getPseudoUnloadUaContains(), exClass);
    }

    private boolean checkPseudoUnloadUaContains(List<String> pseudoUnloadUaContains, Class<? extends RuntimeException> exClass) {
        if(pseudoUnloadUaContains == null) {
            if(exClass != null) {
                throwException(exClass, "pseudoUnloadUaContains is null!");
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkSearchProperties(Class<? extends RuntimeException> exClass) {
        return checkSearchProperties(localFtsProperties.getSearch(), exClass);
    }

    private boolean checkSearchProperties(SearchProperties searchProperties, Class<? extends RuntimeException> exClass) {
        if(searchProperties == null) {
            if(exClass != null) {
                throwException(exClass, "Search properties object is null!");
            } else {
                return false;
            }
        }

        if(searchProperties.getEnabled() != null && searchProperties.getEnabled()) {
            if(searchProperties.getMode() == null) {
                if(exClass != null) {
                    throwException(exClass, "Search mode is null!");
                } else {
                    return false;
                }
            }

            if(searchProperties.getMode() == SearchMode.INDEXED) {
                if(searchProperties.getIndexPath() == null) {
                    if(exClass != null) {
                        throwException(exClass, "Index path is null!");
                    } else {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean checkUploadProperties(Class<? extends RuntimeException> exClass) {
        return checkUploadProperties(localFtsProperties.getUpload(), exClass);
    }

    private boolean checkUploadProperties(UploadProperties uploadProperties, Class<? extends RuntimeException> exClass) {
        if(uploadProperties == null) {
            if(exClass != null) {
                throwException(exClass, "Upload properties object is null!");
            } else {
                return false;
            }
        }

        UploadDirectoryProperties uploadDirectoryProperties = uploadProperties.getDirectory();
        boolean checkUploadDirectoryProperties = checkUploadDirectoryProperties(uploadDirectoryProperties, exClass);
        if(!checkUploadDirectoryProperties) {
            return false;
        }

        return true;
    }

    private boolean checkUploadDirectoryProperties(UploadDirectoryProperties uploadDirectoryProperties, Class<? extends RuntimeException> exClass) {
        if(uploadDirectoryProperties == null) {
            if(exClass != null) {
                throwException(exClass, "Upload directory properties object is null!");
            } else {
                return false;
            }
        }
        if(uploadDirectoryProperties.getPseudoUaContains() == null) {
            if(exClass != null) {
                throwException(exClass, "Upload directory properties object is null!");
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkLogProperties(Class<? extends RuntimeException> exClass) {
        return checkLogProperties(localFtsProperties.getLog(), exClass);
    }

    private boolean checkLogProperties(LogProperties logProperties, Class<? extends RuntimeException> exClass) {
        if(logProperties == null) {
            if(exClass != null) {
                throwException(exClass, "Log properties object is null!");
            } else {
                return false;
            }
        }

        String logFilePath = logProperties.getFilePath();
        boolean checkLogFilePath = checkLogFilePath(logFilePath, exClass);
        if(!checkLogFilePath) {
            return false;
        }

        LogLevel rootLevel = logProperties.getRootLevel();
        boolean checkLogRootLevel = checkLogRootLevel(rootLevel, exClass);
        if(!checkLogRootLevel) {
            return false;
        }

        return true;
    }

    private boolean checkLogFilePath(String logFilePath, Class<? extends RuntimeException> exClass) {
        if(logFilePath == null) {
            if(exClass != null) {
                throwException(exClass, "Log file path is null!");
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
            if(exClass != null) {
                throwException(exClass, "Log file path does not match rules:" + logFilePath);
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkLogRootLevel(LogLevel rootLevel, Class<? extends RuntimeException> exClass) {
        if(rootLevel == null) {
            if(exClass != null) {
                throwException(exClass, "Log root level is null!");
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkZipMaxFolderSize() {
        return checkZipMaxFolderSize(null);
    }

    private boolean checkZipMaxFolderSize(Class<? extends RuntimeException> exClass) {
        return checkZipMaxFolderSize(localFtsProperties.getZip().getMaxFolderSize(), exClass);
    }

    private boolean checkZipMaxFolderSize(String zipMaxFolderSize, Class<? extends RuntimeException> exClass) {
        //allow null
        if(zipMaxFolderSize == null) {
            return true;
        }
        DataSize dataSize = null;
        try {
            dataSize = DataSize.parse(zipMaxFolderSize);
        } catch (IllegalArgumentException e) {
            LOGGER.error("解析Zip max folder size配置失败:{}", e.getMessage());
            if(exClass != null) {
                throwException(exClass, "Error parsing zip max folder size '" + zipMaxFolderSize + "': " + e.getMessage());
            } else {
                return false;
            }
        }
        if(dataSize.toBytes() < 0) {
            if(exClass != null) {
                throwException(exClass, "Zip max folder size '" + dataSize.toBytes() + "' is negative!");
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkZip(Class<? extends RuntimeException> exClass) {
        return checkZip(localFtsProperties.getZip(), exClass);
    }

    private boolean checkZip(ZipProperties zipProperties, Class<? extends RuntimeException> exClass) {
        if(zipProperties == null) {
            if(exClass != null) {
                throwException(exClass, "localfts.zip not configured!");
            } else {
                return false;
            }
        }
        return true;
    }

    private boolean checkZipFolderPath(boolean allowNonExist, Class<? extends RuntimeException> exClass) {
        return checkZipFolderPath(localFtsProperties.getZip().getPath(), localFtsProperties.getRootPath(), allowNonExist,
                exClass);
    }

    private boolean checkZipFolderPath(String zipFolderPath, String rootPath, boolean allowNonExist, Class<? extends RuntimeException> exClass) {
        if(zipFolderPath == null) {
            if(exClass != null) {
                throwException(exClass, "Zip folder path is null!");
            } else {
                return false;
            }
        }

        boolean isMatch;
        if(Util.isSystemWindows()) {
            isMatch = Constants.PATTERN_PATH_WINDOWS_RELATIVE.matcher(zipFolderPath).matches();
        } else {
            isMatch = Constants.PATTERN_PATH_LINUX_MACOS_RELATIVE.matcher(zipFolderPath).matches();
        }
        if(!isMatch) {
            if(exClass != null) {
                throwException(exClass, "Zip folder path '" + zipFolderPath + "' does not match rules!");
            } else {
                return false;
            }
        }

        File rootFile = IOUtil.getFile(rootPath);
        File zipFolderPathFile = new File(rootFile, zipFolderPath);
        if(!allowNonExist && !zipFolderPathFile.exists()) {
            if(exClass != null) {
                throwException(exClass, "Zip folder path '" + zipFolderPath + "' does not exist!");
            } else {
                return false;
            }
        }
        if(zipFolderPathFile.isFile()) {
            if(exClass != null) {
                throwException(exClass, "Zip folder path '" + zipFolderPath + "' is not a directory!");
            } else {
                return false;
            }
        }

        return true;
    }

    private void createZipFolder(Class<? extends RuntimeException> exClass) {
        createZipFolder(localFtsProperties.getZip().getPath(), localFtsProperties.getRootPath(),
                localFtsProperties.getZip().getDeleteOnExit(), exClass);
    }

    private void createZipFolder(String zipFolderPath, String rootPath, boolean zipDeleteOnExit, Class<? extends RuntimeException> exClass) {
        File rootFile = IOUtil.getFile(rootPath);
        File zipFolderPathFile = new File(rootFile, zipFolderPath);
        if(!zipFolderPathFile.exists()) {
            createFolderHierarchically(zipFolderPathFile, zipFolderStack, zipDeleteOnExit, exClass);
        } else if(zipFolderPathFile.isDirectory()){
            if(zipDeleteOnExit) {
                createHintFile(zipFolderPathFile, exClass);
            }
            zipFolderStack.push(zipFolderPathFile);
        } else {
            throwException(exClass, "Zip folder path '" + zipFolderPath + "' is a file!");
        }
    }

    private void createHintFile(File directory, Class<? extends RuntimeException> exClass) {
        if(!directory.exists()) {
            throwException(exClass, "Directory '" + directory.getAbsolutePath() + "' does not exist!");
        }
        if(!directory.isDirectory()) {
            throwException(exClass, "Directory '" + directory.getAbsolutePath() + "' is not a directory!");
        }

        String fileName = Constants.FOLDER_DELETE_ON_EXIT_HINT_FILE_NAME.replaceAll("\\$\\{pid}", "" + pid) + ".txt";
        String fileContent = Constants.FOLDER_DELETE_ON_EXIT_HINT_FILE_CONTENT.replaceAll("\\$\\{pid}", "" + pid);
        try {
            boolean createFile = IOUtil.createFile(directory, fileName);
            if(!createFile) {
                throwException(exClass, "Create hint file under '" + directory.getAbsolutePath() + "' failed!");
            }
        } catch (IOException e) {
            LOGGER.error("Create hint file under '{}' failed:{}", directory.getAbsolutePath(), e.getMessage(), e);
            throwException(exClass, "Create hint file under '" + directory.getAbsolutePath() + "' failed!");
        }

        File file = new File(directory, fileName);
        try {
            IOUtil.rewriteFile(file, fileContent);
        } catch (IOException e) {
            LOGGER.error("Rewrite hint file under '{}' failed:{}", directory.getAbsolutePath(), e.getMessage(), e);
            throwException(exClass, "Rewrite hint file under '" + directory.getAbsolutePath() + "' failed!");
        }
    }

    /**
     * 先创建父文件夹，再创建子文件夹，并将File对象添加到栈中
     * @param folderFile
     * @param stack
     * @param exClass
     */
    private void createFolderHierarchically(File folderFile, Stack<File> stack, boolean zipDeleteOnExit, Class<? extends RuntimeException> exClass) {
        if(folderFile == null) {
            throwException(exClass, "folderFile is null!");
        }

        boolean folderExists = folderFile.exists();
        if(folderExists && folderFile.isFile()) {
            throwException(exClass, "folderFile[" + folderFile.getAbsolutePath() + "] is a file!");
        }

        if(!folderExists) {
            File parentFile = folderFile.getParentFile();
            if (parentFile != null) {
                boolean parentFileExists = parentFile.exists();
                if (parentFileExists && parentFile.isFile()) {
                    throwException(exClass, "parentFile[" + parentFile.getAbsolutePath() + "] is a file!");
                }
                if (!parentFileExists) {
                    createFolderHierarchically(parentFile, stack, zipDeleteOnExit, exClass);
                }
            } else {
                LOGGER.warn("parentFile[{}] is null, ignoring", folderFile.getAbsolutePath());
            }

            try {
                LOGGER.debug("Creating folder {}", folderFile.getAbsolutePath());
                boolean mkdir = folderFile.mkdir();
                if (!mkdir) {
                    if (!folderFile.exists()) {
                        throwException(exClass, "mkdir[" + folderFile.getAbsolutePath() + "] failed!");
                    } else if (folderFile.isFile()) {
                        throwException(exClass, "folderFile[" + folderFile.getAbsolutePath() + "] is a file!(when mkdir)");
                    }
                }
            } catch (SecurityException e) {
                LOGGER.error("Error creating folder {}", folderFile.getAbsolutePath(), e);
                throwException(exClass, "mkdir[" + folderFile.getAbsolutePath() + "] encountered SecurityException!");
            }

            if(zipDeleteOnExit) {
                try {
                    LOGGER.debug("Creating hint file under {}", folderFile.getAbsolutePath());
                    createHintFile(folderFile, exClass);
                } catch (SecurityException e) {
                    LOGGER.error("Error creating hint file under {}", folderFile.getAbsolutePath(), e);
                    throwException(exClass, "create hint file[" + folderFile.getAbsolutePath() + "] encountered SecurityException!");
                }
            }
        }

        stack.push(folderFile);
    }

    private void clearCreatedFolders() {
        while(!zipFolderStack.isEmpty()) {
            File file = zipFolderStack.pop();
            if(file.exists()) {
                try {
                    LOGGER.info("Deleting {}", file.getAbsolutePath());
                    IOUtil.deleteDirectory(file.getAbsolutePath(), true);
                } catch (SecurityException | IOException e) {
                    LOGGER.error("{}'s deletion encountered SecurityException | IOException", file.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * 运行时检查关键配置，检查不通过时抛出异常
     * @throws LocalFtsCriticalException
     */
    public void checkCriticalConfig() {
        checkRootPath(LocalFtsCriticalException.class);
        if(localFtsProperties.getZip().getEnabled()) {
            checkZipFolderPath(false, LocalFtsCriticalException.class);
        }
    }

    private boolean checkRootPath(Class<? extends RuntimeException> exClass) {
        return checkRootPath(localFtsProperties.getRootPath(), exClass);
    }

    private boolean checkRootPath() {
        return checkRootPath(localFtsProperties.getRootPath());
    }

    private boolean checkRootPath(String rootPath) {
        return checkRootPath(rootPath, null);
    }

        /**
         * 检查根路径配置
         * @param rootPath
         * @param exClass
         * @return true:校验通过 false:校验不通过
         */
    private boolean checkRootPath(String rootPath, Class<? extends RuntimeException> exClass) {
        if(rootPath == null) {
            if(exClass != null) {
                throwException(exClass, "Root path is null!");
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
            if(exClass != null) {
                throwException(exClass, "Root path '" + rootPath + "' does not match rules!");
            } else {
                return false;
            }
        }

        File rootPathFile = IOUtil.getFile(rootPath);
        if(!rootPathFile.exists()) {
            if(exClass != null) {
                throwException(exClass, "Root path '" + rootPath + "' does not exist!");
            } else {
                return false;
            }
        }
        if(!rootPathFile.isDirectory()) {
            if(exClass != null) {
                throwException(exClass, "Root path '" + rootPath + "' is not a directory!");
            } else {
                return false;
            }
        }
        return true;
    }

    private String changeRootPathToDefault() {
        String newRootPath = System.getProperty("user.home");
        if(StringUtils.isEmpty(newRootPath)) {
            if (Util.isSystemWindows()) {
                newRootPath = Constants.ROOT_PATH_DEFAULT_WINDOWS;
            } else {
                newRootPath = Constants.ROOT_PATH_DEFAULT_LINUX_MACOS;
            }
        }
        localFtsProperties.setRootPath(newRootPath);
        return newRootPath;
    }

    @Contract("_, _ -> fail")
    private <T extends RuntimeException> void throwException(Class<T> exClass, String message) {
        try {
            Constructor<T> constructor = exClass.getConstructor(String.class);
            throw constructor.newInstance(message);
        } catch (NoSuchMethodException e) {
            LOGGER.warn("error finding exception constructor {}: no such constructor", exClass.getName());
            throw new RuntimeException(e.getMessage());
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            LOGGER.warn("error instantiating exception {}", exClass.getName());
            throw new RuntimeException(e.getMessage());
        }
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
            LOGGER.error("Error getting server ips:{}", e.getMessage(), e);
            model.setItems(new FtsServerIpInfoModel.IpInfoItem[0]);
        }
        return model;
    }

    @Override
    public void destroy() throws Exception {
        if(getLocalFtsProperties().getZip().getEnabled()) {
            //清理压缩文件夹
            Boolean deleteOnExit = getLocalFtsProperties().getZip().getDeleteOnExit();
            if (deleteOnExit != null && deleteOnExit) {
                clearCreatedFolders();
            }
        }
    }
}
