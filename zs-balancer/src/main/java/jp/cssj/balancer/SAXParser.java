package jp.cssj.balancer;

import org.apache.xerces.xni.parser.XMLDocumentFilter;

/**
 * 独自のタグバランサを利用するSAXParserです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: SAXParser.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class SAXParser extends org.cyberneko.html.parsers.SAXParser {
	public SAXParser() {
		super();
		try {
			this.setFeature("http://cyberneko.org/html/features/balance-tags", false);
			final TagBalancer balancer = new TagBalancer();
			XMLDocumentFilter[] filters = { balancer };
			this.setProperty("http://cyberneko.org/html/properties/filters", filters);
		} catch (Exception e) {
			// ignore
		}
	}
}
