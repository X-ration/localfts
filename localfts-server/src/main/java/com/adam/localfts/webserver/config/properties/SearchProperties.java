package com.adam.localfts.webserver.config.properties;

import com.adam.localfts.webserver.common.search.SearchMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SearchProperties {
    private Boolean enabled;
    private SearchMode mode;
    private Integer timeout;
    private Integer activeTaskThreshold;
    @Getter(AccessLevel.NONE)
    private String activeTaskThresholdStr;
    private Boolean indexBeforeStart;
    private String indexPath;
    private Boolean useExistingIndex;
    private Boolean indexFileContent;

    public String activeTaskThresholdStr() {
        return activeTaskThresholdStr;
    }
}
