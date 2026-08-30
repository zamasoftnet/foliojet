package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

/**
 * background-origin のボックスです。
 *
 * @author MIYABE Tatsuhiko
 */
public enum BackgroundOriginValue implements Value {
	BORDER_BOX_VALUE(BackgroundOriginValue.BORDER_BOX),

	PADDING_BOX_VALUE(BackgroundOriginValue.PADDING_BOX),

	CONTENT_BOX_VALUE(BackgroundOriginValue.CONTENT_BOX);

	public static final byte BORDER_BOX = 1;

	public static final byte PADDING_BOX = 2;

	public static final byte CONTENT_BOX = 3;

	private final byte backgroundOrigin;

	private BackgroundOriginValue(byte backgroundOrigin) {
		this.backgroundOrigin = backgroundOrigin;
	}

	public byte getBackgroundOrigin() {
		return this.backgroundOrigin;
	}

	@Override
	public String toString() {
		switch (this.backgroundOrigin) {
		case BORDER_BOX:
			return "border-box";
		case PADDING_BOX:
			return "padding-box";
		case CONTENT_BOX:
			return "content-box";
		default:
			throw new IllegalStateException();
		}
	}
}
