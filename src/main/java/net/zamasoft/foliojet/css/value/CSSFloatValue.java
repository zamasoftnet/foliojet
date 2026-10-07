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
	 * Footnote float (GCPM/Prince family) (F0, 2026-07-31;
	 * design: consult-codex-2026-07-31-footnote.txt). Drawn in normal flow
	 * until layout is wired in F3.
	 */
	public static final byte FOOTNOTE = 5;

	/**
	 * Page float (GCPM/Prince {@code float: top})
	 * (2026-08-02; first in PLAN §2). Aligns to the top of the type area. Top/bottom are
	 * physical directions, so this is the sheet top even in vertical writing
	 * (2026-10-05, css-page-floats: block-start or inline-start depending on writing direction).
	 */
	public static final byte PAGE_TOP = 6;

	/**
	 * Page float ({@code float: bottom}). Aligns to the bottom of the type area
	 * (above footnotes if present). Uses the sheet bottom even in vertical writing,
	 * placing at line end and shortening lines (2026-10-05; previously vertical writing
	 * used block-end=left edge. That placement is {@link #PAGE_BLOCK_END}).
	 */
	public static final byte PAGE_BOTTOM = 7;

	/** Parallel note at the type area's logical inline-start (left in horizontal writing, top in vertical writing). */
	public static final byte PAGE_NOTE_START = 8;

	/** Parallel note at the type area's logical inline-end (right in horizontal writing, bottom in vertical writing). */
	public static final byte PAGE_NOTE_END = 9;

	/** Page float aligned to type-area block-start ({@code float: block-start}). Same as top in horizontal writing. */
	public static final byte PAGE_BLOCK_START = 10;

	/** Page float aligned to type-area block-end ({@code float: block-end}). Same as bottom in horizontal writing. */
	public static final byte PAGE_BLOCK_END = 11;

	/** Whether this is a float placed per page (footnote/page float). */
	public static boolean isPageLevel(final byte floating) {
		return floating == FOOTNOTE || isPageFloat(floating) || floating == PAGE_NOTE_START
				|| floating == PAGE_NOTE_END;
	}

	/** Whether this is a page float aligned to a type-area edge (top/bottom/block-start/block-end). */
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
