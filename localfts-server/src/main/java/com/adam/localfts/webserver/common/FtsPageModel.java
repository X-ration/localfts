package com.adam.localfts.webserver.common;

import com.adam.localfts.webserver.common.compress.CompressedColumns;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FtsPageModel extends PageObject<FtsPageModel.FtsPageFileModel> {
    private String path;
    private FtsPageFileModel currentPathModel;

    @Getter
    @Setter
    public class FtsPageFileModel implements CompressedColumns {
        private String fileName;
        private boolean isDirectory;
        private long fileSize;
        private long lastModified;
        private String lastModifiedStr;
        private String fileSizeStr;
        //private boolean compressed;
        private FolderCompressStatus compressStatus;
        private String compressedPath;
        private Long compressedFileSize;
        private String compressedFileSizeStr;
        private Long compressedFileLastModified;
        private String compressedFileLastModifiedStr;
    }
}
