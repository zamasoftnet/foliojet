package net.zamasoft.foliojet.xml.parser;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.ParserFactory;

/**
 * CommonMark(Markdown)パーサーファクトリです。
 */
public class MarkdownParserFactory implements ParserFactory {
	public boolean match(String key) {
		String mimeType = (String) key;
		return MimeTypeHelper.equals("text/markdown", mimeType) || MimeTypeHelper.equals("text/x-markdown", mimeType);
	}

	public Parser createParser() {
		return new MarkdownParser();
	}
}
