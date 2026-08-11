package net.zamasoft.foliojet.css.value;

/**
 * 縦中横の指定の内部表現です(2026-08-11)。
 *
 * <p>
 * {@code -cssj-text-combine}/{@code -epub-text-combine}の{@code horizontal}と、
 * 標準の{@code text-combine-upright: all}は、どちらも「縦組みの中で数字等を
 * 横に組む」指定だが、<b>幅の扱いが違う</b>。前者は自然幅のまま(はみ出す)、
 * 後者は1em幅へ収める(css-writing-modes-3 §9.1「the combined text is
 * scaled to fit within 1em」)。ショートハンドの展開先(direction・
 * writing-mode・text-indent・line-height)だけでは両者を区別できないため、
 * この内部プロパティで区別を運ぶ。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public enum TextCombineValue implements Value {
	NONE_VALUE(TextCombineValue.NONE),

	HORIZONTAL_VALUE(TextCombineValue.HORIZONTAL),

	ALL_VALUE(TextCombineValue.ALL);

	/** 縦中横ではない。 */
	public static final byte NONE = 0;

	/** 従来の縦中横(自然幅のまま組む)。 */
	public static final byte HORIZONTAL = 1;

	/** 標準の{@code text-combine-upright: all}(1em幅へ収める)。 */
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
