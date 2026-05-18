package net.zamasoft.foliojet.ua;

/**
 * カウンタです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Counter.java 1566 2018-07-04 11:52:15Z miyabe $
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
