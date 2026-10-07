package net.zamasoft.foliojet.css.value;

/**
 * A {@code font-kerning} value (css-fonts-4 §6.3, 2026-08-29).
 *
 * <p>
 * {@code auto} and {@code normal} use kerning (equivalent in this implementation).
 * {@code none} explicitly turns off the OpenType {@code kern} feature.
 * An explicit {@code font-feature-settings} setting takes precedence
 * (the priority order in css-fonts-4 §7.1).
 * </p>
 */
public enum FontKerningValue implements Value {
	AUTO_VALUE, NORMAL_VALUE, NONE_VALUE;
}
