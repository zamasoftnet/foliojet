package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public class AttrValue implements Value {
	private final String name;

	public AttrValue(String name) {
		this.name = name;
	}

	/*
	 * (non-Javadoc)
	 * 
	 */

	public String getName() {
		return this.name;
	}
}