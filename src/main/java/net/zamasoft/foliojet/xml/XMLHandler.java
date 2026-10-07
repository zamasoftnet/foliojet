package net.zamasoft.foliojet.xml;

import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

/**
 * A document event sink combining SAX ContentHandler and LexicalHandler.
 */
public interface XMLHandler extends ContentHandler, LexicalHandler {
	/**
	 * Combines a ContentHandler and LexicalHandler into an XMLHandler.
	 * Ignores events for whichever handler is null.
	 */
	public static XMLHandler of(ContentHandler contentHandler, LexicalHandler lexicalHandler) {
		return new CompositeXMLHandler(contentHandler, lexicalHandler);
	}
}
