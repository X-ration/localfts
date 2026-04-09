/**
 * 基于SimpleHTMLFormatter(org.apache.lucene.search.highlight)扩展
 * 原始协议：Apache License 2.0
 */
package com.adam.localfts.webserver.common.search;

import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.TokenGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2026-03-27: 通过RecoverableHighlighter还原原文，已经不再需要扩展SimpleHTMLFormatter了。
 */
@Deprecated
public class RecoverableSimpleHTMLFormatter extends SimpleHTMLFormatter {
    private final String originalFullText;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public RecoverableSimpleHTMLFormatter(String originalFullText) {
        super();
        this.originalFullText = originalFullText;
    }

    public RecoverableSimpleHTMLFormatter(String preTag, String postTag, String originalFullText) {
        super(preTag, postTag);
        this.originalFullText = originalFullText;
    }

    @Override
    public String highlightTerm(String originalText, TokenGroup tokenGroup) {
        int start = tokenGroup.getStartOffset(), end = tokenGroup.getEndOffset();
        try {
            originalText = originalFullText.substring(start, end);
        } catch (IndexOutOfBoundsException e) {
            logger.warn("Error getting original text, msg={}", e.getMessage());
        }
        return super.highlightTerm(originalText, tokenGroup);
    }
}
