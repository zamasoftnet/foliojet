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

import net.zamasoft.foliojet.message.MessageCodes;
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
 * <b>Every Batik fetch passes through here, or is refused here.</b> {@code ParsedURL.getHandler()}
 * returns the default handler when no protocol-specific handler exists, and none of Batik 1.19's
 * jars define a {@code ParsedURLProtocolHandler} service. {@link #INSTANCE} is registered
 * with a null protocol, making it <b>that default handler</b>.
 * Thus there is no need to replace paths individually: <b>this one location suffices</b>,
 * including paths not yet enumerated. This is analogous to {@code XSLTProcessorFilter}
 * installing a {@code URIResolver} on the XSLT side.
 * </p>
 *
 * <p>
 * Batik itself registers two protocol-specific handlers, which the default handler does not see:
 * {@code data} is decoded in memory and opens nothing, but {@code jar} opened the inner URL with
 * {@code java.net.URL}, past the resolver (access control, local-access guard, redirect guard, size and
 * time limits). {@link #JAR} takes that protocol over and refuses it: nothing in an SVG needs an archive.
 * {@code MyUserAgent} refuses what any other handler would open by itself.
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
	public static final MyParsedURLDefaultProtocolHandler INSTANCE = new MyParsedURLDefaultProtocolHandler(null);

	/** Replaces Batik's own handler for {@code jar:} URLs; whatever it parses is refused when opened. */
	public static final MyParsedURLDefaultProtocolHandler JAR = new MyParsedURLDefaultProtocolHandler("jar");

	/**
	 * Whether opening this URL stays inside the engine: one of the handlers of this class has its protocol (the
	 * resolver serves it, or it is refused when opened), or it is {@code data:}, which Batik decodes in memory.
	 * Anything else would be opened by a handler that does not ask the resolver.
	 */
	static boolean opensThroughEngine(final ParsedURL url) {
		final String protocol = url.getProtocol();
		if (protocol == null) {
			return true;
		}
		final String scheme = protocol.toLowerCase(java.util.Locale.ROOT);
		return "data".equals(scheme) || ParsedURL.getHandler(scheme) instanceof MyParsedURLDefaultProtocolHandler;
	}

	/**
	 * The resource of the program's own class path that this {@code jar:} URL names exactly, or {@code null}.
	 * A document may name such a URL too; it reads nothing that is not already part of the program, and nothing
	 * outside the class path can match.
	 */
	static java.net.URL classPathResource(final String url) {
		final int entryStart = url.indexOf("!/");
		if (entryStart < 0) {
			return null;
		}
		final int fragment = url.indexOf('#', entryStart);
		final String plain = fragment < 0 ? url : url.substring(0, fragment);
		// Compared without percent-escapes: the two sides may escape an install directory differently
		final String wanted = unescaped(plain);
		final String entry = wanted.substring(wanted.indexOf("!/") + 2);
		try {
			final java.util.Enumeration<java.net.URL> resources = ParsedURL.class.getClassLoader().getResources(entry);
			while (resources.hasMoreElements()) {
				final java.net.URL resource = resources.nextElement();
				if (unescaped(resource.toExternalForm()).equals(wanted)) {
					return resource;
				}
			}
		} catch (final IOException e) {
			// not a resource of the class path
		}
		return null;
	}

	private static String unescaped(final String url) {
		try {
			final URI uri = new URI(url);
			return uri.getScheme() + ":" + uri.getSchemeSpecificPart();
		} catch (final URISyntaxException e) {
			return url;
		}
	}

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

	/**
	 * @param protocol null for the default handler, which fetches through the resolver; a protocol name for a
	 *                 handler that takes that protocol away from Batik and refuses it
	 */
	private MyParsedURLDefaultProtocolHandler(final String protocol) {
		super(protocol);
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
		if (this.protocol != null) {
			// A protocol this handler refuses. Nothing is fetched; the parts only have to name the URL in
			// messages, so keep everything after the scheme in the path, as Batik's own jar handler does.
			pURL.refused = true;
			pURL.protocol = this.protocol;
			if (uri.isOpaque()) {
				pURL.path = uri.getRawSchemeSpecificPart();
			}
		}
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

		/** Parsed by a handler that refuses its protocol ({@link MyParsedURLDefaultProtocolHandler#JAR}). */
		boolean refused = false;

		public boolean complete() {
			return true;
		}

		protected InputStream openStreamInternal(final String userAgent, final Iterator mimeTypes,
				final Iterator encodingTypes) throws IOException {
			if (this.refused) {
				final String url = this.uri == null ? this.toString() : this.uri.toString();
				final java.net.URL own = classPathResource(url);
				if (own != null) {
					// Batik's own files: it reads its user-agent style sheet and its broken-link image from its
					// archives through jar: URLs. Refusing them drew no SVG at all.
					this.hasBeenOpened = true;
					return own.openStream();
				}
				// Reported like a resource the access control denies, then failed like one: the reference is
				// ignored and the rest of the SVG is drawn.
				final UserAgent refusing = CURRENT.get();
				if (refusing != null) {
					refusing.message(MessageCodes.WARN_BLOCKED_RESOURCE, url);
				}
				throw new IOException("This kind of URL is not permitted in SVG: " + url);
			}
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
