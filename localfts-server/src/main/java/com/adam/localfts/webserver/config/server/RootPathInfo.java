package com.adam.localfts.webserver.config.server;

import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.Assert;

import java.io.File;

@Getter
public class RootPathInfo {

    private String rootPath;
    private String totalSpace;
    private String usableSpace;
    private String freeSpace;

    public RootPathInfo(String rootPath) {
        updateRootPath(rootPath);
    }

    public void updateRootPath(String rootPath) {
        File rootPathFile = IOUtil.getFile(rootPath);
        Assert.isTrue(rootPathFile.exists() && rootPathFile.isDirectory(), "Root path '" + rootPath + "' does not exist or is not a directory");
        this.rootPath = rootPath;
        this.totalSpace = Util.fileLengthToStringNew(rootPathFile.getTotalSpace());
        this.usableSpace = Util.fileLengthToStringNew(rootPathFile.getUsableSpace());
        this.freeSpace = Util.fileLengthToStringNew(rootPathFile.getFreeSpace());
    }

}
