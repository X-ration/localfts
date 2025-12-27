package com.adam.localfts.webserver.common.compress;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderCompressInfo {

    private String zipFileRelativePath;
    private long compressedFileSize;
    private long compressedFileLastModified;

}
