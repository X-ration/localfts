package com.adam.localfts.webserver.common.sort;

import java.util.Arrays;
import java.util.List;

public enum SearchColumn {
    FILENAME,PARENT_PATH,FILE_CONTENT,TYPE,SIZE,LAST_MODIFIED,
    COMPRESS_STATUS,COMPRESS_FILE_SIZE,COMPRESS_FILE_LAST_MODIFIED;

    public static final List<SearchColumn> COMPRESS_COLUMNS_LIST = Arrays.asList(COMPRESS_STATUS, COMPRESS_FILE_SIZE, COMPRESS_FILE_LAST_MODIFIED);

}
