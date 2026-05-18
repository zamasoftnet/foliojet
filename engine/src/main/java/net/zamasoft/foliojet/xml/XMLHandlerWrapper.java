package net.zamasoft.foliojet.xml;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.ext.LexicalHandler;

/**
 * ContentHandlerのフィルタです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XMLHandlerWrapper.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class XMLHandlerWrapper implements XMLHandler {
	protected final LexicalHandler lexicalHandler;
	protected final ContentHandler contentHandler;
	protected static final DefaultHandler2 DEFAULT_HANDLER = new DefaultHandler2();

	public XMLHandlerWrapper(ContentHandler contentHandler, LexicalHandler lexicalHandler) {
		if (lexicalHandler == null) {
			lexicalHandler = DEFAULT_HANDLER;
		}
		if (contentHandler == null) {
			contentHandler = DEFAULT_HANDLER;
		}
		this.lexicalHandler = lexicalHandler;
		this.contentHandler = contentHandler;
	}

	public XMLHandlerWrapper(XMLHandler xmlHandler) {
		this.lexicalHandler = xmlHandler;
		this.contentHandler = xmlHandler;
	}

	public void comment(char[] ch, int off, int len) throws SAXException {
		this.lexicalHandler.comment(ch, off, len);
	}

	public void endCDATA() throws SAXException {
		this.lexicalHandler.endCDATA();
	}

	public void endDTD() throws SAXException {
		this.lexicalHandler.endDTD();
	}

	public void endEntity(String name) throws SAXException {
		this.lexicalHandler.endEntity(name);
	}

	public void startCDATA() throws SAXException {
		this.lexicalHandler.startCDATA();
	}

	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		this.lexicalHandler.startDTD(name, publicId, systemId);
	}

	public void startEntity(String name) throws SAXException {
		this.lexicalHandler.startEntity(name);
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		this.contentHandler.characters(ch, start, length);
	}

	public void endDocument() throws SAXException {
		this.contentHandler.endDocument();
	}

	public void endElement(String uri, String lName, String qName) throws SAXException {
		this.contentHandler.endElement(uri, lName, qName);
	}

	public void endPrefixMapping(String prefix) throws SAXException {
		this.contentHandler.endPrefixMapping(prefix);
	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		this.contentHandler.ignorableWhitespace(ch, start, length);
	}

	public void processingInstruction(String target, String data) throws SAXException {
		this.contentHandler.processingInstruction(target, data);
	}

	public void setDocumentLocator(Locator locator) {
		this.contentHandler.setDocumentLocator(locator);
	}

	public void skippedEntity(String name) throws SAXException {
		this.contentHandler.skippedEntity(name);
	}

	public void startDocument() throws SAXException {
		this.contentHandler.startDocument();
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		this.contentHandler.startElement(uri, lName, qName, atts);
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		assert prefix != null;
		assert uri != null;
		this.contentHandler.startPrefixMapping(prefix, uri);
	}
}