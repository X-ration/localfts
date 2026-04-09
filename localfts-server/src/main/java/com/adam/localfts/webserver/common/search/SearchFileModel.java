package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.compress.CompressedColumns;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchFileModel implements CompressedColumns {
    private String fileName;
    private String fileContent;
    private String fileEncoding;
    private String parentRelativePath;
    private boolean isDirectory;
    private long lastModified;
    private Long fileSize;
    private FolderCompressStatus compressStatus;
    private String compressedFilePath;
    private Long compressedFileSize;
    private Long compressedFileLastModified;
}
