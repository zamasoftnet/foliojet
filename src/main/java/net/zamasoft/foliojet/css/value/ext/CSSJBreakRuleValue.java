package net.zamasoft.foliojet.css.value.ext;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJBreakRuleValue implements Value {
	public static final CSSJBreakRuleValue NONE_VALUE = new CSSJBreakRuleValue("", "");

	private final String head, tail;

	public CSSJBreakRuleValue(String head, String tail) {
		this.head = head;
		this.tail = tail;
	}

	public String getHead() {
		return this.head;
	}

	public String getTail() {
		return this.tail;
	}

	public String toString() {
		return "'" + this.head + "' '" + this.tail + "'";
	}
}