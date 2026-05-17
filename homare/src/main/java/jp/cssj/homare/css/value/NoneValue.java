package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: NoneValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class NoneValue implements Value {
	public static final NoneValue NONE_VALUE = new NoneValue();

	private NoneValue() {
		// singleton
	}

	public short getValueType() {
		return TYPE_NONE;
	}

	public String toString() {
		return "none";
	}
}