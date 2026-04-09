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
    private long lastModified;
    private String lastModifiedStr;
    private boolean directoryExists;

    private FolderCompressStatus compressStatus;
    /**
     * 文件夹压缩后的压缩文件相对路径
     */
    private String compressedFilePath;
    private Long compressedFileSize;
    private String compressedFileSizeStr;
    private Long compressedFileLastModified;
    private String compressedFileLastModifiedStr;
    private long compressStartTime;
    private String compressStartTimeStr;
    private long compressFinishTime;
    private String compressFinishTimeStr;
    private long compressCostTime = -1L;
    private String compressCostTimeStr;

}
