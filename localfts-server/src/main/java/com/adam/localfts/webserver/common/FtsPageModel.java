package com.adam.localfts.webserver.common;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FtsPageModel {
    private String path;
    private int pageSize;
    private int currentPage;
    private int currentSize;
    private int totalPage;
    private int totalSize;
    private FtsPageFileModel currentPathModel;
    private List<FtsPageFileModel> fileList;

    @Getter
    @Setter
    public class FtsPageFileModel {
        private String fileName;
        private boolean isDirectory;
        private long fileSize;
        private String lastModified;
        private String fileSizeStr;
        //private boolean compressed;
        private FolderCompressStatus compressStatus;
        private String compressedPath;
    }
}
