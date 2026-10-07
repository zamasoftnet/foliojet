package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * A CSS Text 3 {@code text-justify} value (added on 2026-09-02).
 *
 * <p>
 * Determines <b>where to distribute</b> a line's remaining space for justification.
 * {@code auto} depends on the language: Japanese uses JLREQ's staged distribution,
 * Korean uses only word spaces (as in Chrome), and other languages use the existing
 * separable boundaries. {@code inter-word} uses only word spaces (whitespace);
 * {@code inter-character} (alias {@code distribute}) also distributes between characters.
 * {@code none} disables justification.
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
