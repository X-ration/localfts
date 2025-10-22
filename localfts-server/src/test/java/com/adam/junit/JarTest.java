package com.adam.junit;

import com.adam.localfts.webserver.util.JarUtil;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JarTest {

//    @Test
    public void testJar() {
        File file = new File("/D:/Users/Adam/Documents/Coding/localfts/localfts-server/target/localfts-server-1.0.4.jar");
        Assert.assertTrue(file.exists() && file.isFile());
    }

//    @Test
    public void testUpdateJarEntry() throws IOException {
        String jarFilePath = "D:\\Users\\Adam\\Documents\\Coding\\localfts\\localfts-server\\target\\localfts-server-1.0.4.jar";
        String entryName = "/BOOT-INF/classes/application.yml";
        JarUtil.updateJarEntry(jarFilePath, entryName, "abcded", true);
    }

    /**
     * 在测试通过后应当注释掉@Test注解
     * @throws IOException
     */
//    @Test
    public void testUpdateSpringBootJarApplicationYml() throws IOException {
        String jarFilePath = "D:\\Users\\Adam\\Documents\\Coding\\localfts\\localfts-server\\temp_dir\\localfts-server-1.0.4.jar";
        Map<String, Object> configMap = new HashMap<>();
        Map<String, Object> serverMap = new HashMap<>();
        configMap.put("server", serverMap);
        serverMap.put("port", 8080);

        Map<String, Object> localftsMap = new HashMap<>();
        configMap.put("localfts", localftsMap);
        Map<String, Object> localftsTestLanguageMap = new HashMap<>();
        localftsMap.put("test_language", localftsTestLanguageMap);
        localftsTestLanguageMap.put("Simplified_Chinese", false);

        JarUtil.updateSpringBootJarApplicationYml(jarFilePath, configMap, true);
    }

}
