package jp.cssj.homare.css.value;

import jp.cssj.homare.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextDecorationValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextDecorationValue implements Value {
	public static final TextDecorationValue NONE_DECORATION = new TextDecorationValue((byte) 0);

	public static final byte UNDERLINE = AbstractTextParams.DECORATION_UNDERLINE;

	public static final byte OVERLINE = AbstractTextParams.DECORATION_OVERLINE;

	public static final byte LINE_THROUGH = AbstractTextParams.DECORATION_LINE_THROUGH;

	public static final byte BLINK = 0x08;

	private final byte flags;

	private static final TextDecorationValue[] VALUES = new TextDecorationValue[16];

	static {
		for (byte i = 0; i < VALUES.length; ++i) {
			VALUES[i] = new TextDecorationValue(i);
		}
	}

	public static TextDecorationValue create(byte flags) {
		return VALUES[flags];
	}

	private TextDecorationValue(byte flags) {
		this.flags = flags;
	}

	public short getValueType() {
		return TYPE_TEXT_DECORATION;
	}

	public byte getFlags() {
		return this.flags;
	}

	public String toString() {
		if (this.flags == 0) {
			return "none";
		}
		StringBuffer buff = new StringBuffer();
		if ((this.flags & UNDERLINE) != 0) {
			buff.append("underline ");
		}
		if ((this.flags & OVERLINE) != 0) {
			buff.append("overline ");
		}
		if ((this.flags & LINE_THROUGH) != 0) {
			buff.append("line-through ");
		}
		if ((this.flags & BLINK) != 0) {
			buff.append("blink ");
		}
		buff.deleteCharAt(buff.length() - 1);
		return buff.toString();
	}
}