package com.adam.localfts.webserver.common.compress;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderCompressDTO implements CompressedColumns{
    /**
     * 文件夹相对路径
     */
    private String path;
    private FolderCompressStatus compressStatus;
    /**
     * 文件夹压缩后的压缩文件相对路径
     */
    private String compressedFilePath;
    private long compressedFileSize;
    private String compressedFileSizeStr;
    private long compressedFileLastModified;
    private String compressedFileLastModifiedStr;
    private long compressStartTime;
    private String compressStartTimeStr;
    private long compressFinishTime;
    private String compressFinishTimeStr;

}
