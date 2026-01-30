package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.compress.CompressedColumns;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchDTO implements CompressedColumns {
    private String filename;
    private String parentRelativePath;
    private String fileContent;
    private Boolean directory;
    private long fileSize;
    private String fileSizeStr;
    private long lastModified;
    private String lastModifiedStr;
    private FolderCompressStatus compressStatus;
    private long compressedFileSize;
    private String compressedFileSizeStr;
    private long compressedFileLastModified;
    private String compressedFileLastModifiedStr;
    private String compressedFilePath;
}
