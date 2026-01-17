package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.search.AdvancedSearchCondition;
import com.adam.localfts.webserver.common.sort.ListTableColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import com.adam.localfts.webserver.component.WebServerStartListener;
import com.adam.localfts.webserver.config.localfts.SearchMode;
import com.adam.localfts.webserver.config.localfts.SearchProperties;
import com.adam.localfts.webserver.task.LuceneIndexThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

@Service
@DependsOn("ftsServerConfigService")
public class FtsSearchService {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Autowired
    private WebServerStartListener webServerStartListener;
    private final Logger logger = LoggerFactory.getLogger(FtsSearchService.class);

    /**
     * 核心搜索方法
     * @param keyword
     * @param pageNo
     * @param pageSize
     * @param sortColumn
     * @param sortOrder
     * @return
     */
    public PageObject<Void> search(String keyword, AdvancedSearchCondition advancedSearchCondition,
                                   int pageNo, int pageSize, ListTableColumn sortColumn, SortOrder sortOrder) {
        Assert.isTrue(!StringUtils.isEmpty(keyword), "搜索关键词为空");
        Assert.isTrue(pageNo > 0, "非法的页数：" + pageNo);
        Assert.isTrue(pageSize > 0, "非法的每页数量：" + pageSize);
        Assert.isTrue(sortColumn == null || sortOrder != null, "排序顺序为null");
        return new PageObject<>(pageNo, pageSize, null);
    }

    /**
     * 创建索引
     */
    private void createIndex() {
        logger.info("Creating lucene index");
    }

    @PostConstruct
    public void postConstruct() {
        SearchProperties searchProperties = ftsServerConfigService.getLocalFtsProperties().getSearch();
        if(searchProperties.getEnabled() && searchProperties.getMode() == SearchMode.INDEXED) {
            LuceneIndexThread.getInstance().start();
            if(searchProperties.getIndexBeforeStart()) {
                createIndex();
            } else {
                webServerStartListener.addAsyncTask(this::createIndex);
            }
        }
    }

}
