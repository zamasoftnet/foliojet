package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CounterSetValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CounterSetValue implements Value {
	private final String name;

	private final int value;

	public CounterSetValue(String name, int value) {
		this.name = name;
		this.value = value;
	}

	public short getValueType() {
		return TYPE_COUNTER_SET;
	}

	public String getName() {
		return this.name;
	}

	public int getValue() {
		return this.value;
	}
}