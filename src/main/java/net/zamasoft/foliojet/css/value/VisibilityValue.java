package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum VisibilityValue implements Value {
	VISIBLE_VALUE(VisibilityValue.VISIBLE),

	HIDDEN_VALUE(VisibilityValue.HIDDEN),

	COLLAPSE_VALUE(VisibilityValue.COLLAPSE);
	public static final byte VISIBLE = 0;

	public static final byte HIDDEN = 1;

	public static final byte COLLAPSE = 2;

	private final byte visibility;

	private VisibilityValue(byte visibility) {
		this.visibility = visibility;
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