package com.adam.localfts.webserver.task;

import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.common.search.SearchFileModel;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.exception.LocalFtsStartupException;
import com.adam.localfts.webserver.util.Util;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.wltea.analyzer.lucene.IKAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LuceneIndexThread extends Thread{

    private final AtomicBoolean isBusy = new AtomicBoolean(false);
    private final AtomicBoolean batchMode = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LinkedBlockingQueue<SearchFileModel> queue = new LinkedBlockingQueue<>();
    private final String indexPath;
    private final IndexWriter indexWriter;
    private final Directory directory;
    private final Analyzer analyzer;
    private final boolean useExistingIndex;
    private static volatile LuceneIndexThread INSTANCE = null;

    private final Logger logger = LoggerFactory.getLogger(LuceneIndexThread.class);

    private LuceneIndexThread(String indexPath, boolean useExistingIndex) {
        super("LI-Thread");
        this.useExistingIndex = useExistingIndex;
        this.indexPath = indexPath;
        this.analyzer = new IKAnalyzer(false, false, false);
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(this.analyzer);
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        Path path = Paths.get(indexPath);
        if(!Files.isReadable(path)) {
            logger.error("索引路径无法读取：{}", indexPath);
            throw new LocalFtsStartupException("索引路径无法读取：" + indexPath);
        }
        try {
            this.directory = FSDirectory.open(path);
            this.indexWriter = new IndexWriter(directory, indexWriterConfig);
            if(!useExistingIndex) {
                indexWriter.deleteAll();
            }
            indexWriter.commit();
        } catch (IOException e) {
            logger.error("Error constructing instance", e);
            throw new LocalFtsStartupException("无法实例化LuceneIndexThread:" + e.getMessage());
        }
    }

    public static LuceneIndexThread getInstance() {
        return INSTANCE;
    }

    public static void constructOnce(String indexPath, boolean useExistingIndex) {
        synchronized (LuceneIndexThread.class) {
            if(INSTANCE == null) {
                INSTANCE = new LuceneIndexThread(indexPath, useExistingIndex);
            }
        }
    }

    public static boolean constructed() {
        return INSTANCE != null;
    }

    @Override
    public void run() {
        logger.info("Prepare to create and update index");
        while(running.get()) {
            try {
                Util.clearInterruptedAndThrowException();
                if(queue.size() == 0) {
                    isBusy.set(false);
                }
                SearchFileModel model = queue.take();
                isBusy.set(true);
                indexModel(model);
            } catch (InterruptedException e) {
                running.set(false);
            } catch (Exception e) {
                logger.error("Exception occurred", e);
            }
        }
        logger.info("Prepare to close objects for write...");
        try {
            this.indexWriter.commit();
            this.indexWriter.close();
        } catch (IOException e) {
        }
        try {
            this.directory.close();
        } catch (IOException e) {
        }
        this.analyzer.close();
    }

    public void addModel(SearchFileModel model) {
        queue.offer(model);
    }

    public void setBatchMode(boolean enabled) {
        boolean prevEnabled = batchMode.get();
        if(prevEnabled != enabled) {
            logger.info("Batch mode changed from {} to {}", prevEnabled, enabled);
            batchMode.set(enabled);
        }
    }

    public void tryStop() {
        if(constructed()) {
            INSTANCE.interrupt();
        }
    }

    private void addDoc(Document document, SearchFileModel model) {
        try {
            indexWriter.addDocument(document);
        } catch (IOException e) {
            logger.error("Error adding document from model {}", model, e);
            throw new LocalFtsRuntimeException("Error adding document from model:" + e.getMessage());
        }
    }

    public void commitDocs() {
        try {
            indexWriter.commit();
        } catch (IOException e) {
            logger.error("Cannot commit indexWriter", e);
            throw new LocalFtsRuntimeException("Cannot commit indexWriter:" + e.getMessage());
        }
    }

    private void indexModel(SearchFileModel model) {
        Assert.notNull(model, "model is null!");
        FieldType fullFieldType = new FieldType();
        fullFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        fullFieldType.setStored(true);
        fullFieldType.setTokenized(true);
        fullFieldType.setStoreTermVectors(true);
        fullFieldType.setStoreTermVectorPositions(true);
        fullFieldType.setStoreTermVectorOffsets(true);
        FieldType simpleFieldType = new FieldType();
        simpleFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        simpleFieldType.setStored(true);
        simpleFieldType.setTokenized(false);

        Document document = new Document();
        document.add(new Field("fileName", model.getFileName(), fullFieldType));
        document.add(new Field("fileName_lowercase", Util.toLowerCaseAndSC(model.getFileName()), fullFieldType));
        document.add(new SortedDocValuesField("fileName_sort", new BytesRef(model.getFileName())));
        if(model.getFileContent() != null) {
            document.add(new Field("fileContent", model.getFileContent(), fullFieldType));
            document.add(new Field("fileContent_lowercase", Util.toLowerCaseAndSC(model.getFileContent()), fullFieldType));
            //文件内容过长时无法创建排序字段
            //document.add(new SortedDocValuesField("fileContent_sort", new BytesRef(model.getFileContent())));
        }
        document.add(new Field("parentRelativePath", model.getParentRelativePath(), simpleFieldType));
//        document.add(new StringField("parentRelativePath", model.getParentRelativePath(), Field.Store.YES));
        document.add(new SortedDocValuesField("parentRelativePath_sort", new BytesRef(model.getParentRelativePath())));
        document.add(new Field("isDirectory", String.valueOf(model.isDirectory()), simpleFieldType));
        document.add(new SortedDocValuesField("isDirectory_sort", new BytesRef(model.isDirectory() ? "文件夹" : "文件")));
        document.add(new LongPoint("lastModified", model.getLastModified()));
        document.add(new SortedNumericDocValuesField("lastModified_sort", model.getLastModified()));
        document.add(new Field("lastModified_value", String.valueOf(model.getLastModified()), simpleFieldType));
        if(model.isDirectory()) {
            document.add(new Field("compressStatus", model.getCompressStatus().name(), simpleFieldType));
            document.add(new SortedDocValuesField("compressStatus_sort", new BytesRef(model.getCompressStatus().getDesc())));
            if(model.getCompressStatus() == FolderCompressStatus.COMPRESSED) {
                document.add(new Field("compressedFilePath", model.getCompressedFilePath(), simpleFieldType));
                document.add(new SortedDocValuesField("compressedFilePath_sort", new BytesRef(model.getCompressedFilePath())));
                document.add(new LongPoint("compressedFileSize", model.getCompressedFileSize()));
                document.add(new Field("compressedFileSize_value", String.valueOf(model.getCompressedFileSize()), simpleFieldType));
                document.add(new SortedNumericDocValuesField("compressedFileSize_sort", model.getCompressedFileSize()));
                document.add(new LongPoint("compressedFileLastModified", model.getCompressedFileLastModified()));
                document.add(new Field("compressedFileLastModified_value", String.valueOf(model.getCompressedFileLastModified()), simpleFieldType));
                document.add(new SortedNumericDocValuesField("compressedFileLastModified_sort", model.getCompressedFileLastModified()));
            }
        } else {
            document.add(new LongPoint("fileSize", model.getFileSize()));
            document.add(new Field("fileSize_value", String.valueOf(model.getFileSize()), simpleFieldType));
            document.add(new SortedNumericDocValuesField("fileSize_sort", model.getFileSize()));
        }
        addDoc(document, model);
        if(!batchMode.get()) {
            commitDocs();
        }
    }

}
