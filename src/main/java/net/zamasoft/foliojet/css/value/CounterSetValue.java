package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CounterSetValue implements Value {
	private final String name;

	private final int value;

	public CounterSetValue(String name, int value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return this.name;
	}

	public int getValue() {
		return this.value;
	}
}