package com.adam.localfts.webserver.task;

import com.adam.localfts.webserver.common.VoidFunction;
import com.adam.localfts.webserver.common.search.IndexType;
import com.adam.localfts.webserver.common.search.SearchFileModel;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
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

public class FileMonitorThread extends Thread{

    private FtsService ftsService;
    private String rootPath;
    private WatchService watchService;
    private final ConcurrentHashMap<String, Long> lastModifiedMap = new ConcurrentHashMap<>();

    private final static Logger LOGGER = LoggerFactory.getLogger(FileMonitorThread.class);
    private static volatile FileMonitorThread INSTANCE = null;

    private FileMonitorThread(FtsService ftsService, String rootPath) throws IOException {
        super("FM-Thread");
        Assert.notNull(ftsService, "ftsService is null!");
        Assert.notNull(rootPath, "rootPath is null!");
        this.rootPath = rootPath;
        this.ftsService = ftsService;
        File rootPathFile = new File(rootPath);
        Assert.isTrue(rootPathFile.exists(), "Root path " + rootPath + "does not exist!");
        Assert.isTrue(rootPathFile.isDirectory(), "Root path " + rootPath + "is not a directory!");
        LOGGER.info("Prepare to monitor files in {}", rootPath);
        this.watchService = FileSystems.getDefault().newWatchService();
        Path rootPathPath = Paths.get(rootPath);
        //遍历所有子目录并注册
        Files.walkFileTree(rootPathPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerPath(dir);
                return FileVisitResult.CONTINUE;
            }
        });
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

                    handleFileEvent(kind, targetFile);

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
    }

    public static boolean constructed() {
        return INSTANCE != null;
    }

    public static void constructOnce(FtsService ftsService, String rootPath) {
        if(INSTANCE == null) {
            synchronized (FileMonitorThread.class) {
                if(INSTANCE == null) {
                    try {
                        INSTANCE = new FileMonitorThread(ftsService, rootPath);
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
    }

    private void handleFileEvent(WatchEvent.Kind<?> kind, File file) {
        String fileType = file.isDirectory() ? "directory" : "file";
        LOGGER.debug("Monitored file event kind {} of {} {}", kind.name(), fileType, file.getAbsolutePath());
        switch (kind.name()) {
            case "ENTRY_CREATE":
                VoidFunction<SearchFileModel> voidFunction = model -> LuceneIndexThread.getInstance().addOperation(IndexType.CREATE, model);
                if(file.isDirectory()) {
                    LuceneIndexThread.getInstance().setBatchMode(true);
                    ftsService.scanAndApplySearchFileModel(file, voidFunction);
                    registerPath(Paths.get(file.getAbsolutePath()));
                    LuceneIndexThread.getInstance().setBatchMode(false);
                } else {
                    ftsService.convertAndApplySearchFileModel(file, voidFunction);
                }
                break;
            case "ENTRY_MODIFY":
                ftsService.convertAndApplySearchFileModel(file,
                        model -> LuceneIndexThread.getInstance().addOperation(IndexType.UPDATE, model));
                break;
            case "ENTRY_DELETE":
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
                break;
            default:
                LOGGER.warn("Invalid state of kind {}", kind.name());
        }
    }

    private void registerPath(Path path) {
        try {
            path.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            LOGGER.error("监控目录{}失败", path, e);
        }
    }
}
