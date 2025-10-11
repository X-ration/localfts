package com.adam;

import org.junit.Assert;
import org.junit.Test;

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
