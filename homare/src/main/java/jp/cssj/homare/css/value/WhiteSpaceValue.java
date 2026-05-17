package jp.cssj.homare.css.value;

import jp.cssj.homare.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WhiteSpaceValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class WhiteSpaceValue implements Value {
	public static final WhiteSpaceValue NORMAL_VALUE = new WhiteSpaceValue(AbstractTextParams.WHITE_SPACE_NORMAL);

	public static final WhiteSpaceValue PRE_VALUE = new WhiteSpaceValue(AbstractTextParams.WHITE_SPACE_PRE);

	public static final WhiteSpaceValue NOWRAP_VALUE = new WhiteSpaceValue(AbstractTextParams.WHITE_SPACE_NOWRAP);

	public static final WhiteSpaceValue PRE_WRAP_VALUE = new WhiteSpaceValue(AbstractTextParams.WHITE_SPACE_PRE_WRAP);

	public static final WhiteSpaceValue PRE_LINE_VALUE = new WhiteSpaceValue(AbstractTextParams.WHITE_SPACE_PRE_LINE);

	private final byte whiteSpace;

	private WhiteSpaceValue(byte whiteSpace) {
		this.whiteSpace = whiteSpace;
	}

	public short getValueType() {
		return TYPE_WHITE_SPACE;
	}

	public byte getWhiteSpace() {
		return this.whiteSpace;
	}

	public String toString() {
		switch (this.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_NORMAL:
			return "normal";

		case AbstractTextParams.WHITE_SPACE_PRE:
			return "pre";

		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			return "nowrap";

		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			return "pre-wrap";

		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			return "pre-line";

		default:
			throw new IllegalStateException();
		}
	}
}