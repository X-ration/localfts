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
    private String status;
    private String statusDesc;
    /**
     * 文件夹压缩后的压缩文件相对路径
     */
    //todo 优化属性名
    private String zipFilePath;
    private String zipFileSize;
    private String zipFileLastModified;

    private long compressedFileSize;
    private long compressedFileLastModified;

    @Override
    public FolderCompressStatus getCompressStatus() {
        return FolderCompressStatus.valueOf(status);
    }

}
