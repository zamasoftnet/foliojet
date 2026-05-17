package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: InheritValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class InheritValue implements Value {
	public static final InheritValue INHERIT_VALUE = new InheritValue();

	private InheritValue() {
		// singleton
	}

	public short getValueType() {
		return TYPE_INHERIT;
	}

	public String toString() {
		return "inherit";
	}

}