package net.zamasoft.foliojet.css.value.css3;

import java.net.URI;
import net.zamasoft.foliojet.css.value.Value;

/**
 * Unicode-Range です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class SrcValue implements Value {
	private final URI[] uris;

	public SrcValue(URI[] uris) {
		this.uris = uris;
	}

	public URI[] getURIs() {
		return this.uris;
	}

}