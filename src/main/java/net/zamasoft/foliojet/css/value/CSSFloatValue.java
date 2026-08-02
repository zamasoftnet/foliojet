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

	FOOTNOTE_VALUE(CSSFloatValue.FOOTNOTE),

	PAGE_TOP_VALUE(CSSFloatValue.PAGE_TOP),

	PAGE_BOTTOM_VALUE(CSSFloatValue.PAGE_BOTTOM);
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

	/**
	 * ページフロート(GCPM/Prince系の{@code float: top})です
	 * (2026-08-02——PLAN §2の1位)。版面の上端へ寄せる。
	 */
	public static final byte PAGE_TOP = 6;

	/**
	 * ページフロート({@code float: bottom})です。版面の下端
	 * (脚注があればその上)へ寄せる。
	 */
	public static final byte PAGE_BOTTOM = 7;

	/** ページ単位で配置するフロート(脚注・ページフロート)か。 */
	public static boolean isPageLevel(final byte floating) {
		return floating == FOOTNOTE || floating == PAGE_TOP || floating == PAGE_BOTTOM;
	}

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