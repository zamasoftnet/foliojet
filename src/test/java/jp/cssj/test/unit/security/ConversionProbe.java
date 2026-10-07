package jp.cssj.test.unit.security;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * A tool that performs one conversion and extracts <b>both the type area and warnings</b>.
 *
 * <p>
 * For resource-access-control checks, successful conversion alone proves nothing.
 * Resources that cannot be fetched are silently ignored, so <b>success cannot be distinguished
 * from fallback</b>. This tool therefore decompresses and returns PDF content streams, allowing callers
 * to judge whether <b>distinctive pixels or glyphs appeared</b>.
 * </p>
 *
 * @see ProbeServer
 */
public class ConversionProbe {

	private final Map<String, String> properties = new LinkedHashMap<String, String>();

	private boolean genericResolver = true;

	private boolean localAccessAllowed = true;

	/** Add an I/O property. */
	public ConversionProbe property(final String name, final String value) {
		this.properties.put(name, value);
		return this;
	}

	private int includeCount = 0;

	/**
	 * Configure the ACL. With no settings, retain the default (do not fetch).
	 *
	 * <p>
	 * May be called multiple times. Number entries starting at {@code input.include.0}.
	 * The ACL is <b>first-match-wins</b>, so entries are evaluated in insertion order.
	 * </p>
	 *
	 * <p>
	 * Do not use unnumbered {@code input.include}. Numbered reading starts at {@code .0} and
	 * <b>stops at the first missing number</b>; mixing an unnumbered entry with {@code .1}
	 * silently ignores the second entry.
	 * </p>
	 */
	public ConversionProbe include(final String pattern) {
		if (pattern != null) {
			this.properties.put("input.include." + this.includeCount, pattern);
			this.includeCount++;
		}
		return this;
	}

	/**
	 * Whether to inject the embedding application's generic resolver. Inject it by default
	 * (the same setup as production CLI and servers).
	 *
	 * <p>
	 * <b>Not injecting this resolver is essential for untrusted input.</b>
	 * {@code setLocalAccessAllowed(false)} alone is insufficient.
	 * </p>
	 */
	public ConversionProbe genericResolver(final boolean use) {
		this.genericResolver = use;
		return this;
	}

	public ConversionProbe localAccessAllowed(final boolean allowed) {
		this.localAccessAllowed = allowed;
		return this;
	}

	/** Lay out an HTML string and return the result. The base URI is under the working directory. */
	public Result convertHtml(final String html) throws Exception {
		return this.convert(html.getBytes(StandardCharsets.UTF_8), "text/html");
	}

	/**
	 * Fetch the main document <b>from this URL</b> and lay it out.
	 *
	 * <p>
	 * <b>Always use this when measuring fetches from inside SVG.</b> If the main document uses {@code file:},
	 * Batik's default check (reject if the source host differs from the document's) blocks {@code http:}
	 * fetches from SVG first, so <b>you cannot measure whether the path is active</b>.
	 * This actually invalidated a measurement on 2026-09-08. Serving the main document from the same
	 * recording server passes the default check, isolating FolioJet's controls.
	 * </p>
	 */
	public Result convertUrl(final String url) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<String> messages = Collections.synchronizedList(new ArrayList<String>());
		final DirectSession session = this.openSession(messages);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.transcode(URI.create(url));
		} finally {
			session.close();
		}
		return new Result(out.toByteArray(), messages);
	}

	public Result convert(final byte[] source, final String mediaType) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final List<String> messages = Collections.synchronizedList(new ArrayList<String>());
		final DirectSession session = this.openSession(messages);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			this.transcode(session, source, mediaType);
		} finally {
			session.close();
		}
		return new Result(out.toByteArray(), messages);
	}

	private DirectSession openSession(final List<String> messages) throws Exception {
		final MessageHandler handler = new MessageHandler() {
			@Override
			public void message(final short code, final String[] args, final String mes) {
				messages.add(Integer.toHexString(code & 0xFFFF).toUpperCase() + " " + mes);
			}
		};
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		session.setMessageHandler(handler);
		if (this.genericResolver) {
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
		}
		session.setLocalAccessAllowed(this.localAccessAllowed);
		for (final Map.Entry<String, String> e : this.properties.entrySet()) {
			session.property(e.getKey(), e.getValue());
		}
		return session;
	}

	private void transcode(final CTISession session, final byte[] source, final String mediaType) throws Exception {
		// Use a nonexistent file under the working directory as the base URI. All fixture
		// references are absolute URIs, so relative resolution is unused, but specify the base
		// to expose mistaken detection of relative URIs.
		final URI base = new java.io.File("files/unittest/probe-source.html").toURI();
		try (java.io.InputStream in = new java.io.ByteArrayInputStream(source)) {
			CTISessionHelper.transcodeStream(session, in, base, mediaType, "UTF-8");
		}
	}

	/** Result of one conversion. */
	public static class Result {

		private final byte[] pdf;

		private final List<String> messages;

		Result(final byte[] pdf, final List<String> messages) {
			this.pdf = pdf;
			this.messages = messages;
		}

		public byte[] pdf() {
			return this.pdf;
		}

		public List<String> messages() {
			return this.messages;
		}

		/** Whether a warning with this number appeared. {@code 2814} means resource-fetch denial. */
		public boolean hasMessage(final String hexCode) {
			for (final String m : this.messages) {
				if (m.startsWith(hexCode + " ")) {
					return true;
				}
			}
			return false;
		}

		/**
		 * Whether <b>this URI was denied with this code</b>.
		 *
		 * <p>
		 * No fetch alone cannot distinguish a denial from a path that never activated.
		 * Use this to verify that <b>the intended denial occurred for the intended target</b>.
		 * </p>
		 */
		public boolean deniedResource(final String hexCode, final String uri) {
			for (final String m : this.messages) {
				if (m.startsWith(hexCode + " ") && m.contains(uri)) {
					return true;
				}
			}
			return false;
		}

		/** All content streams concatenated. */
		public String operators() throws Exception {
			return String.join("\n", inflateStreams(this.pdf));
		}

		/**
		 * Whether a fill or stroke with this color appears.
		 *
		 * @param op {@code rg} for fill, {@code RG} for stroke
		 */
		public boolean hasColor(final String op, final double r, final double g, final double b) throws Exception {
			final Matcher m = Pattern.compile("([\\d.]+) ([\\d.]+) ([\\d.]+) " + op + "\\b")
					.matcher(this.operators());
			while (m.find()) {
				if (Math.abs(Double.parseDouble(m.group(1)) - r) < 0.01
						&& Math.abs(Double.parseDouble(m.group(2)) - g) < 0.01
						&& Math.abs(Double.parseDouble(m.group(3)) - b) < 0.01) {
					return true;
				}
			}
			return false;
		}

		/** A summary for explaining failures. */
		public String describe() {
			return "PDF " + this.pdf.length + "バイト、警告 " + this.messages;
		}
	}

	private static List<String> inflateStreams(final byte[] pdf) {
		final List<String> result = new ArrayList<String>();
		final String latin = new String(pdf, StandardCharsets.ISO_8859_1);
		final Matcher m = Pattern.compile("stream\\r?\\n(.*?)endstream", Pattern.DOTALL).matcher(latin);
		while (m.find()) {
			final byte[] raw = m.group(1).getBytes(StandardCharsets.ISO_8859_1);
			final Inflater inflater = new Inflater();
			inflater.setInput(raw);
			final ByteArrayOutputStream buff = new ByteArrayOutputStream();
			final byte[] chunk = new byte[8192];
			try {
				while (!inflater.finished()) {
					final int n = inflater.inflate(chunk);
					if (n == 0) {
						break;
					}
					buff.write(chunk, 0, n);
				}
				result.add(buff.toString(StandardCharsets.ISO_8859_1));
			} catch (final Exception e) {
				// Skip uncompressed streams and streams such as images.
			} finally {
				inflater.end();
			}
		}
		return result;
	}
}
