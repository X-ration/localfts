package com.adam.localfts.webserver.config.server;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Map;

@ConfigurationProperties(prefix = "localfts")
@Getter
@Setter
@ToString
public class LocalFtsProperties {

    private String rootPath;
    @NestedConfigurationProperty
    private ZipProperties zip;
    @NestedConfigurationProperty
    private LogProperties log;
    @NestedConfigurationProperty
    private UploadProperties upload;
    private Map<TestLanguageText, Boolean> testLanguage;

}
