package net.zamasoft.foliojet.css.property;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractPropertyInfo.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractPropertyInfo implements PropertyInfo {
	private final String name;

	protected AbstractPropertyInfo(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	public String toString() {
		return this.name;
	}
}
