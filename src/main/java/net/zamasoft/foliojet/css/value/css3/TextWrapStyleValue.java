package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * A CSS Text 4 {@code text-wrap-style} value (added on 2026-07-25).
 *
 * <p>
 * Supports only {@code auto} (greedy) and {@code pretty} (Knuth-Plass global optimization).
 * Accepts {@code balance}/{@code stable} syntactically, but maps them
 * to {@link #AUTO_VALUE} (unsupported).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum TextWrapStyleValue implements Value {
	AUTO_VALUE(AbstractTextParams.TEXT_WRAP_STYLE_AUTO),

	PRETTY_VALUE(AbstractTextParams.TEXT_WRAP_STYLE_PRETTY);

	private final byte textWrapStyle;

	private TextWrapStyleValue(byte textWrapStyle) {
		this.textWrapStyle = textWrapStyle;
	}

	public byte getTextWrapStyle() {
		return this.textWrapStyle;
	}

	public String toString() {
		switch (this.textWrapStyle) {
		case AbstractTextParams.TEXT_WRAP_STYLE_AUTO:
			return "auto";

		case AbstractTextParams.TEXT_WRAP_STYLE_PRETTY:
			return "pretty";

		default:
			throw new IllegalStateException();
		}
	}
}
