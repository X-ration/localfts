package com.adam.localfts.webserver.service;

import com.adam.localfts.webserver.common.*;
import com.adam.localfts.webserver.common.compress.*;
import com.adam.localfts.webserver.common.search.SearchFileModel;
import com.adam.localfts.webserver.common.sort.CompressManagementColumn;
import com.adam.localfts.webserver.common.sort.ListTableColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import com.adam.localfts.webserver.config.properties.IndexFileContentProperties;
import com.adam.localfts.webserver.exception.InvalidRangeException;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.util.IOUtil;
import com.adam.localfts.webserver.util.Util;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;
import ua_parser.Client;
import ua_parser.Parser;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.adam.localfts.webserver.common.Constants.CRLF;

@Service
public class FtsService {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;
    @Value("${server.error.path:${error.path:/error}}")
    private String errorPath;

    private final Map<String, ReentrantLock> zipFileLockMap = new ConcurrentHashMap<>();
    private final ReadWriteLock zipPathSelfGlobalLock = new ReentrantReadWriteLock();
    private final Map<String, FolderCompressingContextHolder> folderCompressingContextHolderMap = new ConcurrentHashMap<>();
    private final Map<ListTableColumn, Comparator<FtsPageModel.FtsPageFileModel>> listTableComparatorMap = new HashMap<>();
    private final Map<CompressManagementColumn, Comparator<FolderCompressDTO>> compressManagementComparatorMap = new HashMap<>();
    private final Collator CHINESE_COLLATOR = Collator.getInstance(Locale.CHINA);

    private static final Logger LOGGER = LoggerFactory.getLogger(FtsService.class);

    public ReturnObject<Void> createFolder(String relativePath, String folderName) {
        Assert.isTrue(relativePath != null && relativePath.startsWith("/"), "非法请求参数(relativePath)");
        Assert.notNull(folderName, "非法请求参数(folderName:null)");

        String fileInvalidCharacterString = getFileInvalidCharacterString();
        String[] ficsArr = fileInvalidCharacterString.split(" ");
        for(String fics: ficsArr) {
            if(folderName.contains(fics)) {
                return ReturnObject.fail("文件夹名称包含非法字符" + fics);
            }
        }

        if(folderName.length() > 200) {
            return ReturnObject.fail("文件夹名称长度超过200");
        }

        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        File rootDirectory = IOUtil.getFile(rootPath);
        File directory = new File(rootDirectory, relativePath);
        if(!directory.exists()) {
            return ReturnObject.fail("目标路径不存在");
        }
        if(directory.isFile()) {
            return ReturnObject.fail("目标路径不是文件夹");
        }
        if((directory.getAbsolutePath() + File.separator + folderName).length() > 240) {
            return ReturnObject.fail("待创建的文件夹全路径名长度超过240");
        }

        File newDirectory = new File(directory, folderName);
        if(newDirectory.exists()) {
            return ReturnObject.fail("目标路径下已存在同名文件或文件夹");
        }

        try {
            boolean mkdir = newDirectory.mkdir();
            if (mkdir) {
                return ReturnObject.success();
            } else {
                return ReturnObject.fail("创建文件夹失败");
            }
        } catch (SecurityException e) {
            LOGGER.error("Creating folder '{}' under '{}' encountered SecurityException", folderName, relativePath, e);
            return ReturnObject.fail("创建文件夹失败(SecurityException)");
        }
    }

    public boolean checkDirectoryExists(String path, boolean isAbsolute) {
        if(!isAbsolute) {
            Assert.isTrue(path != null && path.startsWith("/"), "非法请求参数");
        }
        String actualPath = path;
        if(!isAbsolute) {
            String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
            actualPath = rootPath + path;
        }
        File directory = IOUtil.getFile(actualPath);
        return directory.exists() && directory.isDirectory();
    }

    public ReturnObject<Map<String, Boolean>> checkPathExists(String[] relativePaths) {
        Assert.isTrue(relativePaths != null, "relativePaths is null!");
        Map<String, Boolean> pathExistsMap = new HashMap<>();
        for(String relativePath: relativePaths) {
            boolean pathExists = checkDirectoryExists(relativePath, false);
            pathExistsMap.put(relativePath, pathExists);
        }
        return ReturnObject.success(pathExistsMap);
    }

    public ReturnObject<FtsSubDirectoryModel> getSubDirectoryModel(final String relativePath, boolean fromRoot) {
        boolean condition = null != relativePath && relativePath.startsWith("/");
        if(!condition) {
            return ReturnObject.fail("非法请求参数");
        }
        Matcher matcher = ftsServerConfigService.getStandardRelativePathPattern().matcher(relativePath);
        if(!matcher.matches()) {
            return ReturnObject.fail("请求路径参数不符合规则");
        }
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        File rootDirectory = new File(rootPath);
        condition = rootDirectory.exists() && rootDirectory.isDirectory();
        if(!condition) {
            return ReturnObject.fail("根路径不存在或不是文件夹");
        }
        File directory = new File(rootDirectory, relativePath);
        condition = directory.exists() && directory.isDirectory();
        if(!condition) {
            return ReturnObject.fail("请求路径不存在或不是文件夹");
        }

        FtsSubDirectoryModel model = new FtsSubDirectoryModel();
        String[] pathSplits = relativePath.split("/");
        List<String> pathList = Arrays.stream(pathSplits).filter(str -> !StringUtils.isEmpty(str)).collect(Collectors.toList());
        File directoryToUse = fromRoot ? rootDirectory : directory;
        String relativePathToUse = fromRoot ? null : relativePath;
        setSubDirectoryModel(relativePathToUse, directoryToUse, model, pathList, 0, false, fromRoot);

        return ReturnObject.success(model);
    }

    private void setSubDirectoryModel(final String relativePath, final File directory, final FtsSubDirectoryModel model,
                                      final List<String> pathList, final int pathListIndex, final boolean isRecursiveCall,
                                      final boolean fromRoot) {
        String newRelativePath;
        if(!isRecursiveCall) {
            if(fromRoot) {
                model.setName("/");
                newRelativePath = "/";
            } else {
                model.setName(directory.getName());
                newRelativePath = relativePath;
            }
        } else {
            model.setName(directory.getName());
            if(relativePath.equals("/")) {
                newRelativePath = relativePath + directory.getName();
            } else {
                newRelativePath = relativePath + "/" + directory.getName();
            }
        }
        model.setRelativePath(newRelativePath);
        String path = null;
        if(isRecursiveCall) {
            if(pathListIndex < pathList.size() && pathListIndex >= 0) {
                path = pathList.get(pathListIndex);
            }
            if(path == null || !path.equals(directory.getName())) {
                return;
            }
        }

        List<FtsSubDirectoryModel> subModelList = new LinkedList<>();
        File[] subDirectories = directory.listFiles(f -> f.exists() && f.isDirectory());
        int nextPathListIndex = isRecursiveCall ? pathListIndex + 1 : pathListIndex;
        if(subDirectories != null) {
            for(File subDirectory : subDirectories) {
                FtsSubDirectoryModel subModel = new FtsSubDirectoryModel();
                setSubDirectoryModel(newRelativePath, subDirectory, subModel, pathList, nextPathListIndex, true, fromRoot);
                subModelList.add(subModel);
            }
        }
        model.setSubModelList(subModelList);
    }

    /**
     * 只在应用启动时调用一次，遍历根路径的文件。不应暴露给用户
     * @param function
     * @return
     */
    public void scanAndApplySearchFileModel(VoidFunction<SearchFileModel> function) {
        Util.incrementAndCheckMethodCallCount("scanAndApplySearchFileModel", 1);
        scanAndApplySearchFileModel(null, function);
    }

    public void scanAndApplySearchFileModel(File directory, VoidFunction<SearchFileModel> function) {
        Assert.notNull(function, "function is null!");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        File rootDirectory = new File(rootPath);
        Assert.isTrue(rootDirectory.exists() && rootDirectory.isDirectory(), "根路径不存在或不是文件夹");
        if(directory == null) {
            directory = rootDirectory;
        } else {
            Assert.isTrue(directory.exists() && directory.isDirectory(), "扫描路径" + directory.getAbsolutePath() + "不存在或不是文件夹");
            Assert.isTrue(directory.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath()), "扫描路径" + directory.getAbsolutePath() + "不是根路径的子文件夹");
        }
        File zipDirectory = new File(rootDirectory, zipFolderPath);
        String zipFileParentRelativePath = getZipFileParentRelativePath(zipFolderPath);
        IndexFileContentProperties indexFileContentProperties = ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent();
        Integer maxStringLength = indexFileContentProperties.getMaxStringLength();
        boolean requireFileContent = indexFileContentProperties.getEnabled();
        boolean tryReadAllFiles = indexFileContentProperties.getTryReadAllFiles();
        scanAndApplySearchFileModel(directory, zipDirectory, function, rootPath, zipFileParentRelativePath, requireFileContent, maxStringLength, tryReadAllFiles, directory != rootDirectory);
    }

    private void scanAndApplySearchFileModel(File directory, File zipDirectory, VoidFunction<SearchFileModel> function,
                                             String rootPath, String zipFileParentRelativePath, boolean requireFileContent, Integer maxStringLength, boolean tryReadAllFiles, boolean indexDirectory) {
        if(!directory.exists()) {
            return;
        }
        if(directory.isFile()) {
            LOGGER.warn("Path {} is not a directory!", directory.getAbsolutePath());
            return;
        }
        if(indexDirectory) {
            SearchFileModel model = new SearchFileModel();
            model.setFileName(directory.getName());
            model.setDirectory(true);
            model.setFileSize(0L);
            model.setLastModified(directory.lastModified());
            setParentRelativePath(model, directory, rootPath);
            fillSearchModelCompress(directory, zipDirectory, rootPath, zipFileParentRelativePath, model);
            function.apply(model);
        }
        File[] items = directory.listFiles();
        if(items == null || items.length == 0) {
            return;
        }
        for(File item: items) {
            if(item.isFile()) {
                SearchFileModel model = new SearchFileModel();
                model.setFileName(item.getName());
                model.setDirectory(false);
                setParentRelativePath(model, item, rootPath);
                if(requireFileContent && item.length() > 0) {
                    String fileContent = getFileContent(item, tryReadAllFiles, maxStringLength);
                    model.setFileContent(fileContent);
                }
                model.setFileSize(item.length());
                model.setLastModified(item.lastModified());
                function.apply(model);
            } else {
                scanAndApplySearchFileModel(item, zipDirectory, function, rootPath, zipFileParentRelativePath,
                        requireFileContent, maxStringLength, tryReadAllFiles, true);
            }
        }
    }

    public void convertAndApplySearchFileModel(File file, VoidFunction<SearchFileModel> function) {
        if(!file.exists()) {
            return;
        }
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        File rootDirectory = new File(rootPath);
        Assert.isTrue(rootDirectory.exists() && rootDirectory.isDirectory(), "根路径不存在或不是文件夹");
        File zipDirectory = new File(rootDirectory, zipFolderPath);
        String zipFileParentRelativePath = getZipFileParentRelativePath(zipFolderPath);
        IndexFileContentProperties indexFileContentProperties = ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent();
        Integer maxStringLength = indexFileContentProperties.getMaxStringLength();
        boolean requireFileContent = indexFileContentProperties.getEnabled();
        boolean tryReadAllFiles = indexFileContentProperties.getTryReadAllFiles();

        if(file.isDirectory()) {
            SearchFileModel model = new SearchFileModel();
            model.setFileName(file.getName());
            model.setDirectory(true);
            model.setFileSize(0L);
            model.setLastModified(file.lastModified());
            setParentRelativePath(model, file, rootPath);
            fillSearchModelCompress(file, zipDirectory, rootPath, zipFileParentRelativePath, model);
            function.apply(model);
        } else {
            SearchFileModel model = new SearchFileModel();
            model.setFileName(file.getName());
            model.setDirectory(false);
            setParentRelativePath(model, file, rootPath);
            if(requireFileContent && file.length() > 0) {
                String fileContent = getFileContent(file, tryReadAllFiles, maxStringLength);
                model.setFileContent(fileContent);
            }
            model.setFileSize(file.length());
            model.setLastModified(file.lastModified());
            function.apply(model);
        }
    }

    private String getFileContent(File file, boolean tryReadAllFiles, int maxStringLength) {
        try {
            String fileContent = null;
            if(file.getName().toLowerCase().endsWith(".class")) {
                fileContent = IOUtil.getClassFileContent(file);
            } else if(tryReadAllFiles || isFileReadable(file.getName())) {
                if(isFilePlainReadable(file.getName())) {
                    fileContent = IOUtil.getFileContentPlain(file);
                } else {
                    fileContent = IOUtil.getFileContentTika(file, maxStringLength);
                }
                if(fileContent != null) {
                    fileContent = fileContent.replaceAll("(?m)^\\s+$", "") // 清空空白行
                            .replaceAll("(\\r\\n|\\r|\\n){2,}", "\n")
                            .replaceAll("\\r\\n|\\r|\\n", System.lineSeparator()) // 统一所有换行
                            .replaceAll("\\u00A0", " ")                // NBSP → 普通空格
                            .trim();
                }
            }
            return fileContent;
        } catch (IOException | TikaException e) {
            LOGGER.warn("Exception occured getting file content of {}, ex type:{}, msg:{}",
                    file.getAbsolutePath(), e.getClass().getName(), e.getMessage());
            return null;
        }
    }

    public String getParentRelativePath(File file) {
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String parentRelativePath = file.getAbsolutePath().substring(rootPath.length())
                .replace(File.separator, "/");
        parentRelativePath = parentRelativePath.substring(0, parentRelativePath.length() - file.getName().length());
        if(!parentRelativePath.equals("/")) {
            parentRelativePath = parentRelativePath.substring(0, parentRelativePath.length() - 1);
        }
        return parentRelativePath;
    }

    private void setParentRelativePath(SearchFileModel model, File file, String rootPath) {
        String parentRelativePath = file.getAbsolutePath().substring(rootPath.length())
                .replace(File.separator, "/");
        parentRelativePath = parentRelativePath.substring(0, parentRelativePath.length() - file.getName().length());
        if(!parentRelativePath.equals("/")) {
            parentRelativePath = parentRelativePath.substring(0, parentRelativePath.length() - 1);
        }
        model.setParentRelativePath(parentRelativePath);
    }

    private boolean isFilePlainReadable(String fileName) {
        return checkFileExtIgnoreCase(fileName, Constants.PLAIN_READABLE_FILE_EXTS);
    }

    private boolean isFileReadable(String fileName) {
        return checkFileExtIgnoreCase(fileName, Constants.READABLE_FILE_EXTS);
    }

    private boolean checkFileExtIgnoreCase(String fileName, String[] exts) {
        Assert.notNull(fileName, "fileName is null!");
        Assert.notNull(exts, "exts is null!");
        for(String ext: exts) {
            if(!ext.startsWith(".")) {
                ext = "." + ext;
            }
            if(fileName.toLowerCase().endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public FtsPageModel getDirectoryModel(String relativePath, int pageNo, int pageSize, ListTableColumn sortColumn, SortOrder sortOrder) {
        Assert.isTrue(null != relativePath && relativePath.startsWith("/") && pageNo > 0 && pageSize > 0 && pageSize <= 50, "非法请求参数");
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        File rootDirectory = new File(rootPath);
        Assert.isTrue(rootDirectory.exists() && rootDirectory.isDirectory(), "根路径不存在或不是文件夹");
        boolean zipEnabled = ftsServerConfigService.getLocalFtsProperties().getZip().getEnabled();
        File zipDirectory = new File(rootDirectory, zipFolderPath);
        String actualPath = rootPath + relativePath;
        File directory = IOUtil.getFile(actualPath);
        Assert.isTrue(directory.exists() && directory.isDirectory(), "非法的请求路径");
        FtsPageModel model = new FtsPageModel();
        model.setPath(relativePath);
        model.setCurrentPage(pageNo);
        model.setPageSize(pageSize);

        SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();

        String zipFileParentRelativePath = getZipFileParentRelativePath(zipFolderPath);
        FtsPageModel.FtsPageFileModel currentPathModel = model.new FtsPageFileModel();
        currentPathModel.setFileSize(0);
        if(zipEnabled) {
            fillDirectoryModelCompress(directory, zipDirectory, rootPath, zipFileParentRelativePath, currentPathModel, simpleDateFormat);
        }
        long currentPathLastModified = directory.lastModified();
        currentPathModel.setLastModified(currentPathLastModified);
        currentPathModel.setLastModifiedStr(simpleDateFormat.format(new Date(currentPathLastModified)));
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
        List<FtsPageModel.FtsPageFileModel> tempList = new ArrayList<>(actualPageSize);
        for(int i = 0; i < items.length; i++) {
            File item = items[i];
            FtsPageModel.FtsPageFileModel fileModel = model.new FtsPageFileModel();
            boolean isDirectory = item.isDirectory();
            fileModel.setDirectory(isDirectory);
            fileModel.setFileName(item.getName());
            if (isDirectory) {
                fileModel.setFileSize(0);
                if (zipEnabled) {
                    fillDirectoryModelCompress(item, zipDirectory, rootPath, zipFileParentRelativePath, fileModel, simpleDateFormat);
                }
            } else {
                fileModel.setFileSize(item.length());
            }
            fileModel.setFileSizeStr(Util.fileLengthToStringNew(fileModel.getFileSize()));
            long itemLastModified = item.lastModified();
            fileModel.setLastModified(itemLastModified);
            fileModel.setLastModifiedStr(simpleDateFormat.format(new Date(itemLastModified)));
            tempList.add(fileModel);
        }

        if(sortColumn != null) {
            Comparator<FtsPageModel.FtsPageFileModel> comparator = listTableComparatorMap.get(sortColumn);
            if(comparator == null) {
                LOGGER.warn("List table sort by '{}' requires a comparator!", sortColumn);
            } else {
                if(sortOrder == SortOrder.DESC) {
                    comparator = comparator.reversed();
                }
                tempList.sort(comparator);
            }
        }
        List<FtsPageModel.FtsPageFileModel> fileModelList = new LinkedList<>();
        for(int i=0;i<tempList.size();i++) {
            if(i >= lIndex && i < rIndex) {
                fileModelList.add(tempList.get(i));
            }
        }
        model.setData(fileModelList);
        return model;
    }

    private void fillSearchModelCompress(File directory, File zipDirectory, String rootPath, String zipFileParentRelativePath,
                                         SearchFileModel model) {
        String folderAbsolutePath = directory.getAbsolutePath();
        //添加压缩文件标识
        FolderCompressStatus folderCompressStatus = getFolderCompressStatus(folderAbsolutePath, true);
        model.setCompressStatus(folderCompressStatus);
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        File zipFile = new File(zipDirectory, zipFileName);
        if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
            String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
            if(Util.isSystemWindows()) {
                zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
            }
            model.setCompressedFilePath(zipFileRelativePath);
            long zipFileSize = zipFile.length();
            model.setCompressedFileSize(zipFileSize);
            long zipFileLastModified = zipFile.lastModified();
            model.setCompressedFileLastModified(zipFileLastModified);
        }
    }

    private void fillDirectoryModelCompress(File directory, File zipDirectory, String rootPath, String zipFileParentRelativePath,
                                            FtsPageModel.FtsPageFileModel fileModel, SimpleDateFormat simpleDateFormat) {
        String folderAbsolutePath = directory.getAbsolutePath();
        //添加压缩文件标识
        FolderCompressStatus folderCompressStatus = getFolderCompressStatus(folderAbsolutePath, true);
        fileModel.setCompressStatus(folderCompressStatus);
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        File zipFile = new File(zipDirectory, zipFileName);
        if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
            String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
            if(Util.isSystemWindows()) {
                zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
            }
            fileModel.setCompressedPath(zipFileRelativePath);
            long zipFileSize = zipFile.length();
            fileModel.setCompressedFileSize(zipFileSize);
            fileModel.setCompressedFileSizeStr(Util.fileLengthToStringNew(zipFileSize));
            long zipFileLastModified = zipFile.lastModified();
            fileModel.setCompressedFileLastModified(zipFileLastModified);
            fileModel.setCompressedFileLastModifiedStr(simpleDateFormat.format(new Date(zipFileLastModified)));
        }
    }

    private String getZipFileParentRelativePath(String zipFolderPath) {
        if(Util.isSystemWindows() && !zipFolderPath.startsWith("\\")) {
            return "\\" + zipFolderPath;
        } else if((Util.isSystemMacOS() || Util.isSystemLinux()) && !zipFolderPath.startsWith("/")) {
            return "/" + zipFolderPath;
        } else {
            return zipFolderPath;
        }
    }

    public CompressManagementPageModel listCompressTask(int pageNo, int pageSize, CompressManagementColumn sortColumn, SortOrder sortOrder) {
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        FolderCompressCounter counter = new FolderCompressCounter();
        SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();
        Stream<FolderCompressDTO> allStream = folderCompressingContextHolderMap.entrySet().stream()
                .map(entry -> {
                    String folderAbsolutePath = entry.getKey();
                    FolderCompressingContextHolder folderCompressingContextHolder = entry.getValue();
                    String relativePath = folderAbsolutePath.substring(rootPath.length());
                    if(Util.isSystemWindows()) {
                        relativePath = relativePath.replaceAll("\\\\", "/");
                    }
                    FolderCompressStatus folderCompressStatus = getFolderCompressStatus(folderAbsolutePath, true);
                    counter.countFolder(folderCompressStatus);
                    boolean directoryExists = checkDirectoryExists(folderAbsolutePath, true);
                    FolderCompressDTO folderCompressDTO = new FolderCompressDTO();
                    folderCompressDTO.setPath(relativePath);
                    if(directoryExists) {
                        File directory = new File(folderAbsolutePath);
                        long lastModified = directory.lastModified();
                        folderCompressDTO.setLastModified(lastModified);
                        if(lastModified != 0L) {
                            folderCompressDTO.setLastModifiedStr(simpleDateFormat.format(new Date(lastModified)));
                        }
                    }
                    folderCompressDTO.setDirectoryExists(directoryExists);
                    folderCompressDTO.setCompressStatus(folderCompressStatus);
                    FolderCompressInfo folderCompressInfo = getFolderCompressInfo(folderAbsolutePath, true);
                    if(folderCompressStatus == FolderCompressStatus.COMPRESSING || folderCompressStatus == FolderCompressStatus.COMPRESSED) {
                        long compressStartTime = folderCompressingContextHolder.getStartTime();
                        folderCompressDTO.setCompressStartTime(compressStartTime);
                        folderCompressDTO.setCompressStartTimeStr(simpleDateFormat.format(new Date(compressStartTime)));
                    }
                    if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
                        folderCompressDTO.setCompressedFilePath(folderCompressInfo.getZipFileRelativePath());
                        long compressedFileSize = folderCompressInfo.getCompressedFileSize();
                        folderCompressDTO.setCompressedFileSize(compressedFileSize);
                        folderCompressDTO.setCompressedFileSizeStr(Util.fileLengthToStringNew(compressedFileSize));
                        long compressedFileLastModified = folderCompressInfo.getCompressedFileLastModified();
                        folderCompressDTO.setCompressedFileLastModified(compressedFileLastModified);
                        String lastModifiedString = simpleDateFormat.format(new Date(compressedFileLastModified));
                        folderCompressDTO.setCompressedFileLastModifiedStr(lastModifiedString);
                        long compressFinishTime = folderCompressingContextHolder.getFinishTime();
                        folderCompressDTO.setCompressFinishTime(compressFinishTime);
                        folderCompressDTO.setCompressFinishTimeStr(simpleDateFormat.format(new Date(compressFinishTime)));
                        long compressCostTime = compressFinishTime - folderCompressingContextHolder.getStartTime();
                        folderCompressDTO.setCompressCostTime(compressCostTime);
                        String compressCostTimeStr = Util.formatCostTime(compressCostTime);
                        folderCompressDTO.setCompressCostTimeStr(compressCostTimeStr);
                    }
                    return folderCompressDTO;
                });
        if(sortColumn != null) {
            Comparator<FolderCompressDTO> comparator = compressManagementComparatorMap.get(sortColumn);
            if(comparator == null) {
                LOGGER.warn("Compress management table sort by '{}' requires a comparator!", sortColumn);
            } else {
                if(sortOrder == SortOrder.DESC) {
                    comparator = comparator.reversed();
                }
                allStream = allStream.sorted(comparator);
            }
        }
        List<FolderCompressDTO> allList = allStream.collect(Collectors.toList());
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
    public ReturnObject<FolderCompressDTO> compressFolder(String relativePath) throws IOException {
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
        String zipFileParentRelativePath = getZipFileParentRelativePath(zipFolderPath);
        String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
        if(Util.isSystemWindows()) {
            zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
        }
        File rootPathFile = new File(rootPath);
        File zipFile = new File(rootPathFile, zipFileRelativePath);
        File zipFolderFile = new File(rootPathFile, zipFolderPath);
        boolean zipFileExists = zipFile.exists();
        Assert.isTrue(!zipFileExists || zipFile.isFile(), "压缩文件路径下存在同名文件夹，无法压缩到指定位置");

        String zipFileAbsolutePath = zipFile.getAbsolutePath();
        boolean needCompress = false;
        boolean interrupted = false;
        String exMessage = null;
        if(!zipFileExists) {
            Lock zipPathSelfLock = IOUtil.equalsOrSubPath(folderFile, zipFolderFile) ? zipPathSelfGlobalLock.writeLock() : zipPathSelfGlobalLock.readLock();
            try {
                zipPathSelfLock.lock();
                ReentrantLock zipFileLock = getZipFileLock(zipFileAbsolutePath);
                try {
                    zipFileLock.lock();
                    if (!zipFile.exists()) {
                        needCompress = true;
                        FolderCompressingContextHolder folderCompressingContextHolder = new FolderCompressingContextHolder(-1L);
                        folderCompressingContextHolderMap.put(folderAbsolutePath, folderCompressingContextHolder);
                        IOUtil.compressFolderAsZip(folderAbsolutePath, zipFolderFile.getAbsolutePath(), zipFileName);
                        folderCompressingContextHolder.setCompressSize(zipFile.length());
                        folderCompressingContextHolder.setExecuteThread(null);
                        folderCompressingContextHolder.updateFinishTimeNow();
                        if (!folderCompressingContextHolderMap.containsKey(folderAbsolutePath)) {
                            LOGGER.warn("FolderCompressingContextHolder {} has been removed", zipFileAbsolutePath);
                            folderCompressingContextHolderMap.put(folderAbsolutePath, folderCompressingContextHolder);
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        interrupted = true;
                    } else {
                        LOGGER.error("exception occurs when compressing folder", e);
                        exMessage = e.getMessage();
                    }
                    String fm = interrupted ? "interrupt folder compressing" : "exception occurs when compressing folder";
                    LOGGER.info(fm + ":{} -- delete zip file:{}", folderAbsolutePath, zipFileAbsolutePath);
                    boolean deletes = zipFile.delete();
                    if (!deletes) {
                        LOGGER.warn("delete zip file failed:{}", zipFileAbsolutePath);
                    } else {
                        LOGGER.info("successfully deleted zip file:{}", zipFileAbsolutePath);
                    }
                    folderCompressingContextHolderMap.remove(folderAbsolutePath);
                } finally {
                    zipFileLock.unlock();
                }
            } finally {
                zipPathSelfLock.unlock();
            }
        }

        FolderCompressStatus folderCompressStatus = getFolderCompressStatus(relativePath, false);
        if(needCompress) {
            long end = System.currentTimeMillis();
            String statusMessage = interrupted ? "被中断" : (exMessage == null ? "完成" : "发生异常：" + exMessage);
            LOGGER.info("压缩文件夹（路径：{}）{}，压缩文件路径：{}，耗时{}毫秒", folderAbsolutePath, statusMessage, zipFileAbsolutePath, end - start);
        }
        if(folderCompressStatus == FolderCompressStatus.NOT_COMPRESSED) {
            String reason = interrupted ? "压缩任务被取消" : (exMessage == null ? "未知原因" : "发生异常：" + exMessage);
            return ReturnObject.fail(reason);
        } else {
            SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();
            FolderCompressDTO folderCompressDTO = new FolderCompressDTO();
            folderCompressDTO.setPath(relativePath);
            boolean directoryExists = checkDirectoryExists(relativePath, false);
            folderCompressDTO.setDirectoryExists(directoryExists);
            if(directoryExists) {
                File directory = new File(folderAbsolutePath);
                long lastModified = directory.lastModified();
                folderCompressDTO.setLastModified(lastModified);
                if(lastModified != 0L) {
                    folderCompressDTO.setLastModifiedStr(simpleDateFormat.format(new Date(lastModified)));
                }
            }
            FolderCompressingContextHolder folderCompressingContextHolder = folderCompressingContextHolderMap.get(folderAbsolutePath);
            if(folderCompressStatus == FolderCompressStatus.COMPRESSING || folderCompressStatus == FolderCompressStatus.COMPRESSED) {
                long compressStartTime = folderCompressingContextHolder.getStartTime();
                folderCompressDTO.setCompressStartTime(compressStartTime);
                folderCompressDTO.setCompressStartTimeStr(simpleDateFormat.format(new Date(compressStartTime)));
            }
            if(folderCompressStatus == FolderCompressStatus.COMPRESSED) {
                folderCompressDTO.setCompressedFilePath(zipFileRelativePath);
                folderCompressDTO.setCompressedFileSizeStr(Util.fileLengthToStringNew(zipFile.length()));
                folderCompressDTO.setCompressedFileLastModifiedStr(simpleDateFormat.format(new Date(zipFile.lastModified())));
                long compressFinishTime = folderCompressingContextHolder.getFinishTime();
                folderCompressDTO.setCompressFinishTime(compressFinishTime);
                folderCompressDTO.setCompressFinishTimeStr(simpleDateFormat.format(new Date(compressFinishTime)));
                long compressCostTime = compressFinishTime - folderCompressingContextHolder.getStartTime();
                folderCompressDTO.setCompressCostTime(compressCostTime);
                String compressCostTimeStr = Util.formatCostTime(compressCostTime);
                folderCompressDTO.setCompressCostTimeStr(compressCostTimeStr);
            }
            folderCompressDTO.setCompressStatus(folderCompressStatus);
            return ReturnObject.success(folderCompressDTO);
        }
    }

    public ReturnObject<Void> cancelCompress(String relativePath) {
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFolderPath = rootPath + relativePath;
        File folderFile = new File(actualFolderPath);
        String folderAbsolutePath = folderFile.getAbsolutePath();

        FolderCompressingContextHolder folderCompressingContextHolder = folderCompressingContextHolderMap.get(folderAbsolutePath);
        if(folderCompressingContextHolder == null) {
            LOGGER.warn("FolderCompressingInfo not found for {}, no need to cancel", folderAbsolutePath);
            return ReturnObject.success();
        }

        boolean interrupt = folderCompressingContextHolder.interruptThread();
        if(interrupt) {
            return ReturnObject.success();
        } else {
            FolderCompressStatus folderCompressStatus = getFolderCompressStatus(relativePath, false);
            String message = null;
            switch (folderCompressStatus) {
                case NOT_COMPRESSED:
                    message = "文件夹未压缩";
                    break;
                case COMPRESSING:
                    message = "文件夹正在压缩，请重试";
                    break;
                case COMPRESSED:
                    message = "已完成压缩";
                    break;
                default:
                    LOGGER.warn("Unknown compress status:{}", folderCompressStatus);
                    message = "未知原因";
            }
            return ReturnObject.fail(message);
        }
    }

    public ReturnObject<Void> deleteCompressFile(String relativePath) {
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFolderPath = rootPath + relativePath;
        File folderFile = new File(actualFolderPath);
        String folderAbsolutePath = folderFile.getAbsolutePath();
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        String zipFileParentRelativePath = getZipFileParentRelativePath(zipFolderPath);
        String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;

        File rootPathFile = new File(rootPath);
        File zipFile = new File(rootPathFile, zipFileRelativePath);
        FolderCompressingContextHolder folderCompressingContextHolder = folderCompressingContextHolderMap.get(folderAbsolutePath);
        if(folderCompressingContextHolder == null) {
            LOGGER.warn("folder {} zip task does not exist, no need to delete", folderAbsolutePath);
            return ReturnObject.success();
        }

        FolderCompressStatus folderCompressStatus = getFolderCompressStatus(relativePath, false);
        boolean interrupt = false;
        if(folderCompressStatus == FolderCompressStatus.COMPRESSING) {
            interrupt = folderCompressingContextHolder.interruptThread();
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
            folderCompressingContextHolderMap.remove(folderAbsolutePath);
            return ReturnObject.success();
        } else {
            return ReturnObject.fail("文件可能正在被占用");
        }
    }

    public FolderCompressInfo getFolderCompressInfo(String path, boolean isAbsolute) {
        FolderCompressInfo folderCompressInfo = new FolderCompressInfo();
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFolderPath = path;
        if(!isAbsolute) {
            actualFolderPath = rootPath + path;
        }
        File folderFile = new File(actualFolderPath);
//        Assert.isTrue(folderFile.exists() && folderFile.isDirectory(), "非法的请求路径");
        if(folderFile.exists() && folderFile.isDirectory()) {
            folderCompressInfo.setFolderLastModified(folderFile.lastModified());
        }

        String folderAbsolutePath = folderFile.getAbsolutePath();
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        String zipFileParentRelativePath = getZipFileParentRelativePath(zipFolderPath);
        String zipFileRelativePath = zipFileParentRelativePath + "/" + zipFileName;
        if(Util.isSystemWindows()) {
            zipFileRelativePath = zipFileRelativePath.replaceAll("\\\\", "/");
        }
        folderCompressInfo.setZipFileRelativePath(zipFileRelativePath);

        FolderCompressingContextHolder folderCompressingContextHolder = folderCompressingContextHolderMap.get(folderAbsolutePath);
        if(folderCompressingContextHolder != null) {
            folderCompressInfo.setCompressStartTime(folderCompressingContextHolder.getStartTime());
            folderCompressInfo.setCompressFinishTime(folderCompressingContextHolder.getFinishTime());
        }

        File rootPathFile = new File(rootPath);
        File zipFile = new File(rootPathFile, zipFileRelativePath);
        long zipFileLastModified = 0;
        if(zipFile.exists() && zipFile.isFile()) {
            zipFileLastModified = zipFile.lastModified();
            folderCompressInfo.setCompressedFileSize(zipFile.length());
        }
        folderCompressInfo.setCompressedFileLastModified(zipFileLastModified);

        return folderCompressInfo;
    }

    public FolderCompressStatus getFolderCompressStatus(String path, boolean isAbsolute) {
        String zipFolderPath = ftsServerConfigService.getLocalFtsProperties().getZip().getPath();
        String rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        String actualFolderPath;
        if(isAbsolute) {
            actualFolderPath = path;
        } else {
            actualFolderPath = rootPath + path;
        }
        File folderFile = new File(actualFolderPath);
//        Assert.isTrue(folderFile.exists() && folderFile.isDirectory(), "非法的请求路径");

        String folderAbsolutePath = folderFile.getAbsolutePath();
        String zipFileName = folderPathToZipFileName(folderAbsolutePath, rootPath);
        String zipFileParentRelativePath = getZipFileParentRelativePath(zipFolderPath);
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
            FolderCompressingContextHolder folderCompressingContextHolder = folderCompressingContextHolderMap.get(folderAbsolutePath);
            if(folderCompressingContextHolder != null) {
                long recordSize = folderCompressingContextHolder.getCompressSize();
                long zipFileSize = zipFile.length();
                if (recordSize == zipFileSize) {
                    return FolderCompressStatus.COMPRESSED;
                } else {
                    return FolderCompressStatus.COMPRESSING;
                }
            } else {
                return FolderCompressStatus.COMPRESSED;
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

    public ReturnObject<List<ReturnObject<String>>> uploadFiles(String dirName, MultipartFile[] files, boolean isFolderUpload) {
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
            ReturnObject<String> innerReturnObject = transferFile(rootPath, dirName, file, !isFolderUpload);
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
        processedPath = processedPath.replaceAll("_", "__");
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

    public String getFileInvalidCharacterString() {
        if(Util.isSystemWindows()) {
            return Constants.FILE_INVALID_CHARACTER_WINDOWS;
        } else if(Util.isSystemMacOS() || Util.isSystemLinux()) {
            return Constants.FILE_INVALID_CHARACTER_LINUX_MACOS;
        } else {
            LOGGER.warn("Invalid os name:{}", Util.getOsName());
            throw new LocalFtsRuntimeException("Invalid os name:" + Util.getOsName());
        }
    }

    //是否在离开页面时不触发页面卸载事件
    public boolean isPseudoUnload(String userAgent) {
        if(userAgent == null) {
            return false;
        }
        Parser uaParser = new Parser();
        Client uaClient = uaParser.parse(userAgent);
        if(uaClient.userAgent.family.equalsIgnoreCase("Safari") || uaClient.device.family.equalsIgnoreCase("iPad")) {
            return true;
        }
        List<String> pseudoUnloadUaContains = ftsServerConfigService.getLocalFtsProperties().getPseudoUnloadUaContains();
        for(String pseudoUnloadUaStr: pseudoUnloadUaContains) {
            if(userAgent.contains(pseudoUnloadUaStr)) {
                return true;
            }
        }
        return false;
    }

    //伪支持的浏览器展示上传文件夹元素并给出提示
    public boolean isPseudoDirectoryUpload(String userAgent) {
        //默认允许
        if(userAgent == null) {
            return true;
        }
        Parser uaParser = new Parser();
        Client uaClient = uaParser.parse(userAgent);
        if(uaClient.userAgent.family.equalsIgnoreCase("Safari") || uaClient.device.family.equalsIgnoreCase("iPad")) {
            return false;
        }
        List<String> pseudoUaContains = ftsServerConfigService.getLocalFtsProperties().getUpload().getDirectory().getPseudoUaContains();
        for(String uaStr: pseudoUaContains) {
            if(userAgent.contains(uaStr)) {
                return false;
            }
        }
        return true;
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
            } else {
                //此处为上传文件夹特有的提示
                return ReturnObject.fail("不允许直接上传文件！", filePath);
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

    @PostConstruct
    public void postConstruct() {
        listTableComparatorMap.put(ListTableColumn.FILENAME, (fm1, fm2) ->
                CHINESE_COLLATOR.compare(fm1.getFileName(), fm2.getFileName()));
        listTableComparatorMap.put(ListTableColumn.TYPE, (fm1, fm2) -> {
            String typeStr1 = fm1.isDirectory() ? "文件夹" : "文件";
            String typeStr2 = fm2.isDirectory() ? "文件夹" : "文件";
            return CHINESE_COLLATOR.compare(typeStr1, typeStr2);
        });
        listTableComparatorMap.put(ListTableColumn.SIZE, (fm1, fm2) -> {
            long fileSize1 = fm1.getFileSize(), fileSize2 = fm2.getFileSize();
            if(fm1.isDirectory()) {
                fileSize1 = -1L;
            }
            if(fm2.isDirectory()) {
                fileSize2 = -1L;
            }
            return Long.compare(fileSize1, fileSize2);
        });
        listTableComparatorMap.put(ListTableColumn.LAST_MODIFIED,
                Comparator.comparing(FtsPageModel.FtsPageFileModel::getLastModified));
        listTableComparatorMap.put(ListTableColumn.COMPRESS_STATUS, this::compareCompressStatus);
        listTableComparatorMap.put(ListTableColumn.COMPRESS_FILE_SIZE, this::compareCompressedFileSize);
        listTableComparatorMap.put(ListTableColumn.COMPRESS_FILE_LAST_MODIFIED, this::compareCompressedFileLastModified);

        compressManagementComparatorMap.put(CompressManagementColumn.FOLDER_NAME, (fcd1, fcd2) ->
                CHINESE_COLLATOR.compare(fcd1.getPath(), fcd2.getPath()));
        compressManagementComparatorMap.put(CompressManagementColumn.FOLDER_LAST_MODIFIED,
                Comparator.comparing(FolderCompressDTO::getLastModified));
        compressManagementComparatorMap.put(CompressManagementColumn.COMPRESS_STATUS, this::compareCompressStatus);
        compressManagementComparatorMap.put(CompressManagementColumn.COMPRESS_FILE_SIZE, this::compareCompressedFileSize);
        compressManagementComparatorMap.put(CompressManagementColumn.COMPRESS_FILE_LAST_MODIFIED, this::compareCompressedFileLastModified);
        compressManagementComparatorMap.put(CompressManagementColumn.COMPRESS_START_TIME,
                Comparator.comparing(FolderCompressDTO::getCompressStartTime));
        compressManagementComparatorMap.put(CompressManagementColumn.COMPRESS_FINISH_TIME,
                Comparator.comparing(FolderCompressDTO::getCompressFinishTime));
        compressManagementComparatorMap.put(CompressManagementColumn.COMPRESS_COST_TIME,
                Comparator.comparing(FolderCompressDTO::getCompressCostTime));
    }

    public int compareCompressedFileSize(CompressedColumns cc1, CompressedColumns cc2) {
        return compareCompressedColumns(cc1, cc2,
                (acc1, acc2) -> Long.compare(acc1.getCompressedFileSize(), acc2.getCompressedFileSize()));
    }

    public int compareCompressedFileLastModified(CompressedColumns cc1, CompressedColumns cc2) {
        return compareCompressedColumns(cc1, cc2,
                (acc1, acc2) -> Long.compare(acc1.getCompressedFileLastModified(), acc2.getCompressedFileLastModified()));
    }

    private int compareCompressedColumns(CompressedColumns cc1, CompressedColumns cc2,
                                         BiFunction<CompressedColumns, CompressedColumns, Integer> biFunction) {
        FolderCompressStatus compressStatus1 = cc1.getCompressStatus(), compressStatus2 = cc2.getCompressStatus();
        if (compressStatus1 != FolderCompressStatus.COMPRESSED && compressStatus2 != FolderCompressStatus.COMPRESSED) {
            return 0;
        } else if (compressStatus1 == FolderCompressStatus.COMPRESSED && compressStatus2 != FolderCompressStatus.COMPRESSED) {
            return 1;
        } else if (compressStatus1 != FolderCompressStatus.COMPRESSED && compressStatus2 == FolderCompressStatus.COMPRESSED) {
            return -1;
        } else {
            return biFunction.apply(cc1, cc2);
        }
    }

    public int compareCompressStatus(CompressedColumns cc1, CompressedColumns cc2) {
        if(cc1.getCompressStatus() == null && cc2.getCompressStatus() == null) {
            return 0;
        } else if(cc1.getCompressStatus() == null) {
            return -1;
        } else if(cc2.getCompressStatus() == null) {
            return 1;
        } else {
            return CHINESE_COLLATOR.compare(cc1.getCompressStatus().getDesc(), cc2.getCompressStatus().getDesc());
        }
    }

}
