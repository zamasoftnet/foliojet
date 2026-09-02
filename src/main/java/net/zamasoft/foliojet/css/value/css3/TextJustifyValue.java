package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * CSS Text 3 の {@code text-justify} の値です(2026-09-02新設)。
 *
 * <p>
 * 両端揃えで行の余りを<b>どこへ配るか</b>。{@code auto}は言語で決める
 * ——和文は JLREQ の段階的な配分、韓国語は語間だけ(Chrome と同じ)、
 * それ以外は従来の分離可能境界。{@code inter-word}は語間(空白)だけ、
 * {@code inter-character}(別名 {@code distribute})は文字間にも配る。
 * {@code none}は両端揃えをしない。
 * </p>
 */
public enum TextJustifyValue implements Value {
	AUTO_VALUE(AbstractTextParams.TEXT_JUSTIFY_AUTO),
	NONE_VALUE(AbstractTextParams.TEXT_JUSTIFY_NONE),
	INTER_WORD_VALUE(AbstractTextParams.TEXT_JUSTIFY_INTER_WORD),
	INTER_CHARACTER_VALUE(AbstractTextParams.TEXT_JUSTIFY_INTER_CHARACTER);

	private final byte textJustify;

	private TextJustifyValue(final byte textJustify) {
		this.textJustify = textJustify;
	}

	public byte getTextJustify() {
		return this.textJustify;
	}

	@Override
	public String toString() {
		switch (this.textJustify) {
		case AbstractTextParams.TEXT_JUSTIFY_AUTO:
			return "auto";
		case AbstractTextParams.TEXT_JUSTIFY_NONE:
			return "none";
		case AbstractTextParams.TEXT_JUSTIFY_INTER_WORD:
			return "inter-word";
		case AbstractTextParams.TEXT_JUSTIFY_INTER_CHARACTER:
			return "inter-character";
		default:
			throw new IllegalStateException();
		}
	}
}
