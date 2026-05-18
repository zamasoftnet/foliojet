package net.zamasoft.foliojet.xml;

import net.zamasoft.foliojet.plugin.Plugin;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ParserFactory.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface ParserFactory extends Plugin<String> {
	public Parser createParser();
}