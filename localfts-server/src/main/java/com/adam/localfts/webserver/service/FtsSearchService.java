package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.ReturnObject;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.common.search.AdvancedSearchCondition;
import com.adam.localfts.webserver.common.search.SearchDTO;
import com.adam.localfts.webserver.common.search.SearchMode;
import com.adam.localfts.webserver.common.search.SearchType;
import com.adam.localfts.webserver.common.sort.SearchColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import com.adam.localfts.webserver.component.ShutdownListener;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.service.search.LuceneSearchServiceImpl;
import com.adam.localfts.webserver.service.search.PlainSearchServiceImpl;
import com.adam.localfts.webserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@DependsOn("ftsServerConfigService")
public class FtsSearchService {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Autowired
    private FtsService ftsService;
    @Autowired
    private PlainSearchServiceImpl plainSearchService;
    @Autowired
    private LuceneSearchServiceImpl luceneSearchService;
    @Autowired
    private ShutdownListener shutdownListener;
    @Autowired
    private ThreadPoolExecutor searchThreadPool;

    private final Map<String, Future<PageObject<SearchDTO>>> searchFutureMap = new ConcurrentHashMap<>();
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
    public ReturnObject<PageObject<SearchDTO>> search(String keyword, String searchId, AdvancedSearchCondition advancedSearchCondition,
                                                     int pageNo, int pageSize, SearchColumn sortColumn, SortOrder sortOrder) {
        Assert.isTrue(!StringUtils.isEmpty(keyword), "搜索关键词为空");
        Assert.isTrue(pageNo > 0, "非法的页数：" + pageNo);
        Assert.isTrue(pageSize > 0, "非法的每页数量：" + pageSize);
        Assert.isTrue(sortColumn == null || sortColumn == SearchColumn.DEFAULT || sortOrder != null, "排序顺序为null");
        SearchMode searchMode = ftsServerConfigService.getLocalFtsProperties().getSearch().getMode();
        Boolean indexFileContent = searchMode == SearchMode.INDEXED ? ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent().getEnabled() : false;
        if(advancedSearchCondition != null) {
            try {
                advancedSearchCondition = advancedSearchCondition.copy();
            } catch (CloneNotSupportedException e) {
                logger.error("克隆高级高级搜索条件时发生异常", e);
                throw new LocalFtsRuntimeException("搜索时发生" + CloneNotSupportedException.class.getName() + "异常", e);
            }
            preHandleAdvancedSearchCondition(advancedSearchCondition);
            if(searchMode == SearchMode.PLAIN || (searchMode == SearchMode.INDEXED && indexFileContent &&
                    advancedSearchCondition.getSearchType() == SearchType.FILENAME_ONLY)) {
                String[] fileInvalidCharacters = ftsServerConfigService.getFileInvalidCharacters();
                for(String fic: fileInvalidCharacters) {
                    if(keyword.contains(fic)) {
                        advancedSearchCondition.setEmptyResult(true);
                        break;
                    }
                }
            }
        }
        logger.debug("Actual search parameters: keyword={},searchId={},pageNo={},pageSize={},sortColumn={},sortOrder={},advancedSearchCondition={}",
                keyword, searchId, pageNo, pageSize, sortColumn, sortOrder, advancedSearchCondition);
        if(advancedSearchCondition != null && advancedSearchCondition.emptyResult()) {
            return ReturnObject.success(new PageObject<>(pageNo, pageSize, null));
        }
        if(shutdownListener.isShuttingDown()) {
            return ReturnObject.fail("应用正在关闭");
        }
        final AdvancedSearchCondition finalAdvancedSearchCondition = advancedSearchCondition;
        /*int activeTaskThreshold = -1;
        if(ftsServerConfigService.getLocalFtsProperties().getSearch().getActiveTaskThreshold() != null) {
            activeTaskThreshold = ftsServerConfigService.getLocalFtsProperties().getSearch().getActiveTaskThreshold();
        }
        int activeTaskCount = searchThreadPool.getActiveCount();
        if(activeTaskThreshold != -1 && activeTaskCount >= activeTaskThreshold) {
            logger.warn("搜索任务数{}已达上限", activeTaskCount);
            return ReturnObject.fail("搜索任务数已达上限");
//            return ReturnObject.fail("搜索服务不可用");
        } else {
            logger.debug("当前活跃搜索任务数：{}", activeTaskCount);
        }*/
        Callable<PageObject<SearchDTO>> callable;
        switch (searchMode) {
            case PLAIN:
                callable = () -> plainSearchService.search(keyword, searchId, finalAdvancedSearchCondition, pageNo, pageSize, sortColumn, sortOrder);
                break;
            case INDEXED:
                callable = () -> luceneSearchService.search(keyword, searchId, finalAdvancedSearchCondition, pageNo, pageSize, sortColumn, sortOrder);
                break;
            default:
                logger.warn("Unknown search mode:{}", searchMode);
                return ReturnObject.fail("搜索服务不可用");
        }
        Future<PageObject<SearchDTO>> future = null;
        try {
            future = searchThreadPool.submit(callable);
            searchFutureMap.put(searchId, future);
//            logger.debug("Put future {} with search id {}", future, searchId);
            PageObject<SearchDTO> pageObject;
            if(ftsServerConfigService.getLocalFtsProperties().getSearch().getTimeout() != null) {
                int timeoutSeconds = ftsServerConfigService.getLocalFtsProperties().getSearch().getTimeout();
                pageObject = future.get(timeoutSeconds, TimeUnit.SECONDS);
            } else {
                pageObject = future.get();
            }
            searchFutureMap.remove(searchId, future);
            return ReturnObject.success(pageObject);
        } catch (RejectedExecutionException e) {
            logger.warn("搜索线程池已满, searchId={}", searchId);
            return ReturnObject.fail("搜索线程池已满");
            //return ReturnObject.fail("搜索服务不可用");
        } catch (TimeoutException e) {
            logger.warn("搜索任务超时, searchId={}", searchId);
            future.cancel(true);
            searchFutureMap.remove(searchId, future);
            return ReturnObject.fail("搜索超时");
        } catch (ExecutionException e) {
            logger.error("搜索执行异常, searchId={}", searchId, e);
            searchFutureMap.remove(searchId, future);
            return ReturnObject.fail("搜索失败");
        } catch (CancellationException e) {
            logger.warn("搜索任务被取消, searchId={}", searchId);;
            searchFutureMap.remove(searchId, future);
            return ReturnObject.fail("搜索任务被取消");
        } catch (InterruptedException e) {
            logger.warn("搜索任务被中断, searchId={}", searchId);
            searchFutureMap.remove(searchId, future);
            return ReturnObject.fail("搜索任务被中断");
        }
    }

    public void cancelSearch(String searchId) {
        Assert.isTrue(!StringUtils.isEmpty(searchId), "searchId is empty!");
        Future<PageObject<SearchDTO>> future = searchFutureMap.get(searchId);
        if(future != null) {
            if(!future.isDone()) {
                future.cancel(true);
                logger.debug("Canceled search id={},future={},cancelled={}", searchId, future, future.isCancelled());
            } else {
                logger.warn("Unable to cancel search id {}: search task is done", searchId);
            }
            searchFutureMap.remove(searchId, future);
        } else {
            logger.debug("Future is null, search id={}", searchId);
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
        if(advancedSearchCondition.getFolderCompressStatus() == FolderCompressStatus.NOT_COMPRESSED && (
                advancedSearchCondition.compressedFileSizeLower() != null
                || advancedSearchCondition.compressedFileSizeUpper() != null
                || advancedSearchCondition.compressedFileLastModifiedLower() != null
                || advancedSearchCondition.compressedFileLastModifiedUpper() != null
        )) {
            advancedSearchCondition.setEmptyResult(true);
        }
        if(advancedSearchCondition.emptyResult()) {
            return;
        }

        if(!CollectionUtils.isEmpty(searchPathList)) {
            boolean showHidden = ftsServerConfigService.getLocalFtsProperties().getShowHidden();
            List<String> newSearchPathList = searchPathList.stream()
                    .distinct()
                    .filter(path -> !StringUtils.isEmpty(path))
                    .filter(path -> standardPathPattern.matcher(path).matches())
                    .filter(path -> showHidden ? ftsService.checkDirectoryExists(path, false) :
                            ftsService.checkDirectoryExistsNoHidden(path, false))
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
        if(advancedSearchCondition.getCaseAndSTCSensitive() == null) {
            advancedSearchCondition.setCaseAndSTCSensitive(false);
        }
        if(ftsServerConfigService.getLocalFtsProperties().getSearch().getMode() == SearchMode.PLAIN) {
            advancedSearchCondition.setSearchType(SearchType.FILENAME_ONLY);
        } else if(ftsServerConfigService.getLocalFtsProperties().getSearch().getMode() == SearchMode.INDEXED &&
                !ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent().getEnabled()) {
            advancedSearchCondition.setSearchType(SearchType.FILENAME_ONLY);
        }
        if(advancedSearchCondition.getSearchType() == null) {
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
        if(!advancedSearchCondition.emptyResult()) {
            if(advancedSearchCondition.fileSizeLower() != null || advancedSearchCondition.fileSizeUpper() != null) {
                advancedSearchCondition.setDirectory(false);
            }
            if(advancedSearchCondition.getFolderCompressStatus() != null
                    || advancedSearchCondition.compressedFileSizeLower() != null || advancedSearchCondition.compressedFileSizeUpper() != null
                    || advancedSearchCondition.compressedFileLastModifiedLower() != null || advancedSearchCondition.compressedFileLastModifiedUpper() != null
            ) {
                if(advancedSearchCondition.getDirectory() != null && !advancedSearchCondition.getDirectory()) {
                    advancedSearchCondition.setEmptyResult(true);
                } else {
                    advancedSearchCondition.setDirectory(true);
                }
            }
        }
        advancedSearchCondition.clean();
    }

}
