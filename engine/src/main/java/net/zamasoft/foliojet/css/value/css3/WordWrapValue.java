package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WordWrapValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class WordWrapValue implements CSS3Value {
	public static final WordWrapValue NORMAL_VALUE = new WordWrapValue(AbstractTextParams.WORD_WRAP_NORMAL);

	public static final WordWrapValue BREAK_WORD_VALUE = new WordWrapValue(AbstractTextParams.WORD_WRAP_BREAK_WORD);

	private final byte wordWrap;

	private WordWrapValue(byte wordWrap) {
		this.wordWrap = wordWrap;
	}

	public short getValueType() {
		return TYPE_WORD_WRAP;
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