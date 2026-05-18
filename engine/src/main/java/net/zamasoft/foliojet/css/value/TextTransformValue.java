package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.style.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextTransformValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TextTransformValue implements Value {
	public static final TextTransformValue NONE_VALUE = new TextTransformValue(AbstractTextParams.TEXT_TRANSFORM_NONE);

	public static final TextTransformValue CAPITALIZE_VALUE = new TextTransformValue(
			AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE);

	public static final TextTransformValue UPPERCASE_VALUE = new TextTransformValue(
			AbstractTextParams.TEXT_TRANSFORM_UPPERCASE);

	public static final TextTransformValue LOWERCASE_VALUE = new TextTransformValue(
			AbstractTextParams.TEXT_TRANSFORM_LOWERCASE);

	private final byte textTransform;

	private TextTransformValue(byte textTransform) {
		this.textTransform = textTransform;
	}

	public short getValueType() {
		return TYPE_TEXT_TRANSFORM;
	}

	public byte getTextTransform() {
		return this.textTransform;
	}

	public String toString() {
		switch (this.textTransform) {
		case AbstractTextParams.TEXT_TRANSFORM_NONE:
			return "none";

		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE:
			return "capitalize";

		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE:
			return "uppercase";

		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE:
			return "lowercase";

		default:
			throw new IllegalStateException();
		}
	}
}