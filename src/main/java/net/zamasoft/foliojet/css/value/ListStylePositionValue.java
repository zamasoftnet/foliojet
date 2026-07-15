package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum ListStylePositionValue implements Value {
	INSIDE_VALUE(ListStylePositionValue.INSIDE),

	OUTSIDE_VALUE(ListStylePositionValue.OUTSIDE);
	public static final byte INSIDE = 0;

	public static final byte OUTSIDE = 1;

	private final byte listStylePosition;

	private ListStylePositionValue(byte listStylePosition) {
		this.listStylePosition = listStylePosition;
	}

	public byte getListStylePosition() {
		return this.listStylePosition;
	}

	public String toString() {
		switch (this.listStylePosition) {
		case OUTSIDE:
			return "outside";

		case INSIDE:
			return "inside";

		default:
			throw new IllegalStateException();
		}
	}
}