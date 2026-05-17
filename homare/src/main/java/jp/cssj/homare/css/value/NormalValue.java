package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: NormalValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class NormalValue implements Value {
	public static final NormalValue NORMAL_VALUE = new NormalValue();

	private NormalValue() {
		// singleton
	}

	public short getValueType() {
		return TYPE_NORMAL;
	}

	public String toString() {
		return "normal";
	}
}