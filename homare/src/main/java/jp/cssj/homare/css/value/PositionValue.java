package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: PositionValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class PositionValue implements Value {
	public static final byte STATIC = 0;

	public static final byte RELATIVE = 1;

	public static final byte ABSOLUTE = 2;

	public static final byte FIXED = 3;

	public static final byte _CSSJ_CURRENT_PAGE = 4;

	public static final PositionValue STATIC_VALUE = new PositionValue(STATIC);

	public static final PositionValue RELATIVE_VALUE = new PositionValue(RELATIVE);

	public static final PositionValue ABSOLUTE_VALUE = new PositionValue(ABSOLUTE);

	public static final PositionValue FIXED_VALUE = new PositionValue(FIXED);

	public static final PositionValue _CSSJ_CURRENT_PAGE_VALUE = new PositionValue(_CSSJ_CURRENT_PAGE);

	private final byte position;

	private PositionValue(byte position) {
		this.position = position;
	}

	public short getValueType() {
		return Value.TYPE_POSITION;
	}

	public byte getPosition() {
		return this.position;
	}

	public String toString() {
		switch (this.position) {
		case STATIC:
			return "static";

		case RELATIVE:
			return "relative";

		case ABSOLUTE:
			return "absolute";

		case FIXED:
			return "fixed";

		case _CSSJ_CURRENT_PAGE:
			return "-cssj-current-page";

		default:
			throw new IllegalStateException();
		}
	}
}