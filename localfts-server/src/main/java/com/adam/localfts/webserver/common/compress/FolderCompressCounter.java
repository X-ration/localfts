package com.adam.localfts.webserver.common.compress;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class FolderCompressCounter {
    private int totalCount;
    private int notCompressedCount;
    private int compressingCount;
    private int compressedCount;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void countFolder(FolderCompressStatus folderCompressStatus) {
        totalCount++;
        switch (folderCompressStatus) {
            case NOT_COMPRESSED:
                notCompressedCount++;
                break;
            case COMPRESSING:
                compressingCount++;
                break;
            case COMPRESSED:
                compressedCount++;
                break;
            default:
                logger.warn("Unknown status:{}, discarding", folderCompressStatus);
                totalCount--;
        }
    }
}
