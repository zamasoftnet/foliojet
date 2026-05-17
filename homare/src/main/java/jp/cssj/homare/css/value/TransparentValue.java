package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TransparentValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TransparentValue implements Value {
	public static final TransparentValue TRANSPARENT_VALUE = new TransparentValue();

	private TransparentValue() {
		// singleton
	}

	public short getValueType() {
		return TYPE_TRANSPARENT;
	}

	public String toString() {
		return "transparent";
	}
}