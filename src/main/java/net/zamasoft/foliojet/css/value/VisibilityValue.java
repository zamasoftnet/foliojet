package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: VisibilityValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class VisibilityValue implements Value {
	public static final byte VISIBLE = 0;

	public static final byte HIDDEN = 1;

	public static final byte COLLAPSE = 2;

	public static final VisibilityValue VISIBLE_VALUE = new VisibilityValue(VISIBLE);

	public static final VisibilityValue HIDDEN_VALUE = new VisibilityValue(HIDDEN);

	public static final VisibilityValue COLLAPSE_VALUE = new VisibilityValue(COLLAPSE);

	private final byte visibility;

	private VisibilityValue(byte visibility) {
		this.visibility = visibility;
	}

	public short getValueType() {
		return TYPE_VISIBILITY;
	}

	public byte getVisibility() {
		return this.visibility;
	}

	public String toString() {
		switch (this.visibility) {
		case VISIBLE:
			return "visible";

		case HIDDEN:
			return "hidden";

		case COLLAPSE:
			return "collapse";

		default:
			throw new IllegalStateException();
		}
	}
}