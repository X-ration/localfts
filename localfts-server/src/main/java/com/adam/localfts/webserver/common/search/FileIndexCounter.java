package com.adam.localfts.webserver.common.search;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

@Getter
@Setter
public class FileIndexCounter {
    private long totalFilesCount;
    private long totalIndexCount;
    private long fileTotalCount;
    private long fileIndexCount;
    private long directoryTotalCount;
    private long directoryIndexCount;

    public void count(File file, boolean index) {
        if(file == null) {
            return;
        }
        totalFilesCount++;
        if(index) {
            totalIndexCount++;
        }
        if(file.isFile()) {
            fileTotalCount++;
            if(index) {
                fileIndexCount++;
            }
        } else {
            directoryTotalCount++;
            if(index) {
                directoryIndexCount++;
            }
        }
    }

    @Override
    public String toString() {
        return "FileIndexCounter{" +
                "totalFilesCount=" + totalFilesCount +
                ", totalIndexCount=" + totalIndexCount +
                ", fileTotalCount=" + fileTotalCount +
                ", fileIndexCount=" + fileIndexCount +
                ", directoryTotalCount=" + directoryTotalCount +
                ", directoryIndexCount=" + directoryIndexCount +
                '}';
    }
}
