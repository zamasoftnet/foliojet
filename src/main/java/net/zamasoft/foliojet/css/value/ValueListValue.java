package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class ValueListValue implements Value {
	private final Value[] values;

	public static final ValueListValue EMPTY_VALUE_LIST = new ValueListValue(new Value[0]);

	public ValueListValue(Value[] values) {
		this.values = values;
	}

	public Value[] getValues() {
		return this.values;
	}
}