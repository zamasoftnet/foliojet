package net.zamasoft.foliojet.xml.jaxp;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.DefaultXMLHandlerFilter;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.SourceLocator;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.util.XMLParsers;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;

import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Locator2;

/**
 * 標準JAXPのSAXパーサーによりXMLを解析します。
 */
public class JAXPParser implements Parser {
	public void parse(final UserAgent ua, final Source source, final XMLHandler xmlHandler)
			throws SAXException, IOException {
		String encoding = source.getEncoding();
		if (encoding != null) {
			ua.getDocumentContext().setEncoding(encoding);
		}

		final XMLReader reader;
		try {
			reader = XMLParsers.createXMLReader();
		} catch (Exception e) {
			throw new SAXException(e);
		}

		final InputSource inputSource = XMLUtils.toInputSource(source);
		reader.setProperty("http://xml.org/sax/properties/lexical-handler", xmlHandler);
		reader.setContentHandler(new DefaultXMLHandlerFilter(xmlHandler) {
			public void setDocumentLocator(Locator locator) {
				super.setDocumentLocator(locator);
				Parser.SOURCE_LOCATOR.set(new SAXSourceLocator(locator));
				try {
					if (locator instanceof Locator2) {
						String encoding = ((Locator2) locator).getEncoding();
						if (encoding != null) {
							ua.getDocumentContext().setEncoding(encoding);
						}
					} else {
						ua.getDocumentContext().setEncoding("UTF-8");
					}
				} catch (UnsupportedEncodingException ex) {
					throw new RuntimeException(ex);
				}
			}
		});
		try {
			reader.parse(inputSource);
		} finally {
			Parser.SOURCE_LOCATOR.remove();
		}
	}

	private static class SAXSourceLocator implements SourceLocator {
		private final Locator locator;

		SAXSourceLocator(Locator locator) {
			this.locator = locator;
		}

		public String getPublicId() {
			return this.locator.getPublicId();
		}

		public String getSystemId() {
			return this.locator.getSystemId();
		}

		public int getLineNumber() {
			return this.locator.getLineNumber();
		}

		public int getColumnNumber() {
			return this.locator.getColumnNumber();
		}

		public int getCharacterOffset() {
			return -1;
		}

		public String getEncoding() {
			if (this.locator instanceof Locator2) {
				return ((Locator2) this.locator).getEncoding();
			}
			return null;
		}

		public String getXMLVersion() {
			if (this.locator instanceof Locator2) {
				return ((Locator2) this.locator).getXMLVersion();
			}
			return null;
		}
	}
}
