package com.adam.localfts.webserver.config.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TestLanguageText {
    Simplified_Chinese("这是一条测试信息");
    ;

    private String text;

}
