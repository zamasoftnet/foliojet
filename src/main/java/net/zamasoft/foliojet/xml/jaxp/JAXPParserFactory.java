package net.zamasoft.foliojet.xml.jaxp;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.ParserFactory;

/**
 * 標準JAXPによるXMLパーサーファクトリです。
 */
public class JAXPParserFactory implements ParserFactory {
	public boolean match(String key) {
		String mimeType = (String) key;
		if (MimeTypeHelper.equals("text/xml", mimeType) || MimeTypeHelper.equals("application/xml", mimeType)
				|| MimeTypeHelper.equals("text/xhtml", mimeType) || MimeTypeHelper.equals("application/xhtml", mimeType)
				|| MimeTypeHelper.equals("application/xhtml+xml", mimeType)) {
			return true;
		}
		return false;
	}

	public Parser createParser() {
		return new JAXPParser();
	}
}
