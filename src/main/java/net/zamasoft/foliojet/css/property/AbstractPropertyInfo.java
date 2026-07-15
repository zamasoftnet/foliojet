package net.zamasoft.foliojet.css.property;

/**
 * @author MIYABE Tatsuhiko
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
