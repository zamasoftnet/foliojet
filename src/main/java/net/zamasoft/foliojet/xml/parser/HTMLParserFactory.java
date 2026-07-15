package net.zamasoft.foliojet.xml.parser;

import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.ParserFactory;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: HTMLParserFactory.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class HTMLParserFactory implements ParserFactory {

	public boolean match(String key) {
		return true;
	}

	public int priority() {
		return -1000;
	}

	public Parser createParser() {
		return new HTMLParser();
	}
}
