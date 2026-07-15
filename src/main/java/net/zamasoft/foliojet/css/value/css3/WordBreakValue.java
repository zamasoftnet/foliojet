package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum WordBreakValue implements Value {
	NORMAL_VALUE(WordBreakValue.NORMAL),

	BREAK_ALL_VALUE(WordBreakValue.BREAK_ALL),

	KEEP_ALL_VALUE(WordBreakValue.KEEP_ALL);
	public static final byte NORMAL = 1;

	public static final byte BREAK_ALL = 2;

	public static final byte KEEP_ALL = 3;

	private final byte wordBreak;

	private WordBreakValue(byte wordBreak) {
		this.wordBreak = wordBreak;
	}

	public byte getWordBreak() {
		return this.wordBreak;
	}

	public String toString() {
		switch (this.wordBreak) {
		case NORMAL:
			return "normal";

		case BREAK_ALL:
			return "break-all";

		case KEEP_ALL:
			return "keep-all";

		default:
			throw new IllegalStateException();
		}
	}
}