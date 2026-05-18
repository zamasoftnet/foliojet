package net.zamasoft.foliojet.xml;

import java.io.IOException;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;

/* NoAndroid begin */
import org.apache.xerces.xni.XMLLocator;
/* NoAndroid end */
/* Android begin *//*
					import mf.org.apache.xerces.xni.XMLLocator;
					*//* Android end */

import org.xml.sax.SAXException;

/**
 * パーサーのインターフェースです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Parser.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface Parser {
	public static final ThreadLocal<XMLLocator> XML_LOCATOR = new ThreadLocal<XMLLocator>();

	/**
	 * ドキュメントを解析してSAXイベントを生成します。
	 * 
	 * @param ua
	 * @param source
	 * @param xmlHandler
	 * @throws SAXException
	 * @throws IOException
	 */
	public void parse(UserAgent ua, Source source, XMLHandler xmlHandler) throws SAXException, IOException;
}