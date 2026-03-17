package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.util.Util;
import org.apache.lucene.search.highlight.Encoder;

public class EscapeHTMLCharsEncoder implements Encoder {
    @Override
    public String encodeText(String originalText) {
        return Util.escapeHtmlChars(originalText);
    }
}
