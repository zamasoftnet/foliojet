package jp.cssj.homare.css.value.css3;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WordWrapValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class WordBreakValue implements CSS3Value {
	public static final byte NORMAL = 1;

	public static final byte BREAK_ALL = 2;

	public static final byte KEEP_ALL = 3;

	public static final WordBreakValue NORMAL_VALUE = new WordBreakValue(NORMAL);

	public static final WordBreakValue BREAK_ALL_VALUE = new WordBreakValue(BREAK_ALL);

	public static final WordBreakValue KEEP_ALL_VALUE = new WordBreakValue(KEEP_ALL);

	private final byte wordBreak;

	private WordBreakValue(byte wordBreak) {
		this.wordBreak = wordBreak;
	}

	public short getValueType() {
		return TYPE_WORD_BREAK;
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