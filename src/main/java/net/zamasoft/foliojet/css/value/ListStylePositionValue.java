package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ListStylePositionValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ListStylePositionValue implements Value {
	public static final byte INSIDE = 0;

	public static final byte OUTSIDE = 1;

	public static final ListStylePositionValue INSIDE_VALUE = new ListStylePositionValue(INSIDE);

	public static final ListStylePositionValue OUTSIDE_VALUE = new ListStylePositionValue(OUTSIDE);

	private final byte listStylePosition;

	private ListStylePositionValue(byte listStylePosition) {
		this.listStylePosition = listStylePosition;
	}

	public short getValueType() {
		return TYPE_LIST_STYLE_POSITION;
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