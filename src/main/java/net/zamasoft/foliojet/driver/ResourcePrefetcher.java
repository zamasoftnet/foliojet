package net.zamasoft.foliojet.driver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.zamasoft.foliojet.css.html.HTMLStyle;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.SourceWrapper;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * 主文書の読み先行バッファと外部リソースの発見・先読み(input.prefetch、
 * 2026-08-27)。
 *
 * <p>
 * Copperのストリームレイアウトはパーサ駆動スレッド=レイアウトスレッドで、
 * 画像・CSSは消費点で直列に同期解決される。パーサがリソース待ちで
 * 止まっている間、主文書の受信も止まり、直列のHTTP往復がそのまま
 * wall-clockになる(実測: wikipedia記事1.1MB・画像約90点で、本文取得0.7秒の
 * ところ変換21秒。リソース解決を遮断すると1.3秒)。
 * </p>
 *
 * <p>
 * 対策として、専用の仮想スレッドが主文書を有界バッファへ先行して読み、
 * パーサはバッファから読む。読んだバイトはその場で軽量走査し、
 * <b>エンジンが確実に要求するURL</b>(stylesheetのhref、imgのsrc/srcset——
 * srcset候補の選択規則は{@link HTMLStyle#pickFromSrcset}と同一)だけを
 * {@link MySourceResolver#prefetch}へ渡す。パーサ側のreadを待たせる要素は
 * なく(バッファ満杯時は読み手側が待つ=ネットワークへの背圧)、走査の
 * 失敗は走査を止めるだけで変換には影響しない。単純なコピーteeでは
 * パーサがリソース待ちで止まると先読み窓が開かない(パーサが読んだ
 * バイトしか流れてこない)ため、読み手を分離した先行バッファが本質
 * (2026-08-27レビュー、grok/codex)。
 * </p>
 *
 * <p>
 * 発見の取りこぼし(バッファ上限・不明なエンコーディング・書式の癖)は
 * 性能が同期経路へ退化するだけで、正しさは変わらない。逆に誤検出
 * (コメント内のURL等)は、ACLを通過した余分な取得が上限
 * (リゾルバ側の件数・並列度)の範囲で起きるだけである。UTF-16系の
 * 文書は走査しない(バイト走査がASCII互換前提のため)。
 * </p>
 */
final class ResourcePrefetcher {

	private ResourcePrefetcher() {
		// utility
	}

	/** 読み先行バッファの容量。この分だけ主文書の先を発見できる。 */
	private static final int BUFFER_SIZE = 2 * 1024 * 1024;

	/** 1回の下位readの単位。 */
	private static final int CHUNK_SIZE = 64 * 1024;

	/** 走査で先読みへ渡すURIの上限(リゾルバ側にも独自の上限がある)。 */
	private static final int MAX_URIS = 256;

	/**
	 * 主文書Sourceを読み先行+発見走査つきに包みます。走査できない
	 * 種別(明らかに非HTML/XML、UTF-16系)はそのまま返します。
	 */
	static Source wrap(final Source source, final MySourceResolver resolver) {
		String mimeType = null;
		String encoding = null;
		try {
			mimeType = source.getMimeType();
			encoding = source.getEncoding();
		} catch (final IOException e) {
			MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch scan off: metadata unavailable");
			return source;
		}
		if (mimeType != null) {
			final String lower = mimeType.toLowerCase(Locale.ROOT);
			if (!lower.contains("html") && !lower.contains("xml")) {
				final String mt = mimeType;
				MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch scan off: mimeType=" + mt);
				return source;
			}
		}
		if (encoding != null && encoding.toLowerCase(Locale.ROOT).startsWith("utf-16")) {
			final String enc = encoding;
			MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch scan off: encoding=" + enc);
			return source;
		}
		MySourceResolver.PREFETCH_LOG.fine(() -> "prefetch scan on: " + source.getURI());
		final Scanner scanner = new Scanner(source.getURI(), encoding, resolver);
		return new PrefetchingSource(source, scanner);
	}

	/** 最初のgetInputStream()だけを読み先行ストリームへ差し替えるSource。 */
	private static final class PrefetchingSource extends SourceWrapper {
		private final Scanner scanner;
		private boolean used;

		PrefetchingSource(final Source source, final Scanner scanner) {
			super(source);
			this.scanner = scanner;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			final InputStream in = super.getInputStream();
			if (this.used) {
				return in;
			}
			this.used = true;
			return new ReadAheadInputStream(in, this.scanner);
		}

		@Override
		public Reader getReader() throws IOException {
			// 走査はバイト列に対して行うため、Reader要求もgetInputStream()を
			// 通して包む(委譲のままだと先行バッファを素通りする)
			final String encoding = this.getEncoding();
			if (encoding == null) {
				return super.getReader();
			}
			return new InputStreamReader(this.getInputStream(), encoding);
		}
	}

	/**
	 * 専用の仮想スレッドが下位ストリームを有界リングバッファへ先行して
	 * 読み、その場で走査へ流すInputStream。読み手(パーサ)はバッファから
	 * 読む。下位のIOException・EOFはバッファを飲み切った後に伝える
	 * (順序を保つ)。
	 */
	static final class ReadAheadInputStream extends InputStream {
		private final InputStream delegate;
		private final byte[] buffer = new byte[BUFFER_SIZE];
		private final Object lock = new Object();
		private int head;
		private int tail;
		private int count;
		private boolean eof;
		private IOException error;
		private boolean closed;
		private final Thread readerThread;
		private final byte[] one = new byte[1];

		ReadAheadInputStream(final InputStream delegate, final Scanner scanner) {
			this.delegate = delegate;
			this.readerThread = Thread.ofVirtual().name("foliojet-prefetch-scan").start(() -> this.pump(scanner));
		}

		private void pump(final Scanner scanner) {
			Scanner active = scanner;
			final byte[] chunk = new byte[CHUNK_SIZE];
			try {
				for (;;) {
					final int n;
					try {
						n = this.delegate.read(chunk, 0, chunk.length);
					} catch (final IOException e) {
						synchronized (this.lock) {
							this.error = e;
							this.lock.notifyAll();
						}
						return;
					}
					if (n < 0) {
						synchronized (this.lock) {
							this.eof = true;
							this.lock.notifyAll();
						}
						return;
					}
					if (n == 0) {
						continue;
					}
					if (active != null) {
						try {
							active.feed(chunk, 0, n);
						} catch (final Throwable t) {
							// 走査は任意——以降の走査だけを止める
							active = null;
						}
					}
					synchronized (this.lock) {
						int off = 0;
						while (off < n) {
							while (this.count == this.buffer.length && !this.closed) {
								try {
									this.lock.wait();
								} catch (final InterruptedException e) {
									return;
								}
							}
							if (this.closed) {
								return;
							}
							final int space = this.buffer.length - this.count;
							int can = Math.min(space, n - off);
							while (can > 0) {
								final int run = Math.min(can, this.buffer.length - this.tail);
								System.arraycopy(chunk, off, this.buffer, this.tail, run);
								this.tail = (this.tail + run) % this.buffer.length;
								this.count += run;
								off += run;
								can -= run;
							}
							this.lock.notifyAll();
						}
					}
				}
			} finally {
				synchronized (this.lock) {
					// 何があっても読み手を待たせたままにしない
					if (!this.eof && this.error == null && !this.closed) {
						this.error = new IOException("prefetch read-ahead terminated");
					}
					this.lock.notifyAll();
				}
			}
		}

		@Override
		public int read() throws IOException {
			final int n = this.read(this.one, 0, 1);
			return n <= 0 ? -1 : (this.one[0] & 0xFF);
		}

		@Override
		public int read(final byte[] b, final int off, final int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			synchronized (this.lock) {
				while (this.count == 0) {
					if (this.error != null) {
						throw this.error;
					}
					if (this.eof) {
						return -1;
					}
					if (this.closed) {
						throw new IOException("stream closed");
					}
					try {
						this.lock.wait();
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new InterruptedIOException();
					}
				}
				int n = Math.min(len, this.count);
				final int result = n;
				int dst = off;
				while (n > 0) {
					final int run = Math.min(n, this.buffer.length - this.head);
					System.arraycopy(this.buffer, this.head, b, dst, run);
					this.head = (this.head + run) % this.buffer.length;
					this.count -= run;
					dst += run;
					n -= run;
				}
				this.lock.notifyAll();
				return result;
			}
		}

		@Override
		public int available() {
			synchronized (this.lock) {
				return this.count;
			}
		}

		@Override
		public void close() throws IOException {
			synchronized (this.lock) {
				this.closed = true;
				this.lock.notifyAll();
			}
			this.readerThread.interrupt();
			this.delegate.close();
		}
	}

	/**
	 * 増分バイト走査のHTMLスキャナ。コメント・script/styleの生テキストを
	 * 状態機械で読み飛ばし、base/link/imgのタグだけを取り出す。タグ文字列は
	 * UTF-8(置換つき)で復号する——URLはほぼASCIIで、ASCII互換
	 * エンコーディングなら属性の区切りを壊さない。
	 */
	static final class Scanner {
		private static final int STATE_DATA = 0;
		private static final int STATE_TAG = 1;
		private static final int STATE_COMMENT = 2;
		private static final int STATE_RAWTEXT = 3;

		/** 1タグの最大蓄積(暴走防止)。 */
		private static final int MAX_TAG_BYTES = 64 * 1024;

		private final MySourceResolver resolver;
		private final String encoding;
		private URI base;
		private boolean baseSeen;
		private final Set<URI> seen = new HashSet<>();

		private int state = STATE_DATA;
		private final StringBuilder tag = new StringBuilder();
		/** COMMENT/RAWTEXT終端検出用の直近文字。 */
		private final StringBuilder tailWindow = new StringBuilder();
		private String rawTextEnd;

		Scanner(final URI documentURI, final String encoding, final MySourceResolver resolver) {
			this.base = documentURI;
			this.encoding = encoding;
			this.resolver = resolver;
		}

		void feed(final byte[] buf, final int off, final int len) {
			for (int i = off, end = off + len; i < end; i++) {
				final char c = (char) (buf[i] & 0xFF);
				switch (this.state) {
				case STATE_DATA:
					if (c == '<') {
						this.state = STATE_TAG;
						this.tag.setLength(0);
					}
					break;
				case STATE_TAG:
					if (c == '>') {
						this.endTag();
					} else if (this.tag.length() < MAX_TAG_BYTES) {
						this.tag.append(c);
						if (this.tag.length() == 3 && this.tag.charAt(0) == '!' && this.tag.charAt(1) == '-'
								&& this.tag.charAt(2) == '-') {
							this.state = STATE_COMMENT;
							this.tailWindow.setLength(0);
						}
					} else {
						// 異常に長いタグは捨てて同期し直す
						this.state = STATE_DATA;
					}
					break;
				case STATE_COMMENT:
					this.tailWindow.append(c);
					if (this.tailWindow.length() > 3) {
						this.tailWindow.deleteCharAt(0);
					}
					if (c == '>' && this.tailWindow.indexOf("-->") >= 0) {
						this.state = STATE_DATA;
					}
					break;
				case STATE_RAWTEXT:
					this.tailWindow.append(Character.toLowerCase(c));
					if (this.tailWindow.length() > this.rawTextEnd.length()) {
						this.tailWindow.deleteCharAt(0);
					}
					if (this.tailWindow.indexOf(this.rawTextEnd) >= 0) {
						// 終了タグの名前まで読んだ——残り(空白と>)はDATAで無害
						this.state = STATE_DATA;
					}
					break;
				default:
					throw new IllegalStateException();
				}
			}
		}

		/** '>'まで蓄積したタグ内容を処理します。 */
		private void endTag() {
			this.state = STATE_DATA;
			final String text = this.tag.toString();
			this.tag.setLength(0);
			if (text.isEmpty() || text.charAt(0) == '!' || text.charAt(0) == '?' || text.charAt(0) == '/') {
				return;
			}
			int p = 0;
			while (p < text.length() && !isSpace(text.charAt(p)) && text.charAt(p) != '/') {
				p++;
			}
			final String name = text.substring(0, p).toLowerCase(Locale.ROOT);
			switch (name) {
			case "script":
			case "style":
				// 生テキスト要素の中はタグとして解釈しない
				if (!text.endsWith("/")) {
					this.state = STATE_RAWTEXT;
					this.rawTextEnd = "</" + name;
					this.tailWindow.setLength(0);
				}
				return;
			case "base": {
				// 最初のbaseだけが効く(HTML仕様)
				if (!this.baseSeen) {
					this.baseSeen = true;
					final String href = attr(text, p, "href");
					if (href != null) {
						try {
							this.base = URIHelper.resolve(this.encoding, this.base, href);
						} catch (final URISyntaxException e) {
							// baseが読めないなら以降の相対URLは当てにならない
							this.base = null;
						}
					}
				}
				return;
			}
			case "link": {
				final String rel = attr(text, p, "rel");
				if (rel == null || !rel.toLowerCase(Locale.ROOT).contains("stylesheet")) {
					return;
				}
				this.prefetch(attr(text, p, "href"));
				return;
			}
			case "img": {
				// エンジンの選択(HTMLStyle HTMLCodes.IMG、2026-08-20)は
				// srcsetの最高解像度候補、無ければsrc。同じ画像がsrcだけの
				// <img>でも現れる実サイト(wikipediaの地図マーカー等)が
				// あるため、両方を先読みする(どちらも実際に要求され得る)
				this.prefetch(HTMLStyle.pickFromSrcset(attr(text, p, "srcset")));
				this.prefetch(attr(text, p, "src"));
				return;
			}
			default:
			}
		}

		private void prefetch(final String href) {
			if (href == null || href.isEmpty() || this.base == null || this.seen.size() >= MAX_URIS) {
				return;
			}
			if (href.startsWith("data:") || href.startsWith("#")) {
				return;
			}
			try {
				final URI uri = URIHelper.resolve(this.encoding, this.base, href);
				if (this.seen.add(uri)) {
					this.resolver.prefetch(uri);
				}
			} catch (final URISyntaxException | RuntimeException e) {
				// 発見の失敗は無視(実要求の正規経路が正)
			}
		}

		private static boolean isSpace(final char c) {
			return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
		}

		/**
		 * タグ文字列から属性値を取り出します(引用符・無引用対応、
		 * 大文字小文字非区別)。値の文字参照は主要なもののみ復号。
		 */
		private static String attr(final String tag, final int from, final String name) {
			final String lower = tag.toLowerCase(Locale.ROOT);
			int i = from;
			while (i < tag.length()) {
				final int at = lower.indexOf(name, i);
				if (at < 0) {
					return null;
				}
				// 属性名の前は空白、後は(空白*)=(空白*)値
				final int before = at - 1;
				final int after = at + name.length();
				if (before >= 0 && !isSpace(tag.charAt(before))) {
					i = after;
					continue;
				}
				int q = after;
				while (q < tag.length() && isSpace(tag.charAt(q))) {
					q++;
				}
				if (q >= tag.length() || tag.charAt(q) != '=') {
					i = after;
					continue;
				}
				q++;
				while (q < tag.length() && isSpace(tag.charAt(q))) {
					q++;
				}
				if (q >= tag.length()) {
					return null;
				}
				final char quote = tag.charAt(q);
				String value;
				if (quote == '"' || quote == '\'') {
					final int close = tag.indexOf(quote, q + 1);
					if (close < 0) {
						return null;
					}
					value = tag.substring(q + 1, close);
				} else {
					int e = q;
					while (e < tag.length() && !isSpace(tag.charAt(e))) {
						e++;
					}
					value = tag.substring(q, e);
				}
				return decodeEntities(value);
			}
			return null;
		}

		/** URL中に現れる主要な文字参照だけを復号します。 */
		private static String decodeEntities(final String s) {
			if (s.indexOf('&') < 0) {
				return s.trim();
			}
			final StringBuilder sb = new StringBuilder(s.length());
			for (int i = 0; i < s.length(); i++) {
				final char c = s.charAt(i);
				if (c != '&') {
					sb.append(c);
					continue;
				}
				final int semi = s.indexOf(';', i + 1);
				if (semi < 0 || semi - i > 10) {
					sb.append(c);
					continue;
				}
				final String ent = s.substring(i + 1, semi);
				String repl = switch (ent.toLowerCase(Locale.ROOT)) {
				case "amp" -> "&";
				case "lt" -> "<";
				case "gt" -> ">";
				case "quot" -> "\"";
				case "apos", "#39" -> "'";
				default -> null;
				};
				if (repl == null && ent.startsWith("#")) {
					try {
						final int cp = ent.charAt(1) == 'x' || ent.charAt(1) == 'X'
								? Integer.parseInt(ent.substring(2), 16)
								: Integer.parseInt(ent.substring(1));
						if (cp > 0 && Character.isValidCodePoint(cp)) {
							repl = new String(Character.toChars(cp));
						}
					} catch (final RuntimeException e) {
						// 復号できない参照はそのまま
					}
				}
				if (repl != null) {
					sb.append(repl);
					i = semi;
				} else {
					sb.append(c);
				}
			}
			return sb.toString().trim();
		}
	}
}
