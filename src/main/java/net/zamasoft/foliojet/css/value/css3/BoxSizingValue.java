package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum BoxSizingValue implements Value {
	CONTENT_BOX_VALUE(BoxSizingValue.CONTENT_BOX),

	BORDER_BOX_VALUE(BoxSizingValue.BORDER_BOX);
	public static final byte CONTENT_BOX = 1;

	public static final byte BORDER_BOX = 2;

	private final byte boxSizing;

	private BoxSizingValue(byte boxSizing) {
		this.boxSizing = boxSizing;
	}

	public byte getBoxSizing() {
		return this.boxSizing;
	}

	public String toString() {
		switch (this.boxSizing) {
		case CONTENT_BOX:
			return "content-box";

		case BORDER_BOX:
			return "border-box";

		default:
			throw new IllegalStateException();
		}
	}
}