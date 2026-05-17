package jp.cssj.homare.xml.xerces;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import jp.cssj.homare.xml.Parser;
import jp.cssj.homare.xml.ParserFactory;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: XMLParserFactory.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class XMLParserFactory implements ParserFactory {
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
		return new Xerces2Parser();
	}
}