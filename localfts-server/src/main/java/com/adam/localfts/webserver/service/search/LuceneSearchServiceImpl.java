package com.adam.localfts.webserver.service.search;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.search.AdvancedSearchCondition;
import com.adam.localfts.webserver.common.search.SearchDTO;
import com.adam.localfts.webserver.common.sort.SearchColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import org.springframework.stereotype.Service;

@Service
public class LuceneSearchServiceImpl implements SearchServiceInterface{
    @Override
    public PageObject<SearchDTO> search(String keyword, AdvancedSearchCondition advancedSearchCondition,
                                        int pageNo, int pageSize, SearchColumn sortColumn, SortOrder sortOrder) {
        return new PageObject<>();
    }
}
