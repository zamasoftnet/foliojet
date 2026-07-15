package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class StringValue implements Value {
	private final String stringValue;

	public StringValue(String stringValue) {
		this.stringValue = stringValue;
	}

	public String getString() {
		return this.stringValue;
	}

	public String toString() {
		return this.stringValue;
	}
}