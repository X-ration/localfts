package com.adam.localfts.webserver;

import org.slf4j.Logger;
import org.springframework.util.Assert;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.Enumeration;

public class IOUtil {

    private static final int BUFFER_SIZE = 1024;

    /**
     * @param randomAccessFile
     * @param outputStream
     * @param startPosition inclusive
     * @param endPosition inclusive
     * @return
     * @throws IOException
     */
    public static void transfer(RandomAccessFile randomAccessFile, OutputStream outputStream, long startPosition, long endPosition)
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
        outputStream.flush();
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

    public static void debugPrintRequestHeaders(HttpServletRequest request, Logger logger, String methodName) {
        Enumeration<String> headerNames = request.getHeaderNames();
        logger.debug("******[{}] Debug print request header start******", methodName);
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            logger.debug("{}: {}", headerName, headerValue);
        }
        logger.debug("******[{}] Debug print request header end******", methodName);
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

    public static File getFileSlashed(String path) {
        return new File(path.replaceAll("\\\\", "/"));
    }

}
