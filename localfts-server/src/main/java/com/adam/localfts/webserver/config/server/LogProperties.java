package com.adam.localfts.webserver.config.server;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

@Getter
@Setter
@ToString
public class LogProperties {
    private String filePath;
    private LogLevel rootLevel;
}
