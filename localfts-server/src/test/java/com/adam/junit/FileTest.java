package com.adam.junit;

import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class FileTest {

//    @Test
    public void testCompressFolderAsZip() throws IOException {
//        IOUtil.compressFolderAsZip("D:\\Users\\Adam\\Documents\\测试文件夹\\测试文件夹（有文件）", "D:\\Users\\Adam\\Documents\\测试文件夹", "D:\\Users\\Adam\\Documents");
        IOUtil.compressFolderAsZip("D:\\Users\\Adam\\Documents\\测试文件夹\\测试文件夹（有文件）", "D:\\Users\\Adam\\Documents\\测试文件夹", "D:\\Users\\Adam\\Documents\\");
    }

    @Test
    public void testCheckMiddlePathExistsAsFile() {
        testCheckMiddlePathExistsAsFile("D:\\Users\\Adam\\Documents", "测试文件夹（有文件）/头像", false);
        testCheckMiddlePathExistsAsFile("D:\\Users\\Adam\\Documents", "测试文件夹（有文件）/头像/IMG_0702.JPG", true);
        testCheckMiddlePathExistsAsFile("D:\\Users\\Adam\\Documents", "/测试文件夹（有文件）/头像", false);
        testCheckMiddlePathExistsAsFile("D:\\Users\\Adam\\Documents", "/测试文件夹（有文件）/头像/IMG_0702.JPG", true);
    }

    private void testCheckMiddlePathExistsAsFile(String directoryPath, String childPath, boolean result) {
        File directory = new File(directoryPath);
        Assert.assertTrue(directory.exists() && directory.isDirectory());
        boolean check = IOUtil.checkMiddlePathExistsAsFile(directory, childPath);
        System.out.println("Directory:" + directoryPath + ",childPath:" + childPath + ",result:" + check);
        Assert.assertEquals(check, result);
    }

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
