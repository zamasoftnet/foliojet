package net.zamasoft.foliojet.css.value;

import java.net.URI;

/**
 * @author MIYABE Tatsuhiko
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

	public String toString() {
		return "url(" + this.uri + ")";
	}
}