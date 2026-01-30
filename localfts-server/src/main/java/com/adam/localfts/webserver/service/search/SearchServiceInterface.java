package com.adam.localfts.webserver.service.search;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.search.AdvancedSearchCondition;
import com.adam.localfts.webserver.common.search.SearchDTO;
import com.adam.localfts.webserver.common.sort.SearchColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;

public interface SearchServiceInterface {
    PageObject<SearchDTO> search(String keyword, AdvancedSearchCondition advancedSearchCondition,
                                 int pageNo, int pageSize, SearchColumn sortColumn, SortOrder sortOrder);
}
