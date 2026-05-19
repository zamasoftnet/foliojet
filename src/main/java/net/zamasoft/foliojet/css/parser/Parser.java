package net.zamasoft.foliojet.css.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.htmlunit.cssparser.parser.CSSErrorHandler;
import org.htmlunit.cssparser.parser.CSSException;
import org.htmlunit.cssparser.parser.CSSParseException;
import org.htmlunit.cssparser.parser.DocumentHandler;
import org.htmlunit.cssparser.parser.javacc.CSS3Parser;

public class Parser {
	private final CSS3Parser parser = new CSS3Parser();
	private String defaultCharset;

	public Parser() {
		this.parser.setErrorHandler(new ThrowingErrorHandler());
	}

	public void setDocumentHandler(DocumentHandler handler) {
		this.parser.setDocumentHandler(handler);
	}

	public void setDefaultCharset(String defaultCharset) {
		this.defaultCharset = defaultCharset;
	}

	public String getDefaultCharset() {
		return this.defaultCharset;
	}

	public void parseStyleSheet(InputSource source) throws IOException, CSSException {
		this.parser.parseStyleSheet(normalizeStyleSheet(source));
	}

	public void parseStyleDeclaration(InputSource source) throws IOException, CSSException {
		this.parser.parseStyleDeclaration(normalizeStyleDeclaration(source));
	}

	private static InputSource normalizeStyleSheet(InputSource source) throws IOException {
		String css = read(source.getReader());
		css = css.replace("{literal}", "").replace("{/literal}", "");
		return copySource(source, css);
	}

	private static InputSource normalizeStyleDeclaration(InputSource source) throws IOException {
		return copySource(source, read(source.getReader()).trim());
	}

	private static InputSource copySource(InputSource source, String css) {
		InputSource copy = new InputSource(new StringReader(css));
		copy.setEncoding(source.getEncoding());
		copy.setURI(source.getURI());
		copy.setMedia(source.getMedia());
		copy.setTitle(source.getTitle());
		return copy;
	}

	private static String read(Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[4096];
		for (int len = reader.read(buffer); len != -1; len = reader.read(buffer)) {
			builder.append(buffer, 0, len);
		}
		return builder.toString();
	}

	private static class ThrowingErrorHandler implements CSSErrorHandler {
		public void warning(CSSParseException exception) throws CSSException {
			// The legacy parser reported recoverable warnings through events only.
		}

		public void error(CSSParseException exception) throws CSSException {
			throw exception;
		}

		public void fatalError(CSSParseException exception) throws CSSException {
			throw exception;
		}
	}
}
