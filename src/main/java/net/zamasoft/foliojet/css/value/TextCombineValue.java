package net.zamasoft.foliojet.css.value;

/**
 * Internal representation of tate-chu-yoko settings (2026-08-11).
 *
 * <p>
 * {@code horizontal} in {@code -cssj-text-combine}/{@code -epub-text-combine} and
 * the standard {@code text-combine-upright: all} both specify horizontal layout of digits, etc.
 * within vertical writing, but <b>handle width differently</b>. The former retains the natural
 * width (overflowing); the latter fits within a width of 1 em (css-writing-modes-3 §9.1:
 * "the combined text is scaled to fit within 1em"). The properties expanded from the shorthand
 * (direction, writing-mode, text-indent, line-height) cannot distinguish the two,
 * so this internal property carries the distinction.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum TextCombineValue implements Value {
	NONE_VALUE(TextCombineValue.NONE),

	HORIZONTAL_VALUE(TextCombineValue.HORIZONTAL),

	ALL_VALUE(TextCombineValue.ALL);

	/** No tate-chu-yoko. */
	public static final byte NONE = 0;

	/** Traditional tate-chu-yoko (laid out at its natural width). */
	public static final byte HORIZONTAL = 1;

	/** The standard {@code text-combine-upright: all} (fits within a width of 1 em). */
	public static final byte ALL = 2;

	private final byte textCombine;

	private TextCombineValue(byte textCombine) {
		this.textCombine = textCombine;
	}

	public byte getTextCombine() {
		return this.textCombine;
	}

	public String toString() {
		switch (this.textCombine) {
		case NONE:
			return "none";

		case HORIZONTAL:
			return "horizontal";

		case ALL:
			return "all";

		default:
			throw new IllegalStateException();
		}
	}
}
