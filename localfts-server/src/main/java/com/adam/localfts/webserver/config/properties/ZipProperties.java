package com.adam.localfts.webserver.config.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZipProperties {
    private Boolean enabled;
    private String path;
    private Boolean deleteOnExit;
    private String maxFolderSize;
    private Boolean backgroundEnabled;
}
