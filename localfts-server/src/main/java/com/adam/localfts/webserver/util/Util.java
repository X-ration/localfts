package com.adam.localfts.webserver.util;

import com.adam.localfts.webserver.common.Constants;
import com.adam.localfts.webserver.common.HttpRangeObject;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.adam.localfts.webserver.common.Constants.*;

public class Util {

    public static boolean isValidFileSuffix(String suffix) {
        Pattern pattern;
        if(Util.isSystemWindows()) {
            pattern = Constants.PATTERN_FILE_SUFFIX_WINDOWS;
        } else if(Util.isSystemLinux() || Util.isSystemMacOS()) {
            pattern = Constants.PATTERN_FILE_SUFFIX_LINUX_MACOS;
        } else {
            throw new LocalFtsRuntimeException("Unknown system");
        }
        return pattern.matcher(suffix).matches();
    }

    public static SimpleDateFormat getSimpleDateFormat() {
        return new SimpleDateFormat(DATE_FORMAT_FILE_STANDARD);
    }

    public static String formatCostTime(long timeMills) {
        Assert.isTrue(timeMills >= 0, "timeMills is negative!");
        if(timeMills == 0) {
            return "0秒";
        }
        StringBuilder stringBuilder = new StringBuilder();
        if(timeMills > 1000 * 3600 * 24) {
            long days = timeMills / (1000 * 3600 * 24);
            timeMills = timeMills % (1000 * 3600 * 24);
            stringBuilder.append(days).append("天");
        }
        if(timeMills > 1000 * 3600) {
            long hours = timeMills / (1000 * 3600);
            timeMills = timeMills % (1000 * 3600);
            stringBuilder.append(hours).append("小时");
        }
        if(timeMills > 1000 * 60) {
            long minutes = timeMills / (1000 * 60);
            timeMills = timeMills % (1000 * 60);;
            stringBuilder.append(minutes).append("分钟");
        }
        if(timeMills > 1000) {
            long seconds = timeMills / 1000;
            timeMills = timeMills % 1000;
            stringBuilder.append(seconds).append("秒");
        }
        if(timeMills > 0) {
            stringBuilder.append(timeMills).append("毫秒");
        }
        return stringBuilder.toString();
    }

    public static String getServerTimeFormattedString() {
        return getServerTimeFormattedString(Locale.SIMPLIFIED_CHINESE);
    }

    public static String getServerTimeFormattedString(Locale locale) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT_FILE_STANDARD);
        String timeString = simpleDateFormat.format(new Date());
        TimeZone timeZone = TimeZone.getDefault();
        return timeString + " " + timeZone.getDisplayName(locale);
    }

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
