package net.zamasoft.foliojet.xml.parser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayDeque;

import net.zamasoft.balancer.ElementProps;
import net.zamasoft.balancer.SAXParser;
import net.zamasoft.balancer.TagBalancer;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.SourceLocator;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;

import org.htmlunit.cyberneko.xerces.xni.Augmentations;
import org.htmlunit.cyberneko.xerces.xni.NamespaceContext;
import org.htmlunit.cyberneko.xerces.xni.QName;
import org.htmlunit.cyberneko.xerces.xni.XMLAttributes;
import org.htmlunit.cyberneko.xerces.xni.XMLLocator;
import org.htmlunit.cyberneko.xerces.xni.XNIException;
import org.htmlunit.cyberneko.xerces.xni.parser.XMLDocumentFilter;
import org.htmlunit.cyberneko.filters.DefaultFilter;
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

		final boolean chageDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);

		final TagBalancer balancer = new TagBalancer();
		XMLDocumentFilter[] filters = { new DefaultFilter() {
			private boolean firstElement = true;

			public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
					Augmentations augs) throws XNIException {
				super.startDocument(locator, encoding, namespaceContext, augs);
				xmlHandler.setDocumentLocator(new HTMLSourceLocator(locator));
			}

			public void startElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
				if (!chageDefaultNamespace) {
					if (element.getUri() != null && (element.getPrefix() == null || element.getPrefix().length() == 0)) {
						element.setUri(null);
					}
				}
				super.startElement(element, attributes, augs);
				if (this.firstElement && element.getLocalpart().equalsIgnoreCase("body")) {
					// 標準モードへの切り替え
					if (ua.getDocumentContext().getCompatibleMode() <= DocumentContext.CM_STRICT) {
						balancer.setElementProps(ElementProps.getElementProps("html4.xml"));
					}
					this.firstElement = false;
				}
			}

			public void endElement(QName element, Augmentations augs) throws XNIException {
				if (!chageDefaultNamespace) {
					if (element.getUri() != null && (element.getPrefix() == null || element.getPrefix().length() == 0)) {
						element.setUri(null);
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
		try (Reader in = new BufferedReader(new LegacyCommentReader(source.getReader()))) {
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
		InputStream in = new BufferedInputStream(new LegacyCommentInputStream(source.getInputStream()));
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

	private static byte[] closeLastOpenComment(byte[] bytes) {
		int close = indexOf(bytes, new byte[] { '-', '-', '>' }, 4);
		if (close != -1) {
			return bytes;
		}
		for (int i = 4; i < bytes.length; ++i) {
			if (bytes[i] == '>') {
				byte[] fixed = new byte[bytes.length + 2];
				System.arraycopy(bytes, 0, fixed, 0, i);
				fixed[i] = '-';
				fixed[i + 1] = '-';
				System.arraycopy(bytes, i, fixed, i + 2, bytes.length - i);
				return fixed;
			}
		}
		return bytes;
	}

	private static int indexOf(byte[] bytes, byte[] pattern, int start) {
		for (int i = start; i <= bytes.length - pattern.length; ++i) {
			int j = 0;
			while (j < pattern.length && bytes[i + j] == pattern[j]) {
				++j;
			}
			if (j == pattern.length) {
				return i;
			}
		}
		return -1;
	}

	private static String closeLastOpenComment(String text) {
		if (text.indexOf("-->", 4) != -1) {
			return text;
		}
		int gt = text.indexOf('>', 4);
		if (gt == -1) {
			return text;
		}
		return text.substring(0, gt) + "--" + text.substring(gt);
	}

	private static class LegacyCommentInputStream extends FilterInputStream {
		private final ArrayDeque<Integer> pending = new ArrayDeque<Integer>();

		LegacyCommentInputStream(InputStream in) {
			super(in);
		}

		public int read() throws IOException {
			if (this.pending.isEmpty()) {
				this.fill();
			}
			return this.pending.isEmpty() ? -1 : this.pending.removeFirst().intValue();
		}

		public int read(byte[] b, int off, int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			int first = this.read();
			if (first == -1) {
				return -1;
			}
			b[off] = (byte) first;
			int count = 1;
			while (count < len) {
				int c = this.read();
				if (c == -1) {
					break;
				}
				b[off + count] = (byte) c;
				++count;
			}
			return count;
		}

		private void fill() throws IOException {
			int c = super.read();
			if (c != '<') {
				if (c != -1) {
					this.pending.add(Integer.valueOf(c));
				}
				return;
			}
			int c1 = super.read();
			int c2 = super.read();
			int c3 = super.read();
			if (c1 == '!' && c2 == '-' && c3 == '-') {
				this.readComment();
				return;
			}
			this.pending.add(Integer.valueOf(c));
			if (c1 != -1) {
				this.pending.add(Integer.valueOf(c1));
			}
			if (c2 != -1) {
				this.pending.add(Integer.valueOf(c2));
			}
			if (c3 != -1) {
				this.pending.add(Integer.valueOf(c3));
			}
		}

		private void readComment() throws IOException {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			out.write('<');
			out.write('!');
			out.write('-');
			out.write('-');
			int dashCount = 2;
			for (;;) {
				int c = super.read();
				if (c == -1) {
					byte[] fixed = closeLastOpenComment(out.toByteArray());
					for (byte b : fixed) {
						this.pending.add(Integer.valueOf(b & 0xFF));
					}
					return;
				}
				out.write(c);
				if (c == '-') {
					++dashCount;
				} else if (c == '>' && dashCount >= 2) {
					byte[] bytes = out.toByteArray();
					for (byte b : bytes) {
						this.pending.add(Integer.valueOf(b & 0xFF));
					}
					return;
				} else {
					dashCount = 0;
				}
			}
		}
	}

	private static class LegacyCommentReader extends FilterReader {
		private final ArrayDeque<Integer> pending = new ArrayDeque<Integer>();

		LegacyCommentReader(Reader in) {
			super(in);
		}

		public int read() throws IOException {
			if (this.pending.isEmpty()) {
				this.fill();
			}
			return this.pending.isEmpty() ? -1 : this.pending.removeFirst().intValue();
		}

		public int read(char[] cbuf, int off, int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			int first = this.read();
			if (first == -1) {
				return -1;
			}
			cbuf[off] = (char) first;
			int count = 1;
			while (count < len) {
				int c = this.read();
				if (c == -1) {
					break;
				}
				cbuf[off + count] = (char) c;
				++count;
			}
			return count;
		}

		private void fill() throws IOException {
			int c = super.read();
			if (c != '<') {
				if (c != -1) {
					this.pending.add(Integer.valueOf(c));
				}
				return;
			}
			int c1 = super.read();
			int c2 = super.read();
			int c3 = super.read();
			if (c1 == '!' && c2 == '-' && c3 == '-') {
				this.readComment();
				return;
			}
			this.pending.add(Integer.valueOf(c));
			if (c1 != -1) {
				this.pending.add(Integer.valueOf(c1));
			}
			if (c2 != -1) {
				this.pending.add(Integer.valueOf(c2));
			}
			if (c3 != -1) {
				this.pending.add(Integer.valueOf(c3));
			}
		}

		private void readComment() throws IOException {
			StringBuilder out = new StringBuilder();
			out.append("<!--");
			int dashCount = 2;
			for (;;) {
				int c = super.read();
				if (c == -1) {
					String fixed = closeLastOpenComment(out.toString());
					for (int i = 0; i < fixed.length(); ++i) {
						this.pending.add(Integer.valueOf(fixed.charAt(i)));
					}
					return;
				}
				out.append((char) c);
				if (c == '-') {
					++dashCount;
				} else if (c == '>' && dashCount >= 2) {
					for (int i = 0; i < out.length(); ++i) {
						this.pending.add(Integer.valueOf(out.charAt(i)));
					}
					return;
				} else {
					dashCount = 0;
				}
			}
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

		public String getEncoding() {
			return this.locator.getEncoding();
		}

		public String getXMLVersion() {
			return this.locator.getXMLVersion();
		}
	}
}

