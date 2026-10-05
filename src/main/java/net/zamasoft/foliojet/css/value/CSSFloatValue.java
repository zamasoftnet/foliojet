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

	PAGE_BOTTOM_VALUE(CSSFloatValue.PAGE_BOTTOM),

	PAGE_NOTE_START_VALUE(CSSFloatValue.PAGE_NOTE_START),

	PAGE_NOTE_END_VALUE(CSSFloatValue.PAGE_NOTE_END),

	PAGE_BLOCK_START_VALUE(CSSFloatValue.PAGE_BLOCK_START),

	PAGE_BLOCK_END_VALUE(CSSFloatValue.PAGE_BLOCK_END);
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
	 * (2026-08-02——PLAN §2の1位)。版面の上端へ寄せる。上下は物理の向きで、縦組みでも用紙の上
	 * (2026-10-05、css-page-floats の「書字方向に応じて block-start か inline-start」)。
	 */
	public static final byte PAGE_TOP = 6;

	/**
	 * ページフロート({@code float: bottom})です。版面の下端
	 * (脚注があればその上)へ寄せる。縦組みでも用紙の下で、行の末尾側に置いて行を短くする
	 * (2026-10-05。それまでは縦組みでブロックの末尾=左端に置いていた。その置き方は {@link #PAGE_BLOCK_END})。
	 */
	public static final byte PAGE_BOTTOM = 7;

	/** 版面の論理行頭側に置く並列注（横組=左、縦組=上）。 */
	public static final byte PAGE_NOTE_START = 8;

	/** 版面の論理行末側に置く並列注（横組=右、縦組=下）。 */
	public static final byte PAGE_NOTE_END = 9;

	/** 版面のブロックの先頭へ寄せるページフロート({@code float: block-start})。横組では top と同じ。 */
	public static final byte PAGE_BLOCK_START = 10;

	/** 版面のブロックの末尾へ寄せるページフロート({@code float: block-end})。横組では bottom と同じ。 */
	public static final byte PAGE_BLOCK_END = 11;

	/** ページ単位で配置するフロート(脚注・ページフロート)か。 */
	public static boolean isPageLevel(final byte floating) {
		return floating == FOOTNOTE || isPageFloat(floating) || floating == PAGE_NOTE_START
				|| floating == PAGE_NOTE_END;
	}

	/** 版面の端へ寄せるページフロート(top・bottom・block-start・block-end)か。 */
	public static boolean isPageFloat(final byte floating) {
		return floating == PAGE_TOP || floating == PAGE_BOTTOM || floating == PAGE_BLOCK_START
				|| floating == PAGE_BLOCK_END;
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

		case PAGE_TOP:
			return "top";

		case PAGE_BOTTOM:
			return "bottom";

		case PAGE_NOTE_START:
			return "-cssj-note-start";

		case PAGE_NOTE_END:
			return "-cssj-note-end";

		case PAGE_BLOCK_START:
			return "block-start";

		case PAGE_BLOCK_END:
			return "block-end";

		default:
			throw new IllegalStateException();
		}
	}
}
