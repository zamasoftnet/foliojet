package net.zamasoft.foliojet.css.parser;

/**
 * A CSS parsing error.
 */
public class CSSException extends RuntimeException {
	public CSSException(String message) {
		super(message);
	}

	public CSSException(String message, Throwable cause) {
		super(message, cause);
	}
}
