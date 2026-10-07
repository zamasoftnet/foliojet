package net.zamasoft.foliojet.xml;

import org.xml.sax.Locator;

/**
 * Location information in XML/HTML source.
 * Parsers pass this through {@link org.xml.sax.ContentHandler#setDocumentLocator}.
 */
public interface SourceLocator extends Locator {
	public int getCharacterOffset();

}
