package net.zamasoft.foliojet.xml.parser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import net.zamasoft.balancer.SAXParser;
import net.zamasoft.balancer.TagBalancer;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.SourceLocator;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;

import org.htmlunit.cyberneko.xerces.xni.Augmentations;
import org.htmlunit.cyberneko.xerces.xni.NamespaceContext;
import org.htmlunit.cyberneko.xerces.xni.XMLLocator;
import org.htmlunit.cyberneko.xerces.xni.XNIException;
import org.htmlunit.cyberneko.xerces.xni.parser.XMLDocumentFilter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Parses HTML with NekoHTML.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: HTMLParser.java 1608 2021-04-18 03:57:50Z miyabe $
 */
public class HTMLParser implements Parser {

	public void parse(final UserAgent ua, final Source source, XMLHandler xmlHandler) throws SAXException, IOException {
		final SAXParser parser = new SAXParser();

		parser.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
		parser.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");
		parser.setFeature("http://cyberneko.org/html/features/scanner/ignore-specified-charset", false);
		parser.setFeature("http://xml.org/sax/features/namespaces", true);
		parser.setFeature("http://cyberneko.org/html/features/scanner/cdata-sections", true);

		final boolean changeDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);

		final TagBalancer balancer = new TagBalancer();
		XMLDocumentFilter[] filters = { new ForeignContentFilter(ua, balancer, changeDefaultNamespace) {
			@Override
			public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
					Augmentations augs) throws XNIException {
				super.startDocument(locator, encoding, namespaceContext, augs);
				xmlHandler.setDocumentLocator(new HTMLSourceLocator(locator));
			}
		}, balancer };
		parser.setProperty("http://cyberneko.org/html/properties/filters", filters);

		parser.setProperty("http://xml.org/sax/properties/lexical-handler", xmlHandler);
		parser.setContentHandler(xmlHandler);

		if (source.isReader()) {
			// Character stream
			this.parseReader(ua, source, parser);
		} else {
			// Byte stream
			this.parseStream(ua, source, parser);
		}
	}

	protected void parseReader(final UserAgent ua, final Source source, final SAXParser parser)
			throws SAXException, IOException {
		// Character stream
		//
		// The input goes to the parser as it is (2026-11-11). Until then a filter read every character to find a
		// comment that is never closed ("<!-- ... >" with no "-->" up to the end of the input) and ended it at its
		// first ">", as the browsers of the 2000s did: about 300 lines, one more pass over every input, and the whole
		// rest of the document held in memory once such a comment began. Today's browsers take the rest of such a
		// document as the comment (Chrome prints nothing after it), and so does the parser now.
		try (Reader in = new BufferedReader(source.getReader(), 64 * 1024)) {
			String encoding = source.getEncoding();
			if (encoding != null) {
				ua.getDocumentContext().setEncoding(encoding);
			}
			InputSource inputSource = new InputSource(in);
			parser.parse(inputSource);
		}
	}

	protected void parseStream(final UserAgent ua, final Source source, final SAXParser parser)
			throws SAXException, IOException {
		// Byte stream
		// Check BOM. (No filter for comments that are never closed: see parseReader.)
		InputStream in = new BufferedInputStream(source.getInputStream(), 64 * 1024);
		String encoding = XMLUtils.checkBOM(in);
		if (encoding == null) {
			// Transport encoding (HTTP Content-Type or the CTI-supplied value) has priority after BOM (HTML encoding rules).
			// Until 2026-10-04, this was ignored; bodies barred from parseReader by input limits used auto-detection.
			encoding = source.getEncoding();
		}

		if (encoding != null) {
			try (Reader r = new InputStreamReader(in, encoding)) {
				ua.getDocumentContext().setEncoding(encoding);
				InputSource inputSource = new InputSource(r);
				parser.parse(inputSource);
			}
		} else
			try {
				encoding = UAProps.INPUT_DEFAULT_ENCODING.getString(ua);
				if (encoding.equalsIgnoreCase("JISUniAutoDetect")) {
					Charset cs = CharsetDetector.detectCharset(in);
					if (cs != null) {
						encoding = cs.name();
					}
				}
				String declEncoding = XMLUtils.checkXMLDeclEncoding(in);
				if (declEncoding != null) {
					encoding = declEncoding;
				}
				parser.setProperty("http://cyberneko.org/html/properties/default-encoding", encoding);
				ua.getDocumentContext().setEncoding(encoding);
				InputSource inputSource = new InputSource(in);
				parser.parse(inputSource);
			} finally {
				in.close();
			}
	}

	private static class HTMLSourceLocator implements SourceLocator {
		private final XMLLocator locator;

		HTMLSourceLocator(XMLLocator locator) {
			this.locator = locator;
		}

		public String getPublicId() {
			return this.locator.getPublicId();
		}

		public String getSystemId() {
			return this.locator.getLiteralSystemId();
		}

		public int getLineNumber() {
			return this.locator.getLineNumber();
		}

		public int getColumnNumber() {
			return this.locator.getColumnNumber();
		}

		public int getCharacterOffset() {
			return this.locator.getCharacterOffset();
		}

	}
}

