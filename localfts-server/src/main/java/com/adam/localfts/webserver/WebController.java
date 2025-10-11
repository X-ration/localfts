package com.adam.localfts.webserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Controller
@RequestMapping("")
public class WebController {

    @Autowired
    private FtsService ftsService;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @GetMapping("")
    public String index(Model model, @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        return "redirect:/list?path=/";
    }

    @GetMapping("/list")
    public String list(Model model, @RequestParam(name = "path") String relativePath, @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize) {
        FtsPageModel ftsPageModel = ftsService.getDirectoryModel(relativePath, pageNo, pageSize);
        model.addAttribute("ftsPage", ftsPageModel);
        FtsServerIpInfoModel serverIpInfoModel = ftsService.getServerIpInfoModel();
        model.addAttribute("serverIpInfo", serverIpInfoModel);
        return "list";
    }

    @GetMapping("/downloadFile")
    public void downloadFile(@RequestParam String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Assert.isTrue(null != fileName & !"".equals(fileName), "非法请求参数");
        ftsService.downloadFile(fileName, request, response);
    }

    @RequestMapping(method = RequestMethod.HEAD, value = "/downloadFile")
    public void headDownloadFile(@RequestParam String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Assert.isTrue(null != fileName & !"".equals(fileName), "非法请求参数");
        ftsService.headDownloadFile(fileName, request, response);
    }

    @GetMapping("/uploadFile")
    public String uploadFile(Model model, @RequestParam String dirName, @RequestParam(required = false) Boolean uploadStatus,
                             @RequestParam(required = false) String uploadMessage) {
        Assert.isTrue(null != dirName && dirName.startsWith("/"), "非法请求参数");
        ftsService.ensureDirectoryExists(dirName);
        FtsServerIpInfoModel serverIpInfoModel = ftsService.getServerIpInfoModel();
        model.addAttribute("serverIpInfo", serverIpInfoModel);
        model.addAttribute("currentPath", dirName);
        if(uploadStatus != null) {
            model.addAttribute("uploadStatus", uploadStatus);
            model.addAttribute("uploadMessage", uploadMessage);
        }
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
    public String uploadFileTransfer(MultipartFile file, @RequestParam String dirName, RedirectAttributes redirectAttributes) throws UnsupportedEncodingException {
        Assert.isTrue(file != null && dirName != null && dirName.startsWith("/"), "非法请求参数");
        ReturnObject<Void> returnObject = ftsService.uploadFile(dirName, file);
        redirectAttributes.addFlashAttribute("uploadStatus", returnObject.isSuccess());
        redirectAttributes.addFlashAttribute("uploadMessage", returnObject.getMessage());
        return "redirect:/uploadFile?dirName=" + UriUtils.encode(dirName, "UTF-8");
    }
}
