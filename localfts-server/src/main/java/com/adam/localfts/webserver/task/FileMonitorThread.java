package com.adam.localfts.webserver.task;

import com.adam.localfts.webserver.common.VoidFunction;
import com.adam.localfts.webserver.common.search.IndexType;
import com.adam.localfts.webserver.common.search.SearchFileModel;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.service.FtsServerConfigService;
import com.adam.localfts.webserver.service.FtsService;
import com.adam.localfts.webserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FileMonitorThread extends Thread{

    private FtsService ftsService;
    private FtsServerConfigService ftsServerConfigService;
    private String rootPath;
    private WatchService watchService;
    private boolean indexHiddenFiles;
    private final boolean workForIndex;
    private final ConcurrentHashMap<String, WatchKey> watchKeyMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledFutureMap = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    private final FileVisitor<? super java.nio.file.Path> registerFileVisitor = new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if(!indexHiddenFiles && dir.toFile().isHidden()) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            registerPath(dir);
            return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            LOGGER.warn("监听目录{}时出现异常，异常类型：{}，异常信息：{}", file, exc.getClass().getName(), exc.getMessage());
            return FileVisitResult.SKIP_SUBTREE;
        }
    };
    private final FileVisitor<? super java.nio.file.Path> unRegisterFileVisitor = new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            unregisterPath(dir);
            return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            LOGGER.warn("取消监听目录{}时出现异常，异常类型：{}，异常信息：{}", file, exc.getClass().getName(), exc.getMessage());
            return FileVisitResult.SKIP_SUBTREE;
        }
    };

    private final static Logger LOGGER = LoggerFactory.getLogger(FileMonitorThread.class);
    private static volatile FileMonitorThread INSTANCE = null;

    private FileMonitorThread(FtsService ftsService, FtsServerConfigService ftsServerConfigService, boolean workForIndex) throws IOException {
        super("FM-Thread");
        Assert.notNull(ftsService, "ftsService is null!");
        Assert.notNull(ftsServerConfigService, "ftsServerConfigService is null!");
        this.ftsService = ftsService;
        this.ftsServerConfigService = ftsServerConfigService;
        this.rootPath = ftsServerConfigService.getLocalFtsProperties().getRootPath();
        this.indexHiddenFiles = ftsServerConfigService.getLocalFtsProperties().getShowHidden();
        this.workForIndex = workForIndex;
        File rootPathFile = new File(rootPath);
        Assert.isTrue(rootPathFile.exists(), "Root path " + rootPath + "does not exist!");
        Assert.isTrue(rootPathFile.isDirectory(), "Root path " + rootPath + "is not a directory!");
        this.watchService = FileSystems.getDefault().newWatchService();
        if(workForIndex) {
            scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
            scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
            LOGGER.info("Prepare to monitor files in {}", rootPath);
            Path rootPathPath = Paths.get(rootPath);
            //遍历所有子目录并注册
            Files.walkFileTree(rootPathPath, registerFileVisitor);
        } else {
            scheduledThreadPoolExecutor = null;
            String zipDir = ftsServerConfigService.getZipDir();
            Path zipPath = Paths.get(zipDir);
            try {
                zipPath.register(watchService, StandardWatchEventKinds.ENTRY_DELETE);
                LOGGER.debug("已监控压缩文件目录{}", zipPath);
            } catch (IOException e) {
                LOGGER.error("监控压缩文件目录{}失败", zipPath, e);
            }
        }
    }

    @Override
    public void run() {
        while(true) {
            try {
                Util.clearInterruptedAndThrowException();
                WatchKey watchKey = watchService.take();
                Path parentDir = (Path) watchKey.watchable();
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    // 忽略系统溢出事件
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path fileName = (Path) event.context();
                    File targetFile = parentDir.resolve(fileName).toFile();

                    if(workForIndex) {
                        handleFileEvent(kind, targetFile);
                    } else {
                        handleDeleteZipFileEvent(kind, targetFile);
                    }

                    boolean reset = watchKey.reset();
                    if(!reset) {
                        LOGGER.warn("目录监听失效：{}", parentDir);
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        LOGGER.info("Prepare to close watchService {}", watchService);
        try {
            watchService.close();
        } catch (IOException e) {
            LOGGER.error("Error closing watchService, message:{}", e.getMessage());
        }
        LOGGER.info("Thread is terminating...");
    }

    public static boolean constructed() {
        return INSTANCE != null;
    }

    public static void constructOnce(FtsService ftsService, FtsServerConfigService ftsServerConfigService, boolean workForIndex) {
        if(INSTANCE == null) {
            synchronized (FileMonitorThread.class) {
                if(INSTANCE == null) {
                    try {
                        INSTANCE = new FileMonitorThread(ftsService, ftsServerConfigService, workForIndex);
                    } catch (IOException e) {
                        LOGGER.error("IOException occurred when constructing FileMonitorThread instance", e);
                        throw new LocalFtsRuntimeException("IOException occurred when constructing FileMonitorThread instance, msg:" + e.getMessage());
                    }
                }
            }
        }
    }

    public static FileMonitorThread getInstance() {
        return INSTANCE;
    }

    public void tryStop() {
        this.interrupt();
        if(scheduledThreadPoolExecutor != null) {
            scheduledThreadPoolExecutor.shutdownNow();
        }
    }

    private void handleDeleteZipFileEvent(WatchEvent.Kind<?> kind, File file) {
        String absolutePath = file.getAbsolutePath();
        String fileType = file.isDirectory() ? "directory" : "file";
        fileType = (file.isHidden() ? "hidden " : "") + fileType;
        LOGGER.debug("Monitored file event kind {} of {} {}", kind.name(), fileType, absolutePath);
        if("ENTRY_DELETE".equals(kind.name())) {
            handleDeleteZipFile(absolutePath, file);
        }
    }

    private void handleFileEvent(WatchEvent.Kind<?> kind, File file) {
        String absolutePath = file.getAbsolutePath();
        String appLogDir = ftsServerConfigService.getAppLogDir();
        String appIndexDir = ftsServerConfigService.getAppSearchIndexDir();
        if(appLogDir != null && absolutePath.startsWith(appLogDir)) {
//            LOGGER.debug("不处理应用日志文件{}", absolutePath);
            return;
        } else if(appIndexDir != null && absolutePath.startsWith(appIndexDir)) {
//            LOGGER.debug("不处理应用索引文件{}", absolutePath);
            return;
        }
        String fileType = file.isDirectory() ? "directory" : "file";
        fileType = (file.isHidden() ? "hidden " : "") + fileType;
        LOGGER.debug("Monitored file event kind {} of {} {}", kind.name(), fileType, absolutePath);
        switch (kind.name()) {
            case "ENTRY_CREATE":
                createIndex(file);
                break;
            case "ENTRY_MODIFY":
                if(file.isDirectory()) {
                    WatchKey watchKey = watchKeyMap.get(absolutePath);
                    Path path = Paths.get(absolutePath);
                    if(!indexHiddenFiles && file.isHidden() && watchKey != null) {
                        try {
                            Files.walkFileTree(path, unRegisterFileVisitor);
                        } catch (IOException e) {
                            LOGGER.error("IOException occurred when un-monitoring path {}", path, e);
                            throw new LocalFtsRuntimeException("IOException occurred when un-monitoring path " + path + ", msg:" + e.getMessage());
                        }
                        deleteIndex(file);
                    } else if(!file.isHidden() && watchKey == null) {
                        try {
                            Files.walkFileTree(path, registerFileVisitor);
                        } catch (IOException e) {
                            LOGGER.error("IOException occurred when monitoring path {}", path, e);
                            throw new LocalFtsRuntimeException("IOException occurred when monitoring path " + path + ", msg:" + e.getMessage());
                        }
                        createIndex(file);
                    }
                    ftsService.convertAndApplySearchFileModel(file,
                            model -> LuceneIndexThread.getInstance().addOperation(IndexType.UPDATE, model));
                } else if(!indexHiddenFiles && file.isHidden()) {
                    deleteIndex(file);
                } else {
                    handleModifyFile(file);
                }
                break;
            case "ENTRY_DELETE":
                deleteIndex(file);
                handleDeleteZipFile(absolutePath, file);
                break;
            default:
                LOGGER.warn("Invalid state of kind {}", kind.name());
        }
    }

    private void handleDeleteZipFile(String absolutePath, File file) {
        if(absolutePath == null) {
            absolutePath = file.getAbsolutePath();
        }
        if(absolutePath.startsWith(ftsServerConfigService.getZipDir()) && absolutePath.endsWith(".zip")) {
            ftsService.clearFolderCompressingContextHolder(file);
        }
    }

    private void handleModifyFile(File file) {
        String absolutePath = file.getAbsolutePath();
        scheduledFutureMap.compute(absolutePath,(k, oldFuture) -> {
            if(oldFuture != null && !oldFuture.isDone()) {
                boolean cancel = oldFuture.cancel(true);
//                LOGGER.debug("Cancel {}, work queue count={}", cancel ? "success" : "fail", scheduledThreadPoolExecutor.getQueue().size());
            }
            return scheduledThreadPoolExecutor.schedule(() -> {
                LOGGER.debug("Scheduled update file model {}", absolutePath);
                ftsService.convertAndApplySearchFileModel(file,
                        model -> LuceneIndexThread.getInstance().addOperation(IndexType.UPDATE, model));
                scheduledFutureMap.remove(absolutePath);
            }, 500, TimeUnit.MILLISECONDS);
        });
    }

    private void createIndex(File file) {
        VoidFunction<SearchFileModel> voidFunction = model -> LuceneIndexThread.getInstance().addOperation(IndexType.CREATE, model);
        if(!indexHiddenFiles && file.isHidden() ) {
            return;
        }
        if(file.isDirectory()) {
            LuceneIndexThread.getInstance().setBatchMode(true);
            ftsService.scanAndApplySearchFileModel(file, indexHiddenFiles, voidFunction);
            registerPath(Paths.get(file.getAbsolutePath()));
            LuceneIndexThread.getInstance().setBatchMode(false);
        } else {
            ftsService.convertAndApplySearchFileModel(file, voidFunction);
        }
    }

    private void deleteIndex(File file) {
        SearchFileModel model = new SearchFileModel();
        model.setFileName(file.getName());
        String parentRelativePath = ftsService.getParentRelativePath(file);
        model.setParentRelativePath(parentRelativePath);
        /**
         * 由于File对象不存在，isFile方法永远返回false，进入DELETE_DIRECTORY分支，会首先删除当前model，
         * 其次删除前缀匹配当前model.parentRelativePath + fileName的所有文件。
         * 如果这是一个文件，前缀匹配是不会结果的。
         * 如果这是一个目录，前缀匹配刚好会匹配到目录的子文件。
         */
//                if(file.isFile()) {
//                    LuceneIndexThread.getInstance().addOperation(IndexType.DELETE, model);
//                } else {
        LuceneIndexThread.getInstance().addOperation(IndexType.DELETE_DIRECTORY, model);
//                }
    }

    private void registerPath(Path path) {
        String appLogDir = ftsServerConfigService.getAppLogDir();
        String appIndexDir = ftsServerConfigService.getAppSearchIndexDir();
        if(appLogDir != null && path.startsWith(appLogDir)) {
            LOGGER.debug("不监听应用日志目录{}", appLogDir);
        } else if(appIndexDir != null && path.startsWith(appIndexDir)) {
            LOGGER.debug("不监听应用索引目录{}", appIndexDir);
        } else {
            try {
                WatchKey watchKey = path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                watchKeyMap.put(path.toString(), watchKey);
                LOGGER.debug("已监控目录{}", path);
            } catch (IOException e) {
                LOGGER.error("监控目录{}失败", path, e);
            }
        }
    }

    private void unregisterPath(Path path) {
        WatchKey watchKey = watchKeyMap.get(path.toString());
        if(watchKey != null) {
            watchKey.cancel();
            watchKeyMap.remove(path.toString());
            LOGGER.debug("已取消监控目录{}", path);
        }
    }
}
