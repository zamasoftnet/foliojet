package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: StringValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class StringValue implements Value {
	private final String stringValue;

	public StringValue(String stringValue) {
		this.stringValue = stringValue;
	}

	public short getValueType() {
		return Value.TYPE_STRING;
	}

	public String getString() {
		return this.stringValue;
	}

	public String toString() {
		return this.stringValue;
	}
}