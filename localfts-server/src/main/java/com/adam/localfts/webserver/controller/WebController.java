package com.adam.localfts.webserver.controller;

import com.adam.localfts.webserver.common.*;
import com.adam.localfts.webserver.common.compress.CompressManagementPageModel;
import com.adam.localfts.webserver.common.compress.FolderCompressDTO;
import com.adam.localfts.webserver.common.compress.FolderCompressInfo;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.common.sort.CompressManagementColumn;
import com.adam.localfts.webserver.common.sort.ListTableColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("")
public class WebController {

    @Autowired
    private FtsService ftsService;
    @Autowired
    private FtsServerConfigService ftsServerConfigService;

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
        boolean directoryExists = ftsService.checkDirectoryExists(relativePath);
        model.addAttribute("directoryExists", directoryExists);
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        model.addAttribute("zipEnabled", zipEnabled);
        if(pageNo <= 0) {
            pageNo = 1;
        }
        if(pageSize <= 0) {
            pageSize = 20;
        }
        if(!zipEnabled && (sortColumn == ListTableColumn.COMPRESS_STATUS || sortColumn == ListTableColumn.COMPRESS_FILE_LAST_MODIFIED)) {
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
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        if(!zipEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
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
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        if(!zipEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        boolean directoryExists = ftsService.checkDirectoryExists(relativePath);
        model.addAttribute("directoryExists", directoryExists);
        model.addAttribute("currentPath", relativePath);
        if(directoryExists) {
            FolderCompressStatus compressStatus = ftsService.getFolderCompressStatus(relativePath, false);
            model.addAttribute("compressStatus", compressStatus.name());
            FolderCompressInfo folderCompressInfo = ftsService.getFolderCompressInfo(relativePath, false);
            SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();
            if(compressStatus == FolderCompressStatus.COMPRESSING || compressStatus == FolderCompressStatus.COMPRESSED) {
                long compressStartTime = folderCompressInfo.getCompressStartTime();
                String compressStartTimeStr = simpleDateFormat.format(new Date(compressStartTime));
                model.addAttribute("compressStartTime", compressStartTimeStr);
            }
            if(compressStatus == FolderCompressStatus.COMPRESSED) {
                model.addAttribute("compressedFilePath", folderCompressInfo.getZipFileRelativePath());
                long compressedFileSize = folderCompressInfo.getCompressedFileSize();
                String compressedFileSizeStr = Util.fileLengthToStringNew(compressedFileSize);
                model.addAttribute("compressedFileSize", compressedFileSizeStr);
                String compressedFileLastModified = simpleDateFormat.format(new Date(folderCompressInfo.getCompressedFileLastModified()));
                model.addAttribute("compressedFileLastModified", compressedFileLastModified);
                long compressFinishTime = folderCompressInfo.getCompressFinishTime();
                String compressFinishTimeStr = simpleDateFormat.format(new Date(compressFinishTime));
                model.addAttribute("compressFinishTime", compressFinishTimeStr);
            }
            boolean needSizeCheck = ftsServerConfigService.getLocalFtsProperties().getZip().getMaxFolderSize() != null;
            model.addAttribute("needSizeCheck", needSizeCheck);
            Boolean backgroundEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getBackgroundEnabled();
            model.addAttribute("backgroundEnabled", backgroundEnabled);
            boolean pseudoUnload = ftsService.isPseudoUnload(userAgent);
            model.addAttribute("pseudoUnload", pseudoUnload);
        }
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
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        if(!zipEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
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
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        if(!zipEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Assert.isTrue(null != relativePath && !"".equals(relativePath), "非法请求参数");
        return ftsService.cancelCompress(relativePath);
    }

    @PostMapping("/deleteCompressFile")
    @ResponseBody
    public ReturnObject<Void> deleteCompressFile(@RequestParam(value = "path") String relativePath) {
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        if(!zipEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
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
        boolean directoryExists = ftsService.checkDirectoryExists(dirName);
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
}
