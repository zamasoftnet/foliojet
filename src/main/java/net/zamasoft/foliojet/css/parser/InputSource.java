package net.zamasoft.foliojet.css.parser;

import java.io.Reader;

/**
 * CSSの入力ソース。
 */
public class InputSource {
	private final Reader reader;

	private String uri;

	private String media;

	private String title;

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

	public String getMedia() {
		return this.media;
	}

	public void setMedia(String media) {
		this.media = media;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
}
