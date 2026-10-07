package net.zamasoft.foliojet.css.parser;

import java.io.Reader;

/**
 * A CSS input source.
 */
public class InputSource {
	private final Reader reader;

	private String uri;

	private String encoding;

	public InputSource(Reader reader) {
		this.reader = reader;
	}

	public Reader getReader() {
		return this.reader;
	}

	public String getURI() {
		return this.uri;
	}

	public void setURI(String uri) {
		this.uri = uri;
	}

	public String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
}
