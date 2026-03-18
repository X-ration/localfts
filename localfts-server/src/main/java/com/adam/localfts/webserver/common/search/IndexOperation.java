package com.adam.localfts.webserver.common.search;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class IndexOperation {
    private IndexType indexType;
    private SearchFileModel searchFileModel;
}
