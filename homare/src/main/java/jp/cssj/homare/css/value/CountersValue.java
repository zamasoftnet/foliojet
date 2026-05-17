package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CountersValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CountersValue implements Value {
	private final String name, delimiter;

	private final ListStyleTypeValue style;

	public CountersValue(String name, String delimiter, ListStyleTypeValue style) {
		this.name = name;
		this.delimiter = delimiter;
		this.style = style;
	}

	public CountersValue(String name, String delimiter) {
		this(name, delimiter, ListStyleTypeValue.DECIMAL_VALUE);
	}

	public short getValueType() {
		return TYPE_COUNTERS;
	}

	public String getName() {
		return this.name;
	}

	public short getStyle() {
		return this.style.getListStyleType();
	}

	public String getDelimiter() {
		return this.delimiter;
	}

	public String toString() {
		return "counters(" + this.name + ",'" + this.delimiter + "'," + this.style + ")";
	}
}