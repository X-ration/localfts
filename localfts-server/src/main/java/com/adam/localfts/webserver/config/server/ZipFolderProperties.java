package com.adam.localfts.webserver.config.server;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ZipFolderProperties {
    private String path;
    private boolean deleteOnExit;
}
