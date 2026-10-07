package net.zamasoft.foliojet.css.value;

import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * A {@code font-variant-caps} value.
 *
 * <p>
 * Carries forward the value type used by the old CSS2 {@code font-variant: small-caps}
 * and represents the full CSS Fonts caps longhand. Specified values map to OpenType features
 * and pass from {@code CSSStyle.getFontStyle()} to pdfg2d.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum FontVariantValue implements Value {
	NORMAL_VALUE(FontVariantValue.NORMAL),

	SMALL_CAPS_VALUE(FontVariantValue.SMALL_CAPS),

	ALL_SMALL_CAPS_VALUE(FontVariantValue.ALL_SMALL_CAPS),

	PETITE_CAPS_VALUE(FontVariantValue.PETITE_CAPS),

	ALL_PETITE_CAPS_VALUE(FontVariantValue.ALL_PETITE_CAPS),

	UNICASE_VALUE(FontVariantValue.UNICASE),

	TITLING_CAPS_VALUE(FontVariantValue.TITLING_CAPS);
	public static final byte NORMAL = 1;

	public static final byte SMALL_CAPS = 2;

	public static final byte ALL_SMALL_CAPS = 3;

	public static final byte PETITE_CAPS = 4;

	public static final byte ALL_PETITE_CAPS = 5;

	public static final byte UNICASE = 6;

	public static final byte TITLING_CAPS = 7;

	private final byte fontVariant;

	private FontVariantValue(byte fontVariant) {
		this.fontVariant = fontVariant;
	}

	/**
	 * Returns the variant code.
	 *
	 * @return
	 */
	public byte getFontVariant() {
		return this.fontVariant;
	}

	/**
	 * Returns the corresponding OpenType feature tags. {@code all-*} enables both the lowercase
	 * and uppercase tags. If the font lacks the feature, it naturally has no effect in pdfg2d.
	 */
	public FontFeatureSet featureSet() {
		return switch (this.fontVariant) {
		case NORMAL -> FontFeatureSet.EMPTY;
		case SMALL_CAPS -> features("smcp");
		case ALL_SMALL_CAPS -> features("smcp", "c2sc");
		case PETITE_CAPS -> features("pcap");
		case ALL_PETITE_CAPS -> features("pcap", "c2pc");
		case UNICASE -> features("unic");
		case TITLING_CAPS -> features("titl");
		default -> throw new IllegalStateException();
		};
	}

	private static FontFeatureSet features(final String... names) {
		final int[] tags = new int[names.length];
		final int[] values = new int[names.length];
		for (int i = 0; i < names.length; ++i) {
			tags[i] = FontFeatureSet.packTag(names[i]);
			values[i] = 1;
		}
		return FontFeatureSet.of(tags, values);
	}

	public String toString() {
		switch (this.fontVariant) {
		case NORMAL:
			return "normal";

		case SMALL_CAPS:
			return "small-caps";

		case ALL_SMALL_CAPS:
			return "all-small-caps";

		case PETITE_CAPS:
			return "petite-caps";

		case ALL_PETITE_CAPS:
			return "all-petite-caps";

		case UNICASE:
			return "unicase";

		case TITLING_CAPS:
			return "titling-caps";

		default:
			throw new IllegalStateException();
		}
	}
}
