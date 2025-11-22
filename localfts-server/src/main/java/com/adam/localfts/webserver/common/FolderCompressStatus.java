package com.adam.localfts.webserver.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum FolderCompressStatus {
    NOT_COMPRESSED("未压缩"), COMPRESSING("压缩中"), COMPRESSED("已压缩");
    private String desc;
}
