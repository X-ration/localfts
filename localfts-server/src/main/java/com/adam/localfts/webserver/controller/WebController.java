package com.adam.localfts.webserver.controller;

import com.adam.localfts.webserver.common.*;
import com.adam.localfts.webserver.common.compress.CompressManagementPageModel;
import com.adam.localfts.webserver.common.compress.FolderCompressDTO;
import com.adam.localfts.webserver.common.compress.FolderCompressInfo;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.common.search.*;
import com.adam.localfts.webserver.common.sort.CompressManagementColumn;
import com.adam.localfts.webserver.common.sort.ListTableColumn;
import com.adam.localfts.webserver.common.sort.SearchColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import com.adam.localfts.webserver.component.ShutdownListener;
import com.adam.localfts.webserver.service.FtsSearchService;
import com.adam.localfts.webserver.service.FtsServerConfigService;
import com.adam.localfts.webserver.service.FtsService;
import com.adam.localfts.webserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;
import org.thymeleaf.util.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("")
public class WebController {

    @Autowired
    private FtsService ftsService;
    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Autowired
    private FtsSearchService ftsSearchService;
    @Autowired
    private ShutdownListener shutdownListener;

    private final static Logger LOGGER = LoggerFactory.getLogger(WebController.class);

    @GetMapping("")
    public String index(Model model, @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        return "redirect:/list?path=/";
    }

    @GetMapping("/list")
    public String list(Model model, @RequestParam(name = "path") String relativePath,
                       @RequestParam(defaultValue = "1") int pageNo,
                       @RequestParam(defaultValue = "20") int pageSize,
                       @RequestParam(required = false)ListTableColumn sortColumn,
                       @RequestParam(required = false) SortOrder sortOrder
                       ) {
        model.addAttribute("currentPath", relativePath);
        boolean directoryExists = ftsService.checkDirectoryExists(relativePath, false);
        model.addAttribute("directoryExists", directoryExists);
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        model.addAttribute("zipEnabled", zipEnabled);
        if(pageNo <= 0) {
            pageNo = 1;
        }
        if(pageSize <= 0) {
            pageSize = 20;
        }
        if(!zipEnabled && ListTableColumn.COMPRESS_COLUMNS_LIST.contains(sortColumn)) {
            sortColumn = null;
        }
        if(sortColumn != null && sortOrder == null) {
            sortOrder = SortOrder.ASC;
        }
        model.addAttribute("sortColumn", sortColumn);
        model.addAttribute("sortOrder", sortOrder);
        if(directoryExists) {
            FtsPageModel ftsPageModel = ftsService.getDirectoryModel(relativePath, pageNo, pageSize, sortColumn, sortOrder);
            model.addAttribute("ftsPage", ftsPageModel);
        }
        boolean mkdirEnabled = ftsServerConfigService.getLocalFtsProperties().getMkdir().getEnabled();
        model.addAttribute("mkdirEnabled", mkdirEnabled);
        String fileInvalidCharacterString = ftsService.getFileInvalidCharacterString();
        model.addAttribute("fileInvalidCharacterString", fileInvalidCharacterString);
        FtsServerIpInfoModel serverIpInfoModel = ftsServerConfigService.getFtsServerIpInfoModel();
        model.addAttribute("serverIpInfo", serverIpInfoModel);
        String serverTime = Util.getServerTimeFormattedString();
        model.addAttribute("serverTime", serverTime);
        return "list";
    }

    @PostMapping("/listSubDirectory")
    @ResponseBody
    public ReturnObject<FtsSubDirectoryModel> listSubDirectory(@RequestParam(name = "path") String relativePath,
                                                               @RequestParam(required = false, defaultValue = "true") boolean fromRoot
    ) {
        boolean directoryExists = ftsService.checkDirectoryExists(relativePath, false);
        if(!directoryExists) {
            return ReturnObject.fail("该路径不存在");
        }
        return ftsService.getSubDirectoryModel(relativePath, fromRoot);
    }

    @PostMapping("/checkPathExists")
    @ResponseBody
    public ReturnObject<Map<String, Boolean>> checkPathExists(@RequestParam(name = "paths") String[] relativePaths) {
        if(relativePaths == null || relativePaths.length == 0) {
            return ReturnObject.fail("请求参数未传入");
        }
        return ftsService.checkPathExists(relativePaths);
    }

    /**
     * 全局压缩管理页面
     * @param model
     * @return
     */
    @GetMapping("/compressManagement")
    public String compressManagement(@RequestParam(defaultValue = "1") int pageNo,
                                     @RequestParam(defaultValue = "10") int pageSize,
                                     @RequestParam(required = false) CompressManagementColumn sortColumn,
                                     @RequestParam(required = false) SortOrder sortOrder,
                                     Model model) {
        if(pageNo <= 0) {
            pageNo = 1;
        }
        if(pageSize <= 0) {
            pageSize = 10;
        }
        if(sortColumn != null && sortOrder == null) {
            sortOrder = SortOrder.ASC;
        }
        model.addAttribute("sortColumn", sortColumn);
        model.addAttribute("sortOrder", sortOrder);
        CompressManagementPageModel list = ftsService.listCompressTask(pageNo, pageSize, sortColumn, sortOrder);
        model.addAttribute("pagedList", list);
        boolean needSizeCheck = ftsServerConfigService.getLocalFtsProperties().getZip().getMaxFolderSize() != null;
        model.addAttribute("needSizeCheck", needSizeCheck);
        Boolean backgroundEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getBackgroundEnabled();
        model.addAttribute("backgroundEnabled", backgroundEnabled);
        FtsServerIpInfoModel serverIpInfoModel = ftsServerConfigService.getFtsServerIpInfoModel();
        model.addAttribute("serverIpInfo", serverIpInfoModel);
        String serverTime = Util.getServerTimeFormattedString();
        model.addAttribute("serverTime", serverTime);
        return "compress_management";
    }

    /**
     * 文件夹压缩管理页面
     * @param relativePath
     * @param model
     * @return
     */
    @GetMapping("/compressFolder")
    public String compressFolder(@RequestParam(value = "path") String relativePath, @RequestHeader(required = false, value = "User-Agent")String userAgent, Model model) {
        boolean directoryExists = ftsService.checkDirectoryExists(relativePath, false);
        model.addAttribute("directoryExists", directoryExists);
        model.addAttribute("currentPath", relativePath);
        FolderCompressStatus compressStatus = ftsService.getFolderCompressStatus(relativePath, false);
        model.addAttribute("compressStatus", compressStatus.name());

        SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();
        FolderCompressInfo folderCompressInfo = ftsService.getFolderCompressInfo(relativePath, false);
        String lastModifiedStr = null;
        if(directoryExists) {
            lastModifiedStr = simpleDateFormat.format(new Date(folderCompressInfo.getFolderLastModified()));
        }
        model.addAttribute("lastModified", lastModifiedStr);
        if(compressStatus == FolderCompressStatus.COMPRESSING || compressStatus == FolderCompressStatus.COMPRESSED) {
            long compressStartTime = folderCompressInfo.getCompressStartTime();
            if(compressStartTime != -1L) {
                String compressStartTimeStr = simpleDateFormat.format(new Date(compressStartTime));
                model.addAttribute("compressStartTime", compressStartTimeStr);
            }
        }
        if(compressStatus == FolderCompressStatus.COMPRESSED) {
            model.addAttribute("compressedFilePath", folderCompressInfo.getZipFileRelativePath());
            long compressedFileSize = folderCompressInfo.getCompressedFileSize();
            String compressedFileSizeStr = Util.fileLengthToStringNew(compressedFileSize);
            model.addAttribute("compressedFileSize", compressedFileSizeStr);
            String compressedFileLastModified = simpleDateFormat.format(new Date(folderCompressInfo.getCompressedFileLastModified()));
            model.addAttribute("compressedFileLastModified", compressedFileLastModified);
            long compressFinishTime = folderCompressInfo.getCompressFinishTime();
            if(compressFinishTime != -1L) {
                String compressFinishTimeStr = simpleDateFormat.format(new Date(compressFinishTime));
                model.addAttribute("compressFinishTime", compressFinishTimeStr);
            }
            long compressCostTime = compressFinishTime - folderCompressInfo.getCompressStartTime();
            String compressCostTimeStr = Util.formatCostTime(compressCostTime);
            model.addAttribute("compressCostTime", compressCostTimeStr);
        }
        boolean needSizeCheck = ftsServerConfigService.getLocalFtsProperties().getZip().getMaxFolderSize() != null;
        model.addAttribute("needSizeCheck", needSizeCheck);
        Boolean backgroundEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getBackgroundEnabled();
        model.addAttribute("backgroundEnabled", backgroundEnabled);
        boolean pseudoUnload = ftsService.isPseudoUnload(userAgent);
        model.addAttribute("pseudoUnload", pseudoUnload);
        FtsServerIpInfoModel serverIpInfoModel = ftsServerConfigService.getFtsServerIpInfoModel();
        model.addAttribute("serverIpInfo", serverIpInfoModel);
        String serverTime = Util.getServerTimeFormattedString();
        model.addAttribute("serverTime", serverTime);
        return "compress_folder";
    }

    @PostMapping("/createFolder")
    @ResponseBody
    public ReturnObject<Void> createFolder(@RequestParam(value = "path") String relativePath,
                                           @RequestParam(value = "name") String folderName) {
        Assert.isTrue(null != relativePath && !"".equals(relativePath), "非法请求参数(path)");
        Assert.isTrue(null != folderName && !"".equals(folderName), "非法请求参数(name)");
        return ftsService.createFolder(relativePath, folderName);
    }

    @PostMapping("/compressFolder")
    @ResponseBody
    public ReturnObject<FolderCompressDTO> compressFolder(@RequestParam(value = "path") String relativePath) {
        Assert.isTrue(null != relativePath && !"".equals(relativePath), "非法请求参数");
        try {
            return ftsService.compressFolder(relativePath);
        } catch (IOException e) {
            LOGGER.error("压缩文件夹'{}'时出错", relativePath, e);
            return ReturnObject.fail(e.getMessage());
        }
    }

    @PostMapping("/cancelCompress")
    @ResponseBody
    public ReturnObject<Void> cancelCompress(@RequestParam(value = "path") String relativePath) {
        Assert.isTrue(null != relativePath && !"".equals(relativePath), "非法请求参数");
        return ftsService.cancelCompress(relativePath);
    }

    @PostMapping("/deleteCompressFile")
    @ResponseBody
    public ReturnObject<Void> deleteCompressFile(@RequestParam(value = "path") String relativePath) {
        Assert.isTrue(null != relativePath && !"".equals(relativePath), "非法请求参数");
        return ftsService.deleteCompressFile(relativePath);
    }

    @GetMapping("/downloadFile")
    public void downloadFile(@RequestParam(value = "fileName") String filePath, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Assert.isTrue(null != filePath & !"".equals(filePath), "非法请求参数");
        ftsService.downloadFile(filePath, request, response);
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/downloadFile")
    public void headDownloadFile(@RequestParam String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Assert.isTrue(null != fileName & !"".equals(fileName), "非法请求参数");
        ftsService.headDownloadFile(fileName, request, response);
    }

    @GetMapping("/uploadFile")
    public String uploadFile(Model model, @RequestParam String dirName, @RequestHeader(required = false, value = "User-Agent")String userAgent) {
        Assert.isTrue(null != dirName && dirName.startsWith("/"), "非法请求参数");
        boolean directoryExists = ftsService.checkDirectoryExists(dirName, false);
        model.addAttribute("directoryExists", directoryExists);
        boolean pseudoDirectoryUpload = ftsService.isPseudoDirectoryUpload(userAgent);
        model.addAttribute("pseudoDirectoryUpload", pseudoDirectoryUpload);
        FtsServerIpInfoModel serverIpInfoModel = ftsServerConfigService.getFtsServerIpInfoModel();
        model.addAttribute("serverIpInfo", serverIpInfoModel);
        model.addAttribute("currentPath", dirName);
        String serverTime = Util.getServerTimeFormattedString();
        model.addAttribute("serverTime", serverTime);
        return "upload";
    }

    /**
     * 经实际测试，上传2GB文件大概用时文件写入临时位置57s，写入指定位置29s，共86s
     * @param file
     * @param dirName
     * @param redirectAttributes
     * @return
     */
    @PostMapping("/uploadFileTransfer")
    public String uploadFileTransfer(MultipartFile file, @RequestParam String dirName, RedirectAttributes redirectAttributes) {
        Assert.isTrue(file != null && dirName != null && dirName.startsWith("/"), "非法请求参数");
        ReturnObject<String> returnObject = ftsService.uploadFile(dirName, file);
        redirectAttributes.addFlashAttribute("uploadFileRetObject", returnObject);
        return "redirect:/uploadFile?dirName=" + UriUtils.encode(dirName, "UTF-8");
    }

    @PostMapping("/uploadFilesTransfer")
    public String uploadFilesTransfer(MultipartFile[] files, @RequestParam String dirName, RedirectAttributes redirectAttributes) {
        LOGGER.debug("uploadFilesTransfer files count={}, dirName={}", files.length, dirName);
        Assert.isTrue(files != null && dirName != null && dirName.startsWith("/"), "非法请求参数");
        ReturnObject<List<ReturnObject<String>>> returnObject = ftsService.uploadFiles(dirName, files, false);
        redirectAttributes.addFlashAttribute("uploadDirRetObject", returnObject);
        return "redirect:/uploadFile?dirName=" + UriUtils.encode(dirName, "UTF-8");
    }

    @PostMapping("/uploadFolderTransfer")
    public String uploadFolderTransfer(MultipartFile[] files, @RequestParam String dirName, RedirectAttributes redirectAttributes) {
        LOGGER.debug("uploadFolderTransfer files count={}, dirName={}", files.length, dirName);
        Assert.isTrue(files != null && dirName != null && dirName.startsWith("/"), "非法请求参数");
        ReturnObject<List<ReturnObject<String>>> returnObject = ftsService.uploadFiles(dirName, files, true);
        redirectAttributes.addFlashAttribute("uploadDirRetObject", returnObject);
        return "redirect:/uploadFile?dirName=" + UriUtils.encode(dirName, "UTF-8");
    }

    @PostMapping("/cancelSearch")
    @ResponseBody
    public ReturnObject<Void> cancelSearch(@RequestParam String searchId) {
        Assert.isTrue(!StringUtils.isEmpty(searchId), "searchId is empty!");
        ftsSearchService.cancelSearch(searchId);
        return ReturnObject.success();
    }

    @PostMapping("/searchApi")
    @ResponseBody
    public ReturnObject<PageObject<SearchDTO>> searchApi(@RequestParam String keyword,
            @RequestParam String searchId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) SearchColumn sortColumn,
            @RequestParam(required = false) SortOrder sortOrder,
            AdvancedSearchCondition advancedSearchCondition
    ) {
        if(!ftsServerConfigService.getLocalFtsProperties().getSearch().getEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if(!StringUtils.isEmpty(searchId)) {
            ftsSearchService.cancelSearch(searchId);
        }
        if(StringUtils.isEmpty(keyword)) {
            return ReturnObject.fail("搜索关键词为空");
        }
        LOGGER.debug("search keyword={},searchId={},pageNo={},pageSize={},sortColumn={},sortOrder={},advancedSearchCondition={}",
                keyword, searchId, pageNo, pageSize, sortColumn, sortOrder, advancedSearchCondition);
        SearchMode searchMode = ftsServerConfigService.getLocalFtsProperties().getSearch().getMode();
        Boolean indexFileContent = searchMode == SearchMode.INDEXED ? ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent().getEnabled() : false;
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        if(pageNo <= 0) {
            pageNo = 1;
        }
        if(pageSize <= 0) {
            pageSize = 20;
        }
        if(sortColumn != null) {
            boolean checkSortColumn = checkSearchSortColumn(sortColumn, advancedSearchCondition, zipEnabled, searchMode, indexFileContent);
            if(!checkSortColumn) {
                sortColumn = null;
            }
        }
        if(sortColumn != null && sortColumn != SearchColumn.DEFAULT && sortOrder == null) {
            sortOrder = SortOrder.ASC;
        }
        return ftsSearchService.search(keyword, searchId, advancedSearchCondition, pageNo, pageSize, sortColumn, sortOrder);
    }

    @GetMapping("/search")
    public String search(Model model) {
        if(!ftsServerConfigService.getLocalFtsProperties().getSearch().getEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        FtsServerIpInfoModel serverIpInfoModel = ftsServerConfigService.getFtsServerIpInfoModel();
        model.addAttribute("serverIpInfo", serverIpInfoModel);
        String serverTime = Util.getServerTimeFormattedString();
        model.addAttribute("serverTime", serverTime);
        String urlRelativePathPattern = ftsServerConfigService.getStandardRelativePathPattern().pattern();
        model.addAttribute("urlRelativePathPattern", urlRelativePathPattern);
        String fileSuffixPattern = ftsServerConfigService.getFileSuffixPattern().pattern();
        model.addAttribute("fileSuffixPattern", fileSuffixPattern);
        String [] fileInvalidCharacters = ftsServerConfigService.getFileInvalidCharacters();
        model.addAttribute("fileInvalidCharacters", fileInvalidCharacters);
        SearchMode searchMode = ftsServerConfigService.getLocalFtsProperties().getSearch().getMode();
        model.addAttribute("searchMode", searchMode);
        Boolean indexFileContent = searchMode == SearchMode.INDEXED ? ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent().getEnabled() : false;
        model.addAttribute("indexFileContent", indexFileContent);
        Integer timeout = ftsServerConfigService.getLocalFtsProperties().getSearch().getTimeout();
        model.addAttribute("timeout", timeout);
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        model.addAttribute("zipEnabled", zipEnabled);
        FileTypeGroup[] fileTypeGroups = FileTypeGroup.values();
        model.addAttribute("fileTypeGroups", fileTypeGroups);
        String searchId = Util.getRandomUUIDString();
        model.addAttribute("searchId", searchId);
        return "search";
    }

    private boolean checkSearchSortColumn(SearchColumn sortColumn, AdvancedSearchCondition advancedSearchCondition,
                                          boolean zipEnabled, SearchMode searchMode, boolean indexFileContent) {
        if(!zipEnabled && SearchColumn.COMPRESS_COLUMNS_LIST.contains(sortColumn)) {
            return false;
        }
        if(advancedSearchCondition == null || advancedSearchCondition.isEmpty()) {
            return true;
        }
        boolean requireFile = requireFile(advancedSearchCondition);
        boolean requireDirectory = requireDirectory(advancedSearchCondition);
        boolean requireFileOrDirectory = requireFileOrDirectory(advancedSearchCondition);
        if(sortColumn == SearchColumn.TYPE) {
            if(!requireFileOrDirectory) {
                return false;
            }
        }
        if(sortColumn == SearchColumn.SIZE) {
            if(requireDirectory) {
                return false;
            }
        }
        if(SearchColumn.COMPRESS_COLUMNS_LIST.contains(sortColumn)) {
            if(requireFile) {
                return false;
            }
        }
        return true;
    }

    private boolean requireFileOrDirectory(AdvancedSearchCondition advancedSearchCondition) {
        if(advancedSearchCondition.getSearchType() != null) {
            boolean check = advancedSearchCondition.getSearchType() != SearchType.FILE_CONTENT_ONLY;
            if(!check) {
                return false;
            }
        }
        return advancedSearchCondition.getDirectory() == null;
    }

    private boolean requireFile(AdvancedSearchCondition advancedSearchCondition) {
        return advancedSearchCondition.getDirectory() != null && !advancedSearchCondition.getDirectory();
    }

    private boolean requireDirectory(AdvancedSearchCondition advancedSearchCondition) {
        if(advancedSearchCondition.getSearchType() != null) {
            boolean check = advancedSearchCondition.getSearchType() != SearchType.FILE_CONTENT_ONLY;
            if(!check) {
                return false;
            }
        }
        return advancedSearchCondition.getDirectory() != null && advancedSearchCondition.getDirectory();
    }
}
