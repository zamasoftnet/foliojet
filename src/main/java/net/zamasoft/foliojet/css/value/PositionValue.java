package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum PositionValue implements Value {
	STATIC_VALUE(PositionValue.STATIC),

	RELATIVE_VALUE(PositionValue.RELATIVE),

	ABSOLUTE_VALUE(PositionValue.ABSOLUTE),

	FIXED_VALUE(PositionValue.FIXED),

	STICKY_VALUE(PositionValue.STICKY);
	public static final byte STATIC = 0;

	public static final byte RELATIVE = 1;

	public static final byte ABSOLUTE = 2;

	public static final byte FIXED = 3;

	/** paged mediaでは相対配置の包含ブロックだけを作り、insetによる移動はしない。 */
	public static final byte STICKY = 4;

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

		case STICKY:
			return "sticky";

		default:
			throw new IllegalStateException();
		}
	}
}
