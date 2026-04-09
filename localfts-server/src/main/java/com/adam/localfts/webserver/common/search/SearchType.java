package com.adam.localfts.webserver.common.search;

/**
 * 搜索关键字匹配类型
 */
public enum SearchType {
    /**
     * 只匹配文件名
     */
    FILENAME_ONLY,
    /**
     * 只匹配文件内容
     */
    FILE_CONTENT_ONLY,
    /**
     * 同时匹配
     */
    BOTH
}
