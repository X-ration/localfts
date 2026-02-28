package com.adam.localfts.webserver.config.properties;

import com.adam.localfts.webserver.config.common.LogLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class LogProperties {
    private String filePath;
    private LogLevel rootLevel;
}
