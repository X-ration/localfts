package com.adam.junit;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

public class UrlEncodeTest {

    @Test
    public void testUrlEncode() {
        String encoded = UriUtils.encode("测试带空格的文件 ", StandardCharsets.UTF_8);
        Assert.assertTrue(encoded.endsWith("%20"));
    }

}
