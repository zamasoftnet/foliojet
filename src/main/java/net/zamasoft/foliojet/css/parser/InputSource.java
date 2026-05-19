package net.zamasoft.foliojet.css.parser;

import java.io.Reader;

/**
 * Input source with the legacy metadata FolioJet still needs around the
 * HtmlUnit CSS parser input.
 */
public class InputSource extends org.htmlunit.cssparser.parser.InputSource {
	private String encoding;

	public InputSource(Reader reader) {
		super(reader);
	}

	public String getEncoding() {
		return this.encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
}
