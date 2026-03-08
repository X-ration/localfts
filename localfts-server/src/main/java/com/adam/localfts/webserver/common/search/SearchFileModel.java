package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.compress.CompressedColumns;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * TODO 决定带Str的属性是否要保留
 */
@Getter
@Setter
public class SearchFileModel implements CompressedColumns {
    private String fileName;
    private String fileContent;
    private boolean isDirectory;
    private long fileSize;
    private long lastModified;
    private String lastModifiedStr;
    private String fileSizeStr;
    private FolderCompressStatus compressStatus;
    private String compressedPath;
    private long compressedFileSize;
    private String compressedFileSizeStr;
    private long compressedFileLastModified;
    private String compressedFileLastModifiedStr;
}
