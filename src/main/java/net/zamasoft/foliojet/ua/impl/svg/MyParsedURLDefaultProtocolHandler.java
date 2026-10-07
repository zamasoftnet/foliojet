package net.zamasoft.foliojet.ua.impl.svg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import org.apache.batik.util.AbstractParsedURLProtocolHandler;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.ParsedURLData;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;

/**
 * A handler that delegates Batik URL parsing and <b>fetching</b> to FolioJet.
 *
 * <p>
 * Batik fetches its own resources, such as CSS within SVG ({@code @import},
 * {@code <?xml-stylesheet?>}), color profiles, and external documents.
 * These call the {@link ParsedURL#openStream()} family; {@link ParsedURLData} opens
 * the actual resource with {@code java.net.URL}. <b>Without intercepting this,
 * a fetch path remains that bypasses FolioJet's {@code input.include}/{@code input.exclude}.</b>
 * </p>
 *
 * <p>
 * <b>All Batik fetches pass through here.</b> {@code ParsedURL.getHandler()} returns
 * the default handler when no protocol-specific handler exists, and none of Batik 1.19's
 * jars define a {@code ParsedURLProtocolHandler} service. This class is registered
 * with {@code super(null)}, making it <b>that default handler</b>.
 * Thus there is no need to replace paths individually: <b>this one location suffices</b>,
 * including paths not yet enumerated. This is analogous to {@code XSLTProcessorFilter}
 * installing a {@code URIResolver} on the XSLT side.
 * </p>
 *
 * <p>
 * <b>Bind the fetching UserAgent to the thread.</b> The handler is a process-wide static
 * instance registered with {@link ParsedURL#registerHandler}, and cannot receive context
 * through its arguments. SVG construction proceeds synchronously within
 * {@link SVGImageLoader#getImage}, and Batik itself is not thread-safe,
 * so binding there suffices. <b>Reject fetching when no UserAgent is bound</b>
 * (do not allow passthrough).
 * </p>
 */
class MyParsedURLDefaultProtocolHandler extends AbstractParsedURLProtocolHandler {
	public static final MyParsedURLDefaultProtocolHandler INSTANCE = new MyParsedURLDefaultProtocolHandler();

	/** UserAgent for the SVG currently being built. */
	private static final ThreadLocal<UserAgent> CURRENT = new ThreadLocal<UserAgent>();

	/**
	 * Routes this thread's fetches to this UserAgent.
	 *
	 * @return the previous value; pass it to {@link #leave(UserAgent)} to restore
	 */
	static UserAgent enter(final UserAgent ua) {
		final UserAgent previous = CURRENT.get();
		CURRENT.set(ua);
		return previous;
	}

	/** Restores the value returned by {@link #enter(UserAgent)}. */
	static void leave(final UserAgent previous) {
		if (previous == null) {
			CURRENT.remove();
		} else {
			CURRENT.set(previous);
		}
	}

	private MyParsedURLDefaultProtocolHandler() {
		super(null);
	}

	public ParsedURLData parseURL(String url) {
		if (url == null) {
			return this.createParsedURLData();
		}
		try {
			URI uri = URIHelper.create("UTF-8", url);
			return this.build(uri);
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	public ParsedURLData parseURL(ParsedURL base, String href) {
		URI uri;
		try {
			if (base == null) {
				if (href == null) {
					return this.createParsedURLData();
				}
				uri = URIHelper.create("UTF-8", href);
			} else {
				uri = URIHelper.create("UTF-8", base.toString());
				if (href != null) {
					if (uri.isOpaque() && href.startsWith("#")) {
						// **Same-document fragment references with an opaque base URI (data:, etc.)**
						// (discovered on 2026-08-06 when a premium icon's clip-path="url(#id)"
						// became blank). java.net.URI#resolve() cannot
						// apply RFC3986 relative resolution rules to an opaque base,
						// so it ignores the base and returns href itself (only #clip0,
						// without scheme/ssp). Batik mistook this for
						// another document and could not resolve url(#id) references such as clip-path,
						// leaving an empty clip region (= rendering disappeared).
						// Keep the scheme and raw scheme-specific-part,
						// replacing only the fragment to make a same-document reference
						// (use getRawSchemeSpecificPart() to avoid corrupting already
						// percent-encoded data by encoding it again).
						uri = new URI(uri.getScheme() + ":" + uri.getRawSchemeSpecificPart() + href);
					} else {
						uri = uri.resolve(href);
					}
				}
			}
			return this.build(uri);
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	private ParsedURLData build(final URI uri) {
		final MyParsedURLData pURL = this.createParsedURLData();
		pURL.uri = uri;
		pURL.protocol = uri.getScheme();
		pURL.host = uri.getHost();
		pURL.port = uri.getPort();
		pURL.path = uri.getPath();
		pURL.ref = uri.getFragment();
		return pURL;
	}

	protected MyParsedURLData createParsedURLData() {
		return new MyParsedURLData();
	}

	/**
	 * {@link ParsedURLData} that routes fetching to FolioJet's resolver.
	 *
	 * <p>
	 * Both {@code openStream} and {@code openStreamRaw} converge on
	 * {@code openStreamInternal}, so override only that method.
	 * </p>
	 */
	static class MyParsedURLData extends ParsedURLData {
		/** The original parsed URI. Retained to avoid converting to a string and parsing again. */
		URI uri = null;

		public boolean complete() {
			return true;
		}

		protected InputStream openStreamInternal(final String userAgent, final Iterator mimeTypes,
				final Iterator encodingTypes) throws IOException {
			if (this.stream != null) {
				return this.stream;
			}
			this.hasBeenOpened = true;
			if (this.uri == null) {
				throw new IOException("URIがありません。");
			}
			final UserAgent ua = CURRENT.get();
			if (ua == null) {
				// **Do not allow passthrough.** A fetch reaching here is outside SVG construction,
				// with no way to consult FolioJet's ACL.
				throw new IOException("SVGの外からの取得は許しません: " + this.uri);
			}
			// **Read fully before returning.** When Batik closes streams varies by path;
			// a path that never closes its stream would leave the Source unreleased.
			// Only CSS and color profiles pass through here, and both are small.
			final byte[] body;
			final Source source = ua.resolve(SVGImageLoader.toSourceURI(this.uri));
			try {
				final String mimeType = source.getMimeType();
				if (mimeType != null) {
					this.contentType = mimeType;
				}
				final String encoding = source.getEncoding();
				if (encoding != null) {
					this.contentEncoding = encoding;
				}
				try (InputStream in = source.getInputStream()) {
					body = in.readAllBytes();
				}
			} finally {
				ua.release(source);
			}
			this.stream = new ByteArrayInputStream(body);
			return this.stream;
		}
	}
}
