package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 */
public enum WhiteSpaceValue implements Value {
	NORMAL_VALUE(AbstractTextParams.WHITE_SPACE_NORMAL),

	PRE_VALUE(AbstractTextParams.WHITE_SPACE_PRE),

	NOWRAP_VALUE(AbstractTextParams.WHITE_SPACE_NOWRAP),

	PRE_WRAP_VALUE(AbstractTextParams.WHITE_SPACE_PRE_WRAP),

	PRE_LINE_VALUE(AbstractTextParams.WHITE_SPACE_PRE_LINE);

	private final byte whiteSpace;

	private WhiteSpaceValue(byte whiteSpace) {
		this.whiteSpace = whiteSpace;
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