package com.adam.localfts.webserver.common;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FtsPageModel extends PageObject<FtsPageModel.FtsPageFileModel> {
    private String path;
    private FtsPageFileModel currentPathModel;

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
