package jp.cssj.homare.css.value.ext;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJBreakRuleValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJBreakRuleValue implements ExtValue {
	public static final CSSJBreakRuleValue NONE_VALUE = new CSSJBreakRuleValue("", "");

	private final String head, tail;

	public CSSJBreakRuleValue(String head, String tail) {
		this.head = head;
		this.tail = tail;
	}

	public short getValueType() {
		return TYPE_CSSJ_NO_BREAK_RULE;
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