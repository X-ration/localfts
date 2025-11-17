package com.adam.localfts.webserver.util;

import com.adam.localfts.webserver.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class IOUtil {

    private static final int BUFFER_SIZE = 1024;
    private static final String[] SELECTED_HTTP_REQUEST_HEADERS = new String[]{"range", "if-range", "user-agent"};
    private static final Logger LOGGER = LoggerFactory.getLogger(IOUtil.class);

    /**
     * 压缩文件夹为zip文件
     * @param folderPath 要压缩的文件夹绝对路径
     * @param zipFilePath 压缩后要写入的zip文件绝对路径
     * @param zipFileName 压缩文件名
     * @return 压缩文件File对象
     * @throws IOException
     */
    public static File compressFolderAsZip(final String folderPath, final String zipFilePath, final String zipFileName) throws IOException {
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
            zipDirectory(folder, folder.getName(), true, zipOutputStream, bufferedOutputStream);
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
    private static void zipDirectory(File currentFile, String parentEntryName, boolean isOuterCall, ZipOutputStream zipOutputStream, BufferedOutputStream bufferedOutputStream) throws IOException{
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
                    zipDirectory(file, entryName + file.getName(), false, zipOutputStream, bufferedOutputStream);
                }
            }
        } else {
            LOGGER.trace("zipDirectory writing file entry {}", parentEntryName);
            ZipEntry entry = new ZipEntry(parentEntryName);
            zipOutputStream.putNextEntry(entry);
            try(FileInputStream fileInputStream = new FileInputStream(currentFile);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)
            ) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                    bufferedOutputStream.write(buffer, 0, bytesRead);
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

}
