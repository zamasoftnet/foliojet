package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSFloatValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSFloatValue implements Value {
	public static final byte NONE = 0;

	public static final byte LEFT = 1;

	public static final byte RIGHT = 2;

	public static final byte START = 3;

	public static final byte END = 4;

	public static final CSSFloatValue NONE_VALUE = new CSSFloatValue(NONE);

	public static final CSSFloatValue LEFT_VALUE = new CSSFloatValue(LEFT);

	public static final CSSFloatValue RIGHT_VALUE = new CSSFloatValue(RIGHT);

	public static final CSSFloatValue START_VALUE = new CSSFloatValue(START);

	public static final CSSFloatValue END_VALUE = new CSSFloatValue(END);

	private final byte floating;

	private CSSFloatValue(byte floating) {
		this.floating = floating;
	}

	public short getValueType() {
		return Value.TYPE_FLOAT;
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

		default:
			throw new IllegalStateException();
		}
	}
}