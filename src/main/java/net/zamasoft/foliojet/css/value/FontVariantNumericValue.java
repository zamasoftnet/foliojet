package net.zamasoft.foliojet.css.value;

import java.util.Arrays;

import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * {@code font-variant-numeric}の値です(css-fonts-3)。互いに排他的な
 * 数字字形・幅・分数の三カテゴリと、独立なordinal/slashed-zeroを保持し、
 * OpenType featureタグへ正規化します。
 */
public final class FontVariantNumericValue implements Value {
	public static final FontVariantNumericValue NORMAL_VALUE =
			new FontVariantNumericValue(null, null, null, false, false);

	private final String figure;
	private final String spacing;
	private final String fraction;
	private final boolean ordinal;
	private final boolean slashedZero;

	private FontVariantNumericValue(final String figure, final String spacing, final String fraction,
			final boolean ordinal, final boolean slashedZero) {
		this.figure = figure;
		this.spacing = spacing;
		this.fraction = fraction;
		this.ordinal = ordinal;
		this.slashedZero = slashedZero;
	}

	public static FontVariantNumericValue create(final String figure, final String spacing, final String fraction,
			final boolean ordinal, final boolean slashedZero) {
		if (figure == null && spacing == null && fraction == null && !ordinal && !slashedZero) {
			return NORMAL_VALUE;
		}
		return new FontVariantNumericValue(figure, spacing, fraction, ordinal, slashedZero);
	}

	public boolean isNormal() {
		return this == NORMAL_VALUE;
	}

	/** CSSキーワードを対応するOpenType feature(値1)へ変換します。 */
	public FontFeatureSet featureSet() {
		if (this.isNormal()) {
			return FontFeatureSet.EMPTY;
		}
		final int[] tags = new int[5];
		int count = 0;
		if (this.figure != null) {
			tags[count++] = FontFeatureSet.packTag(this.figure);
		}
		if (this.spacing != null) {
			tags[count++] = FontFeatureSet.packTag(this.spacing);
		}
		if (this.fraction != null) {
			tags[count++] = FontFeatureSet.packTag(this.fraction);
		}
		if (this.ordinal) {
			tags[count++] = FontFeatureSet.packTag("ordn");
		}
		if (this.slashedZero) {
			tags[count++] = FontFeatureSet.packTag("zero");
		}
		final int[] values = new int[count];
		Arrays.fill(values, 1);
		return FontFeatureSet.of(Arrays.copyOf(tags, count), values);
	}

	@Override
	public String toString() {
		if (this.isNormal()) {
			return "normal";
		}
		final StringBuilder out = new StringBuilder();
		append(out, keyword(this.figure));
		append(out, keyword(this.spacing));
		append(out, keyword(this.fraction));
		if (this.ordinal) {
			append(out, "ordinal");
		}
		if (this.slashedZero) {
			append(out, "slashed-zero");
		}
		return out.toString();
	}

	private static String keyword(final String tag) {
		if (tag == null) {
			return null;
		}
		return switch (tag) {
		case "lnum" -> "lining-nums";
		case "onum" -> "oldstyle-nums";
		case "pnum" -> "proportional-nums";
		case "tnum" -> "tabular-nums";
		case "frac" -> "diagonal-fractions";
		case "afrc" -> "stacked-fractions";
		default -> tag;
		};
	}

	private static void append(final StringBuilder out, final String value) {
		if (value == null) {
			return;
		}
		if (out.length() > 0) {
			out.append(' ');
		}
		out.append(value);
	}
}
