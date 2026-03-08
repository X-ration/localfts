package com.adam.localfts.webserver.util;

import com.adam.localfts.webserver.common.lucene.IKAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LuceneIndexUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneIndexUtil.class);

    public static void createEmptyIndex(String indexDir) throws IOException {
        Assert.notNull(indexDir, "indexDir is null!");
        Path indexPath = Paths.get(indexDir);
        File indexPathFile = indexPath.toFile();
        if(indexPathFile.exists()) {
            Assert.isTrue(indexPathFile.isDirectory(), "indexPath exists, but is a file!");
        } else {
            try {
                boolean mkdirs = indexPathFile.mkdirs();
                Assert.isTrue(mkdirs, "Failed to create dir:" + indexDir);
            } catch (SecurityException e) {
                LOGGER.error("SecurityException occured creating dir:" + indexDir, e);
                Util.throwException(e.getClass(), e.getMessage());
            }
        }
        Assert.isTrue(Files.isReadable(indexPath), "indexPath is not readable!");
        Analyzer analyzer = new IKAnalyzer();
        IndexWriterConfig icw = new IndexWriterConfig(analyzer);
        icw.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        try (Directory directory = FSDirectory.open(indexPath);
             IndexWriter indexWriter = new IndexWriter(directory, icw)
        ) {
            indexWriter.commit();
        }
    }

}
