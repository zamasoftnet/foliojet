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
 * Read-ahead buffering of the main document and discovery/prefetch of external resources
 * (input.prefetch, 2026-08-27).
 *
 * <p>
 * In Copper's streaming layout, the parser-driving thread is the layout thread, and images
 * and CSS resolve synchronously in sequence at their consumption points. While the parser
 * waits for resources, main-document reception also stops, so serial HTTP round trips
 * directly determine wall-clock time. Measured: a 1.1 MB Wikipedia article with about 90 images
 * took 21 s to convert despite a 0.7 s body fetch, or 1.3 s with resource resolution blocked.
 * </p>
 *
 * <p>
 * As a countermeasure, a dedicated virtual thread reads the main document ahead into a bounded
 * buffer, and the parser reads from that buffer. Immediately scans read bytes lightly,
 * passing only <b>URLs the engine will definitely request</b> (stylesheet href and img src/srcset;
 * the srcset selection rules match {@link HTMLStyle#pickFromSrcset}) to
 * {@link MySourceResolver#prefetch}. Nothing in this scan makes the parser's read wait
 * (when the buffer fills, the upstream reader waits, providing network backpressure).
 * A scan failure only stops scanning and does not affect conversion. A simple copying tee
 * cannot open a prefetch window while the parser waits for resources, since only bytes already
 * read by the parser pass through. Thus, a read-ahead buffer with a separate reader is essential
 * (2026-08-27 review, grok/codex).
 * </p>
 *
 * <p>
 * Missed discoveries (buffer limit, unknown encoding, formatting quirks) merely degrade
 * performance to the synchronous path without affecting correctness. False positives
 * (e.g., URLs in comments) only cause extra ACL-approved fetches within the resolver's count
 * and concurrency limits. Does not scan UTF-16 documents, since byte scanning assumes
 * an ASCII-compatible encoding.
 * </p>
 */
final class ResourcePrefetcher {

	private ResourcePrefetcher() {
		// utility
	}

	/** Read-ahead buffer capacity. This is how far ahead in the main document discovery can look. */
	private static final int BUFFER_SIZE = 2 * 1024 * 1024;

	/** The size of one underlying read. */
	private static final int CHUNK_SIZE = 64 * 1024;

	/** The maximum URIs passed to prefetch by scanning (the resolver also has its own limit). */
	private static final int MAX_URIS = 256;

	/**
	 * Wraps the main-document Source with read-ahead buffering and discovery scanning.
	 * Returns unscannable types unchanged (clearly non-HTML/XML or UTF-16).
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

	/** A Source that replaces only the first getInputStream() with a read-ahead stream. */
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
			// Scanning operates on bytes, so route Reader requests through getInputStream()
			// as well (plain delegation would bypass the read-ahead buffer).
			final String encoding = this.getEncoding();
			if (encoding == null) {
				return super.getReader();
			}
			return new InputStreamReader(this.getInputStream(), encoding);
		}
	}

	/**
	 * An InputStream whose dedicated virtual thread reads ahead from the underlying stream
	 * into a bounded ring buffer and passes bytes to scanning immediately. The reader (parser)
	 * reads from the buffer. Reports underlying IOExceptions and EOF only after the buffer
	 * is exhausted, preserving order.
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
							// Scanning is optional: stop only further scanning.
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
					// Never leave the reader waiting, whatever happens.
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
			// **Do not interrupt the reader thread** (2026-09-28). Interrupting a virtual thread waiting on
			// a socket read closes that socket (JDK 21). For a CTIP body, it is the client connection itself,
			// so closing midway through the body on an abort or conversion failure
			// disconnected the client. After the current read returns, the thread sees closed and exits.
			// (The CTIP body receiver discards the rest of the body through the client's EOF.)
			// For other underlying streams, close() releases the read.
			this.delegate.close();
		}
	}

	/**
	 * An incremental byte-scanning HTML scanner. A state machine skips comments and script/style
	 * raw text, extracting only base/link/img tags. Decodes tag text as UTF-8 with replacement:
	 * URLs are mostly ASCII, and ASCII-compatible encodings preserve attribute delimiters.
	 */
	static final class Scanner {
		private static final int STATE_DATA = 0;
		private static final int STATE_TAG = 1;
		private static final int STATE_COMMENT = 2;
		private static final int STATE_RAWTEXT = 3;

		/** The maximum buffered size for one tag (prevents runaway growth). */
		private static final int MAX_TAG_BYTES = 64 * 1024;

		private final MySourceResolver resolver;
		private final String encoding;
		private URI base;
		private boolean baseSeen;
		private final Set<URI> seen = new HashSet<>();

		private int state = STATE_DATA;
		private final StringBuilder tag = new StringBuilder();
		/** Recent characters used to detect COMMENT/RAWTEXT endings. */
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
						// Discard abnormally long tags and resynchronize.
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
						// The closing tag name has been read; the remainder (whitespace and >) is harmless in DATA.
						this.state = STATE_DATA;
					}
					break;
				default:
					throw new IllegalStateException();
				}
			}
		}

		/** Processes tag contents accumulated through '>'. */
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
				// Do not interpret raw-text element contents as tags.
				if (!text.endsWith("/")) {
					this.state = STATE_RAWTEXT;
					this.rawTextEnd = "</" + name;
					this.tailWindow.setLength(0);
				}
				return;
			case "base": {
				// Only the first base takes effect (HTML specification).
				if (!this.baseSeen) {
					this.baseSeen = true;
					final String href = attr(text, p, "href");
					if (href != null) {
						try {
							this.base = URIHelper.resolve(this.encoding, this.base, href);
						} catch (final URISyntaxException e) {
							// If base cannot be parsed, subsequent relative URLs are unreliable.
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
				// The engine's selection (HTMLStyle HTMLCodes.IMG, 2026-08-20) uses the highest-resolution
				// srcset candidate, or src if absent. Real sites sometimes also reference the same image
				// in an <img> with only src (e.g., Wikipedia map markers),
				// so prefetch both (both can actually be requested).
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
				// Ignore discovery failures (the actual request's normal path is authoritative).
			}
		}

		private static boolean isSpace(final char c) {
			return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
		}

		/**
		 * Extracts attribute values from tag text (quoted or unquoted, case-insensitive).
		 * Decodes only common character references in values.
		 */
		private static String attr(final String tag, final int from, final String name) {
			final String lower = tag.toLowerCase(Locale.ROOT);
			int i = from;
			while (i < tag.length()) {
				final int at = lower.indexOf(name, i);
				if (at < 0) {
					return null;
				}
				// An attribute name is preceded by whitespace, then followed by (whitespace*)=(whitespace*)value.
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

		/** Decodes only common character references that occur in URLs. */
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
						// Leave undecodable references unchanged.
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
