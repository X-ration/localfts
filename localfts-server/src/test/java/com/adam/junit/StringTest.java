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
}
