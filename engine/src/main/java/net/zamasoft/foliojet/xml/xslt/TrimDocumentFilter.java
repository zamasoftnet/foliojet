package net.zamasoft.foliojet.xml.xslt;

import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.XMLHandler;

import org.xml.sax.SAXException;

/**
 * ドキュメントの開始と終了を除去するフィルタです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TrimDocumentFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class TrimDocumentFilter extends DefaultXMLHandlerFilter {
	public TrimDocumentFilter() {
		// default constructor
	}

	public TrimDocumentFilter(XMLHandler outHandler) {
		super(outHandler);
	}

	public void endDocument() throws SAXException {
		// ignore
	}

	public void startDocument() throws SAXException {
		// ignore
	}
}
