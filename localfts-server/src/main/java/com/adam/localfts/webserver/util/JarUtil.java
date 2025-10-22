package com.adam.localfts.webserver.util;

import com.adam.localfts.webserver.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

public class JarUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(JarUtil.class);
    private static final Pattern PATTERN_JAR_ENTRY_NAME = Pattern.compile("/?[a-zA-Z_$]{1}[a-zA-Z_$\\-.]*(/[a-zA-Z_$]{1}[a-zA-Z_$\\-.]*)*/?");
    private static final String SPRING_BOOT_JAR_APPLICATION_YML_ENTRY_NAME = "BOOT-INF/classes/application.yml";


    /**
     * 更新Jar包中的entry
     * 采用创建临时文件-读取原jar包的所有entry-写入所有entry(包括要更新的)-替换jar文件-删除临时文件
     * @param jarFilePath
     * @param jarEntryName
     * @param content
     * @param replaceJar 是否替换原文件：若为false，则先将原文件后缀重命名为.original.jar再进行移动
     * @throws IOException
     */
    public static void updateJarEntry(String jarFilePath, String jarEntryName, String content, boolean replaceJar) throws IOException {
        Assert.notNull(content, "content is null!");
        updateJarEntry(jarFilePath, jarEntryName, inputStream -> content, replaceJar);
    }

    /**
     * 更新Jar包中的entry
     * 采用创建临时文件-读取原jar包的所有entry-写入所有entry(包括要更新的)-替换jar文件-删除临时文件
     * @param jarFilePath
     * @param jarEntryName
     * @param contentFunction
     * @param replaceJar
     * @throws IOException
     */
    public static void updateJarEntry(String jarFilePath, String jarEntryName, Function<InputStream, String> contentFunction, boolean replaceJar) throws IOException {
        Assert.notNull(jarFilePath, "jar file path is null!");
        Assert.notNull(jarEntryName, "entry name is null!");
        checkJarFilePath(jarFilePath);

        boolean isMatch = PATTERN_JAR_ENTRY_NAME.matcher(jarEntryName).matches();
        Assert.isTrue(isMatch, "jar entry name does not match rules:" + jarEntryName);
        if(jarEntryName.startsWith("/")) {
            jarEntryName = jarEntryName.substring(1);
        }

        File file = new File(jarFilePath);
        Assert.isTrue(file.exists(), "file '" + jarFilePath + "' does not exist!");
        Assert.isTrue(file.isFile(), "file '" + jarFilePath + "' is not a file!");
        Assert.isTrue(file.canRead(), "file '" + jarFilePath + "' cannot read!");

        File tempFile = File.createTempFile("localfts_temp_jar", ".jar");
        tempFile.deleteOnExit();

        try(JarFile jarFile = new JarFile(file);
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            JarOutputStream jarOutputStream = new JarOutputStream(fileOutputStream)
        ) {
            Enumeration<JarEntry> jarEntryEnumeration = jarFile.entries();
            while(jarEntryEnumeration.hasMoreElements()) {
                JarEntry jarEntry = jarEntryEnumeration.nextElement();
                String entryName = jarEntry.getName();
                LOGGER.trace("reading jar entry '{}' in '{}'", entryName, jarFilePath);

                JarEntry outJarEntry = new JarEntry(entryName);
                //Spring Boot jar包要求内嵌jar包未压缩
                if(entryName.endsWith(".jar")) {
                    outJarEntry.setMethod(JarEntry.STORED);
                    outJarEntry.setSize(jarEntry.getSize());
                    outJarEntry.setCompressedSize(jarEntry.getCompressedSize());
                    outJarEntry.setCrc(jarEntry.getCrc());
                }
                jarOutputStream.putNextEntry(outJarEntry);
                if(entryName.equals(jarEntryName)) {
                    InputStream inputStream = jarFile.getInputStream(jarEntry);
                    String content = contentFunction.apply(inputStream);
                    if(content != null) {
                        LOGGER.trace("ready to write content '{}' to entry '{}', jarFilePath='{}'", content, entryName, jarFilePath);
                        jarOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
                    } else {
                        LOGGER.warn("file content is null, will not write to entry:" + entryName);
                    }
                } else {
                    try(InputStream inputStream = jarFile.getInputStream(jarEntry)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while((bytesRead = inputStream.read(buffer)) != -1) {
                            jarOutputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
                jarOutputStream.closeEntry();
            }
        }

        if(replaceJar) {
            if(!file.delete()) {
                throw new IOException("Can not delete original jar file:" + jarFilePath);
            }
        } else {
            String fileName = file.getName();
            String filePathName = file.getParent();
            int index = fileName.lastIndexOf('.');
            String newFileName = fileName.substring(0, index) + ".original.jar";
            File newFile = new File(filePathName + File.separatorChar + newFileName);
            if(newFile.exists() && !newFile.delete()) {
                throw new IOException("Cannot delete jar file:" + newFile.getAbsolutePath());
            }
            if(!file.renameTo(newFile)) {
                throw new IOException("Cannot rename original jar file:" + jarFilePath);
            }
        }

        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 更新Spring Boot jar包中的application.yml文件
     * @param jarFilePath
     * @param configMap 要写入的配置map。
     * @param replaceJar
     */
    public static void updateSpringBootJarApplicationYml(String jarFilePath, Map<String, Object> configMap, boolean replaceJar) throws IOException {
        Assert.notNull(jarFilePath, "jar file path is null!");
        Assert.notNull(configMap, "config map is null!");
        checkConfigMap(configMap, null);
        Function<InputStream, String> contentFunction = inputStream -> {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(inputStream);
            checkConfigMap(yamlMap, null);
            Map<String, Object> finalConfigMap = new HashMap<>();
            mergeConfigMap(yamlMap, finalConfigMap);
            mergeConfigMap(configMap, finalConfigMap);
            String content = yaml.dumpAsMap(finalConfigMap);
            if(content.endsWith(Constants.CRLF)) {
                content = content.substring(0, content.length() - 2);
            } else if(content.endsWith(Constants.CR) || content.endsWith(Constants.LF)) {
                content = content.substring(0, content.length() - 1);
            }
            return content;
        };
        updateJarEntry(jarFilePath, SPRING_BOOT_JAR_APPLICATION_YML_ENTRY_NAME, contentFunction, false);
    }

    private static void mergeConfigMap(Map<String, Object> sourceMap, Map<String, Object> destMap) {
        for(Map.Entry<String, Object> entry: sourceMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if(value instanceof Map) {
                Map<String, Object> valueMap = (Map<String, Object>) value;
                Object destValue = destMap.get(key);
                Map<String, Object> destSubMap;
                if(destValue != null && destValue instanceof Map) {
                    destSubMap = (Map<String, Object>) destValue;
                    mergeConfigMap(valueMap, destSubMap);
                } else {
                    destSubMap = new HashMap<>();
                    destMap.put(key, destSubMap);
                    mergeConfigMap(valueMap, destSubMap);
                    if(destValue != null) {
                        LOGGER.warn("Replacing value '{}' with map '{}', key={}", destValue, destSubMap, key);
                    }
                }
            } else {
                Object destValue = destMap.get(key);
                if(destValue != null && destValue instanceof Map) {
                    LOGGER.warn("Replacing map value '{}' with '{}', key={}", destValue, value, key);
                }
                destMap.put(key, value);
            }
        }
    }

    private static void checkConfigMap(Map<String, Object> rootConfigMap, Map<String, Object> configMap) {
        Map<String, Object> mapToIterate = configMap == null ? rootConfigMap : configMap;
        Assert.notEmpty(mapToIterate, "config map is empty, root config map='" + rootConfigMap + "'");
        for(Map.Entry<String, Object> entry: mapToIterate.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Assert.notNull(key, "config map '" + mapToIterate + "' contains null key," + ((mapToIterate == rootConfigMap) ? "and is root config map." : "root config map='" + rootConfigMap + "'"));
            Assert.notNull(value, "config map '" + mapToIterate + "' contains null value," + ((mapToIterate == rootConfigMap) ? "and is root config map." : "root config map='" + rootConfigMap + "'"));
            if(value instanceof Map) {
                try {
                    Map<String, Object> valueMap = (Map<String, Object>) value;
                    checkConfigMap(rootConfigMap, valueMap);
                } catch (ClassCastException e) {
                    throw new IllegalArgumentException("value map '" + value + "' contains invalid key/value type, root config map='" + rootConfigMap + "'");
                }
            }
        }
    }

    private static void checkJarFilePath(String jarFilePath) {
        Assert.isTrue(jarFilePath.endsWith(".jar"), "file path does not ends with .jar:" + jarFilePath);
        boolean isMatch;
        if(Util.isSystemWindows()) {
            isMatch = Constants.PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(jarFilePath).matches();
        } else if(Util.isSystemLinux() || Util.isSystemMacOS()) {
            isMatch = Constants.PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(jarFilePath).matches();
        } else {
            throw new RuntimeException("Unknown system");
        }
        Assert.isTrue(isMatch, "jar file path does not match rules:" + jarFilePath);
    }

}
