package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum WordWrapValue implements Value {
	NORMAL_VALUE(AbstractTextParams.WORD_WRAP_NORMAL),

	BREAK_WORD_VALUE(AbstractTextParams.WORD_WRAP_BREAK_WORD);

	private final byte wordWrap;

	private WordWrapValue(byte wordWrap) {
		this.wordWrap = wordWrap;
	}

	public byte getWordWrap() {
		return this.wordWrap;
	}

	public String toString() {
		switch (this.wordWrap) {
		case AbstractTextParams.WORD_WRAP_NORMAL:
			return "normal";

		case AbstractTextParams.WORD_WRAP_BREAK_WORD:
			return "break-word";

		default:
			throw new IllegalStateException();
		}
	}
}