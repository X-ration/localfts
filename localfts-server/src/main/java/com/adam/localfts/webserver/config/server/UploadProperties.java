package com.adam.localfts.webserver.config.server;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
@ToString
public class UploadProperties {
    @NestedConfigurationProperty
    private UploadDirectoryProperties directory;
}
