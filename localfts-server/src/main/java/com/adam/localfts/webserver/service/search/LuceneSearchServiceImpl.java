package com.adam.localfts.webserver.service.search;

import com.adam.localfts.webserver.common.PageObject;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.common.search.*;
import com.adam.localfts.webserver.common.sort.SearchColumn;
import com.adam.localfts.webserver.common.sort.SortOrder;
import com.adam.localfts.webserver.exception.LocalFtsRuntimeException;
import com.adam.localfts.webserver.exception.LocalFtsStartupException;
import com.adam.localfts.webserver.service.FtsServerConfigService;
import com.adam.localfts.webserver.util.ReflectUtil;
import com.adam.localfts.webserver.util.Util;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Service
public class LuceneSearchServiceImpl implements SearchServiceInterface {

    private String indexPath;
    private final Logger logger = LoggerFactory.getLogger(LuceneSearchServiceImpl.class);
    @Autowired
    private FtsServerConfigService ftsServerConfigService;

    @Override
    public PageObject<SearchDTO> search(String keyword, String searchId, AdvancedSearchCondition advancedSearchCondition,
                                        int pageNo, int pageSize, SearchColumn sortColumn, SortOrder sortOrder) {
        if(advancedSearchCondition != null && advancedSearchCondition.emptyResult()) {
            return new PageObject<>();
        }
        Assert.notNull(indexPath, "indexPath is null!");

        try {
            Path path = Paths.get(indexPath);
            if(!Files.isReadable(path)) {
                logger.error("Cannot read index path:{}", indexPath);
                throw new LocalFtsStartupException("Cannot read index path " + indexPath);
            }
            Directory directory = FSDirectory.open(path);
            IndexReader indexReader = DirectoryReader.open(directory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            boolean needLowerCaseAndSC = advancedSearchCondition != null && advancedSearchCondition.getCaseAndSTCSensitive() != null && !advancedSearchCondition.getCaseAndSTCSensitive();
            IKAnalyzer analyzer = new IKAnalyzer(false, needLowerCaseAndSC, needLowerCaseAndSC);
            BooleanQuery.Builder rootQueryBuilder = new BooleanQuery.Builder();
            boolean isSimpleKeywordQuery = advancedSearchCondition == null || advancedSearchCondition.isEmpty()
                    || advancedSearchCondition.getSearchType() != SearchType.BOTH;
            if(isSimpleKeywordQuery) {
                String keywordField = (advancedSearchCondition == null || advancedSearchCondition.getSearchType() == SearchType.FILENAME_ONLY )
                        ? "fileName" : "fileContent";
                if(needLowerCaseAndSC) {
                    keywordField = keywordField + "_lowercase";
                    keyword = keyword.toLowerCase();
                }
                QueryParser keywordQueryParser = new QueryParser(keywordField, analyzer);
                Query keywordQuery = keywordQueryParser.parse(keyword);
                rootQueryBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
            } else {
                String fileNameField = "fileName", fileContentField = "fileContent";
                if(needLowerCaseAndSC) {
                    fileNameField = fileNameField + "_lowercase";
                    fileContentField = fileContentField + "_lowercase";
                    keyword = keyword.toLowerCase();
                }
                QueryParser fileNameQueryParser = new QueryParser(fileNameField, analyzer);
                Query fileNameQuery = fileNameQueryParser.parse(keyword);
                QueryParser fileContentQueryParser = new QueryParser(fileContentField, analyzer);
                Query fileContentQuery = fileContentQueryParser.parse(keyword);
                BooleanQuery keywordQuery = new BooleanQuery.Builder()
                        .add(fileNameQuery, BooleanClause.Occur.SHOULD)
                        .add(fileContentQuery, BooleanClause.Occur.SHOULD)
                        .build();
                rootQueryBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
            }
            if(advancedSearchCondition != null && !advancedSearchCondition.isEmpty()) {
                if (!CollectionUtils.isEmpty(advancedSearchCondition.getSearchPaths())) {
                    BooleanQuery.Builder searchPathQueryBuilder = new BooleanQuery.Builder();
                    for(String searchPath: advancedSearchCondition.getSearchPaths()) {
                        Term term = new Term("parentRelativePath", searchPath);
                        PrefixQuery prefixQuery = new PrefixQuery(term);
                        searchPathQueryBuilder.add(prefixQuery, BooleanClause.Occur.SHOULD);
                    }
                    BooleanQuery searchPathQuery = searchPathQueryBuilder.build();
                    rootQueryBuilder.add(searchPathQuery, BooleanClause.Occur.MUST);
                }
                if(advancedSearchCondition.getFilterFileType() != null && advancedSearchCondition.getFilterFileType()) {
                    BooleanQuery.Builder fileTypeQueryBuilder = new BooleanQuery.Builder();
                    boolean isLinux = Util.isSystemLinux();
                    for(String suffix: advancedSearchCondition.getFileTypes()) {
                        String field = "fileName_simple_suffix";
                        String text = Util.reverseStr(suffix.toLowerCase());
                        if(isLinux) {
                            if(advancedSearchCondition.getFilterFileTypeCaseSensitive() != null && advancedSearchCondition.getFilterFileTypeCaseSensitive()) {
                                text = Util.reverseStr(suffix);
                            } else {
                                field = "fileName_simple_lowercase_suffix";
                            }
                        }
                        Term term = new Term(field, text);
                        PrefixQuery query = new PrefixQuery(term);
                        fileTypeQueryBuilder.add(query, BooleanClause.Occur.SHOULD);
                    }
                    BooleanQuery fileTypeQuery = fileTypeQueryBuilder.build();
                    rootQueryBuilder.add(fileTypeQuery, BooleanClause.Occur.MUST);
                }
                if(advancedSearchCondition.getDirectory() != null) {
                    Term term = new Term("isDirectory", String.valueOf(advancedSearchCondition.getDirectory()));
                    TermQuery termQuery = new TermQuery(term);
                    rootQueryBuilder.add(termQuery, BooleanClause.Occur.MUST);
                }
                if(advancedSearchCondition.lastModifiedLower() != null || advancedSearchCondition.lastModifiedUpper() != null) {
                    Query lastModifiedQuery = constructDateRangeQuery("lastModified",
                            advancedSearchCondition.lastModifiedLower(), advancedSearchCondition.lastModifiedUpper());
                    rootQueryBuilder.add(lastModifiedQuery, BooleanClause.Occur.MUST);
                }
                if(advancedSearchCondition.fileSizeLower() != null || advancedSearchCondition.fileSizeUpper() != null) {
                    Query fileSizeQuery = constructLongRangeQuery("fileSize",
                            advancedSearchCondition.fileSizeLower(), advancedSearchCondition.fileSizeUpper());
                    rootQueryBuilder.add(fileSizeQuery, BooleanClause.Occur.MUST);
                }
                if(advancedSearchCondition.getFolderCompressStatus() != null) {
                    Term term = new Term("compressStatus", advancedSearchCondition.getFolderCompressStatus().name());
                    TermQuery compressStatusQuery = new TermQuery(term);
                    rootQueryBuilder.add(compressStatusQuery, BooleanClause.Occur.MUST);
                }
                if(advancedSearchCondition.compressedFileSizeLower() != null || advancedSearchCondition.compressedFileSizeUpper() != null) {
                    Query compressedFileSizeQuery = constructLongRangeQuery("compressedFileSize",
                            advancedSearchCondition.compressedFileSizeLower(), advancedSearchCondition.compressedFileSizeUpper());
                    rootQueryBuilder.add(compressedFileSizeQuery, BooleanClause.Occur.MUST);
                }
                if(advancedSearchCondition.compressedFileLastModifiedLower() != null || advancedSearchCondition.compressedFileLastModifiedUpper() != null) {
                    Query compressedFileLastModifiedQuery = constructDateRangeQuery("compressedFileLastModified",
                            advancedSearchCondition.compressedFileLastModifiedLower(), advancedSearchCondition.compressedFileLastModifiedUpper());
                    rootQueryBuilder.add(compressedFileLastModifiedQuery, BooleanClause.Occur.MUST);
                }
            }

            long startMillis = System.currentTimeMillis();
            Query rootQuery = rootQueryBuilder.build();
            Sort sort = null;
            TopDocs topDocs = null;
            if(sortColumn != null) {
                sort = buildSort(sortColumn, sortOrder);
            }
            if(pageNo == 1) {
                topDocs = search(indexSearcher, rootQuery, pageSize, sort);
            } else {
                ScoreDoc lastScoreDoc = null;
                topDocs = search(indexSearcher, rootQuery, (pageNo - 1) * pageSize, sort);
                if(topDocs.scoreDocs.length > 0) {
                    lastScoreDoc = topDocs.scoreDocs[topDocs.scoreDocs.length - 1];
                }
                //Assert.notNull(lastScoreDoc, "lastScoreDoc is null!");
                topDocs = searchAfter(indexSearcher, lastScoreDoc, rootQuery, pageSize, sort);
            }

            if(topDocs == null) {
                long endMillis = System.currentTimeMillis();
                logger.info("搜索(结果为空)完成，耗时{}ms, searchId={}", (endMillis - startMillis), searchId);
                return new PageObject<>();
            } else {
                List<SearchDTO> dataList = new LinkedList<>();
                SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();
                long startId = (pageNo - 1) * pageSize;
                boolean indexFileContent = ftsServerConfigService.getLocalFtsProperties().getSearch().getIndexFileContent().getEnabled();
                for(int i=0;i<topDocs.scoreDocs.length;i++) {
                    ScoreDoc scoreDoc = topDocs.scoreDocs[i];
                    Document document = indexSearcher.doc(scoreDoc.doc);
                    long id = startId + i + 1;
                    SearchDTO searchDTO = mapToDTO(document, id, simpleDateFormat);
                    handleHighlighter(searchDTO, rootQuery, indexReader, scoreDoc.doc, document, analyzer, indexFileContent);
                    dataList.add(searchDTO);
                }
                long endMillis = System.currentTimeMillis();
                logger.info("搜索{}完成，耗时{}ms, searchId={}", sort == null ? "" : "并排序", (endMillis - startMillis), searchId);
                return new PageObject<>(pageNo, pageSize, topDocs.totalHits, dataList);
            }
        } catch (Exception e) {
            throw new LocalFtsRuntimeException("搜索时出现异常", e);
        }
    }

    public void setIndexPath(String indexPath) {
        this.indexPath = indexPath;
    }

    private Query constructDateRangeQuery(String field, Date lower, Date upper) {
        long lowerRange = lower != null ? lower.getTime() : Long.MIN_VALUE;
        long upperRange = upper != null ? upper.getTime() : Long.MAX_VALUE;
        return LongPoint.newRangeQuery(field, lowerRange, upperRange);
    }

    private Query constructLongRangeQuery(String field, Long lower, Long upper) {
        long lowerRange = lower != null ? lower : Long.MIN_VALUE;
        long upperRange = upper != null ? upper : Long.MAX_VALUE;
        return LongPoint.newRangeQuery(field, lowerRange, upperRange);
    }

    private TopDocs search(IndexSearcher indexSearcher, Query query, int n, Sort sort) throws IOException {
        if(sort == null) {
            return indexSearcher.search(query, n);
        } else {
            return indexSearcher.search(query, n, sort);
        }
    }

    private TopDocs searchAfter(IndexSearcher indexSearcher, ScoreDoc scoreDoc, Query query, int n, Sort sort) throws IOException{
        if(sort == null) {
            return indexSearcher.searchAfter(scoreDoc, query, n);
        } else {
            return indexSearcher.searchAfter(scoreDoc, query, n, sort);
        }
    }

    private SearchDTO mapToDTO(Document document, long id, SimpleDateFormat simpleDateFormat) {
        SearchDTO searchDTO = new SearchDTO();
        searchDTO.setId(id);
        if(document == null) {
            logger.warn("document is null!");
            return searchDTO;
        }
        String fileName = document.get("fileName");
        searchDTO.setFilename(fileName);
        searchDTO.setFilenameFormatted(fileName);
        searchDTO.setParentRelativePath(document.get("parentRelativePath"));
        String fileContent = document.get("fileContent");
        searchDTO.setFileContent(fileContent);
        boolean isDirectory = Boolean.parseBoolean(document.get("isDirectory"));
        searchDTO.setDirectory(isDirectory);
        long lastModified = Long.parseLong(document.get("lastModified_value"));
        searchDTO.setLastModified(lastModified);
        searchDTO.setLastModifiedStr(simpleDateFormat.format(new Date(lastModified)));
        if(isDirectory) {
            FolderCompressStatus compressStatus = FolderCompressStatus.valueOf(document.get("compressStatus"));
            searchDTO.setCompressStatus(compressStatus);
            if(compressStatus == FolderCompressStatus.COMPRESSED) {
                searchDTO.setCompressedFilePath(document.get("compressedFilePath"));
                long compressedFileSize = Long.parseLong(document.get("compressedFileSize_value"));
                searchDTO.setCompressedFileSize(compressedFileSize);
                searchDTO.setCompressedFileSizeStr(Util.fileLengthToStringNew(compressedFileSize));
                long compressedFileLastModified = Long.parseLong(document.get("compressedFileLastModified_value"));
                searchDTO.setCompressedFileLastModified(compressedFileLastModified);
                searchDTO.setCompressedFileLastModifiedStr(simpleDateFormat.format(new Date(compressedFileLastModified)));
            }
        } else {
            long fileSize = Long.parseLong(document.get("fileSize_value"));
            searchDTO.setFileSize(fileSize);
            searchDTO.setFileSizeStr(Util.fileLengthToStringNew(fileSize));
        }
        return searchDTO;
    }

    private void handleHighlighter(SearchDTO searchDTO, Query query, IndexReader indexReader, int docId, Document document, IKAnalyzer analyzer, boolean indexFileContent) {
        String fileNameFragment = getBestFragment(query, indexReader, docId, document, analyzer, "fileName");
        if(fileNameFragment != null) {
            searchDTO.setFilenameFormatted(fileNameFragment);
        }
        if(!searchDTO.getDirectory() && indexFileContent) {
            String fileContentFragment = getBestFragment(query, indexReader, docId, document, analyzer, "fileContent");
            if (fileContentFragment != null) {
                searchDTO.setFileContent(fileContentFragment);
            } else {
                String fileContent = searchDTO.getFileContent();
                if (fileContent != null) {
                    if (fileContent.length() > 1000) {
                        fileContent = fileContent.substring(0, 1000) + "...";
                    }
                    fileContent = Util.escapeHtmlChars(fileContent);
                    searchDTO.setFileContent(fileContent);
                }
            }
        }
    }

    private String getBestFragment(Query query, IndexReader indexReader, int docId, Document document, IKAnalyzer analyzer, String field) {
        boolean lowerCaseEnglish = analyzer.lowerCaseEnglish();
        String originalField = field;
        if(lowerCaseEnglish) {
            field = field + "_lowercase";
        }
        String fieldText = document.get(field);
        if(StringUtils.isEmpty(fieldText)) {
            return null;
        }
        String originalFieldText = document.get(originalField);
        if(StringUtils.isEmpty(originalFieldText)) {
            return null;
        }
        try {
            QueryScorer queryScorer = new QueryScorer(query, field);
            RecoverableSimpleHTMLFormatter simpleHTMLFormatter = new RecoverableSimpleHTMLFormatter("<span style=\"color:red;\">", "</span>", originalFieldText);
            RecoverableHighlighter highlighter = new RecoverableHighlighter(simpleHTMLFormatter, new EscapeHTMLCharsEncoder(), queryScorer);
            TokenStream tokenStream = TokenSources.getAnyTokenStream(indexReader, docId, field, analyzer);
            Fragmenter fragmenter = new SimpleSpanFragmenter(queryScorer);
            highlighter.setTextFragmenter(fragmenter);
            TextFragment[] textFragments = highlighter.getOriginalBestTextFragments(tokenStream, fieldText, originalFieldText, true, 1);
            if(textFragments == null || textFragments.length == 0) {
                return null;
            }
            TextFragment textFragment = textFragments[0];
            String fragmentStr = textFragment.toString();
            Integer textStartPos = ReflectUtil.getFieldValue(textFragment, "textStartPos", Integer.class);
            Integer textEndPos = ReflectUtil.getFieldValue(textFragment, "textEndPos", Integer.class);
            CharSequence markedUpText = ReflectUtil.getFieldValue(textFragment, "markedUpText", CharSequence.class);
            if(textStartPos == null || textStartPos != 0) {
                fragmentStr = "..." + fragmentStr;
            }
            if(textEndPos == null || markedUpText == null || textEndPos != markedUpText.length()) {
                fragmentStr = fragmentStr + "...";
            }
            return fragmentStr;
        } catch (Exception e) {
            logger.warn("Error getting best fragment, ex.type={}, ex.message={}",e.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private Sort buildSort(SearchColumn searchColumn, SortOrder sortOrder) {
        Assert.notNull(searchColumn, "searchColumn is null!");
        Assert.notNull(sortOrder, "sortOrder is null!");
        Assert.isTrue(searchColumn != SearchColumn.DEFAULT, "searchColumn is DEFAULT!");
        String field;
        SortField.Type type;
        boolean reverse = sortOrder == SortOrder.DESC;
        switch (searchColumn) {
            case FILENAME:
                field = "fileName";
                type = SortField.Type.STRING_VAL;
                break;
            case PARENT_PATH:
                field = "parentRelativePath";
                type = SortField.Type.STRING_VAL;
                break;
            case TYPE:
                field = "isDirectory";
                type = SortField.Type.STRING_VAL;
                break;
            case SIZE:
                field = "fileSize";
                type = SortField.Type.LONG;
                break;
            case LAST_MODIFIED:
                field = "lastModified";
                type = SortField.Type.LONG;
                break;
            case COMPRESS_STATUS:
                field = "compressStatus";
                type = SortField.Type.STRING_VAL;
                break;
            case COMPRESS_FILE_SIZE:
                field = "compressedFileSize";
                type = SortField.Type.LONG;
                break;
            case COMPRESS_FILE_LAST_MODIFIED:
                field = "compressedFileLastModified";
                type = SortField.Type.LONG;
                break;
            default:
                throw new LocalFtsRuntimeException("Invalid switch");
        }
        field = field + "_sort";
        SortField sortField = new SortField(field, type, reverse);
        return new Sort(sortField);
    }

    private void closeObjects(IndexReader indexReader, Directory directory, Analyzer analyzer) {
        try {
            indexReader.close();
        } catch (IOException e) {
        }
        try {
            directory.close();
        } catch (IOException e) {
        }
        analyzer.close();
    }

}
