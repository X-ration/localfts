package com.adam.localfts.webserver.common;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderCompressData {
    /**
     * 文件夹相对路径
     */
    private String path;
    private String status;
    private String statusDesc;
    /**
     * 文件夹压缩后的压缩文件相对路径
     */
    private String zipFilePath;
}
