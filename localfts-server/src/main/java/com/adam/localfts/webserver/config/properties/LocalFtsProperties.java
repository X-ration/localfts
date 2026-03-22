package com.adam.localfts.webserver.config.properties;

import com.adam.localfts.webserver.config.common.TestLanguageText;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "localfts")
@Getter
@Setter
@ToString
public class LocalFtsProperties {

    private String rootPath;
    private Boolean showHidden;
    @NestedConfigurationProperty
    private ZipProperties zip;
    @NestedConfigurationProperty
    private LogProperties log;
    @NestedConfigurationProperty
    private UploadProperties upload;
    private List<String> pseudoUnloadUaContains;
    @NestedConfigurationProperty
    private MkdirProperties mkdir;
    @NestedConfigurationProperty
    private SearchProperties search;
    private Map<TestLanguageText, Boolean> testLanguage;

}
