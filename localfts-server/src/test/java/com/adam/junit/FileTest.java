package com.adam.junit;

import com.adam.localfts.webserver.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class FileTest {

    @Test
    public void testFile() {
        File file = new File("D:\\Users\\Adam\\Documents\\Ebook\\新建 文本文档.txt");
        System.out.println("name:" + file.getName());
        System.out.println("path:" + file.getPath());
        System.out.println("parent:" + file.getParent());
    }

    @Test
    public void testFileSpace() {
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
