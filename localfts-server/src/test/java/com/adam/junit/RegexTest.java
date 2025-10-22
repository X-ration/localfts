package com.adam.junit;

import com.adam.localfts.webserver.common.HttpRangeObject;
import com.adam.localfts.webserver.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexTest {

    @Test
    public void checkExtractHttpRangeHeader() {
        String headerValue = "bytes=739505227-760047038";
        Pattern pattern = Pattern.compile("bytes=([0-9]+)-([0-9]+)");
        Matcher matcher = pattern.matcher(headerValue);
        Assert.assertTrue(matcher.matches());
        String lower = matcher.group(1), upper = matcher.group(2);
        System.out.println(lower + upper);
    }

    @Test
    public void checkExtractHttpRangeHeader2() {
        String headerValue = "bytes=739505227-";
        Pattern pattern = Pattern.compile("bytes=([0-9]+)-([0-9]+)?");
        Matcher matcher = pattern.matcher(headerValue);
        Assert.assertTrue(matcher.matches());
        String lower = matcher.group(1), upper = matcher.group(2);
        System.out.println(lower + upper);
    }

    /**
     *  请求头Range 请求实体的一个或者多个子范围
     * 	表示头500个字节：bytes=0-499
     * 	表示第二个500字节：bytes=500-999
     * 	表示最后500个字节：bytes=-500
     * 	表示500字节以后的范围：bytes=500-
     * 	第一个和最后一个字节：bytes=0-0,-1
     *  同时指定几个范围：bytes=500-600,601-999
     */
    @Test
    public void checkExtractHttpRangeHeader3() {
        assertExtractHttpRangeHeader("bytes=739505227-760047038", 800000000L, 739505227L, 760047038L);
        assertExtractHttpRangeHeader("bytes=739505227-760047038,0-0,0-,-10", 800000000L, 739505227L, 760047038L, 0L, 0L, 0L, null, -10L);
    }

    private void assertExtractHttpRangeHeader(String headerValue, long fileLength, Long... boundValues) {
        HttpRangeObject httpRangeObject = Util.resolveHttpRangeHeader(headerValue, fileLength);
        List<HttpRangeObject.Range> rangeList = httpRangeObject.getRangeList();
        int totalBoundSize = 0;
        for(int i=0;i<rangeList.size();i++) {
            HttpRangeObject.Range range = rangeList.get(i);
            totalBoundSize += (range.isLastN() ? 1 : 2);
        }
        Assert.assertEquals(totalBoundSize, boundValues.length);
        int j = 0;
        for(int i=0;i<rangeList.size();i++) {
            HttpRangeObject.Range range = rangeList.get(i);
            if(range.isLastN()) {
                Assert.assertEquals(range.getLastN(), boundValues[j++]);
            } else {
                Assert.assertEquals(range.getLower(), boundValues[j++]);
                Assert.assertEquals(range.getUpper(), boundValues[j++]);
            }
        }
    }

    @Test
    public void checkReplace() {
        String path = "D:\\Users\\Adam".replaceAll("\\\\", "/");
        Assert.assertEquals("D:/Users/Adam", path);
    }

    @Test
    public void checkRelativePathRegex() {
        Assert.assertTrue(checkWindowsRelativePathRegex("log"));
        Assert.assertTrue(checkWindowsRelativePathRegex("."));
        Assert.assertTrue(checkWindowsRelativePathRegex("log\\log"));
        Assert.assertTrue(checkWindowsRelativePathRegex(".\\log"));
//        Assert.assertTrue(checkWindowsRelativePathRegex("\\log"));
//        Assert.assertTrue(checkWindowsRelativePathRegex("log\\"));
        Assert.assertTrue(checkLinuxMacOSRelativePathRegex("log"));
        Assert.assertTrue(checkLinuxMacOSRelativePathRegex("."));
        Assert.assertTrue(checkLinuxMacOSRelativePathRegex("log/log"));
        Assert.assertTrue(checkLinuxMacOSRelativePathRegex("./log"));
//        Assert.assertTrue(checkLinuxMacOSRelativePathRegex("/log"));
//        Assert.assertTrue(checkLinuxMacOSRelativePathRegex("log/"));
    }

    @Test
    public void checkAbsolutePathRegex() {
        Assert.assertTrue(checkWindowsAbsolutePathRegex("D:"));
        Assert.assertTrue(checkWindowsAbsolutePathRegex("D:\\Users"));
        Assert.assertTrue(checkWindowsAbsolutePathRegex("D:\\Users\\Adam"));
//        Assert.assertTrue(checkWindowsRootPathRegex("D:\\Users\\Adam\\"));
        Assert.assertTrue(checkLinuxMacOSAbsolutePathRegex("/"));
        Assert.assertTrue(checkLinuxMacOSAbsolutePathRegex("/home"));
        Assert.assertTrue(checkLinuxMacOSAbsolutePathRegex("/home/adam"));
//        Assert.assertTrue(checkLinuxMacOSRootPathRegex("/home/adam/"));
    }

    private boolean checkWindowsRelativePathRegex(String path) {
        return checkRegex("[^\\\\]+(\\\\[^\\\\]+)*?", path);
    }

    private boolean checkLinuxMacOSRelativePathRegex(String path) {
        return checkRegex("[^/]+(/[^/]+)*?", path);
    }

    private boolean checkLinuxMacOSAbsolutePathRegex(String rootPath) {
        return checkRegex("/|(/[^/]+)+?", rootPath);
    }

    private boolean checkWindowsAbsolutePathRegex(String rootPath) {
        return checkRegex("[A-Z]:(\\\\[^\\\\]+)*?", rootPath);
    }

    private boolean checkRegex(String patternString, String string) {
        Pattern rootPathPattern = Pattern.compile(patternString);
        return rootPathPattern.matcher(string).matches();
    }

}
