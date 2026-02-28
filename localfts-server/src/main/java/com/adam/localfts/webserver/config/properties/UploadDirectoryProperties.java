package com.adam.localfts.webserver.config.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class UploadDirectoryProperties {
    private List<String> pseudoUaContains;
}
