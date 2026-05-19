package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CounterValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CounterValue implements Value {
	private final String name;

	private final short style;

	public CounterValue(String name, short style) {
		this.name = name;
		this.style = style;
	}

	public CounterValue(String name) {
		this(name, ListStyleTypeValue.DECIMAL);
	}

	public short getValueType() {
		return TYPE_COUNTER;
	}

	public String getName() {
		return this.name;
	}

	public short getStyle() {
		return this.style;
	}
}