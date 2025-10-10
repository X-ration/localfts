package com.adam.localfts.webserver;

import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Stack;


public class Util {

    public static void debugPrintRequestHeaders(HttpServletRequest request, Logger logger) {
        Enumeration<String> headerNames = request.getHeaderNames();
        logger.debug("******Debug print request header start******");
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            logger.debug("{}: {}", headerName, headerValue);
        }
        logger.debug("******Debug print request header end******");
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

    public static String getOsName() {
        return System.getProperty("os.name");
    }

    public static boolean isSystemWindows() {
        String osName = getOsName().toLowerCase();
        return osName.startsWith("windows");
    }

    public static boolean isSystemLinux() {
        String osName = getOsName().toLowerCase();
        return osName.startsWith("linux");
    }

    public static boolean isSystemMacOS() {
        String osName = getOsName().toLowerCase();
        return osName.startsWith("mac");
    }

    /**
     * 从long型字节数转换为带单位的字符串表示
     * @param lengthInBytes
     * @return
     */
    public static String fileLengthToStringNew(long lengthInBytes) {
        if(lengthInBytes < 1024) {
            return lengthInBytes + "B";
        }
        double div = lengthInBytes;
        for(int i=1;i<=4;i++) {
            div = div / 1024;
            if(div < 1024) {
                String unit;
                switch (i) {
                    case 1:
                        unit = "KiB";
                        break;
                    case 2:
                        unit = "MiB";
                        break;
                    case 3:
                        unit = "GiB";
                        break;
                    case 4:
                        unit = "TiB";
                        break;
                    default:
                        unit = "Invalid";
                }
                return String.format("%.1f", div) + unit;
            }
        }
        return String.format("%.1f", div) + "TiB";
    }

    /**
     * 从long型字节数转换为带单位的字符串表示(旧)
     * @param lengthInBytes
     * @return
     */
    public static String fileLengthToStringOld(long lengthInBytes) {
        long div = lengthInBytes, left = 0;
        Stack<String> stringStack = new Stack<>();
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<=4;i++) {
            left = div % 1024;
            div = div / 1024;
            if(left != 0) {
                String unit;
                switch (i) {
                    case 0:
                        unit = "B";
                        break;
                    case 1:
                        unit = "KiB";
                        break;
                    case 2:
                        unit = "MiB";
                        break;
                    case 3:
                        unit = "GiB";
                        break;
                    case 4:
                        unit = "TiB";
                        break;
                    default:
                        unit = "Invalid";
                }
                stringStack.add(left + unit);
            }
            if(div == 0) {
                break;
            }
        }
        if(stringStack.isEmpty()) {
            return "0B";
        } else {
            while(true) {
                sb.append(stringStack.pop());
                if(stringStack.isEmpty()) {
                    break;
                } else {
                    sb.append(",");
                }
            }
            return sb.toString();
        }
    }

    public static void main(String[] args) {
        testFileLengthToStringOld();
        testFileLengthToStringNew();
    }
    private static void testFileLengthToStringOld() {
        System.out.println("****Test fileLengthToStringOld start****");
        System.out.println(fileLengthToStringOld(83));
        System.out.println(fileLengthToStringOld(8300));
        System.out.println(fileLengthToStringOld(7340080));
        System.out.println(fileLengthToStringOld(8300000));
        System.out.println(fileLengthToStringOld(8300000000L));
        System.out.println(fileLengthToStringOld(8300000000000L));
        System.out.println(fileLengthToStringOld(8300000000000000L));
        System.out.println(fileLengthToStringOld(8300000000000000000L));
        System.out.println("****Test fileLengthToStringOld end****");
    }
    private static void testFileLengthToStringNew() {
        System.out.println("****Test fileLengthToStringNew start****");
        System.out.println(fileLengthToStringNew(83));
        System.out.println(fileLengthToStringNew(8300));
        System.out.println(fileLengthToStringNew(7340080));
        System.out.println(fileLengthToStringNew(8300000));
        System.out.println(fileLengthToStringNew(8300000000L));
        System.out.println(fileLengthToStringNew(8300000000000L));
        System.out.println(fileLengthToStringNew(8300000000000000L));
        System.out.println(fileLengthToStringNew(8300000000000000000L));
        System.out.println("****Test fileLengthToStringNew end****");
    }

}
