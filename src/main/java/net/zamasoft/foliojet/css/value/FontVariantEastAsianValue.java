package net.zamasoft.foliojet.css.value;

import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * A {@code font-variant-east-asian} value. The CSS layer retains its category structure
 * (variants, widths, and ruby), and {@code CSSStyle.getFontStyle()} normalizes it
 * into an OpenType feature tag sequence ({@link #featureSet()}).
 *
 * @author MIYABE Tatsuhiko
 */
public final class FontVariantEastAsianValue implements Value {
	/** {@code normal}. */
	public static final FontVariantEastAsianValue NORMAL_VALUE = new FontVariantEastAsianValue(null, null, false);

	/** The OpenType variant tag (jp78/jp83/jp90/jp04/smpl/trad). Null if unspecified. */
	private final String variant;

	/** The OpenType width tag (fwid/pwid). Null if unspecified. */
	private final String width;

	/** Ruby glyphs (ruby). */
	private final boolean ruby;

	private FontVariantEastAsianValue(final String variant, final String width, final boolean ruby) {
		this.variant = variant;
		this.width = width;
		this.ruby = ruby;
	}

	public static FontVariantEastAsianValue create(final String variant, final String width, final boolean ruby) {
		if (variant == null && width == null && !ruby) {
			return NORMAL_VALUE;
		}
		return new FontVariantEastAsianValue(variant, width, ruby);
	}

	public boolean isNormal() {
		return this == NORMAL_VALUE;
	}

	/** Normalizes to an OpenType feature tag sequence (all with value 1). */
	public FontFeatureSet featureSet() {
		if (this.isNormal()) {
			return FontFeatureSet.EMPTY;
		}
		final int[] tags = new int[3];
		int count = 0;
		if (this.variant != null) {
			tags[count++] = FontFeatureSet.packTag(this.variant);
		}
		if (this.width != null) {
			tags[count++] = FontFeatureSet.packTag(this.width);
		}
		if (this.ruby) {
			tags[count++] = FontFeatureSet.packTag("ruby");
		}
		final int[] values = new int[count];
		java.util.Arrays.fill(values, 1);
		return FontFeatureSet.of(java.util.Arrays.copyOf(tags, count), values);
	}

	@Override
	public String toString() {
		if (this.isNormal()) {
			return "normal";
		}
		final StringBuilder buff = new StringBuilder();
		if (this.variant != null) {
			buff.append(switch (this.variant) {
			case "jp78" -> "jis78";
			case "jp83" -> "jis83";
			case "jp90" -> "jis90";
			case "jp04" -> "jis04";
			case "smpl" -> "simplified";
			case "trad" -> "traditional";
			default -> this.variant;
			});
		}
		if (this.width != null) {
			if (buff.length() > 0) {
				buff.append(' ');
			}
			buff.append(this.width.equals("fwid") ? "full-width" : "proportional-width");
		}
		if (this.ruby) {
			if (buff.length() > 0) {
				buff.append(' ');
			}
			buff.append("ruby");
		}
		return buff.toString();
	}
}
