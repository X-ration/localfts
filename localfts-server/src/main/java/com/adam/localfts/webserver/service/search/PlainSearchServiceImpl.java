package com.adam.localfts.webserver.service.search;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.compress.FolderCompressInfo;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.common.search.AdvancedSearchCondition;
import com.adam.localfts.webserver.common.search.SearchDTO;
import com.adam.localfts.webserver.common.sort.SearchColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import com.adam.localfts.webserver.service.FtsServerConfigService;
import com.adam.localfts.webserver.service.FtsService;
import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlainSearchServiceImpl implements SearchServiceInterface{
    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Autowired
    private FtsService ftsService;
    private final Map<SearchColumn, Comparator<SearchDTO>> searchComparatorMap = new HashMap<>();
    private final Collator CHINESE_COLLATOR = Collator.getInstance(Locale.CHINA);
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    public PageObject<SearchDTO> search(String keyword, String searchId, AdvancedSearchCondition advancedSearchCondition,
                                        int pageNo, int pageSize, SearchColumn sortColumn, SortOrder sortOrder) throws InterruptedException {
        List<File> searchPathFileList;
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        boolean showHidden = ftsServerConfigService.getLocalFtsProperties().getShowHidden();
        File rootDirectory = IOUtil.getFile(rootPath);
        if (advancedSearchCondition != null && !CollectionUtils.isEmpty(advancedSearchCondition.getSearchPaths())) {
            searchPathFileList = advancedSearchCondition.getSearchPaths().stream()
                    .map(path -> new File(rootDirectory, path))
                    .collect(Collectors.toList());
        } else {
            searchPathFileList = Collections.singletonList(rootDirectory);
        }

        long startMills = System.currentTimeMillis();
        SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();
        List<SearchDTO> searchDTOList = searchPathFileList.stream()
                .flatMap(searchPathFile -> searchAllUnderPath(searchPathFile, keyword, advancedSearchCondition, rootPath, showHidden).stream())
                .map(file -> mapToDTO(file, simpleDateFormat, rootPath))
                .collect(Collectors.toList());
        long endMills = System.currentTimeMillis();
        logger.debug("搜索{}耗时{}ms, searchId={}", Util.checkInterrupted() ? "[被中断]" : "", endMills - startMills, searchId);

        Util.clearInterruptedAndThrowException();
        if(sortColumn != null) {
            Comparator<SearchDTO> comparator = searchComparatorMap.get(sortColumn);
            if(comparator == null) {
                logger.warn("Search sort by '{}' requires a comparator! searchId={}", sortColumn, searchId);
            } else {
                if(sortOrder == SortOrder.DESC) {
                    comparator = comparator.reversed();
                }
                startMills = System.currentTimeMillis();
                searchDTOList.sort(comparator);
                endMills = System.currentTimeMillis();
                logger.debug("排序耗时{}ms, searchId={}", endMills - startMills, searchId);
            }
        }
        for(int i=0;i<searchDTOList.size();i++) {
            SearchDTO searchDTO = searchDTOList.get(i);
            searchDTO.setId(i + 1);
        }
        return new PageObject<>(pageNo, pageSize, searchDTOList);
    }

    @PostConstruct
    public void postConstruct() {
        searchComparatorMap.put(SearchColumn.DEFAULT, (sd1, sd2) -> 0);
        searchComparatorMap.put(SearchColumn.FILENAME, (sd1, sd2) ->
                CHINESE_COLLATOR.compare(sd1.getFilename(), sd2.getFilename()));
        searchComparatorMap.put(SearchColumn.PARENT_PATH, (sd1, sd2) ->
                CHINESE_COLLATOR.compare(sd1.getParentRelativePath(), sd2.getParentRelativePath()));
        searchComparatorMap.put(SearchColumn.TYPE, (sd1, sd2) -> {
            String typeStr1 = sd1.getDirectory() ? "文件夹" : "文件";
            String typeStr2 = sd2.getDirectory() ? "文件夹" : "文件";
            return CHINESE_COLLATOR.compare(typeStr1, typeStr2);
        });
        searchComparatorMap.put(SearchColumn.SIZE, (sd1, sd2) -> {
            long fileSize1 = sd1.getFileSize(), fileSize2 = sd2.getFileSize();
            if(sd1.getDirectory()) {
                fileSize1 = -1L;
            }
            if(sd2.getDirectory()) {
                fileSize2 = -1L;
            }
            return Long.compare(fileSize1, fileSize2);
        });
        searchComparatorMap.put(SearchColumn.LAST_MODIFIED,
                Comparator.comparing(SearchDTO::getLastModified));
        searchComparatorMap.put(SearchColumn.COMPRESS_STATUS, ftsService::compareCompressStatus);
        searchComparatorMap.put(SearchColumn.COMPRESS_FILE_SIZE, ftsService::compareCompressedFileSize);
        searchComparatorMap.put(SearchColumn.COMPRESS_FILE_LAST_MODIFIED, ftsService::compareCompressedFileLastModified);
    }

    private SearchDTO mapToDTO(File file, SimpleDateFormat simpleDateFormat, String rootPath) {
        SearchDTO searchDTO = new SearchDTO();
        if(Thread.currentThread().isInterrupted()) {
            return searchDTO;
        }
        searchDTO.setFilename(file.getName());
        searchDTO.setFilenameFormatted(file.getName());
        String parentRelativePath = "/";
        if(!file.getAbsolutePath().equals(rootPath)) {
            File parentFile = file.getParentFile();
            if(!parentFile.getAbsolutePath().equals(rootPath)) {
                String parentFileAbsolutePath = parentFile.getAbsolutePath();
                parentRelativePath = parentFileAbsolutePath.substring(rootPath.length());
                if(Util.isSystemWindows()) {
                    parentRelativePath = parentRelativePath.replaceAll("\\\\", "/");
                }
            }
        }
        searchDTO.setParentRelativePath(parentRelativePath);
        boolean directory = file.isDirectory();
        searchDTO.setDirectory(directory);
        long lastModified = file.lastModified();
        String lastModifiedStr = simpleDateFormat.format(new Date(lastModified));
        searchDTO.setLastModified(lastModified);
        searchDTO.setLastModifiedStr(lastModifiedStr);
        if(!directory) {
            long fileSize = file.length();
            String fileSizeStr = Util.fileLengthToStringNew(fileSize);
            searchDTO.setFileSize(fileSize);
            searchDTO.setFileSizeStr(fileSizeStr);
        } else {
            FolderCompressStatus folderCompressStatus = ftsService.getFolderCompressStatus(file.getAbsolutePath(), true);
            searchDTO.setCompressStatus(folderCompressStatus);
            if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
                FolderCompressInfo folderCompressInfo = ftsService.getFolderCompressInfo(file.getAbsolutePath(), true);
                long compressedFileSize = folderCompressInfo.getCompressedFileSize();
                long compressedFileLastModified = folderCompressInfo.getCompressedFileLastModified();
                String compressedFileSizeStr = Util.fileLengthToStringNew(compressedFileSize);
                String compressedFileLastModifiedStr = simpleDateFormat.format(new Date(compressedFileLastModified));
                searchDTO.setCompressedFileSize(compressedFileSize);
                searchDTO.setCompressedFileLastModified(compressedFileLastModified);
                searchDTO.setCompressedFileSizeStr(compressedFileSizeStr);
                searchDTO.setCompressedFileLastModifiedStr(compressedFileLastModifiedStr);
                searchDTO.setCompressedFilePath(folderCompressInfo.getZipFileRelativePath());
            }
        }
        return searchDTO;
    }
    private List<File> searchAllUnderPath(File searchPathFile, String keyword, AdvancedSearchCondition advancedSearchCondition, String rootPath, boolean showHidden) {
        List<File> resultList = new LinkedList<>();
        searchAllUnderPath(searchPathFile, keyword, advancedSearchCondition, resultList, rootPath, showHidden);
        if(Thread.currentThread().isInterrupted()) {
            return new ArrayList<>();
        }
        return resultList;
    }
    private void searchAllUnderPath(File searchPathFile, String keyword, AdvancedSearchCondition advancedSearchCondition, List<File> resultList, String rootPath, boolean showHidden) {
        if(Thread.currentThread().isInterrupted()) {
            return;
        }
        if(!searchPathFile.exists()) {
            logger.warn("Search ignoring non-existing path:{}", searchPathFile.getAbsolutePath());
            return;
        }
        if(searchPathFile.isFile()) {
            logger.warn("Search within file:{}", searchPathFile.getAbsolutePath());
            return;
        }

        //忽略搜索关键字匹配类型，直接匹配文件名
        //SearchType searchType = advancedSearchCondition.getSearchType();
        File[] files = searchPathFile.listFiles();
        if(files != null) {
            for(File file: files) {
//                String absolutePath = file.getAbsolutePath();
//                String relativePath = absolutePath.substring(rootPath.length());
//                if(Util.isSystemWindows()) {
//                    relativePath = relativePath.replaceAll("\\\\", "/");
//                }
                String filename = file.getName();
                boolean keywordMatches = advancedSearchCondition.getCaseAndSTCSensitive() ? filename.contains(keyword) :
                        Util.toLowerCaseAndSC(filename).contains(Util.toLowerCaseAndSC(keyword));
                if(keywordMatches) {
                    boolean showFile = showHidden || !file.isHidden();
                    if(showFile) {
                        boolean checkSearchCondition = checkSearchCondition(file, advancedSearchCondition);
                        if (checkSearchCondition) {
                            resultList.add(file);
                        }
                    }
                }
                if(file.isDirectory()) {
                    searchAllUnderPath(file, keyword, advancedSearchCondition, resultList, rootPath, showHidden);
                }
            }
        }
    }
    private boolean checkSearchCondition(File file, AdvancedSearchCondition advancedSearchCondition) {
        String absolutePath = file.getAbsolutePath();
        if(advancedSearchCondition != null) {
            if(advancedSearchCondition.getDirectory() != null) {
                if(advancedSearchCondition.getDirectory() && !file.isDirectory()) {
                    return false;
                } else if(!advancedSearchCondition.getDirectory() && !file.isFile()) {
                    return false;
                }
            }
            if(advancedSearchCondition.getFilterFileType() != null && advancedSearchCondition.getFilterFileType()) {
                boolean matchSuffix = false;
                if(!CollectionUtils.isEmpty(advancedSearchCondition.getFileTypes())) {
                    for (String suffix : advancedSearchCondition.getFileTypes()) {
                        if ((Util.isSystemWindows() || Util.isSystemMacOS()) && absolutePath.toLowerCase().endsWith(suffix.toLowerCase())) {
                            matchSuffix = true;
                            break;
                        } else if(Util.isSystemLinux()) {
                            if(advancedSearchCondition.getFilterFileTypeCaseSensitive() != null && advancedSearchCondition.getFilterFileTypeCaseSensitive()) {
                                if (absolutePath.endsWith(suffix)) {
                                    matchSuffix = true;
                                    break;
                                }
                            } else {
                                if (absolutePath.toLowerCase().endsWith(suffix.toLowerCase())) {
                                    matchSuffix = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if(!matchSuffix) {
                    return false;
                }
            }
            long fileLastModified = file.lastModified();
            if(advancedSearchCondition.lastModifiedLower() != null && fileLastModified < advancedSearchCondition.lastModifiedLower().getTime()) {
                return false;
            }
            if(advancedSearchCondition.lastModifiedUpper() != null && fileLastModified > advancedSearchCondition.lastModifiedUpper().getTime()) {
                return false;
            }
            if(advancedSearchCondition.getDirectory() == null || !advancedSearchCondition.getDirectory()) {
                //检查文件条件
                long fileSize = file.length();
                if(advancedSearchCondition.fileSizeLower() != null && fileSize < advancedSearchCondition.fileSizeLower()) {
                    return false;
                }
                if(advancedSearchCondition.fileSizeUpper() != null && fileSize > advancedSearchCondition.fileSizeUpper()) {
                    return false;
                }
            }
            if(advancedSearchCondition.getDirectory() == null || advancedSearchCondition.getDirectory()) {
                //检查文件夹条件
                FolderCompressStatus folderCompressStatus = ftsService.getFolderCompressStatus(file.getAbsolutePath(), true);
                if(advancedSearchCondition.getFolderCompressStatus() != null && folderCompressStatus != advancedSearchCondition.getFolderCompressStatus()) {
                    return false;
                }
                if(folderCompressStatus != FolderCompressStatus.COMPRESSED &&
                        (advancedSearchCondition.compressedFileSizeLower() != null ||
                                advancedSearchCondition.compressedFileSizeUpper() != null ||
                                advancedSearchCondition.compressedFileLastModifiedLower() != null ||
                                advancedSearchCondition.compressedFileLastModifiedUpper() != null
                        )) {
                    return false;
                }
                if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
                    FolderCompressInfo folderCompressInfo = ftsService.getFolderCompressInfo(file.getAbsolutePath(), true);
                    long compressedFileSize = folderCompressInfo.getCompressedFileSize();
                    long compressedFileLastModified = folderCompressInfo.getCompressedFileLastModified();
                    if(advancedSearchCondition.compressedFileSizeLower() != null && compressedFileSize < advancedSearchCondition.compressedFileSizeLower()) {
                        return false;
                    }
                    if(advancedSearchCondition.compressedFileSizeUpper() != null && compressedFileSize > advancedSearchCondition.compressedFileSizeUpper()) {
                        return false;
                    }
                    if(advancedSearchCondition.compressedFileLastModifiedLower() != null && compressedFileLastModified < advancedSearchCondition.compressedFileLastModifiedLower().getTime()) {
                        return false;
                    }
                    if(advancedSearchCondition.compressedFileLastModifiedUpper() != null && compressedFileLastModified > advancedSearchCondition.compressedFileLastModifiedUpper().getTime()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

}
