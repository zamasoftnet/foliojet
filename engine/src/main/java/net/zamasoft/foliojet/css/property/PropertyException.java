package net.zamasoft.foliojet.css.property;

public class PropertyException extends Exception {
	private static final long serialVersionUID = 0L;

	public PropertyException() {
		this("不正な値です");
	}

	public PropertyException(String message) {
		super(message);
	}
}
