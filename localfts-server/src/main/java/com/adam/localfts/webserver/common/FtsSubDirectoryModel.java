package com.adam.localfts.webserver.common;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FtsSubDirectoryModel {
    private String name;
    private String relativePath;
    private List<FtsSubDirectoryModel> subModelList;
}
