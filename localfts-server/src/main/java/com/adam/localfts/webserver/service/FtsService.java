package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.*;
import com.adam.localfts.webserver.component.ShutdownListener;
import com.adam.localfts.webserver.exception.InvalidRangeException;
import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.adam.localfts.webserver.common.Constants.CRLF;
import static com.adam.localfts.webserver.common.Constants.DATE_FORMAT_FILE_STANDARD;

@Service
public class FtsService implements DisposableBean {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Value("${server.error.path:${error.path:/error}}")
    private String errorPath;
    @Autowired
    private ShutdownListener shutdownListener;

    private final Map<String, ReentrantLock> zipFileLockMap = new ConcurrentHashMap<>();
    private final Map<String, FolderCompressingInfo> folderCompressingInfoMap = new ConcurrentHashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(FtsService.class);

    public boolean checkDirectoryExists(String relativePath) {
        Assert.isTrue(relativePath != null && relativePath.startsWith("/"), "非法请求参数");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        File directory = IOUtil.getFile(rootPath + relativePath);
        return directory.exists() && directory.isDirectory();
    }

    public FtsPageModel getDirectoryModel(String relativePath, int pageNo, int pageSize) {
        Assert.isTrue(null != relativePath && relativePath.startsWith("/") && pageNo > 0 && pageSize > 0 && pageSize <= 50, "非法请求参数");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        File rootDirectory = new File(rootPath);
        Assert.isTrue(rootDirectory.exists() && rootDirectory.isDirectory(), "根路径不存在或不是文件夹");
        File zipDirectory = new File(zipFolderPath);
        String actualPath = rootPath + relativePath;
        File directory = IOUtil.getFile(actualPath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "非法的请求路径");
        FtsPageModel model = new FtsPageModel();
        model.setPath(relativePath);
        model.setCurrentPage(pageNo);
        model.setPageSize(pageSize);

        String zipFileParentRelativePath = zipFolderPath.substring(rootPath.length());
        FtsPageModel.FtsPageFileModel currentPathModel = model.new FtsPageFileModel();
        fillDirectoryModel(directory, zipDirectory, rootPath, zipFileParentRelativePath, currentPathModel);
        model.setCurrentPathModel(currentPathModel);

        File[] items = directory.listFiles();
        if(items == null || items.length == 0) {
            model.setCurrentSize(0);
            model.setTotalPage(0);
            model.setTotalSize(0);
            model.setData(null);
            return model;
        }
        int totalSize = items.length, totalPage = totalSize / pageSize + (totalSize % pageSize > 0 ? 1 : 0);
        model.setTotalSize(totalSize);
        model.setTotalPage(totalPage);
        if(pageNo > totalPage) {
            model.setCurrentSize(0);
            model.setData(null);
            return model;
        }

        int actualPageSize = pageNo == totalPage ? (totalSize - pageSize * (pageNo - 1)) : pageSize;
        model.setCurrentSize(actualPageSize);
        //左开右闭区间[lIndex,rIndex)
        int lIndex = pageSize * (pageNo - 1), rIndex = lIndex + actualPageSize;
        List<FtsPageModel.FtsPageFileModel> fileModels = new ArrayList<>(actualPageSize);
        for(int i = 0; i < items.length; i++) {
            if(i >= lIndex && i < rIndex) {
                File item = items[i];
                FtsPageModel.FtsPageFileModel fileModel = model.new FtsPageFileModel();
                boolean isDirectory = item.isDirectory();
                fileModel.setDirectory(isDirectory);
                fileModel.setFileName(item.getName());
                if(isDirectory) {
                    fillDirectoryModel(item, zipDirectory, rootPath, zipFileParentRelativePath, fileModel);
                } else {
                    fileModel.setFileSize(item.length());
                }
                fileModel.setFileSizeStr(Util.fileLengthToStringNew(fileModel.getFileSize()));
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT_FILE_STANDARD);
                fileModel.setLastModified(simpleDateFormat.format(new Date(item.lastModified())));
                fileModels.add(fileModel);
            }
        }
        model.setData(fileModels);
        return model;
    }

    private void fillDirectoryModel(File directory, File zipDirectory, String rootPath, String zipFileParentRelativePath, FtsPageModel.FtsPageFileModel fileModel) {
        fileModel.setFileSize(0);
        String folderAbsolutePath = directory.getAbsolutePath();
        //添加压缩文件标识
        FolderCompressStatus folderCompressStatus = getFolderCompressStatus(folderAbsolutePath, true);
        fileModel.setCompressStatus(folderCompressStatus);
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        File zipFile = new File(zipDirectory, zipFileName);
        String zipFileAbsolutePath = zipFile.getAbsolutePath();
        if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
            String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
            if(Util.isSystemWindows()) {
                zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
            }
            fileModel.setCompressedPath(zipFileRelativePath);
        }
    }

    public CompressManagementPageModel listCompressTask(int pageNo, int pageSize) {
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        FolderCompressCounter counter = new FolderCompressCounter();
        List<FolderCompressData> allList = folderCompressingInfoMap.entrySet().stream()
                .map(entry -> {
                    String folderAbsolutePath = entry.getKey();
                    FolderCompressingInfo folderCompressingInfo = entry.getValue();
                    String relativePath = folderAbsolutePath.substring(rootPath.length());
                    if(Util.isSystemWindows()) {
                        relativePath = relativePath.replaceAll("\\\\", "/");
                    }
                    FolderCompressStatus folderCompressStatus = getFolderCompressStatus(folderAbsolutePath, true);
                    counter.countFolder(folderCompressStatus);
                    FolderCompressData folderCompressData = new FolderCompressData();
                    String zipFileRelativePath = getFolderCompressedZipRelativePath(folderAbsolutePath, true);
                    folderCompressData.setPath(relativePath);
                    folderCompressData.setStatus(folderCompressStatus.name());
                    folderCompressData.setStatusDesc(folderCompressStatus.getDesc());
                    folderCompressData.setZipFilePath(zipFileRelativePath);
                    return folderCompressData;
                })
                .collect(Collectors.toList());
        CompressManagementPageModel pageModel = new CompressManagementPageModel(pageNo, pageSize, allList);
        pageModel.setTotalCount(counter.getTotalCount());
        pageModel.setNotCompressedCount(counter.getNotCompressedCount());
        pageModel.setCompressingCount(counter.getCompressingCount());
        pageModel.setCompressedCount(counter.getCompressedCount());
        return pageModel;
    }

    /**
     * 下载文件夹（zip压缩文件）
     * @param relativePath 相对于根路径的相对路径
     * @return zip压缩文件的相对路径
     */
    public ReturnObject<FolderCompressData> compressFolder(String relativePath) throws IOException {
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String zipMaxFolderSizeStr = ftsServerConfigService.getLocalFtsProperties().getZip().getMaxFolderSize();
        long start = System.currentTimeMillis();
        String actualFolderPath = rootPath + relativePath;
        File folderFile = new File(actualFolderPath);
        Assert.isTrue(folderFile.exists() && folderFile.isDirectory(), "非法的请求路径");

        String folderAbsolutePath = folderFile.getAbsolutePath();
        if(zipMaxFolderSizeStr != null) {
            DataSize zipMaxFolderSize = DataSize.parse(zipMaxFolderSizeStr);
            boolean checkFolderSizeGeLimit = IOUtil.isDirectorySizeGeIterative(folderAbsolutePath, zipMaxFolderSize.toBytes());
            if (checkFolderSizeGeLimit) {
                return ReturnObject.fail("待压缩的文件夹大小超过" + zipMaxFolderSizeStr + "限制，无法压缩");
            }
        }
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        String zipFileParentRelativePath = zipFolderPath.substring(rootPath.length());
        String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
        if(Util.isSystemWindows()) {
            zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
        }
        File rootPathFile = new File(rootPath);
        File zipFile = new File(rootPathFile, zipFileRelativePath);
        boolean zipFileExists = zipFile.exists();
        Assert.isTrue(!zipFileExists || zipFile.isFile(), "压缩文件路径下存在同名文件夹，无法压缩到指定位置");

        String zipFileAbsolutePath = zipFile.getAbsolutePath();
        boolean needCompress = false;
        boolean interrupted = false;
        if(!zipFileExists) {
            ReentrantLock lock = getZipFileLock(zipFileAbsolutePath);
            try {
                lock.lock();
                if (!zipFile.exists()) {
                    needCompress = true;
                    FolderCompressingInfo folderCompressingInfo = new FolderCompressingInfo(-1L);
                    folderCompressingInfoMap.put(folderAbsolutePath, folderCompressingInfo);
                    IOUtil.compressFolderAsZip(folderAbsolutePath, zipFolderPath, zipFileName);
                    folderCompressingInfo.setCompressSize(zipFile.length());
                    folderCompressingInfo.setExecuteThread(null);
                    if(!folderCompressingInfoMap.containsKey(folderAbsolutePath)) {
                        LOGGER.warn("FolderCompressingInfo {} has been removed", zipFileAbsolutePath);
                        folderCompressingInfoMap.put(folderAbsolutePath, folderCompressingInfo);
                    }
                }
            } catch (InterruptedException e) {
                interrupted = true;
                LOGGER.info("interrupt folder compressing:{} -- delete zip file:{}", folderAbsolutePath, zipFileAbsolutePath);
                boolean deletes = zipFile.delete();
                if(!deletes) {
                    LOGGER.warn("delete zip file failed:{}", zipFileAbsolutePath);
                } else {
                    LOGGER.info("successfully deleted zip file:{}", zipFileAbsolutePath);
                }
                folderCompressingInfoMap.remove(folderAbsolutePath);
            } finally {
                lock.unlock();
            }
        }

        FolderCompressStatus folderCompressStatus = getFolderCompressStatus(relativePath, false);
        if(needCompress) {
            long end = System.currentTimeMillis();
            String statusMessage = interrupted ? "被中断" : "完成";
            LOGGER.info("压缩文件夹（路径：{}）{}，压缩文件路径：{}，耗时{}毫秒", folderAbsolutePath, statusMessage, zipFileAbsolutePath, end - start);
        }
        if(folderCompressStatus == FolderCompressStatus.NOT_COMPRESSED) {
            String reason = interrupted ? "压缩任务被取消" : "未知原因";
            return ReturnObject.fail(reason);
        } else {
            FolderCompressData data = new FolderCompressData();
            if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
//                data.setPath(zipFileRelativePath);
                data.setPath(relativePath);
                data.setZipFilePath(zipFileRelativePath);
            }
            data.setStatus(folderCompressStatus.name());
            return ReturnObject.success(data);
        }
    }

    public ReturnObject<Void> cancelCompress(String relativePath) {
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFolderPath = rootPath + relativePath;
        File folderFile = new File(actualFolderPath);
        String folderAbsolutePath = folderFile.getAbsolutePath();

        FolderCompressingInfo folderCompressingInfo = folderCompressingInfoMap.get(folderAbsolutePath);
        if(folderCompressingInfo == null) {
            LOGGER.warn("FolderCompressingInfo not found for {}, no need to cancel", folderAbsolutePath);
            return ReturnObject.success();
        }

        boolean interrupt = folderCompressingInfo.interruptThread();
        if(interrupt) {
            return ReturnObject.success();
        } else {
            return ReturnObject.fail("取消压缩失败，可能已完成压缩");
        }
    }

    public ReturnObject<Void> deleteCompressFile(String relativePath) {
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFolderPath = rootPath + relativePath;
        File folderFile = new File(actualFolderPath);
        String folderAbsolutePath = folderFile.getAbsolutePath();
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        String zipFileParentRelativePath = zipFolderPath.substring(rootPath.length());
        String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;

        File rootPathFile = new File(rootPath);
        File zipFile = new File(rootPathFile, zipFileRelativePath);
        FolderCompressingInfo folderCompressingInfo = folderCompressingInfoMap.get(folderAbsolutePath);
        if(folderCompressingInfo == null) {
            LOGGER.warn("folder {} zip task does not exist, no need to delete", folderAbsolutePath);
            return ReturnObject.success();
        }

        FolderCompressStatus folderCompressStatus = getFolderCompressStatus(relativePath, false);
        boolean interrupt = false;
        if(folderCompressStatus == FolderCompressStatus.COMPRESSING) {
            interrupt = folderCompressingInfo.interruptThread();
            if(!interrupt) {
                return ReturnObject.fail("取消压缩任务失败");
            }
        }

        if(!zipFile.exists()) {
            LOGGER.warn("(folder={})zip file {} does not exist, no need to delete", folderAbsolutePath, zipFile.getAbsolutePath());
            return ReturnObject.success();
        }

        boolean deletes = zipFile.delete();
        if(deletes) {
            folderCompressingInfoMap.remove(folderAbsolutePath);
            return ReturnObject.success();
        } else {
            return ReturnObject.fail("文件可能正在被占用");
        }
    }

    public String getFolderCompressedZipRelativePath(String path, boolean isAbsolute) {
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        long start = System.currentTimeMillis();
        String actualFolderPath = path;
        if(!isAbsolute) {
            actualFolderPath = rootPath + path;
        }
        File folderFile = new File(actualFolderPath);
        Assert.isTrue(folderFile.exists() && folderFile.isDirectory(), "非法的请求路径");

        String folderAbsolutePath = folderFile.getAbsolutePath();
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        String zipFileParentRelativePath = zipFolderPath.substring(rootPath.length());
        String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
        if(Util.isSystemWindows()) {
            zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
        }
        return zipFileRelativePath;
    }

    public FolderCompressStatus getFolderCompressStatus(String path, boolean isAbsolute) {
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        long start = System.currentTimeMillis();
        String actualFolderPath;
        if(isAbsolute) {
            actualFolderPath = path;
        } else {
            actualFolderPath = rootPath + path;
        }
        File folderFile = new File(actualFolderPath);
        Assert.isTrue(folderFile.exists() && folderFile.isDirectory(), "非法的请求路径");

        String folderAbsolutePath = folderFile.getAbsolutePath();
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        String zipFileParentRelativePath = zipFolderPath.substring(rootPath.length());
        String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
        if(Util.isSystemWindows()) {
            zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
        }
        File rootPathFile = new File(rootPath);
        File zipFile = new File(rootPathFile, zipFileRelativePath);
        boolean zipFileExists = zipFile.exists();

        if(!zipFileExists) {
            return FolderCompressStatus.NOT_COMPRESSED;
        } else {
            FolderCompressingInfo folderCompressingInfo = folderCompressingInfoMap.get(folderAbsolutePath);
            long recordSize = folderCompressingInfo != null ? folderCompressingInfo.getCompressSize() : -1L;
            long zipFileSize = zipFile.length();
            if(recordSize == zipFileSize) {
                return FolderCompressStatus.COMPRESSED;
            } else {
                return FolderCompressStatus.COMPRESSING;
            }
        }
    }

    private ReentrantLock getZipFileLock(String zipFileAbsolutePath) {
        ReentrantLock lock = zipFileLockMap.get(zipFileAbsolutePath);
        if (lock == null) {
            lock = new ReentrantLock();
            ReentrantLock existingLock = zipFileLockMap.putIfAbsent(zipFileAbsolutePath, lock);
            if (existingLock != null) {
                lock = existingLock; // 若并发插入，使用已存在的锁
            }
        }
        return lock;
    }

    public void headDownloadFile(String filePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        IOUtil.debugPrintSelectedRequestHeaders(request, LOGGER, "headDownloadFile");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFilePath = rootPath + filePath;
        File file = IOUtil.getFile(actualFilePath);
        Assert.isTrue(file.exists() && file.isFile() && file.canRead(), "非法的请求路径");
        String fileName = filePath.substring(filePath.lastIndexOf("/")+1);
        response.reset();
        response.setCharacterEncoding("UTF-8");
        response.addHeader("Content-Disposition", "attachment;filename=" + UriUtils.encode(fileName, "UTF-8"));
        response.setContentType("application/octet-stream");
        response.addHeader("Accept-Ranges", "bytes");
        response.addHeader("Content-Length", String.valueOf(file.length()));
    }

    public void downloadFile(String filePath, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        IOUtil.debugPrintSelectedRequestHeaders(request, LOGGER, "downloadFile");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFilePath = rootPath + filePath;
        File file = IOUtil.getFile(actualFilePath);
        if(!file.exists()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            request.setAttribute("javax.servlet.error.status_code", HttpStatus.NOT_FOUND.value());
            request.getRequestDispatcher(errorPath).forward(request, response);
            return;
        }
        Assert.isTrue(file.isFile() && file.canRead(), "非法的请求路径:" + actualFilePath);
        String fileName = filePath.substring(filePath.lastIndexOf("/")+1);
        long fileLength = file.length();
        long fileLastModified = file.lastModified();

        boolean isSendCompleteFile = false;
        Long ifRangeHeaderLong = null;
        try {
            ifRangeHeaderLong = IOUtil.getDateHeaderIgnoreCase(request, "If-Range");
            if(ifRangeHeaderLong != null) {
                isSendCompleteFile = fileLastModified / 1000 != ifRangeHeaderLong / 1000;
            }
        } catch (IllegalArgumentException e) {
            //忽略If-Range头
        }
        HttpRangeObject httpRangeObject = null;
        if(!isSendCompleteFile) {
            String rangeHeader = IOUtil.getHeaderIgnoreCase(request, "Range");
            if (rangeHeader != null) {
                try {
                    httpRangeObject = Util.resolveHttpRangeHeader(rangeHeader, fileLength);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("解析Http Range头时出错:{}", e.getMessage());
                    response.setStatus(HttpStatus.BAD_REQUEST.value());
                    return;
                } catch (InvalidRangeException e) {
                    LOGGER.warn("Range头数据不满足条件：{}", e.getMessage());
                    response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                    response.addHeader("Content-Range", "bytes */" + fileLength);
                    return;
                }
            } else {
                isSendCompleteFile = true;
            }
        }
        if(!isSendCompleteFile) {
            LOGGER.info("开始下载文件{}：【{}】[{}][{}]", httpRangeObject.isMultipleRange() ? "(多分段)" : "(分段)",
                    file.getAbsolutePath(), fileLength, httpRangeObject.getOriginalString());
        } else {
            LOGGER.info("开始下载文件：【{}】[{}]", file.getAbsolutePath(), fileLength);
        }

//        String userAgentHeader = IOUtil.getHeaderIgnoreCase(request, "user-agent");

        InputStream inputStream = null;
        OutputStream outputStream = null;
        RandomAccessFile randomAccessFile = null;
        try {
            String encodedFileName = UriUtils.encode(fileName, "UTF-8");
//            if(userAgentHeader != null && userAgentHeader.contains("MSIE")) {
//                encodedFileName = UriUtils.encode(fileName.replaceAll("：", " "), "UTF-8");
//            }
            response.reset();
            //response.setCharacterEncoding("UTF-8");
            response.addHeader("Content-Disposition", "attachment;filename=\"" + encodedFileName + "\";filename*=UTF-8''" + encodedFileName);
            response.addHeader("Accept-Ranges", "bytes");
//            response.setDateHeader("Date", new Date().getTime());
            response.setDateHeader("Last-Modified", file.lastModified());

            if(isSendCompleteFile) {
                response.setStatus(HttpStatus.OK.value());
                response.addHeader("Content-Length", "" + fileLength);
                response.setContentType("application/octet-stream");
                inputStream = new BufferedInputStream(new FileInputStream(file));
                outputStream = new BufferedOutputStream(response.getOutputStream());
                IOUtil.transfer(inputStream, outputStream);
                LOGGER.info("下载文件完成：【{}】[{}],用时{}毫秒", file.getAbsolutePath(), fileLength, (System.currentTimeMillis() - start));
            } else {
                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                if (!httpRangeObject.isMultipleRange()) {
                    response.setContentType("application/octet-stream");

                    HttpRangeObject.Range singleRange = httpRangeObject.get(0);
                    long lowerRange = singleRange.getActualLower(),
                            upperRange = singleRange.getActualUpper();
                    response.addHeader("Content-Range", "bytes " + lowerRange + "-" + upperRange + "/" + fileLength);
                    response.addHeader("Content-Length", String.valueOf(upperRange - lowerRange + 1));
                    randomAccessFile = new RandomAccessFile(file, "r");
                    outputStream = new BufferedOutputStream(response.getOutputStream());
                    IOUtil.transfer(randomAccessFile, outputStream, lowerRange, upperRange, true);

                    LOGGER.info("分段下载文件完成：【{}】[{}][{}],用时{}毫秒", file.getAbsolutePath(), fileLength,
                            httpRangeObject.getOriginalString(), (System.currentTimeMillis() - start));
                } else {
                    String boundary = UUID.randomUUID().toString();
                    response.setContentType("multipart/byteranges; boundary=" + boundary);
                    response.addHeader("Content-Length", String.valueOf(Util.
                            calcMultipleRangeResponseContentLength(httpRangeObject, fileLength, boundary)));
                    randomAccessFile = new RandomAccessFile(file, "r");
                    outputStream = new BufferedOutputStream(response.getOutputStream());
                    for(HttpRangeObject.Range range: httpRangeObject.getRangeList()) {
                        long lowerRange = range.getActualLower(), upperRange = range.getActualUpper();
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("--").append(boundary).append(CRLF)
                                .append("Content-Type: application/octet-stream").append(CRLF)
                                .append("Content-Range: bytes ").append(lowerRange).append("-").append(upperRange).append("/").append(fileLength).append(CRLF)
                                .append(CRLF);
                        outputStream.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
                        IOUtil.transfer(randomAccessFile, outputStream, lowerRange, upperRange, false);
                        outputStream.write(CRLF.getBytes(StandardCharsets.UTF_8));
                    }
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("--").append(boundary).append("--").append(CRLF);
                    outputStream.write(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();

                    LOGGER.info("多分段下载文件完成：【{}】[{}][{}],用时{}毫秒", file.getAbsolutePath(), fileLength,
                            httpRangeObject.getOriginalString(), (System.currentTimeMillis() - start));
                }
            }
        } catch (IOException e) {
            String cause = null;
            if(e instanceof ClientAbortException) {
                cause = "远程主机关闭连接";
            }
            String rangeMessage = isSendCompleteFile ? "" : (httpRangeObject.isMultipleRange() ? "(多分段)" : "(分段)");
            String rangeOriginalString = isSendCompleteFile ? "" : "[" + httpRangeObject.getOriginalString() + "]";
            if(cause != null) {
                LOGGER.error("异常：{}下载文件【{}】{}时发生异常，原因：{}", rangeMessage, filePath, rangeOriginalString, cause);
            } else {
                LOGGER.error("异常：{}下载文件【{}】{}时发生异常", rangeMessage, filePath, rangeOriginalString, e);
            }
        } finally {
            IOUtil.closeStream(inputStream);
            IOUtil.closeStream(outputStream);
            IOUtil.closeRandomAccessFile(randomAccessFile);
        }
    }

    public ReturnObject<String> uploadFile(String dirName, MultipartFile file) {
        Assert.isTrue(dirName != null && dirName.startsWith("/") && file != null, "非法请求参数");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        File directory = IOUtil.getFile(rootPath + dirName);
        if(!directory.exists()) {
            return ReturnObject.fail("请求路径不存在");
        }
        if(!directory.isDirectory()) {
            return ReturnObject.fail("请求路径不是文件夹");
        }
        return transferFile(rootPath, dirName, file, true);
    }

    public ReturnObject<List<ReturnObject<String>>> uploadFiles(String dirName, MultipartFile[] files) {
        long start = System.currentTimeMillis();
        Assert.isTrue(dirName != null && dirName.startsWith("/") && files != null, "非法请求参数");

        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        File directory = IOUtil.getFile(rootPath + dirName);
        if(!directory.exists()) {
            return ReturnObject.fail("请求路径不存在");
        }
        if(!directory.isDirectory()) {
            return ReturnObject.fail("请求路径不是文件夹");
        }

        List<ReturnObject<String>> returnObjectList = new LinkedList<>();
        int successCount = 0;
        for(MultipartFile file: files) {
            ReturnObject<String> innerReturnObject = transferFile(rootPath, dirName, file, false);
            returnObjectList.add(innerReturnObject);
            if(innerReturnObject.isSuccess()) {
                successCount++;
            }
        }
        String returnObjectMessage = "上传" + files.length + "个文件，" + successCount + "个成功，" + (files.length - successCount) + "个失败";
        LOGGER.info("上传{}个文件到路径'{}'完成!总耗时{}毫秒", files.length, dirName, (System.currentTimeMillis() - start));
        return ReturnObject.success(returnObjectMessage, returnObjectList);
    }

    private String folderPathToZipFileName(String folderPath, String rootPath) {
        String processedPath = folderPath;
        if(processedPath.startsWith(rootPath)) {
            processedPath = processedPath.substring(rootPath.length());
        }
        if(processedPath.startsWith(File.separator)) {
            processedPath = processedPath.substring(File.separator.length());
        }
        String zipFileName = null;
        if(Util.isSystemWindows()) {
            zipFileName = processedPath.replaceFirst(":", "").replaceAll("\\\\", "_");
        } else if(Util.isSystemLinux() || Util.isSystemMacOS()) {
            if(processedPath.startsWith("/")) {
                processedPath = processedPath.substring(1);
            }
            zipFileName = processedPath.replaceAll("/", "_");
        }
        return zipFileName + ".zip";
    }

    /**
     * 将文件写入到rootPath下的dirName路径下
     * @param rootPath
     * @param dirName
     * @param file
     * @param createFileDirectly true:略过中间文件夹创建，直接在dirName路径创建文件
     * @return
     */
    private ReturnObject<String> transferFile(final String rootPath, final String dirName, final MultipartFile file, boolean createFileDirectly) {
        long start = System.currentTimeMillis();
        File directory = IOUtil.getFile(rootPath + dirName);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "Invalid dirName '" + dirName + "' in root path '" + rootPath + "'");

        String originalFilename = file.getOriginalFilename() == null ? "未知文件" : file.getOriginalFilename();
//        if(file.getSize() > uploadFileLimit) {
//            returnObject.setSuccess(false);
//            returnObject.setMessage("待上传的文件大小超过系统限制");
//            return returnObject;
//        }

        String filePath = originalFilename;
        if(createFileDirectly) {
            if(filePath.startsWith("/")) {
                filePath = filePath.substring(1);
            }
            if(filePath.contains("/")) {
                filePath = filePath.substring(filePath.lastIndexOf('/') + 1);
            }
        }
        File actualFile = new File(directory, filePath);
        if(actualFile.exists()) {
            return ReturnObject.fail("请求路径下已存在同名文件", filePath);
        }

        if(!createFileDirectly) {
            String parentPath = originalFilename;
            if(parentPath.startsWith("/")) {
                parentPath = parentPath.substring(1);
            }
            if(parentPath.contains("/")) {
                parentPath = parentPath.substring(0, parentPath.lastIndexOf('/'));
            }
            boolean checkMiddlePathExistsAsFile = IOUtil.checkMiddlePathExistsAsFile(directory, parentPath);
            if(checkMiddlePathExistsAsFile) {
                return ReturnObject.fail("请求路径下的子路径存在同名文件，无法创建文件夹", filePath);
            }

            File actualParentDirectory = new File(directory, parentPath);
            if(!actualParentDirectory.exists()) {
                boolean mkdirs = actualParentDirectory.mkdirs();
                if (!mkdirs) {
                    return ReturnObject.fail("在请求路径下创建文件夹失败", filePath);
                } else {
                    LOGGER.info("成功在根路径'{}'下的路径'{}'中创建父级文件夹'{}'", rootPath, dirName, parentPath);
                }
            }
        }
        LOGGER.info("开始上传文件'{}'到路径'{}'", filePath, dirName);
        try {
            file.transferTo(actualFile);
            LOGGER.info("上传文件'{}'到路径'{}'成功!耗时{}毫秒", filePath, dirName, (System.currentTimeMillis() - start));
            return ReturnObject.success(filePath);
        } catch (IOException e) {
            LOGGER.error("上传文件'{}'到路径'{}'时出错", filePath, dirName, e);
            return ReturnObject.fail(e.getMessage(), filePath);
        }
    }

    @Override
    public void destroy() throws Exception {
        if(shutdownListener.getWebServer() != null) {
            //清理压缩文件夹
            Boolean deleteOnExit = ftsServerConfigService.getLocalFtsProperties().getZip().getDeleteOnExit();
            if (deleteOnExit != null && deleteOnExit) {
                String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
                File zipFolderFile = new File(zipFolderPath);
                if (zipFolderFile.exists() && zipFolderFile.isDirectory()) {
//                FileUtils.deleteDirectory(zipFolderFile);
                    LOGGER.info("Deleting zip file folder {}", zipFolderPath);
                    IOUtil.deleteDirectory(zipFolderPath, true);
                } else if (zipFolderFile.exists()) {
                    LOGGER.info("Deleting zip file {}", zipFolderPath);
                    zipFolderFile.delete();
                }
            }
        }
    }
}
