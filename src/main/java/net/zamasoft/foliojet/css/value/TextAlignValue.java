package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum TextAlignValue implements Value {
	LEFT_VALUE(TextAlignValue.LEFT),

	RIGHT_VALUE(TextAlignValue.RIGHT),

	CENTER_VALUE(TextAlignValue.CENTER),

	JUSTIFY_VALUE(TextAlignValue.JUSTIFY),

	START_VALUE(TextAlignValue.START),

	END_VALUE(TextAlignValue.END),

	MATCH_PARENT_VALUE(TextAlignValue.MATCH_PARENT),

	X_JUSTIFY_CENTER_VALUE(TextAlignValue.X_JUSTIFY_CENTER),

	/**
	 * Internal: centered even when the content is wider than the line, overhanging both sides (2026-10-09). The
	 * emphasis marks centered over each character use it; any other line too long for its box is start-aligned.
	 * Not accepted from style sheets.
	 */
	X_CENTER_OVERHANG_VALUE(TextAlignValue.X_CENTER_OVERHANG);
	public static final byte LEFT = 1;

	public static final byte RIGHT = 2;

	public static final byte CENTER = 3;

	public static final byte JUSTIFY = 4;

	public static final byte START = 5;

	public static final byte END = 6;

	public static final byte MATCH_PARENT = 7;

	public static final byte X_JUSTIFY_CENTER = 101;

	public static final byte X_CENTER_OVERHANG = 102;

	private final byte textAlign;

	private TextAlignValue(byte textAlign) {
		this.textAlign = textAlign;
	}

	public byte getTextAlign() {
		return this.textAlign;
	}

	public String toString() {
		switch (this.textAlign) {
		case LEFT:
			return "left";

		case CENTER:
			return "center";

		case RIGHT:
			return "right";

		case JUSTIFY:
			return "justify";

		case START:
			return "start";

		case END:
			return "end";

		case MATCH_PARENT:
			return "match-parent";

		case X_JUSTIFY_CENTER:
			return "-cssj-justify-center";

		case X_CENTER_OVERHANG:
			return "-cssj-center-overhang";

		default:
			throw new IllegalStateException();
		}
	}
}
