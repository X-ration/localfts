package com.adam.junit;

import org.junit.Test;

public class SystemTest {
    @Test
    public void testUserDir() {
        String userDir = System.getProperty("user.dir");
        System.out.println(userDir);
    }
}
