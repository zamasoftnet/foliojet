package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: FontVariantValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FontVariantValue implements Value {
	public static final byte NORMAL = 1;

	public static final byte SMALL_CAPS = 2;

	public static final FontVariantValue NORMAL_VALUE = new FontVariantValue(NORMAL);

	public static final FontVariantValue SMALL_CAPS_VALUE = new FontVariantValue(SMALL_CAPS);

	private final byte fontVariant;

	private FontVariantValue(byte fontVariant) {
		this.fontVariant = fontVariant;
	}

	public short getValueType() {
		return TYPE_FONT_VARIANT;
	}

	/**
	 * バーリアントコードを返します。
	 * 
	 * @return
	 */
	public byte getFontVariant() {
		return this.fontVariant;
	}

	public String toString() {
		switch (this.fontVariant) {
		case NORMAL:
			return "normal";

		case SMALL_CAPS:
			return "small-caps";

		default:
			throw new IllegalStateException();
		}
	}
}