package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CountersValue implements Value {
	private final String name, delimiter;

	private final ListStyleTypeSource style;

	public CountersValue(String name, String delimiter, ListStyleTypeSource style) {
		this.name = name;
		this.delimiter = delimiter;
		this.style = style;
	}

	public CountersValue(String name, String delimiter) {
		this(name, delimiter, ListStyleTypeValue.DECIMAL_VALUE);
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