package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum PositionValue implements Value {
	STATIC_VALUE(PositionValue.STATIC),

	RELATIVE_VALUE(PositionValue.RELATIVE),

	ABSOLUTE_VALUE(PositionValue.ABSOLUTE),

	FIXED_VALUE(PositionValue.FIXED),

	_CSSJ_CURRENT_PAGE_VALUE(PositionValue._CSSJ_CURRENT_PAGE);
	public static final byte STATIC = 0;

	public static final byte RELATIVE = 1;

	public static final byte ABSOLUTE = 2;

	public static final byte FIXED = 3;

	public static final byte _CSSJ_CURRENT_PAGE = 4;

	private final byte position;

	private PositionValue(byte position) {
		this.position = position;
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