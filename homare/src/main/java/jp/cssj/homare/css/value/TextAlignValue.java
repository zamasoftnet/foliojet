package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextAlignValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextAlignValue implements Value {
	public static final byte LEFT = 1;

	public static final byte RIGHT = 2;

	public static final byte CENTER = 3;

	public static final byte JUSTIFY = 4;

	public static final byte START = 5;

	public static final byte END = 6;

	public static final byte X_JUSTIFY_CENTER = 101;

	public static final TextAlignValue LEFT_VALUE = new TextAlignValue(LEFT);

	public static final TextAlignValue RIGHT_VALUE = new TextAlignValue(RIGHT);

	public static final TextAlignValue CENTER_VALUE = new TextAlignValue(CENTER);

	public static final TextAlignValue JUSTIFY_VALUE = new TextAlignValue(JUSTIFY);
	public static final TextAlignValue START_VALUE = new TextAlignValue(START);

	public static final TextAlignValue END_VALUE = new TextAlignValue(END);

	public static final TextAlignValue X_JUSTIFY_CENTER_VALUE = new TextAlignValue(X_JUSTIFY_CENTER);

	private final byte textAlign;

	private TextAlignValue(byte textAlign) {
		this.textAlign = textAlign;
	}

	public short getValueType() {
		return TYPE_TEXT_ALIGN;
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

		case X_JUSTIFY_CENTER:
			return "-cssj-justify-center";

		default:
			throw new IllegalStateException();
		}
	}
}