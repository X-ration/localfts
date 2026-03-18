package com.adam.localfts.webserver.util;

import com.adam.localfts.webserver.common.Constants;
import com.adam.localfts.webserver.common.HttpRangeObject;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.adam.localfts.webserver.common.Constants.*;

public class Util {

    public static final Logger LOGGER = LoggerFactory.getLogger(Util.class);
    private static final Map<String, Integer> METHOD_CALL_COUNTER = new ConcurrentHashMap<>();

    public static boolean isSingleArabicOrEnglish(String str) {
        if(str == null) {
            return false;
        }
        if(str.length() != 1) {
            return false;
        }
        char chr = str.charAt(0);
        chr = toLowerCase(chr);
        return (chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'z');
    }

    public static String reverseStr(String str) {
        if(str == null || str.length() == 0) {
            return str;
        }
        StringBuilder stringBuilder = new StringBuilder(str.length());
        for(int i=str.length() - 1; i >= 0; i--) {
            stringBuilder.append(str.charAt(i));
        }
        return stringBuilder.toString();
    }

    public static String toLowerCaseAndSC(String str) {
        if(str == null) {
            return null;
        }
        String lowerCasedStr = str.toLowerCase();
        String scStr = ZhConverterUtil.toSimple(lowerCasedStr);
        if(scStr == null) {
            LOGGER.warn("Failed to convert str \"{}\" to SC", lowerCasedStr);
            return lowerCasedStr;
        } else {
            return scStr;
        }
    }

    public static Character toLowerCase(Character character) {
//        ZhConverterUtil.toSimple()
        if(character == null) {
            return null;
        } else {
            return character.toString().toLowerCase().charAt(0);
        }
    }

    public static Character toSC(Character character) {
        if(character == null) {
            return null;
        } else {
            String converted = ZhConverterUtil.toSimple(character.toString());
            if(converted == null || converted.length() == 0) {
                LOGGER.warn("Failed to convert character '{}' to SC", character);
                return character;
            } else {
                return converted.charAt(0);
            }
        }
    }

    public static String escapeHtmlChars(String htmlText) {
        if(htmlText == null) {
            return null;
        }
        return htmlText.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\t", "&#9;")
                .replace(System.lineSeparator(), "<br/>");
    }

    public static synchronized void incrementAndCheckMethodCallCount(String methodName, int threshold) {
        int callCount = METHOD_CALL_COUNTER.getOrDefault(methodName, 0);
        Assert.isTrue(++callCount <= threshold, "Method " + methodName + " call count exceeds limit " + threshold + "!");
        METHOD_CALL_COUNTER.put(methodName, callCount);
    }

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

    public static int getAvailableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static int getPhysicalProcessors() {
        Process process = null;
        BufferedReader reader = null;
        int result = -1;
        StringBuilder outputStringBuilder = new StringBuilder();
        try {
            if (isSystemLinux()) {
                ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", "LANG=C lscpu");
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                Pattern pattern = Pattern.compile("^CPU(\\(s\\))?:\\s+(\\d+)$"); // 物理cpu核心数
                Pattern socketPattern = Pattern.compile("^Socket(\\(s\\))?:\\s+(\\d+)$"); // cpu插槽数
                Pattern corePerSocketPattern = Pattern.compile("^Core(\\(s\\))? per socket:\\s+(\\d+)$"); // 每插槽核心数

                int physicalCores = -1;
                int sockets = -1;
                int coresPerSocket = -1;
                while ((line = reader.readLine()) != null) {
                    outputStringBuilder.append(line).append(System.lineSeparator());

                    Matcher m = pattern.matcher(line);
                    if (m.find()) {
                        physicalCores = Integer.parseInt(m.group(2));
                    }
                    m = socketPattern.matcher(line);
                    if (m.find()) {
                        sockets = Integer.parseInt(m.group(2));
                    }
                    m = corePerSocketPattern.matcher(line);
                    if (m.find()) {
                        coresPerSocket = Integer.parseInt(m.group(2));
                    }
                }

                if (sockets != -1 && coresPerSocket != -1) {
                    result = sockets * coresPerSocket;
                }
                else if (physicalCores != -1) {
                    result = physicalCores;
                }
            } else if (isSystemWindows()) {
                ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/C", "wmic", "cpu", "get", "NumberOfCores");
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
                String line;
                int lineNum = 0;
                process.getOutputStream().close();
                while ((line = reader.readLine()) != null) {
                    outputStringBuilder.append(line);
                    line = line.trim();
                    /*if (lineNum == 1 && !line.isEmpty()) {
                        result = Integer.parseInt(line);
                        break;
                    }
                    lineNum++;*/
                    if(line.isEmpty()) {
                        continue;
                    }
                    try {
                        result = Integer.parseInt(line);
                        break;
                    } catch (NumberFormatException e) {
                    }
                }
            } else if (isSystemMacOS()) {
                ProcessBuilder processBuilder = new ProcessBuilder("sysctl", "-n", "hw.physicalcpu");
                processBuilder.redirectErrorStream(true);
                process = processBuilder.start();
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    outputStringBuilder.append(line).append(System.lineSeparator());
                    if (!line.isEmpty()) {
                        result = Integer.parseInt(line.trim());
                        break;
                    }
                }

            }
        } catch (Exception e) {
            LOGGER.warn("Cannot get physical processors, error msg:{}", e.getMessage());
            return 0;
        }
        if (result == -1) {
            LOGGER.warn("Cannot get physical processors, output:{}", outputStringBuilder);
            return 0;
        }
        //LOGGER.info("Physical processors={}", result);
        return result;
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

    @Contract("_, _ -> fail")
    public static <T extends RuntimeException> void throwException(Class<T> exClass, String message) {
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

    public static boolean checkInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    public static void clearInterruptedAndThrowException() throws InterruptedException{
        if(Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    public static String getRandomUUIDString() {
        return UUID.randomUUID().toString();
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
//        testFileLengthToStringOld();
//        testFileLengthToStringNew();
        System.out.println("Physical processors=" + getPhysicalProcessors());
        System.out.println("Logical processors=" + getAvailableProcessors());
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
