package net.zamasoft.foliojet.css.value;

import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * A {@code font-feature-settings} value. Wraps a normalized
 * {@link FontFeatureSet} (the form passed directly to pdfg2d).
 *
 * @author MIYABE Tatsuhiko
 */
public final class FontFeatureSettingsValue implements Value {
	/** {@code normal} (no features specified). */
	public static final FontFeatureSettingsValue NORMAL_VALUE = new FontFeatureSettingsValue(FontFeatureSet.EMPTY);

	private final FontFeatureSet features;

	private FontFeatureSettingsValue(final FontFeatureSet features) {
		this.features = features;
	}

	public static FontFeatureSettingsValue create(final FontFeatureSet features) {
		return features.isEmpty() ? NORMAL_VALUE : new FontFeatureSettingsValue(features);
	}

	public FontFeatureSet getFeatures() {
		return this.features;
	}

	@Override
	public String toString() {
		if (this.features.isEmpty()) {
			return "normal";
		}
		final StringBuilder buff = new StringBuilder();
		for (int i = 0; i < this.features.size(); ++i) {
			if (i > 0) {
				buff.append(", ");
			}
			final int tag = this.features.tagAt(i);
			buff.append('"').append((char) (tag >>> 24)).append((char) ((tag >>> 16) & 0xFF))
					.append((char) ((tag >>> 8) & 0xFF)).append((char) (tag & 0xFF)).append('"');
			final int value = this.features.valueAt(i);
			if (value != 1) {
				buff.append(' ').append(value);
			}
		}
		return buff.toString();
	}
}
