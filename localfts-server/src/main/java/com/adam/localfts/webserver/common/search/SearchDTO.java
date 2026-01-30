package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchDTO {
    private String filename;
    private String fileContent;
    private Boolean directory;
    private long size;
    private String sizeStr;
    private long lastModified;
    private String lastModifiedStr;
    private FolderCompressStatus folderCompressStatus;
    private long compressedFileSize;
    private String compressedFileSizeStr;
    private long compressedFileLastModified;
    private String compressedFileLastModifiedStr;
}
