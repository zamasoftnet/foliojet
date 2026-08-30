package net.zamasoft.foliojet.css.value;

import java.util.Arrays;

import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * {@code font-variant-ligatures}の値です。
 *
 * <p>
 * 各カテゴリを未指定・有効・無効の三状態で保持し、OpenTypeの
 * {@code liga/clig/dlig/hlig/calt}へ変換します。pdfg2dへタグは搬送されますが、
 * 現在のOpenType処理はGSUB単一置換だけで、合字・文脈置換のlookupは未対応です。
 * したがって現時点の描画は受理・保持相当です。
 * </p>
 */
public final class FontVariantLigaturesValue implements Value {
	private static final byte UNSPECIFIED = 0;
	private static final byte ENABLED = 1;
	private static final byte DISABLED = -1;

	public static final FontVariantLigaturesValue NORMAL_VALUE =
			new FontVariantLigaturesValue(UNSPECIFIED, UNSPECIFIED, UNSPECIFIED, UNSPECIFIED);

	public static final FontVariantLigaturesValue NONE_VALUE =
			new FontVariantLigaturesValue(DISABLED, DISABLED, DISABLED, DISABLED);

	private final byte common;
	private final byte discretionary;
	private final byte historical;
	private final byte contextual;

	private FontVariantLigaturesValue(final byte common, final byte discretionary, final byte historical,
			final byte contextual) {
		this.common = common;
		this.discretionary = discretionary;
		this.historical = historical;
		this.contextual = contextual;
	}

	public static FontVariantLigaturesValue create(final boolean commonSpecified, final boolean commonEnabled,
			final boolean discretionarySpecified, final boolean discretionaryEnabled,
			final boolean historicalSpecified, final boolean historicalEnabled,
			final boolean contextualSpecified, final boolean contextualEnabled) {
		final byte common = state(commonSpecified, commonEnabled);
		final byte discretionary = state(discretionarySpecified, discretionaryEnabled);
		final byte historical = state(historicalSpecified, historicalEnabled);
		final byte contextual = state(contextualSpecified, contextualEnabled);
		if (common == UNSPECIFIED && discretionary == UNSPECIFIED && historical == UNSPECIFIED
				&& contextual == UNSPECIFIED) {
			return NORMAL_VALUE;
		}
		if (common == DISABLED && discretionary == DISABLED && historical == DISABLED
				&& contextual == DISABLED) {
			return NONE_VALUE;
		}
		return new FontVariantLigaturesValue(common, discretionary, historical, contextual);
	}

	private static byte state(final boolean specified, final boolean enabled) {
		return specified ? (enabled ? ENABLED : DISABLED) : UNSPECIFIED;
	}

	public boolean isNormal() {
		return this == NORMAL_VALUE;
	}

	/** OpenType featureタグ列へ正規化します。 */
	public FontFeatureSet featureSet() {
		if (this.isNormal()) {
			return FontFeatureSet.EMPTY;
		}
		final int[] tags = new int[5];
		final int[] values = new int[5];
		int count = 0;
		if (this.common != UNSPECIFIED) {
			tags[count] = FontFeatureSet.packTag("liga");
			values[count++] = this.common == ENABLED ? 1 : 0;
			tags[count] = FontFeatureSet.packTag("clig");
			values[count++] = this.common == ENABLED ? 1 : 0;
		}
		if (this.discretionary != UNSPECIFIED) {
			tags[count] = FontFeatureSet.packTag("dlig");
			values[count++] = this.discretionary == ENABLED ? 1 : 0;
		}
		if (this.historical != UNSPECIFIED) {
			tags[count] = FontFeatureSet.packTag("hlig");
			values[count++] = this.historical == ENABLED ? 1 : 0;
		}
		if (this.contextual != UNSPECIFIED) {
			tags[count] = FontFeatureSet.packTag("calt");
			values[count++] = this.contextual == ENABLED ? 1 : 0;
		}
		return FontFeatureSet.of(Arrays.copyOf(tags, count), Arrays.copyOf(values, count));
	}

	@Override
	public String toString() {
		if (this == NORMAL_VALUE) {
			return "normal";
		}
		if (this == NONE_VALUE) {
			return "none";
		}
		final StringBuilder out = new StringBuilder();
		append(out, this.common, "common-ligatures", "no-common-ligatures");
		append(out, this.discretionary, "discretionary-ligatures", "no-discretionary-ligatures");
		append(out, this.historical, "historical-ligatures", "no-historical-ligatures");
		append(out, this.contextual, "contextual", "no-contextual");
		return out.toString();
	}

	private static void append(final StringBuilder out, final byte state, final String enabled,
			final String disabled) {
		if (state == UNSPECIFIED) {
			return;
		}
		if (out.length() > 0) {
			out.append(' ');
		}
		out.append(state == ENABLED ? enabled : disabled);
	}
}
