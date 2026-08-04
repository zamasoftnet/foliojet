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
import net.zamasoft.foliojet.xml.vocab.Foreign;
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
import net.zamasoft.foliojet.ua.CompatibleMode;

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

		final boolean changeDefaultNamespace = UAProps.INPUT_CHANGE_DEFAULT_NAMESPACE.getBoolean(ua);

		final TagBalancer balancer = new TagBalancer();
		XMLDocumentFilter[] filters = { new DefaultFilter() {
			private boolean firstElement = true;

			/**
			 * <b>HTML5のforeign content</b>——{@code <math>}/{@code <svg>}の
			 * 名前空間(入れ子の深さ。0なら外)。
			 */
			private String foreignURI = null;
			private int foreignDepth = 0;

			/**
			 * {@code <math>}/{@code <svg>}とその子孫にHTML5の名前空間を与える。
			 *
			 * <p>
			 * <b>HTMLでは{@code xmlns}を書かないのが普通である。</b>HTML5は
			 * これらをforeign contentとして扱い、構文解析の段階で正しい名前空間へ
			 * 入れる(ブラウザは全部そうする)。NekoHTMLはそこまでやらないので
			 * ここで補う——**やらないとMathMLが平らな文字列になり、しかも
			 * {@code <annotation>}の中の生のLaTeXまで一緒に出る**。arXivが今
			 * HTMLを出している形(ar5iv/LaTeXML)がまさにこれで、
			 * {@code h_{t}}が「htsubscript … h_{t}」と出ていた
			 * (2026-08-05、実地コーパス第11波)。
			 *
			 * <p>
			 * <b>簡略化している点</b>: HTML5が定める復帰点(integration point
			 * ——{@code <foreignObject>}や
			 * {@code <annotation-xml encoding="text/html">}の内側はHTMLへ戻る)は
			 * 見ていない。深さだけで数える。印刷用途では、その内側にHTMLを
			 * 書き戻す文書が実地でほぼ無いため。
			 */
			private void applyForeign(QName element) {
				if (this.foreignDepth == 0) {
					if (element.getUri() == null) {
						final String uri = Foreign.uriOf(element.getLocalpart());
						if (uri == null) {
							return;
						}
						this.foreignURI = uri;
						element.setUri(uri);
					} else if (Foreign.is(element.getUri())) {
						// xmlns が書いてある場合。NekoHTMLが既に付けている
						this.foreignURI = element.getUri();
					} else {
						return;
					}
				} else if (element.getUri() == null) {
					element.setUri(this.foreignURI);
				}
				++this.foreignDepth;
			}

			public void startDocument(XMLLocator locator, String encoding, NamespaceContext namespaceContext,
					Augmentations augs) throws XNIException {
				super.startDocument(locator, encoding, namespaceContext, augs);
				xmlHandler.setDocumentLocator(new HTMLSourceLocator(locator));
			}

			public void startElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
				this.applyForeign(element);
				if (!changeDefaultNamespace && !Foreign.is(element.getUri())) {
					if (element.getUri() != null && (element.getPrefix() == null || element.getPrefix().length() == 0)) {
						element.setUri(null);
					}
				}
				super.startElement(element, attributes, augs);
				if (this.firstElement && element.getLocalpart().equalsIgnoreCase("body")) {
					// 標準モードへの切り替え
					if (ua.getDocumentContext().getCompatibleMode() == CompatibleMode.STRICT) {
						balancer.setElementProps(ElementProps.getElementProps("html4.xml"));
					}
					this.firstElement = false;
				}
			}

			public void endElement(QName element, Augmentations augs) throws XNIException {
				if (this.foreignDepth > 0) {
					if (element.getUri() == null) {
						element.setUri(this.foreignURI);
					}
					if (--this.foreignDepth == 0) {
						this.foreignURI = null;
					}
				}
				if (!changeDefaultNamespace && !Foreign.is(element.getUri())) {
					if (element.getUri() != null && (element.getPrefix() == null || element.getPrefix().length() == 0)) {
						element.setUri(null);
					}
				}
				super.endElement(element, augs);
			}

			public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs) throws XNIException {
				// 空要素は開いてすぐ閉じる。foreign の深さは増減させない
				final int depth = this.foreignDepth;
				final String uri = this.foreignURI;
				this.applyForeign(element);
				this.foreignDepth = depth;
				this.foreignURI = uri;
				if (!changeDefaultNamespace && !Foreign.is(element.getUri())) {
					if (element.getUri() != null && (element.getPrefix() == null || element.getPrefix().length() == 0)) {
						element.setUri(null);
					}
				}
				super.emptyElement(element, attributes, augs);
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
		//
		// 緩衝は**内側にも**要る(2026-07-27)。バイト経路(parseStream)と
		// 同じ理由で、LegacyCommentReaderは1文字ずつ`super.read()`を呼ぶ。
		// 下層は`InputStreamReader`なので生のreadシステムコールにはならないが、
		// `StreamDecoder`が呼び出しごとに一時オブジェクトを作るため、
		// **13MBの和文文書で316MBを確保していた**(実測、2026-07-27)。
		// 内側のBufferedReaderで、その1文字ずつの読みが配列上で完結する。
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
		// バイトストリーム
		// BOMチェック
		// 緩衝は**内側にも**要る(2026-07-27)。
		//
		// LegacyCommentInputStreamは1バイトずつ`super.read()`を呼ぶ実装なので、
		// 外側のBufferedInputStreamだけでは**生のストリームに対して
		// 1バイト1回のread**になる——実測でプロファイルの最上位に
		// `FileInputStream.read0`が30%現れていた。
		//
		// 内側の緩衝で、その1バイトずつの読みがメモリ上で完結する。
		InputStream in = new BufferedInputStream(
				new LegacyCommentInputStream(new BufferedInputStream(source.getInputStream(), 64 * 1024)));
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

	/**
	 * 閉じられていないレガシーコメントを補正しながら読むストリームです。
	 *
	 * <p>
	 * <b>1バイトずつ扱うので、実装の素朴さがそのまま費用になる。</b>
	 * 2026-07-27まで {@code ArrayDeque<Integer>} に積んでおり、11MBの文書で
	 * 1,100万回のボクシングとデック操作が走っていた。可変長のバイトキューへ
	 * 置き換えてある——<b>確保は一度きり、要素あたりの割り当てはゼロ</b>。
	 * </p>
	 *
	 * <p>
	 * 併せて、下層にも緩衝が要る({@link #parseStream}参照)。外側の
	 * {@code BufferedInputStream}だけでは、ここが1バイトずつ呼ぶせいで
	 * <b>生のストリームに対して1バイト1回のread</b>になっていた。
	 * </p>
	 */
	private static class LegacyCommentInputStream extends FilterInputStream {
		/** 先読み済みのバイト列。{@code [head, tail)} が有効範囲。 */
		private byte[] pending = new byte[64];

		private int head = 0, tail = 0;

		LegacyCommentInputStream(InputStream in) {
			super(in);
		}

		private void push(final int b) {
			if (this.tail == this.pending.length) {
				if (this.head > 0) {
					// 前詰めで済むならそれで済ませる
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
			// **要求された分を埋めきること。** 溜まっている分だけ返すと、
			// 呼び出し側(BufferedInputStream)は1バイトずつしか受け取れず、
			// 外側の緩衝が無効になる——実測で1文書あたり5秒遅くなった
			// (2026-07-27に一度そう書いて踏んだ)。
			int count = 0;
			while (count < len) {
				if (this.head == this.tail) {
					this.head = this.tail = 0;
					this.fill();
					if (this.head == this.tail) {
						break;
					}
				}
				// 溜まっている分は**まとめて**写す(従来は1バイトずつだった)
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
	 * {@link LegacyCommentInputStream}の文字版です。
	 *
	 * <p>
	 * <b>両者は必ず同じ形に保つこと。</b>2026-07-27まで、バイト版だけが
	 * 緩衝とキューの手当てを受け、こちらは取り残されていた——
	 * 双子の一方だけを直すと、この非対称がそのまま次の欠陥になる。
	 * </p>
	 */
	private static class LegacyCommentReader extends FilterReader {
		/** 先読み済みの文字列。{@code [head, tail)} が有効範囲。 */
		private char[] pending = new char[64];

		private int head = 0, tail = 0;

		LegacyCommentReader(Reader in) {
			super(in);
		}

		private void push(final int c) {
			if (this.tail == this.pending.length) {
				if (this.head > 0) {
					// 前詰めで済むならそれで済ませる
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
			// **要求された分を埋めきること。** 溜まっている分だけ返すと、
			// 呼び出し側(BufferedReader)は1文字ずつしか受け取れず、
			// 外側の緩衝が無効になる(バイト版と同じ罠)
			int count = 0;
			while (count < len) {
				if (this.head == this.tail) {
					this.head = this.tail = 0;
					this.fill();
					if (this.head == this.tail) {
						break;
					}
				}
				// 溜まっている分は**まとめて**写す
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

		public String getEncoding() {
			return this.locator.getEncoding();
		}

		public String getXMLVersion() {
			return this.locator.getXMLVersion();
		}
	}
}

