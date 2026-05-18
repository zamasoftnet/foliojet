package net.zamasoft.foliojet.css.value.css3;

import java.net.URI;

/**
 * Unicode-Range です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: SrcValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class SrcValue implements CSS3Value {
	private final URI[] uris;

	public SrcValue(URI[] uris) {
		this.uris = uris;
	}

	public URI[] getURIs() {
		return this.uris;
	}

	public short getValueType() {
		return TYPE_SRC;
	}
}