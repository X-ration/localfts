package com.adam;

import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Pattern;

public class RegexTest {

    @Test
    public void checkWindowsRootPathRegex() {
        Assert.assertTrue(checkWindowsRootPathRegex("D:"));
        Assert.assertTrue(checkWindowsRootPathRegex("D:\\Users"));
        Assert.assertTrue(checkWindowsRootPathRegex("D:\\Users\\Adam"));
//        Assert.assertTrue(checkWindowsRootPathRegex("D:\\Users\\Adam\\"));
        Assert.assertTrue(checkLinuxMacOSRootPathRegex("/"));
        Assert.assertTrue(checkLinuxMacOSRootPathRegex("/home"));
        Assert.assertTrue(checkLinuxMacOSRootPathRegex("/home/adam"));
//        Assert.assertTrue(checkLinuxMacOSRootPathRegex("/home/adam/"));
    }

    private boolean checkLinuxMacOSRootPathRegex(String rootPath) {
        return checkRegex("/|(/[^/]+)+?", rootPath);
    }

    private boolean checkWindowsRootPathRegex(String rootPath) {
        return checkRegex("[A-Z]:(\\\\[^\\\\]+)*?", rootPath);
    }

    private boolean checkRegex(String patternString, String string) {
        Pattern rootPathPattern = Pattern.compile(patternString);
        return rootPathPattern.matcher(string).matches();
    }

}
