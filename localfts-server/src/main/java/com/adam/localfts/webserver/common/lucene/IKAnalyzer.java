package com.adam.localfts.webserver.common.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;

public class IKAnalyzer extends Analyzer {
	private boolean useSmart;

	public boolean useSmart() {
		return useSmart;
	}

	public void setUseSmart(boolean useSmart) {
		this.useSmart = useSmart;
	}

	public IKAnalyzer() {
		this(false);
	}

	public IKAnalyzer(boolean useSmart) {
		super();
		this.useSmart = useSmart;
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer _IKTokenizer = new IKTokenizer(this.useSmart());
		return new TokenStreamComponents(_IKTokenizer);
	}
}