package net.zamasoft.foliojet.xml;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * ContentHandlerのフィルタです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: DefaultXMLHandlerFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class DefaultXMLHandlerFilter implements XMLHandlerFilter {
	protected XMLHandler outHandler;

	public DefaultXMLHandlerFilter() {
		// default constructor
	}

	public DefaultXMLHandlerFilter(XMLHandler outHandler) {
		this.outHandler = outHandler;
	}

	public void comment(char[] ch, int off, int len) throws SAXException {
		this.outHandler.comment(ch, off, len);
	}

	public void endCDATA() throws SAXException {
		this.outHandler.endCDATA();
	}

	public void endDTD() throws SAXException {
		this.outHandler.endDTD();
	}

	public void endEntity(String name) throws SAXException {
		this.outHandler.endEntity(name);
	}

	public void startCDATA() throws SAXException {
		this.outHandler.startCDATA();
	}

	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		this.outHandler.startDTD(name, publicId, systemId);
	}

	public void startEntity(String name) throws SAXException {
		this.outHandler.startEntity(name);
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		this.outHandler.characters(ch, start, length);
	}

	public void endDocument() throws SAXException {
		this.outHandler.endDocument();
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		this.outHandler.endElement(uri, lName, qName);
	}

	public void endPrefixMapping(String prefix) throws SAXException {
		this.outHandler.endPrefixMapping(prefix);
	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		this.outHandler.ignorableWhitespace(ch, start, length);
	}

	public void processingInstruction(String target, String data) throws SAXException {
		this.outHandler.processingInstruction(target, data);
	}

	public void setDocumentLocator(Locator locator) {
		this.outHandler.setDocumentLocator(locator);
	}

	public void skippedEntity(String name) throws SAXException {
		this.outHandler.skippedEntity(name);
	}

	public void startDocument() throws SAXException {
		this.outHandler.startDocument();
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		this.outHandler.startElement(uri, lName, qName, atts);
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		this.outHandler.startPrefixMapping(prefix, uri);
	}

	public void setXMLHandler(XMLHandler handler) {
		this.outHandler = handler;
	}

	public XMLHandler getXMLHandler() {
		return this.outHandler;
	}
}