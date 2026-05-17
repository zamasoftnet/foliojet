package jp.cssj.homare.css.value;

import jp.cssj.homare.style.box.params.Types;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: OverflowValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class OverflowValue implements Value {
	public static final OverflowValue VISIBLE_VALUE = new OverflowValue(Types.OVERFLOW_VISIBLE);

	public static final OverflowValue HIDDEN_VALUE = new OverflowValue(Types.OVERFLOW_HIDDEN);

	public static final OverflowValue AUTO_VALUE = new OverflowValue(Types.OVERFLOW_SCROLL);

	public static final OverflowValue SCROLL_VALUE = new OverflowValue(Types.OVERFLOW_AUTO);

	private final byte overflow;

	private OverflowValue(byte overflow) {
		this.overflow = overflow;
	}

	public short getValueType() {
		return TYPE_OVERFLOW;
	}

	public byte getOverflow() {
		return this.overflow;
	}

	public String toString() {
		switch (this.overflow) {
		case Types.OVERFLOW_VISIBLE:
			return "visible";

		case Types.OVERFLOW_HIDDEN:
			return "hidden";

		case Types.OVERFLOW_SCROLL:
			return "scroll";

		case Types.OVERFLOW_AUTO:
			return "auto";

		default:
			throw new IllegalStateException();
		}
	}
}