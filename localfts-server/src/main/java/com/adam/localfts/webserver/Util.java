package com.adam.localfts.webserver;

import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Util {

    private static final Pattern PATTERN_HTTP_HEADER_RANGE_COMMON = Pattern.compile("(-?[0-9]+)-(-?[0-9]+)?");
    private static final Pattern PATTERN_HTTP_HEADER_RANGE_LAST_N = Pattern.compile("-[0-9]+");

    public static final String CRLF = "\r\n";

    public static long calcMultipleRangeResponseContentLength(HttpRangeObject httpRangeObject, long fileLength, String boundary) {
        long total = httpRangeObject.totalRangeLength();
        StringBuilder stringBuilder = new StringBuilder();
        for(HttpRangeObject.Range range: httpRangeObject.getRangeList()) {
            long lowerRange = range.getActualLower(), upperRange = range.getActualUpper();
            stringBuilder.append("--").append(boundary).append(CRLF)
                    .append("Content-Type: application/octet-stream").append(CRLF)
                    .append("Content-Range: bytes ").append(lowerRange).append("-").append(upperRange).append("/").append(fileLength).append(CRLF)
                    .append(CRLF).append(CRLF);
        }
        stringBuilder.append("--").append(boundary).append("--").append(CRLF);
        total += stringBuilder.toString().getBytes(StandardCharsets.UTF_8).length;
        return total;
    }

    /**
     *  @param headerValue
     *  请求头Range 请求实体的一个或者多个子范围
     * 	表示头500个字节：bytes=0-499
     * 	表示第二个500字节：bytes=500-999
     * 	表示最后500个字节：bytes=-500
     * 	表示500字节以后的范围：bytes=500-
     * 	第一个和最后一个字节：bytes=0-0,-1
     *  同时指定几个范围：bytes=500-600,601-999
     */
    public static HttpRangeObject resolveHttpRangeHeader(String headerValue, long fileLength) {
        Assert.isTrue(headerValue.length() >= 6, "Range header invalid length:" + headerValue);
        String[] splits = headerValue.substring(6).split(",");
        boolean[] isLastNs = new boolean[splits.length];
        for(int i=0;i<splits.length;i++) {
            Matcher matcher = PATTERN_HTTP_HEADER_RANGE_COMMON.matcher(splits[i]);
            if(matcher.matches()) {
                Assert.isTrue(matcher.groupCount() == 2, "Range header part '" + splits[i] + "' invalid group count:" + matcher.groupCount() + "!");
                isLastNs[i] = false;
            } else {
                matcher = PATTERN_HTTP_HEADER_RANGE_LAST_N.matcher(splits[i]);
                Assert.isTrue(matcher.matches(), "Range header part '" + splits[i] + "' not match!");
                isLastNs[i] = true;
            }
        }
        HttpRangeObject httpRangeObject = new HttpRangeObject(headerValue.substring(6), fileLength);
        for(int i=0;i<splits.length;i++) {
            if(!isLastNs[i]) {
                Matcher matcher = PATTERN_HTTP_HEADER_RANGE_COMMON.matcher(splits[i]);
                boolean isMatch = matcher.matches();
                String lowerString = matcher.group(1), upperString = matcher.group(2);
                Long lower = Long.parseLong(lowerString);
                Long upper;
                if (upperString != null) {
                    upper = Long.parseLong(upperString);
                } else {
                    upper = null;
                }
                httpRangeObject.addRangeCommon(splits[i], lower, upper);
            } else {
                Long lastN = Long.parseLong(splits[i]);
                httpRangeObject.addRangeLastN(splits[i], lastN);
            }
        }
        return httpRangeObject;
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
