package net.zamasoft.foliojet.ua;

/**
 * カウンタです。
 * 
 * @author MIYABE Tatsuhiko
 */
public class Counter {
	public final String name;
	public int value = 0;

	public Counter(String name) {
		this.name = name;
	}

	public Counter(String name, int value) {
		this(name);
		this.value = value;
	}

	public String toString() {
		return name + ":" + this.value;
	}
}
