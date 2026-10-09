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
				// The balancer's locator: the events of a table it holds back (foster parenting) report the positions
				// they were read at, not the position of the table's end (2026-10-09)
				final XMLLocator handed = balancer.getLocator();
				xmlHandler.setDocumentLocator(new HTMLSourceLocator(handed != null ? handed : locator));
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
		// Buffering is needed **on the inside too** (2026-07-27). For the same reason as
		// the byte path (parseStream), LegacyCommentReader calls `super.read()` one character at a time.
		// The underlying `InputStreamReader` avoids raw read system calls,
		// but `StreamDecoder` creates temporary objects for every call,
		// **allocating 316 MB for a 13 MB Japanese document** (measured, 2026-07-27).
		// An inner BufferedReader confines these single-character reads to an array.
		try (Reader in = new BufferedReader(
				new LegacyCommentReader(new BufferedReader(source.getReader(), 64 * 1024)))) {
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
		// Check BOM.
		// Buffering is needed **on the inside too** (2026-07-27).
		//
		// LegacyCommentInputStream calls `super.read()` one byte at a time,
		// so an outer BufferedInputStream alone still causes **one read on the raw stream
		// per byte**. In measurements, `FileInputStream.read0` topped
		// the profile at 30%.
		//
		// Inner buffering keeps those single-byte reads entirely in memory.
		InputStream in = new BufferedInputStream(
				new LegacyCommentInputStream(new BufferedInputStream(source.getInputStream(), 64 * 1024)));
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

	/**
	 * A stream that corrects unclosed legacy comments while reading.
	 *
	 * <p>
	 * <b>Because it handles one byte at a time, implementation simplicity directly determines cost.</b>
	 * Until 2026-07-27, it used {@code ArrayDeque<Integer>}, causing 11 million boxing and deque
	 * operations for an 11 MB document. Replaced with a variable-length byte queue:
	 * <b>allocate once, with zero per-element allocations</b>.
	 * </p>
	 *
	 * <p>
	 * The underlying layer also needs buffering (see {@link #parseStream}).
	 * With only an outer {@code BufferedInputStream}, this class's single-byte calls caused
	 * <b>one read on the raw stream per byte</b>.
	 * </p>
	 */
	private static class LegacyCommentInputStream extends FilterInputStream {
		/** Bytes read ahead. {@code [head, tail)} is the valid range. */
		private byte[] pending = new byte[64];

		private int head = 0, tail = 0;

		LegacyCommentInputStream(InputStream in) {
			super(in);
		}

		private void push(final int b) {
			if (this.tail == this.pending.length) {
				if (this.head > 0) {
					// Compact toward the start if that suffices.
					System.arraycopy(this.pending, this.head, this.pending, 0, this.tail - this.head);
					this.tail -= this.head;
					this.head = 0;
				} else {
					this.pending = java.util.Arrays.copyOf(this.pending, this.pending.length * 2);
				}
			}
			this.pending[this.tail++] = (byte) b;
		}

		private int available0() {
			return this.tail - this.head;
		}

		public int read() throws IOException {
			if (this.head == this.tail) {
				this.head = this.tail = 0;
				this.fill();
				if (this.head == this.tail) {
					return -1;
				}
			}
			return this.pending[this.head++] & 0xFF;
		}

		public int read(byte[] b, int off, int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			// **Fill the requested amount.** Returning only buffered data gives
			// the caller (BufferedInputStream) just one byte at a time,
			// disabling outer buffering. Measurements showed a 5-second slowdown per document
			// (encountered after implementing it that way on 2026-07-27).
			int count = 0;
			while (count < len) {
				if (this.head == this.tail) {
					this.head = this.tail = 0;
					this.fill();
					if (this.head == this.tail) {
						break;
					}
				}
				// Copy buffered data **in bulk** (previously one byte at a time).
				final int n = Math.min(len - count, this.available0());
				System.arraycopy(this.pending, this.head, b, off + count, n);
				this.head += n;
				count += n;
			}
			return count == 0 ? -1 : count;
		}

		private void fill() throws IOException {
			int c = super.read();
			if (c != '<') {
				if (c != -1) {
					this.push(c);
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
			this.push(c);
			if (c1 != -1) {
				this.push(c1);
			}
			if (c2 != -1) {
				this.push(c2);
			}
			if (c3 != -1) {
				this.push(c3);
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
					for (byte b : closeLastOpenComment(out.toByteArray())) {
						this.push(b & 0xFF);
					}
					return;
				}
				out.write(c);
				if (c == '-') {
					++dashCount;
				} else if (c == '>' && dashCount >= 2) {
					for (byte b : out.toByteArray()) {
						this.push(b & 0xFF);
					}
					return;
				} else {
					dashCount = 0;
				}
			}
		}
	}

	/**
	 * The character version of {@link LegacyCommentInputStream}.
	 *
	 * <p>
	 * <b>Always keep both implementations in the same form.</b> Until 2026-07-27,
	 * only the byte version had received buffering and queue improvements, leaving this one behind.
	 * Fixing only one twin turns that asymmetry directly into the next defect.
	 * </p>
	 */
	private static class LegacyCommentReader extends FilterReader {
		/** Characters read ahead. {@code [head, tail)} is the valid range. */
		private char[] pending = new char[64];

		private int head = 0, tail = 0;

		LegacyCommentReader(Reader in) {
			super(in);
		}

		private void push(final int c) {
			if (this.tail == this.pending.length) {
				if (this.head > 0) {
					// Compact toward the start if that suffices.
					System.arraycopy(this.pending, this.head, this.pending, 0, this.tail - this.head);
					this.tail -= this.head;
					this.head = 0;
				} else {
					this.pending = java.util.Arrays.copyOf(this.pending, this.pending.length * 2);
				}
			}
			this.pending[this.tail++] = (char) c;
		}

		public int read() throws IOException {
			if (this.head == this.tail) {
				this.head = this.tail = 0;
				this.fill();
				if (this.head == this.tail) {
					return -1;
				}
			}
			return this.pending[this.head++];
		}

		public int read(char[] cbuf, int off, int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			// **Fill the requested amount.** Returning only buffered data gives
			// the caller (BufferedReader) just one character at a time,
			// disabling outer buffering (the same trap as the byte version).
			int count = 0;
			while (count < len) {
				if (this.head == this.tail) {
					this.head = this.tail = 0;
					this.fill();
					if (this.head == this.tail) {
						break;
					}
				}
				// Copy buffered data **in bulk**.
				final int n = Math.min(len - count, this.tail - this.head);
				System.arraycopy(this.pending, this.head, cbuf, off + count, n);
				this.head += n;
				count += n;
			}
			return count == 0 ? -1 : count;
		}

		private void fill() throws IOException {
			int c = super.read();
			if (c != '<') {
				if (c != -1) {
					this.push(c);
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
			this.push(c);
			if (c1 != -1) {
				this.push(c1);
			}
			if (c2 != -1) {
				this.push(c2);
			}
			if (c3 != -1) {
				this.push(c3);
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
						this.push(fixed.charAt(i));
					}
					return;
				}
				out.append((char) c);
				if (c == '-') {
					++dashCount;
				} else if (c == '>' && dashCount >= 2) {
					for (int i = 0; i < out.length(); ++i) {
						this.push(out.charAt(i));
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

	}
}

