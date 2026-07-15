package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum BackgroundClipValue implements Value {
	BORDER_BOX_VALUE(BackgroundClipValue.BORDER_BOX),

	PADDING_BOX_VALUE(BackgroundClipValue.PADDING_BOX),

	CONTENT_BOX_VALUE(BackgroundClipValue.CONTENT_BOX),

	TEXT_VALUE(BackgroundClipValue.TEXT);
	public static final byte BORDER_BOX = 1;

	public static final byte PADDING_BOX = 2;

	public static final byte CONTENT_BOX = 3;

	public static final byte TEXT = 4;

	private final byte backgroundClip;

	private BackgroundClipValue(byte backgroundClip) {
		this.backgroundClip = backgroundClip;
	}

	public byte getBackgroundClip() {
		return this.backgroundClip;
	}

	public String toString() {
		switch (this.backgroundClip) {
		case BORDER_BOX:
			return "border-box";

		case PADDING_BOX:
			return "padding-box";

		case CONTENT_BOX:
			return "content-box";

		case TEXT:
			return "text";

		default:
			throw new IllegalStateException();
		}
	}
}