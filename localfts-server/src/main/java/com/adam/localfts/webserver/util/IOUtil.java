package com.adam.localfts.webserver.util;

import com.adam.localfts.webserver.common.Constants;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.mozilla.universalchardet.UniversalDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class IOUtil {

    private static final int BUFFER_SIZE = 1024;
    private static final String[] SELECTED_HTTP_REQUEST_HEADERS = new String[]{"range", "if-range", "user-agent"};
    private static final Logger LOGGER = LoggerFactory.getLogger(IOUtil.class);

    public static String getFileContentPlain(File file, String defaultEncoding, int maxStringLength) throws IOException {
        String charset = detectCharset(file);
        if(charset == null) {
            charset = Charset.isSupported(defaultEncoding) ? defaultEncoding : "UTF-8";
        }
        return getFileContentPlainForceEncoding(file, charset, maxStringLength);
    }

    public static String getFileContentPlainForceEncoding(File file, String encoding, int maxStringLength) throws IOException{
        try(InputStream inputStream = new FileInputStream(file);
            InputStreamReader reader = new InputStreamReader(inputStream, encoding)) {
            StringBuilder stringBuilder = new StringBuilder();
            char[] bufferArr = new char[BUFFER_SIZE];
            int len;
            while((len = reader.read(bufferArr)) != -1) {
                stringBuilder.append(bufferArr, 0, len);
                if(maxStringLength > 0 && stringBuilder.length() >= maxStringLength) {
                    return stringBuilder.substring(0, maxStringLength);
                }
            }
            return stringBuilder.toString();
        }
    }

    public static String getFileContentTika(File file, Integer maxStringLength) throws TikaException, IOException {
        Tika tika = new Tika();
        if(maxStringLength != null) {
            if (maxStringLength == -1 || maxStringLength > 0) {
                tika.setMaxStringLength(maxStringLength);
            } else {
                LOGGER.warn("Unacceptable maxStringLength:{}", maxStringLength);
            }
        }
        return tika.parseToString(file);
    }

    /**
     * 检测文件编码
     * @param file
     * @return 编码格式字符串，检测失败返回null
     */
    public static String detectCharset(File file) {
        byte[] buf = new byte[BUFFER_SIZE];
        try (InputStream in = new FileInputStream(file)) {
            UniversalDetector detector = new UniversalDetector(null);
            int nread;
            while ((nread = in.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();
            return encoding;
        } catch (Exception e) {
            LOGGER.warn("Exception occurred detecting charset of file '{}',ex.type={},ex.message={}", file.getAbsolutePath(), e.getClass().getName(), e.getMessage());
            return null;
        }
    }

    public static String getClassFileContent(File classFile) {
        StringBuilder stringBuilder = new StringBuilder();
        Map<String, String> options = new HashMap<>();
        options.put("hideutf", "false");          // 不隐藏特殊字符
        options.put("showinferrable", "true");    // 显示完整代码
        options.put("showversion", "false");       // 关闭 CFR 版本广告
        options.put("noprogress", "true");          // 关闭 Analysing type
        options.put("silent", "true");              // 关闭类加载失败警告
        CfrDriver driver = new CfrDriver.Builder()
                .withOptions(options)
                .withOutputSink(new OutputSinkFactory() {
                    @Override
                    public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                        return Collections.singletonList(SinkClass.STRING);
                    }
                    @Override
                    public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                        return t -> {
                            String str = String.valueOf(t);
                            if(str != null && !str.startsWith("Analysing type")) {
                                if(str.startsWith("/*")) {
//                                    str = str.replaceAll("/\\*.*\\*/", "");
                                    int idx = str.indexOf("*/");
                                    str = str.substring(idx + 2);
                                    if(str.startsWith("\n")) {
                                        str = str.substring(1);
                                    }
                                }
                                str = str.replace("\n", System.lineSeparator());
                                stringBuilder.append(str);
                            }
                        };
                    }
                })
                .build();
        driver.analyse(Collections.singletonList(classFile.getAbsolutePath()));
        return stringBuilder.toString();
    }

    public static void rewriteFile(File file, String content) throws IOException {
        Assert.notNull(file, "File is null!");
        Assert.notNull(content, "Content is null!");
        Assert.isTrue(file.exists(), "File '" + file.getAbsolutePath() + "' does not exist!");
        Assert.isTrue(file.isFile(), "File '" + file.getAbsolutePath() + "' is not a file!");
        try (FileWriter fileWriter = new FileWriter(file)){
            fileWriter.write(content);
            fileWriter.flush();
        } catch (IOException e) {
            LOGGER.error("Writing to file {} encountered IOException:{}", file.getAbsolutePath(), e.getMessage(), e);
            throw e;
        }
    }

    public static boolean createFile(File directory, String fileName) throws IOException {
        Assert.notNull(directory, "Directory is null!");
        Assert.notNull(fileName, "File name is null!");
        Assert.isTrue(directory.exists(), "Directory '" + directory.getAbsolutePath() + "' does not exist!");
        Assert.isTrue(directory.isDirectory(), "Directory '" + directory.getAbsolutePath() + "' is not a directory!");
        Assert.isTrue(!fileName.contains(File.separator), "File name '" + fileName + "' contains separator!");

        File file = new File(directory, fileName);
        return file.createNewFile();
    }

    /**
     * 压缩文件夹为zip文件
     * @param folderPath 要压缩的文件夹绝对路径
     * @param zipFilePath 压缩后要写入的zip文件绝对路径
     * @param zipFileName 压缩文件名
     * @return 压缩文件File对象
     * @throws IOException
     */
    public static File compressFolderAsZip(final String folderPath, final String zipFilePath, final String zipFileName) throws IOException, InterruptedException {
        LOGGER.debug("compressFolderAsZip starts,folderPath={},zipFilePath={},zipFileName={}", folderPath, zipFilePath, zipFileName);
        boolean isMatch1 = false, isMatch2 = false;
        if(Util.isSystemWindows()) {
            isMatch1 = Constants.PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(folderPath).matches();
            isMatch2 = Constants.PATTERN_PATH_WINDOWS_ABSOLUTE.matcher(zipFilePath).matches();
        } else if(Util.isSystemMacOS() || Util.isSystemLinux()) {
            isMatch1 = Constants.PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(folderPath).matches();
            isMatch2 = Constants.PATTERN_PATH_LINUX_MACOS_ABSOLUTE.matcher(zipFilePath).matches();
        }
        Assert.isTrue(isMatch1, "folderPath '" + folderPath + "' not match rules!");
        Assert.isTrue(isMatch2, "zipFilePath '" + zipFilePath + "' not match rules!");

        File folder = new File(folderPath);
        Assert.isTrue(folder.exists(), "folderPath '" + folderPath + "' does not exist!");
        Assert.isTrue(folder.isDirectory(), "folderPath '" + folderPath + "' is not a directory!");
        Assert.isTrue(folder.canRead(), "folderPath '" + folderPath + "' cannot read!");
        File zipFileFolder = new File(zipFilePath);
        boolean zipFileFolderExists = zipFileFolder.exists();
        Assert.isTrue(!zipFileFolderExists || zipFileFolder.isDirectory(), "zipFileFolder '" + zipFilePath + "' is not a directory!");
        if(!zipFileFolderExists) {
            boolean mkdirs = zipFileFolder.mkdirs();
            Assert.isTrue(mkdirs, "zipFileFolder '" + zipFilePath + "' mkdirs failed");
        }

        File zipFile = new File(zipFileFolder, zipFileName);

        try(FileOutputStream fileOutputStream = new FileOutputStream(zipFile);
            ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(zipOutputStream)
        ) {
            zipDirectory(zipFile, folder, folder.getName(), true, zipOutputStream, bufferedOutputStream);
        }

        LOGGER.debug("compressFolderAsZip ends,folderPath={},zipFilePath={},zipFileName={}", folderPath, zipFilePath, zipFileName);
        return zipFile;
    }

    public static boolean isDirectorySizeGeIterative(String directoryAbsolutePath, long limit) {
        File directory = new File(directoryAbsolutePath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "path '" + directoryAbsolutePath + "' does not refer to a existing directory!");
        long total = 0;
        Deque<File> stack = new ArrayDeque<>();
        stack.push(directory);
        while(!stack.isEmpty()) {
            File file = stack.pop();
            if(file.isFile()) {
                total += file.length();
                if(total >= limit) {
                    return true;
                }
            } else {
                File[] files = file.listFiles();
                if(files != null) {
                    for(File file1: files) {
                        if(file1.isFile()) {
                            stack.push(file1);
                        }
                    }
                    for(File file1: files) {
                        if(file1.isDirectory()) {
                            stack.push(file1);
                        }
                    }
                }
            }
        }
        return total >= limit;
    }

    public static boolean isDirectorySizeGeRecursive(String directoryAbsolutePath, long limit) {
        File directory = new File(directoryAbsolutePath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "path '" + directoryAbsolutePath + "' does not refer to a existing directory!");
        return isFileSizeGeRecursive(directory, limit, new long[1]);
    }

    private static boolean isFileSizeGeRecursive(File file, long limit, long[] total) {
        if(total[0] >= limit) {
            return true;
        }
        if(file.isFile()) {
            total[0] += file.length();
            return total[0] >= limit;
        } else {
            File[] files = file.listFiles();
            if(files != null) {
                for (File file1 : files) {
                    if (file1.isFile()) {
                        boolean result = isFileSizeGeRecursive(file1, limit, total);
                        if (result) {
                            return true;
                        }
                    }
                }
                for (File file1 : files) {
                    if (file1.isDirectory()) {
                        boolean result = isFileSizeGeRecursive(file1, limit, total);
                        if (result) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    /**
     * @param currentFile
     * @param parentEntryName
     * @param isOuterCall 是否为外层调用，当外层调用时不写入目录名称，减少一层文件夹
     * @param zipOutputStream
     * @param bufferedOutputStream
     * @throws IOException
     */
    private static void zipDirectory(File zipFile, File currentFile, String parentEntryName, boolean isOuterCall, ZipOutputStream zipOutputStream, BufferedOutputStream bufferedOutputStream) throws IOException, InterruptedException {
        Util.clearInterruptedAndThrowException();
        if(!currentFile.exists()) {
            LOGGER.warn("zipDirectory ignoring non-exist file:{}", currentFile.getAbsolutePath());
            return;
        }
        if(currentFile.isDirectory()) {
            String entryName = "";
            if(!isOuterCall) {
                entryName = parentEntryName + "/";
                LOGGER.trace("zipDirectory writing directory entry {}", entryName);
                ZipEntry entry = new ZipEntry(entryName);
                zipOutputStream.putNextEntry(entry);
                zipOutputStream.closeEntry();
            }

            File[] files = currentFile.listFiles();
            if(files != null) {
                for(File file: files) {
                    zipDirectory(zipFile, file, entryName + file.getName(), false, zipOutputStream, bufferedOutputStream);
                }
            }
        } else {
            if(equals(zipFile, currentFile)) {
                LOGGER.warn("zipDirectory ignoring self zip file entry {}", parentEntryName);
                return;
            }
            LOGGER.trace("zipDirectory writing file entry {}", parentEntryName);
            ZipEntry entry = new ZipEntry(parentEntryName);
            zipOutputStream.putNextEntry(entry);
            byte[] buffer = new byte[BUFFER_SIZE];
            try(FileInputStream fileInputStream = new FileInputStream(currentFile);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)
            ) {
                int bytesRead;
                try {
                    bytesRead = fileInputStream.read(buffer);
                    if(bytesRead != -1) {
                        Util.clearInterruptedAndThrowException();
                        bufferedOutputStream.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Pre-read file {} failed, e.type={}, e.msg={}, skipped", currentFile.getAbsolutePath(), e.getClass().getName(), e.getMessage());
                    return;
                }
                if(bytesRead != -1) {
                    while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                        Util.clearInterruptedAndThrowException();
                        bufferedOutputStream.write(buffer, 0, bytesRead);
                    }
                }
                bufferedOutputStream.flush();
            } finally {
                zipOutputStream.closeEntry();
            }
        }
    }

    /**
     * @param randomAccessFile
     * @param outputStream
     * @param startPosition inclusive
     * @param endPosition inclusive
     * @return
     * @throws IOException
     */
    public static void transfer(RandomAccessFile randomAccessFile, OutputStream outputStream, long startPosition, long endPosition, boolean flush)
            throws IOException {
        Assert.notNull(randomAccessFile, "randomAccessFile is null");
        Assert.notNull(outputStream, "outputStream is null");
        Assert.isTrue(startPosition >= 0, "invalid startPosition" + startPosition);
        Assert.isTrue(endPosition >= startPosition, "invalid endPosition:" + endPosition);
        Assert.isTrue(startPosition < randomAccessFile.length(), "startPosition(" + startPosition + ") exceed file length limit:" + randomAccessFile.length());
        long length = endPosition - startPosition + 1;

        randomAccessFile.seek(startPosition);
        byte[] buffer = new byte[BUFFER_SIZE];
        int readBytes = 0;
        long remaining = length;
        while(remaining > 0 && (readBytes = randomAccessFile.read(buffer)) != -1) {
            if(remaining >= readBytes) {
                outputStream.write(buffer, 0, readBytes);
            } else {
                outputStream.write(buffer, 0, (int) remaining);
            }
            remaining -= readBytes;
        }
        if(flush) {
            outputStream.flush();
        }
    }

    public static void transfer(InputStream inputStream, OutputStream outputStream) throws IOException{
        Assert.notNull(inputStream, "inputStream is null");
        Assert.notNull(outputStream, "outputStream is null");
        byte[] buffer = new byte[BUFFER_SIZE];
        int readBytes = 0;
        while((readBytes = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, readBytes);
        }
        outputStream.flush();
    }

    public static String getHeaderIgnoreCase(HttpServletRequest request, String headerName) {
        Assert.notNull(request, "request is null");
        Assert.notNull(headerName, "headerName is null");
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String enumHeaderName = headerNames.nextElement();
            if(headerName.equalsIgnoreCase(enumHeaderName)) {
                return request.getHeader(enumHeaderName);
            }
        }
        return null;
    }

    public static Long getDateHeaderIgnoreCase(HttpServletRequest request, String headerName) {
        Assert.notNull(request, "request is null");
        Assert.notNull(headerName, "headerName is null");
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String enumHeaderName = headerNames.nextElement();
            if(headerName.equalsIgnoreCase(enumHeaderName)) {
                return request.getDateHeader(enumHeaderName);
            }
        }
        return null;
    }

    public static void debugPrintRequestHeaders(HttpServletRequest request, Logger logger, String methodName) {
        Enumeration<String> headerNames = request.getHeaderNames();
        logger.debug("******[{}] Debug print all request headers start******", methodName);
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            logger.debug("{}: {}", headerName, headerValue);
        }
        logger.debug("******[{}] Debug print all request headers end******", methodName);
    }

    public static void debugPrintSelectedRequestHeaders(HttpServletRequest request, Logger logger, String methodName) {
        StringBuilder stringBuilder = new StringBuilder();
        for(String headerName: SELECTED_HTTP_REQUEST_HEADERS) {
            stringBuilder.append(headerName).append("=").append(getHeaderIgnoreCase(request, headerName)).append(",");
        }
        if(SELECTED_HTTP_REQUEST_HEADERS.length > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        logger.debug("******[{}] Debug print selected request headers: {}******", methodName, stringBuilder);
    }

    public static void closeRandomAccessFile(RandomAccessFile randomAccessFile) {
        if(randomAccessFile != null) {
            try {
                randomAccessFile.close();
            } catch (IOException e) {
            }
        }
    }

    public static void closeStream(InputStream inputStream) {
        if(inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
            }
        }
    }

    public static void closeStream(OutputStream outputStream) {
        if(outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
            }
        }
    }

    public static File getFile(String path) {
        return new File(path);
    }


    /**
     * 检查特定路径下的子路径中是否存在同名文件
     * @param directory
     * @param path 以/分隔的子路径
     * @return
     */
    public static boolean checkMiddlePathExistsAsFile(File directory, String path) {
        Assert.notNull(directory, "directory is null");
        Assert.notNull(path, "path is null");
        String[] paths = path.split("/");
        File middleDirectory = new File(directory, paths[0]);
        if(middleDirectory.exists() && middleDirectory.isFile()) {
            return true;
        }
        for(int i=1;i< paths.length;i++) {
            middleDirectory = new File(middleDirectory, paths[i]);
            if(middleDirectory.exists() && middleDirectory.isFile()) {
                return true;
            }
        }
        return false;
    }

    public static boolean equals(File file1, File file2) {
        if(file1 == null && file2 == null) {
            return true;
        } else if(file1 == null || file2 == null) {
            return false;
        } else {
            return file1.getAbsolutePath().equals(file2.getAbsolutePath());
        }
    }

    public static boolean isSubPath(File file1, File file2) {
        Objects.requireNonNull(file1);
        Objects.requireNonNull(file2);
        return file2.getAbsolutePath().startsWith(file1.getAbsolutePath());
    }

    public static boolean equalsOrSubPath(File file1, File file2) {
        return equals(file1, file2) || isSubPath(file1, file2);
    }

    public static void deleteDirectory(String absolutePath, final boolean ignoreFailure) throws IOException{
        File directory = new File(absolutePath);
        if(!directory.exists()) {
            LOGGER.warn("Discarding non-exist directory:{}", absolutePath);
            return;
        }
        if(directory.isFile()) {
            LOGGER.warn("Discarding file:{}", absolutePath);
        }
        boolean deletes = deleteFile(directory, ignoreFailure);
        if(!deletes) {
            LOGGER.warn("Failed to delete directory:" + absolutePath);
        }
    }

    private static boolean deleteFile(File file, final boolean ignoreFailure) throws IOException {
        if(file.isFile()) {
            boolean deletes = file.delete();
            if(!deletes) {
                LOGGER.warn("Cannot delete file:" + file.getAbsolutePath());
                if(!ignoreFailure) {
                    throw new IOException("Cannot delete file:" + file.getAbsolutePath());
                }
            }
            return deletes;
        } else {
            File[] files = file.listFiles();
            boolean hasFailure = false;
            if(files != null) {
                for (File file1 : files) {
                    boolean deletes = deleteFile(file1, ignoreFailure);
                    if (!deletes && !hasFailure) {
                        hasFailure = true;
                    }
                }
            }
            if(!hasFailure) {
                boolean deletes = file.delete();
                if(!deletes) {
                    if(!ignoreFailure) {
                        throw new IOException("Cannot delete directory itself:" + file.getAbsolutePath());
                    } else {
                        hasFailure = true;
                    }
                }
            } else {
                if(!ignoreFailure) {
                    throw new IOException("Cannot delete files in directory:" + file.getAbsolutePath());
                }
            }
            return !hasFailure;
        }
    }

}
