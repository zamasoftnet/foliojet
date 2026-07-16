package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 */
public enum TextTransformValue implements Value {
	NONE_VALUE(AbstractTextParams.TEXT_TRANSFORM_NONE),

	CAPITALIZE_VALUE(
			AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE),

	UPPERCASE_VALUE(
			AbstractTextParams.TEXT_TRANSFORM_UPPERCASE),

	LOWERCASE_VALUE(
			AbstractTextParams.TEXT_TRANSFORM_LOWERCASE);

	private final byte textTransform;

	private TextTransformValue(byte textTransform) {
		this.textTransform = textTransform;
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