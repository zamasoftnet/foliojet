package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * CSS Text 4 の {@code text-wrap-style} の値です(2026-07-25新設)。
 *
 * <p>
 * 対応するのは{@code auto}(貪欲法)と{@code pretty}(Knuth-Plass全体最適)
 * の2値のみ。{@code balance}/{@code stable}は構文としては受理するが
 * {@link #AUTO_VALUE}へ落とす(未対応)。
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
