package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class ValueListValue implements Value {
	private final Value[] values;

	public ValueListValue(Value[] values) {
		this.values = values;
	}

	public Value[] getValues() {
		return this.values;
	}
}