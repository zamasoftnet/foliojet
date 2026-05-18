package net.zamasoft.foliojet.css.value;

import java.net.URI;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: URIValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class URIValue implements Value {

	private final URI uri;

	public static URIValue create(URI uri) {
		return new URIValue(uri);
	}

	private URIValue(URI uri) {
		this.uri = uri;
	}

	public URI getURI() {
		return this.uri;
	}

	public short getValueType() {
		return TYPE_URI;
	}

	public String toString() {
		return "url(" + this.uri + ")";
	}
}