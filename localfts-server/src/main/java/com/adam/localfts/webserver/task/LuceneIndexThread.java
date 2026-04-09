package com.adam.localfts.webserver.task;

import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.common.search.IndexOperation;
import com.adam.localfts.webserver.common.search.IndexType;
import com.adam.localfts.webserver.common.search.SearchFileModel;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.exception.LocalFtsStartupException;
import com.adam.localfts.webserver.util.Util;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LuceneIndexThread extends Thread{

    private final AtomicBoolean isBusy = new AtomicBoolean(false);
    private final AtomicBoolean batchMode = new AtomicBoolean(false);
    private final AtomicInteger batchCount = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LinkedBlockingQueue<IndexOperation> queue = new LinkedBlockingQueue<>();
    private final String indexPath;
    private final int maxStringLength;
    private final IndexWriter indexWriter;
    private final Directory directory;
    private final Analyzer analyzer;
    private final boolean useExistingIndex;
    private static volatile LuceneIndexThread INSTANCE = null;

    private final Logger logger = LoggerFactory.getLogger(LuceneIndexThread.class);

    private LuceneIndexThread(String indexPath, int maxStringLength, boolean useExistingIndex) {
        super("LI-Thread");
        this.useExistingIndex = useExistingIndex;
        this.indexPath = indexPath;
        this.maxStringLength = maxStringLength;
        this.analyzer = new IKAnalyzer(false, false, false);
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(this.analyzer);
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        Path path = Paths.get(indexPath);
        if(!Files.isReadable(path)) {
            if(!Files.exists(path)) {
                try {
                    boolean mkdirs = path.toFile().mkdirs();
                    com.adam.localfts.webserver.util.Assert.isTrue(mkdirs, "创建索引路径" + indexPath + "失败", LocalFtsStartupException.class);
                    if(!Files.isReadable(path)) {
                        logger.error("索引路径无法读取：{}", indexPath);
                        throw new LocalFtsStartupException("索引路径无法读取：" + indexPath);
                    }
                } catch (SecurityException e) {
                    logger.error("无法创建索引路径：{}", indexPath, e);
                    throw new LocalFtsStartupException("索引路径无法创建：" + indexPath + ", msg:" + e.getMessage());
                }
            } else {
                logger.error("索引路径无法读取：{}", indexPath);
                throw new LocalFtsStartupException("索引路径无法读取：" + indexPath);
            }
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

    public static void constructOnce(String indexPath, int maxStringLength, boolean useExistingIndex) {
        synchronized (LuceneIndexThread.class) {
            if(INSTANCE == null) {
                INSTANCE = new LuceneIndexThread(indexPath, maxStringLength, useExistingIndex);
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
                IndexOperation indexOperation = queue.take();
                isBusy.set(true);
                indexModel(indexOperation);
                if(batchMode.get()) {
                    int count = batchCount.incrementAndGet();
                    boolean batchCommit = false;
                    if(count >= 100) {
                        batchCommit = true;
                    } else {
                        String fileContent = indexOperation.getSearchFileModel().getFileContent();
                        if(fileContent != null && maxStringLength > 0 && fileContent.length() >= maxStringLength) {
                            batchCommit = true;
                        }
                    }
                    if(batchCommit) {
                        logger.debug("Batch commit docs count {}", count);
                        commitDocs();
                        batchCount.set(0);
                    }
                } else {
                    commitDocs();
                }
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
        logger.info("Thread is terminating...");
    }

    public void addOperation(IndexType indexType, SearchFileModel model) {
        IndexOperation indexOperation = new IndexOperation(indexType, model);
        queue.offer(indexOperation);
    }

    /**
     * 切换批处理状态。当批处理状态由true变为false时会强制提交修改
     * @param enabled
     */
    public void setBatchMode(boolean enabled) {
        boolean prevEnabled = batchMode.get();
        if(prevEnabled != enabled) {
            logger.info("Batch mode changed from {} to {}", prevEnabled, enabled);
            batchMode.set(enabled);
            if(!enabled) {
                logger.debug("Batch commit docs count {}", batchCount);
                commitDocs();
                batchCount.set(0);
            }
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

    private void indexModel(IndexOperation indexOperation) {
        Assert.notNull(indexOperation, "indexOperation is null!");
        Assert.notNull(indexOperation.getIndexType(), "indexOperation.indexType is null!");
        Assert.notNull(indexOperation.getSearchFileModel(), "indexOperation.searchFileModel is null!");

        switch (indexOperation.getIndexType()) {
            case CREATE:
                addModel(indexOperation.getSearchFileModel());
                break;
            case UPDATE:
                updateModel(indexOperation.getSearchFileModel());
                break;
            case DELETE:
                deleteModel(indexOperation.getSearchFileModel());
                break;
            case DELETE_DIRECTORY:
                deleteDirectoryModel(indexOperation.getSearchFileModel());
                break;
            default:
                throw new LocalFtsRuntimeException("Invalid state:" + indexOperation.getIndexType());
        }
    }

    private void addModel(SearchFileModel model) {
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
        document.add(new Field("fileName_simple", model.getFileName(), simpleFieldType));
        String reversedFileName;
        if(Util.isSystemWindows() || Util.isSystemMacOS()) {
            reversedFileName = Util.reverseStr(model.getFileName().toLowerCase());
        } else if(Util.isSystemLinux()){
            reversedFileName = Util.reverseStr(model.getFileName());
            String reversedLowercaseFileName = Util.reverseStr(model.getFileName().toLowerCase());
            document.add(new Field("fileName_simple_lowercase_suffix", reversedLowercaseFileName, simpleFieldType));
        } else {
            throw new LocalFtsRuntimeException("Unknown system:" + Util.getOsName());
        }
        document.add(new Field("fileName_simple_suffix", reversedFileName, simpleFieldType));
        document.add(new SortedDocValuesField("fileName_sort", new BytesRef(Util.convertToPinyin(model.getFileName(), true))));
        if(model.getFileContent() != null) {
            String fileContent = model.getFileContent();
            model.setFileContent(null);  //便于回收
            document.add(new Field("fileContent", fileContent, fullFieldType));
            document.add(new Field("fileContent_lowercase", Util.toLowerCaseAndSC(fileContent), fullFieldType));
            //文件内容过长时无法创建排序字段
            //document.add(new SortedDocValuesField("fileContent_sort", new BytesRef(model.getFileContent())));
        }
        if(model.getFileEncoding() != null) {
            String encoding = model.getFileEncoding();
            document.add(new Field("fileEncoding", encoding, simpleFieldType));
            document.add(new SortedDocValuesField("fileEncoding_sort", new BytesRef(encoding)));
        }
        document.add(new Field("parentRelativePath", model.getParentRelativePath(), simpleFieldType));
//        document.add(new StringField("parentRelativePath", model.getParentRelativePath(), Field.Store.YES));
        document.add(new SortedDocValuesField("parentRelativePath_sort", new BytesRef(Util.convertToPinyin(model.getParentRelativePath(), true))));
        document.add(new Field("isDirectory", String.valueOf(model.isDirectory()), simpleFieldType));
        document.add(new SortedDocValuesField("isDirectory_sort", new BytesRef(model.isDirectory() ? "文件夹" : "文件")));
        document.add(new LongPoint("lastModified", model.getLastModified()));
        document.add(new NumericDocValuesField("lastModified_sort", model.getLastModified()));
        document.add(new Field("lastModified_value", String.valueOf(model.getLastModified()), simpleFieldType));
        if(model.isDirectory()) {
            document.add(new Field("compressStatus", model.getCompressStatus().name(), simpleFieldType));
            document.add(new SortedDocValuesField("compressStatus_sort", new BytesRef(model.getCompressStatus().getDesc())));
            if(model.getCompressStatus() == FolderCompressStatus.COMPRESSED) {
                document.add(new Field("compressedFilePath", model.getCompressedFilePath(), simpleFieldType));
                document.add(new SortedDocValuesField("compressedFilePath_sort", new BytesRef(Util.convertToPinyin(model.getCompressedFilePath(), true))));
                document.add(new LongPoint("compressedFileSize", model.getCompressedFileSize()));
                document.add(new Field("compressedFileSize_value", String.valueOf(model.getCompressedFileSize()), simpleFieldType));
                document.add(new NumericDocValuesField("compressedFileSize_sort", model.getCompressedFileSize()));
                document.add(new LongPoint("compressedFileLastModified", model.getCompressedFileLastModified()));
                document.add(new Field("compressedFileLastModified_value", String.valueOf(model.getCompressedFileLastModified()), simpleFieldType));
                document.add(new NumericDocValuesField("compressedFileLastModified_sort", model.getCompressedFileLastModified()));
            }
        } else {
            document.add(new LongPoint("fileSize", model.getFileSize()));
            document.add(new Field("fileSize_value", String.valueOf(model.getFileSize()), simpleFieldType));
            document.add(new NumericDocValuesField("fileSize_sort", model.getFileSize()));
        }
        addDoc(document, model);
    }

    private void deleteDirectoryModel(SearchFileModel model) {
        deleteModel(model);
        String directoryRelativePath = model.getParentRelativePath();
        if(!directoryRelativePath.equals("/")) {
            directoryRelativePath = directoryRelativePath + "/";
        }
        directoryRelativePath = directoryRelativePath + model.getFileName();
        Term term = new Term("parentRelativePath", directoryRelativePath);
        PrefixQuery query = new PrefixQuery(term);
        try {
            indexWriter.deleteDocuments(query);
        } catch (IOException e) {
            logger.error("Error deleting directory documents with fileName {} and parentRelativePath {}", model.getFileName(), model.getParentRelativePath());
            throw new LocalFtsRuntimeException("Error deleting directory documents with fileName " + model.getFileName() + " and parentRelativePath " + model.getParentRelativePath());
        }
    }

    private void deleteModel(SearchFileModel model) {
        Term term = new Term("fileName_simple", model.getFileName());
        TermQuery termQuery1 = new TermQuery(term);
        term = new Term("parentRelativePath", model.getParentRelativePath());
        TermQuery termQuery2 = new TermQuery(term);
        BooleanQuery query = new BooleanQuery.Builder()
                .add(termQuery1, BooleanClause.Occur.MUST)
                .add(termQuery2, BooleanClause.Occur.MUST)
                .build();
        try {
            indexWriter.deleteDocuments(query);
        } catch (IOException e) {
            logger.error("Error deleting document with fileName {} and parentRelativePath {}", model.getFileName(), model.getParentRelativePath());
            throw new LocalFtsRuntimeException("Error deleting document with fileName " + model.getFileName() + " and parentRelativePath " + model.getParentRelativePath());
        }
    }

    private void updateModel(SearchFileModel model) {
        deleteModel(model);
        addModel(model);
    }

}
