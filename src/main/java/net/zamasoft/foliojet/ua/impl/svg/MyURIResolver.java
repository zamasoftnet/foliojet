package net.zamasoft.foliojet.ua.impl.svg;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.URIResolver;
import org.apache.batik.util.ParsedURL;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.svg.SVGDocument;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;

public class MyURIResolver extends URIResolver {
	protected final UserAgent ua;
	protected final SVGImageLoader loader;

	public MyURIResolver(SVGDocument doc, DocumentLoader dl, UserAgent ua, SVGImageLoader loader) {
		super(doc, dl);
		this.ua = ua;
		this.loader = loader;
	}

	public Node getNode(String uri, Element ref) throws MalformedURLException, IOException, SecurityException {
		try {
			String baseURI = getRefererBaseURI(ref);
			if (baseURI != null && baseURI.length() == 0) {
				baseURI = null;
			}
			// Always resolve fragment-only references (#id) within the same document, regardless of
			// the base URI (WHATWG URL/SVG 2 same-document reference). With an opaque base
			// such as data:, resolution through ParsedURL(base, "#id") misidentifies
			// the reference as another document and reparses the reference string itself,
			// failing with "Content is not allowed in prolog" (discovered on 2026-08-06
			// in yahoo.co.jp icons). Previously, SVGImageLoader worked around this
			// by stripping clip-path attributes and retrying,
			// but that exposed unclipped fill rectangles
			// (icons became gray squares), so consolidate on same-document resolution here
			// (2026-08-07).
			if (uri.charAt(0) == '#') {
				return getNodeByFragment(uri.substring(1), ref);
			}

			ParsedURL pURL;
			if (baseURI != null && !uri.startsWith(baseURI)) {
				pURL = new ParsedURL(baseURI, uri);
			} else {
				pURL = new ParsedURL(uri);
			}
			if (this.documentURI == null) {
				this.documentURI = this.document.getURL();
			}

			String frag = pURL.getRef();
			if ((frag != null) && (this.documentURI != null)) {
				ParsedURL pDocURL = new ParsedURL(this.documentURI);
				if (pDocURL.sameFile(pURL)) {
					return this.document.getElementById(frag);
				}
			}

			String purlStr = pURL.toString();
			if (frag != null) {
				purlStr = purlStr.substring(0, purlStr.length() - (frag.length() + 1));
			}

			try {
				Source source = this.ua.resolve(URIHelper.create("UTF-8", purlStr));
				try {
					Document doc = this.loader.loadDocument(source);
					if (frag != null) {
						return doc.getElementById(frag);
					}
					return doc;
				} finally {
					this.ua.release(source);
				}
			} catch (URISyntaxException e) {
				throw new MalformedURLException(purlStr);
			}
		} catch (IOException e) {
			// The caller (Batik's URIResolver path) handles logging and fallback.
			throw e;
		}
	}
}
