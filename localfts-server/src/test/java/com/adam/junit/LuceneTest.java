package com.adam.junit;

import org.wltea.analyzer.lucene.IKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LuceneTest {

    private final String indexPath = "index-dir";

    @Test
    public void testPrefixQuery() throws IOException {
        Path path = Paths.get(indexPath);
        Assert.assertTrue(Files.isReadable(path));
        Directory directory = FSDirectory.open(path);
        IndexReader indexReader = DirectoryReader.open(directory);
        IKAnalyzer analyzer = new IKAnalyzer();
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        Term term = new Term("parentRelativePath", "/");
        PrefixQuery prefixQuery = new PrefixQuery(term);
        TopDocs topDocs = indexSearcher.search(prefixQuery, 100);
        System.out.println("totalHits=" + topDocs.totalHits);
        Assert.assertTrue(topDocs.totalHits > 0);
    }

    @Test
    public void testQuery() throws IOException, ParseException {
        Path path = Paths.get(indexPath);
        Assert.assertTrue(Files.isReadable(path));
        Directory directory = FSDirectory.open(path);
        IndexReader indexReader = DirectoryReader.open(directory);
        IKAnalyzer analyzer = new IKAnalyzer();
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
//        Term term = new Term("fileName", "doc");
        QueryParser queryParser = new QueryParser("fileName", analyzer);
        Query keywordQuery = queryParser.parse("PPT");
        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
        Query query = queryBuilder.build();
        TopDocs topDocs = indexSearcher.search(query, 10);
        System.out.println("totalHits=" + topDocs.totalHits);
        Assert.assertTrue(topDocs.totalHits > 0);
    }

    @Test
    public void testWildcardQuery() throws IOException{
        Path path = Paths.get(indexPath);
        Assert.assertTrue(Files.isReadable(path));
        Directory directory = FSDirectory.open(path);
        IndexReader indexReader = DirectoryReader.open(directory);
        IKAnalyzer analyzer = new IKAnalyzer();
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        String keyword = ".";
        keyword = keyword.replace("*", "\\*").replace("?", "\\?");
        Term term = new Term("fileName", "*" + keyword + "*");
        WildcardQuery query = new WildcardQuery(term);
        TopDocs topDocs = indexSearcher.search(query, 100);
        System.out.println("totalHits=" + topDocs.totalHits);
        Assert.assertTrue(topDocs.totalHits > 0);
        for(ScoreDoc scoreDoc: topDocs.scoreDocs) {
            Document document = indexSearcher.doc(scoreDoc.doc);
            System.out.println(document.get("fileName"));
        }
    }

    @Test
    public void testTermQuery() throws IOException{
        Path path = Paths.get(indexPath);
        Assert.assertTrue(Files.isReadable(path));
        Directory directory = FSDirectory.open(path);
        IndexReader indexReader = DirectoryReader.open(directory);
        IKAnalyzer analyzer = new IKAnalyzer();
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        Term term = new Term("fileContent", "United");
        TermQuery query = new TermQuery(term);
        TopDocs topDocs = indexSearcher.search(query, 100);
        System.out.println("totalHits=" + topDocs.totalHits);
        Assert.assertTrue(topDocs.totalHits > 0);
        for(ScoreDoc scoreDoc: topDocs.scoreDocs) {
            Document document = indexSearcher.doc(scoreDoc.doc);
            System.out.println(document.get("fileName"));
        }
    }

}
