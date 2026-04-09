package com.adam.junit;

import com.adam.localfts.webserver.util.Util;
import org.junit.Assert;
import org.junit.Test;

public class StringTest {
    @Test
    public void testReverseString() {
        String str = "abcdef";
        str = Util.reverseStr(str);
        Assert.assertTrue(str.equals("fedcba"));
    }
    @Test
    public void testPinyin() {
        String fileName = "sanguoyanyi71.三國演义第七十一回 占对山黄忠逸待劳 据汉水赵云寡胜众.PDF";
        System.out.println(Util.convertToPinyin(fileName));
    }
    @Test
    public void testReplace() {
        String string = "_____";
        string = string.replace("__", "_");
        Assert.assertEquals(string, "___");
    }
}
