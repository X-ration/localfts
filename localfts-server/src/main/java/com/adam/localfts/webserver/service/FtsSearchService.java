package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.sort.ListTableColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
public class FtsSearchService {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;

    public PageObject<Void> search(String keyword, int pageNo, int pageSize, ListTableColumn sortColumn, SortOrder sortOrder) {
        Assert.isTrue(!StringUtils.isEmpty(keyword), "搜索关键词为空");
        Assert.isTrue(pageNo > 0, "非法的页数：" + pageNo);
        Assert.isTrue(pageSize > 0, "非法的每页数量：" + pageSize);
        Assert.isTrue(sortColumn == null || sortOrder != null, "排序顺序为null");
        return new PageObject<>(pageNo, pageSize, null);
    }

}
