package net.zamasoft.foliojet.impl.ua.svg;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.batik.util.AbstractParsedURLProtocolHandler;
import org.apache.batik.util.ParsedURL;
import org.apache.batik.util.ParsedURLData;

import net.zamasoft.zstream.resolver.util.URIHelper;

class MyParsedURLDefaultProtocolHandler extends AbstractParsedURLProtocolHandler {
	public static final MyParsedURLDefaultProtocolHandler INSTANCE = new MyParsedURLDefaultProtocolHandler();

	private MyParsedURLDefaultProtocolHandler() {
		super(null);
	}

	public ParsedURLData parseURL(String url) {
		ParsedURLData pURL = this.createParsedURLData();
		if (url == null) {
			return pURL;
		}
		try {
			URI uri = URIHelper.create("UTF-8", url);
			this.buildParsedURLData(pURL, uri);
			return pURL;
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	public ParsedURLData parseURL(ParsedURL base, String href) {
		ParsedURLData pURL = this.createParsedURLData();
		URI uri;
		try {
			if (base == null) {
				if (href == null) {
					return pURL;
				}
				uri = URIHelper.create("UTF-8", href);
			} else {
				uri = URIHelper.create("UTF-8", base.toString());
				if (href != null) {
					uri = uri.resolve(href);
				}
			}
			this.buildParsedURLData(pURL, uri);
			return pURL;
		} catch (URISyntaxException ex) {
			throw new RuntimeException(ex);
		}
	}

	protected void buildParsedURLData(ParsedURLData pURL, URI uri) {
		pURL.protocol = uri.getScheme();
		pURL.host = uri.getHost();
		pURL.port = uri.getPort();
		pURL.path = uri.getPath();
		pURL.ref = uri.getFragment();
	}

	protected ParsedURLData createParsedURLData() {
		return new ParsedURLData() {
			public boolean complete() {
				return true;
			}
		};
	}
}
