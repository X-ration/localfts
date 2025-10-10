package com.adam;

import com.adam.localfts.webserver.Util;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class FileTest {

    @Test
    public void testFile() {
        File file = new File("D:/Users/Adam");
        Assert.assertTrue(file.exists() && file.isDirectory());
        System.out.println("所有空间：" + Util.fileLengthToStringNew(file.getTotalSpace()));
        System.out.println("可用空间：" + Util.fileLengthToStringNew(file.getUsableSpace()));
        System.out.println("空闲空间：" + Util.fileLengthToStringNew(file.getFreeSpace()));
    }

    @Test
    public void testDirectoryWithWhitespace() {
        File file = new File("D:\\Users\\Adam\\Documents\\Copy of desktop");
        Assert.assertTrue(file.exists() && file.isDirectory());
    }

    @Test
    public void test1() {
        String path = "D:\\Users\\Adam".replaceAll("\\\\", "/");
        File file = new File(path);
        Assert.assertTrue(file.exists() && file.isDirectory());
    }

}
