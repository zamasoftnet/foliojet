package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ValueListValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ValueListValue implements Value {
	private final Value[] values;

	public static final ValueListValue EMPTY_VALUE_LIST = new ValueListValue(new Value[0]);

	public ValueListValue(Value[] values) {
		this.values = values;
	}

	public short getValueType() {
		return TYPE_VALUES;
	}

	public Value[] getValues() {
		return this.values;
	}
}