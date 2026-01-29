package com.adam.localfts.webserver.config.localfts;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SearchProperties {
    private Boolean enabled;
    private SearchMode mode;
    private Boolean indexBeforeStart;
    private String indexPath;
    private Boolean useExistingIndex;
    private Boolean indexFileContent;
}
