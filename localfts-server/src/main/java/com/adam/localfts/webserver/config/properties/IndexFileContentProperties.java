package com.adam.localfts.webserver.config.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class IndexFileContentProperties {
    private Boolean enabled;
    @Getter(AccessLevel.NONE)
    private String maxStringLengthMemoryStr;
    private Integer maxStringLength;
    private Boolean tryReadAllFiles;
    private String defaultEncoding;

    public String maxStringLengthMemoryStr() {
        return maxStringLengthMemoryStr;
    }
}
