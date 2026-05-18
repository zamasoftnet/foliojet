package net.zamasoft.foliojet.xml.html;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import net.zamasoft.balancer.ElementProps;
import net.zamasoft.balancer.TagBalancer;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;

import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLDocumentFilter;
import org.cyberneko.html.filters.DefaultFilter;
import org.cyberneko.html.parsers.SAXParser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * NekoHTMLによりHTMLを解析します。
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
		parser.setFeature("http://cyberneko.org/html/features/balance-tags", false);

		final boolean chageDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);

		final TagBalancer balancer = new TagBalancer();
		XMLDocumentFilter[] filters = { new DefaultFilter() {
			private boolean firstElement = true;

			public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
					Augmentations augs) throws XNIException {
				super.startDocument(locator, encoding, namespaceContext, augs);
				Parser.XML_LOCATOR.set(locator);
			}

			public void startElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
				if (!chageDefaultNamespace) {
					if (element.uri != null && (element.prefix == null || element.prefix.length() == 0)) {
						element.uri = null;
					}
				}
				super.startElement(element, attributes, augs);
				if (this.firstElement && element.localpart.equalsIgnoreCase("body")) {
					// 標準モードへの切り替え
					if (ua.getDocumentContext().getCompatibleMode() <= DocumentContext.CM_STRICT) {
						balancer.setElementProps(ElementProps.getElementProps("html4.xml"));
					}
					this.firstElement = false;
				}
			}

			public void endElement(QName element, Augmentations augs) throws XNIException {
				if (!chageDefaultNamespace) {
					if (element.uri != null && (element.prefix == null || element.prefix.length() == 0)) {
						element.uri = null;
					}
				}
				super.endElement(element, augs);
			}

		}, balancer };
		parser.setProperty("http://cyberneko.org/html/properties/filters", filters);

		parser.setProperty("http://xml.org/sax/properties/lexical-handler", xmlHandler);
		parser.setContentHandler(xmlHandler);

		if (source.isReader()) {
			// キャラクタストリーム
			this.parseReader(ua, source, parser);
		} else {
			// バイトストリーム
			this.parseStream(ua, source, parser);
		}
	}

	protected void parseReader(final UserAgent ua, final Source source, final SAXParser parser)
			throws SAXException, IOException {
		// キャラクタストリーム
		try (Reader in = new BufferedReader(source.getReader())) {
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
		// バイトストリーム
		// BOMチェック
		InputStream in = new BufferedInputStream(source.getInputStream());
		String encoding = XMLUtils.checkBOM(in);

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
}
