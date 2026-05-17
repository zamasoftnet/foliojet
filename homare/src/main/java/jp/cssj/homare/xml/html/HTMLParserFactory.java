package jp.cssj.homare.xml.html;

import jp.cssj.homare.xml.Parser;
import jp.cssj.homare.xml.ParserFactory;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: HTMLParserFactory.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class HTMLParserFactory implements ParserFactory {

	public boolean match(String key) {
		return true;
	}

	public Parser createParser() {
		return new HTMLParser();
	}
}