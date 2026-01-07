package com.adam.localfts.webserver.common.compress;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderCompressInfo {

    private String zipFileRelativePath;
    private long compressedFileSize = -1L;
    private long compressedFileLastModified = -1L;
    private long compressStartTime = -1L;
    private long compressFinishTime = -1L;

}
