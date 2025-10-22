package com.adam.junit;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

public class SnakeYamlTest {

    @Test
    public void testReadYml() throws IOException{
        String filePath = "D:\\Users\\Adam\\Documents\\Coding\\localfts\\localfts-server\\src\\test\\resources\\application.yml";
        File file = new File(filePath);
        Yaml yaml = new Yaml();
        try(FileInputStream fileInputStream = new FileInputStream(file)) {
            Map<String, Object> yamlMap = yaml.load(fileInputStream);
            System.out.println(yamlMap);
        }
    }

}
