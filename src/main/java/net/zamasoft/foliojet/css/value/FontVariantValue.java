package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum FontVariantValue implements Value {
	NORMAL_VALUE(FontVariantValue.NORMAL),

	SMALL_CAPS_VALUE(FontVariantValue.SMALL_CAPS);
	public static final byte NORMAL = 1;

	public static final byte SMALL_CAPS = 2;

	private final byte fontVariant;

	private FontVariantValue(byte fontVariant) {
		this.fontVariant = fontVariant;
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