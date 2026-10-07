package net.zamasoft.foliojet.xml;

import java.io.IOException;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;

import org.xml.sax.SAXException;

/**
 * The parser interface.
 *
 * @author MIYABE Tatsuhiko
 */
public interface Parser {
	/**
	 * Parses a document and generates SAX events.
	 * Reports location information by passing {@link SourceLocator} to setDocumentLocator.
	 *
	 * @param ua
	 * @param source
	 * @param xmlHandler
	 * @throws SAXException
	 * @throws IOException
	 */
	public void parse(UserAgent ua, Source source, XMLHandler xmlHandler) throws SAXException, IOException;
}
