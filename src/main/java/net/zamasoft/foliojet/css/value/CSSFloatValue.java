package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum CSSFloatValue implements Value {
	NONE_VALUE(CSSFloatValue.NONE),

	LEFT_VALUE(CSSFloatValue.LEFT),

	RIGHT_VALUE(CSSFloatValue.RIGHT),

	START_VALUE(CSSFloatValue.START),

	END_VALUE(CSSFloatValue.END),

	FOOTNOTE_VALUE(CSSFloatValue.FOOTNOTE);
	public static final byte NONE = 0;

	public static final byte LEFT = 1;

	public static final byte RIGHT = 2;

	public static final byte START = 3;

	public static final byte END = 4;

	/**
	 * 脚注float(GCPM/Prince系)です(F0、2026-07-31——設計は
	 * consult-codex-2026-07-31-footnote.txt)。レイアウト配線(F3)までは
	 * 通常フローとして描かれる。
	 */
	public static final byte FOOTNOTE = 5;

	private final byte floating;

	private CSSFloatValue(byte floating) {
		this.floating = floating;
	}

	public byte getFloat() {
		return this.floating;
	}

	public String toString() {
		switch (this.floating) {
		case NONE:
			return "none";

		case LEFT:
			return "left";

		case RIGHT:
			return "right";

		case START:
			return "start";

		case END:
			return "end";

		case FOOTNOTE:
			return "footnote";

		default:
			throw new IllegalStateException();
		}
	}
}