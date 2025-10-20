package com.adam.localfts.webserver.config.server;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

import java.util.Map;

@ConfigurationProperties(prefix = "localfts")
@Getter
@Setter
@ToString
public class LocalFtsProperties {

    private String rootPath;
    @NestedConfigurationProperty
    private LogProperties log;
    private Map<TestLanguageText, Boolean> testLanguage;

}
