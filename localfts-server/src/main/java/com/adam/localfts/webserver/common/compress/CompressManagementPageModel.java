package com.adam.localfts.webserver.common.compress;

import com.adam.localfts.webserver.common.PageObject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class CompressManagementPageModel extends PageObject<FolderCompressData> {
    private int totalCount;
    private int notCompressedCount;
    private int compressingCount;
    private int compressedCount;

    public CompressManagementPageModel(int pageNo, int pageSize, List<FolderCompressData> allData) {
        super(pageNo, pageSize, allData);
    }
}
