package net.zamasoft.foliojet.ua;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;

public class DocumentContext {
	public static final byte CM_STRICT = 1;

	public static final byte CM_NORMAL = 2;

	private URI baseURI;

	private String encoding = "ISO-8859-1";

	private byte compatibleMode = CM_NORMAL;

	public void setBaseURI(URI baseURI) {
		this.baseURI = baseURI;
	}

	public URI getBaseURI() {
		return this.baseURI;
	}

	public byte getCompatibleMode() {
		return this.compatibleMode;
	}

	public void setCompatibleMode(byte compatibleMode) {
		this.compatibleMode = compatibleMode;
	}

	public String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) throws UnsupportedEncodingException {
		encoding = encoding.trim();
		try {
			if (!Charset.isSupported(encoding)) {
				throw new UnsupportedEncodingException(encoding);
			}
		} catch (Exception e) {
			throw new UnsupportedEncodingException(encoding);
		}
		this.encoding = encoding;
	}
}
