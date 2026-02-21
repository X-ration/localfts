package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.search.AdvancedSearchCondition;
import com.adam.localfts.webserver.common.search.SearchDTO;
import com.adam.localfts.webserver.common.search.SearchMode;
import com.adam.localfts.webserver.common.search.SearchType;
import com.adam.localfts.webserver.common.sort.SearchColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import com.adam.localfts.webserver.component.WebServerStartListener;
import com.adam.localfts.webserver.config.localfts.SearchProperties;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.service.search.LuceneSearchServiceImpl;
import com.adam.localfts.webserver.service.search.PlainSearchServiceImpl;
import com.adam.localfts.webserver.task.LuceneIndexThread;
import com.adam.localfts.webserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@DependsOn("ftsServerConfigService")
public class FtsSearchService implements DisposableBean {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Autowired
    private FtsService ftsService;
    @Autowired
    private PlainSearchServiceImpl plainSearchService;
    @Autowired
    private LuceneSearchServiceImpl luceneSearchService;
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
    public PageObject<SearchDTO> search(String keyword, AdvancedSearchCondition advancedSearchCondition,
                                        int pageNo, int pageSize, SearchColumn sortColumn, SortOrder sortOrder) {
        Assert.isTrue(!StringUtils.isEmpty(keyword), "搜索关键词为空");
        Assert.isTrue(pageNo > 0, "非法的页数：" + pageNo);
        Assert.isTrue(pageSize > 0, "非法的每页数量：" + pageSize);
        Assert.isTrue(sortColumn == null || sortOrder != null, "排序顺序为null");
        AdvancedSearchCondition advancedSearchConditionCopy = null;
        if(advancedSearchCondition != null) {
            try {
                advancedSearchConditionCopy = advancedSearchCondition.copy();
            } catch (CloneNotSupportedException e) {
                logger.error("克隆高级高级搜索条件时发生异常", e);
                throw new LocalFtsRuntimeException("搜索时发生" + CloneNotSupportedException.class.getName() + "异常", e);
            }
            preHandleAdvancedSearchCondition(advancedSearchConditionCopy);
        }
        logger.debug("Actual search parameters: keyword={},pageNo={},pageSize={},sortColumn={},sortOrder={},advancedSearchCondition={}",
                keyword, pageNo, pageSize, sortColumn, sortOrder, advancedSearchConditionCopy);
        if(advancedSearchConditionCopy != null && advancedSearchConditionCopy.emptyResult()) {
            return new PageObject<>(pageNo, pageSize, null);
        }
        SearchMode searchMode = ftsServerConfigService.getLocalFtsProperties().getSearch().getMode();
        switch (searchMode) {
            case PLAIN:
                return plainSearchService.search(keyword, advancedSearchConditionCopy, pageNo, pageSize, sortColumn, sortOrder);
            case INDEXED:
                return luceneSearchService.search(keyword, advancedSearchConditionCopy, pageNo, pageSize, sortColumn, sortOrder);
            default:
                logger.warn("Unknown search mode:{}", searchMode);
                return new PageObject<>(pageNo, pageSize, null);
        }
    }

    private void preHandleAdvancedSearchCondition(AdvancedSearchCondition advancedSearchCondition) {
        final List<String> searchPathList = advancedSearchCondition.getSearchPaths();
        Pattern standardPathPattern = ftsServerConfigService.getStandardRelativePathPattern();
        if(
                (advancedSearchCondition.lastModifiedLower() != null && advancedSearchCondition.lastModifiedUpper() != null
                        && advancedSearchCondition.lastModifiedLower().after(advancedSearchCondition.lastModifiedUpper()))
                        || (advancedSearchCondition.fileSizeLower() != null && advancedSearchCondition.fileSizeUpper() != null
                        && advancedSearchCondition.fileSizeLower() > advancedSearchCondition.fileSizeUpper())
                        || (advancedSearchCondition.compressedFileSizeLower() != null && advancedSearchCondition.compressedFileSizeUpper() != null
                        && advancedSearchCondition.compressedFileSizeLower() > advancedSearchCondition.compressedFileSizeUpper())
                        || (advancedSearchCondition.compressedFileLastModifiedLower()  != null && advancedSearchCondition.compressedFileLastModifiedUpper() != null
                        && advancedSearchCondition.compressedFileLastModifiedLower().after(advancedSearchCondition.compressedFileLastModifiedUpper()))
        ) {
            advancedSearchCondition.setEmptyResult(true);
        }
        if((
                (advancedSearchCondition.getFilterFileType() != null && advancedSearchCondition.getFilterFileType())
                        || advancedSearchCondition.getSearchType() == SearchType.FILE_CONTENT_ONLY
                        || advancedSearchCondition.fileSizeLower() != null || advancedSearchCondition.fileSizeUpper() != null)
                && (advancedSearchCondition.getDirectory() != null && advancedSearchCondition.getDirectory()
                || advancedSearchCondition.getFolderCompressStatus() != null
                || advancedSearchCondition.compressedFileSizeLower() != null || advancedSearchCondition.compressedFileSizeUpper() != null
                || advancedSearchCondition.compressedFileLastModifiedLower() != null || advancedSearchCondition.compressedFileLastModifiedUpper() != null
        )) {
            advancedSearchCondition.setEmptyResult(true);
        }
        if(advancedSearchCondition.emptyResult()) {
            return;
        }

        if(!CollectionUtils.isEmpty(searchPathList)) {
            List<String> newSearchPathList = searchPathList.stream()
                    .distinct()
                    .filter(path -> !StringUtils.isEmpty(path))
                    .filter(path -> standardPathPattern.matcher(path).matches())
                    .filter(path -> ftsService.checkDirectoryExists(path, false))
                    .filter(path -> {
                        for(String path1: searchPathList) {
                            if(!path.equals(path1) && path.startsWith(path1)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
            advancedSearchCondition.setSearchPathList(newSearchPathList);
        }
        if(advancedSearchCondition.getCaseSensitive() == null) {
            advancedSearchCondition.setCaseSensitive(true);
        }
        if(ftsServerConfigService.getLocalFtsProperties().getSearch().getMode() == SearchMode.PLAIN) {
            advancedSearchCondition.setSearchType(SearchType.FILENAME_ONLY);
        } else if(ftsServerConfigService.getLocalFtsProperties().getSearch().getMode() == SearchMode.INDEXED &&
                !ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent()) {
            advancedSearchCondition.setSearchType(SearchType.FILENAME_ONLY);
        }
        if(advancedSearchCondition.getSearchType() == SearchType.FILE_CONTENT_ONLY) {
            advancedSearchCondition.setDirectory(false);
        }
        if(advancedSearchCondition.getFilterFileType() == null || !advancedSearchCondition.getFilterFileType()) {
            advancedSearchCondition.setFileTypes(null);
        } else if(advancedSearchCondition.getFilterFileType()) {
            if(CollectionUtils.isEmpty(advancedSearchCondition.getFileTypes())) {
                advancedSearchCondition.setEmptyResult(true);
            } else {
                advancedSearchCondition.setDirectory(false);
                List<String> newFileTypeList = advancedSearchCondition.getFileTypes()
                        .stream()
                        .distinct()
                        .filter(Objects::nonNull)
                        .filter(str -> !StringUtils.isEmpty(str))
                        .filter(Util::isValidFileSuffix)
                        .collect(Collectors.toList());
                advancedSearchCondition.setFileTypeList(newFileTypeList);
                if(CollectionUtils.isEmpty(newFileTypeList)) {
                    advancedSearchCondition.setEmptyResult(true);
                }
            }
        }
        advancedSearchCondition.clean();
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

    @Override
    public void destroy() throws Exception {
        if(LuceneIndexThread.constructed()) {
            LuceneIndexThread.getInstance().tryStop();
        }
    }
}
