package net.zamasoft.foliojet.css.parser;

/**
 * CSSの解析エラー。
 */
public class CSSException extends RuntimeException {
	private static final long serialVersionUID = 0;

	public CSSException(String message) {
		super(message);
	}

	public CSSException(String message, Throwable cause) {
		super(message, cause);
	}
}
