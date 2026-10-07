package net.zamasoft.foliojet.xml.filter;

import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.XMLHandler;

import org.xml.sax.SAXException;

/**
 * A filter that removes document start and end events.
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
