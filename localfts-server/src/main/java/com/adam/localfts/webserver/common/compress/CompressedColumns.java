package com.adam.localfts.webserver.common.compress;

/**
 * 定义压缩状态为已压缩时可获取的列
 */
public interface CompressedColumns {
    FolderCompressStatus getCompressStatus();
    Long getCompressedFileSize();
    Long getCompressedFileLastModified();
}
