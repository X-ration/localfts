package com.adam.localfts.webserver.config.server;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZipProperties {
    private String path;
    private Boolean deleteOnExit;
    private String maxFolderSize;
    private Boolean backgroundEnabled;
}
