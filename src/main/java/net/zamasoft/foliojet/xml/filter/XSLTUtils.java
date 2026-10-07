package net.zamasoft.foliojet.xml.filter;

import java.io.IOException;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import net.zamasoft.zstream.resolver.Source;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XSLTUtils.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class XSLTUtils {
	private XSLTUtils() {
		// unused
	}

	public static javax.xml.transform.Source toTrAXSource(Source source) throws IOException {
		StreamSource traxSource;
		if (source.isReader()) {
			traxSource = new StreamSource(source.getReader(), source.getURI().toString());
		} else {
			traxSource = new StreamSource(source.getInputStream(), source.getURI().toString());
		}
		return traxSource;
	}

	/**
	 * Creates an XSLT transformer.
	 *
	 * <p>
	 * <b>Prohibits writing anywhere outside the conversion results.</b> Saxon's
	 * {@code xsl:result-document} can create files through its default output resolver
	 * and also uses {@code URLConnection.getOutputStream()}.
	 * This path bypasses Copper's {@link net.zamasoft.zstream.io.Results results}
	 * and output size limits, allowing writes to arbitrary locations
	 * (observed on 2026-09-08: writing to file: succeeded).
	 * Input uses {@code URIResolver} to check the {@code input.include}/{@code input.exclude}
	 * ACL, so only output passed through unchecked.
	 * </p>
	 */
	public static SAXTransformerFactory createTransformerFactory() {
		final net.sf.saxon.TransformerFactoryImpl tf = new net.sf.saxon.TransformerFactoryImpl();
		final net.sf.saxon.Configuration config = tf.getConfiguration();
		// Reject all xsl:result-document output destinations.
		config.setOutputURIResolver(NO_OUTPUT);
		// Also block Java calls through extension functions and reflection.
		config.setBooleanProperty(net.sf.saxon.lib.Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
		// collection()/uri-collection() use independent paths for directory listing, ZIP access, and network fetching,
		// which ALLOW_EXTERNAL_FUNCTIONS does not disable.
		// They also bypass input.include/input.exclude checks, so reject them.
		config.setCollectionFinder(NO_COLLECTION);
		return tf;
	}

	/**
	 * Routes <b>all</b> input fetching through FolioJet's resolver.
	 *
	 * <p>
	 * Saxon 12's {@code ResourceResolver} is the extension point where fetching for
	 * {@code document()}, {@code unparsed-text()}, {@code xsl:import}/{@code xsl:include},
	 * and external entities <b>converges</b>. The legacy {@code URIResolver} alone cannot
	 * handle {@code unparsed-text()}; when Saxon substitutes it, it passes {@code null}
	 * for the relative URI, causing {@code URIHelper} to fail (observed on 2026-09-08).
	 * </p>
	 *
	 * <p>
	 * Fetching goes through {@code ua.resolve()}, so {@code input.include}/{@code input.exclude}
	 * and local resource permissions apply unchanged.
	 * <b>Rejection raises {@link SecurityException} and stops conversion there.</b>
	 * </p>
	 */
	static void setResourceResolver(final javax.xml.transform.sax.SAXTransformerFactory tf,
			final net.zamasoft.foliojet.ua.UserAgent ua) {
		final net.sf.saxon.Configuration config = ((net.sf.saxon.TransformerFactoryImpl) tf).getConfiguration();
		config.setResourceResolver(request -> {
			if (request.uri == null) {
				return null;
			}
			final java.net.URI uri;
			try {
				uri = net.zamasoft.zstream.resolver.util.URIHelper.create("UTF-8", request.uri);
			} catch (final java.net.URISyntaxException e) {
				throw new net.sf.saxon.trans.XPathException("Invalid URI: " + request.uri, e);
			}
			final byte[] body;
			try {
				final net.zamasoft.zstream.resolver.Source source = ua.resolve(uri);
				try (java.io.InputStream in = source.getInputStream()) {
					body = in.readAllBytes();
				} finally {
					ua.release(source);
				}
			} catch (final java.io.IOException e) {
				throw new net.sf.saxon.trans.XPathException(e.getMessage(), e);
			}
			// **Read fully before returning.** Saxon's read timing is unknown,
			// so passing an open Source would leave its release timing undecidable.
			final javax.xml.transform.stream.StreamSource result = new javax.xml.transform.stream.StreamSource(
					new java.io.ByteArrayInputStream(body));
			result.setSystemId(uri.toString());
			return result;
		});
	}

	/** A collection finder that rejects {@code collection()}/{@code uri-collection()}. */
	private static final net.sf.saxon.lib.CollectionFinder NO_COLLECTION = (context, collectionURI) -> {
		throw new net.sf.saxon.trans.XPathException(
				"collection() is not permitted: " + collectionURI);
	};

	/** An output resolver that rejects all {@code xsl:result-document} writes. */
	private static final net.sf.saxon.lib.OutputURIResolver NO_OUTPUT = new net.sf.saxon.lib.OutputURIResolver() {
		public net.sf.saxon.lib.OutputURIResolver newInstance() {
			return this;
		}

		public javax.xml.transform.Result resolve(final String href, final String base)
				throws javax.xml.transform.TransformerException {
			throw new javax.xml.transform.TransformerException(
					"xsl:result-document is not permitted: " + href);
		}

		public void close(final javax.xml.transform.Result result) {
			// nothing to close
		}
	};

	public static TransformerHandler createIdentityTransformerHandler() throws TransformerConfigurationException {
		return createTransformerFactory().newTransformerHandler();
	}

}
