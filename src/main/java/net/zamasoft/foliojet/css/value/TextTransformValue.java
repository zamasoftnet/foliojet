package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 */
public enum TextTransformValue implements Value {
	NONE_VALUE(AbstractTextParams.TEXT_TRANSFORM_NONE),

	CAPITALIZE_VALUE(AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE),

	UPPERCASE_VALUE(AbstractTextParams.TEXT_TRANSFORM_UPPERCASE),

	LOWERCASE_VALUE(AbstractTextParams.TEXT_TRANSFORM_LOWERCASE),

	/** {@code full-width} と、大文字・小文字の変換との組み合わせ(2026-10-06)。 */
	FULL_WIDTH_VALUE(AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH),

	CAPITALIZE_FULL_WIDTH_VALUE(
			(byte) (AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE | AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH)),

	UPPERCASE_FULL_WIDTH_VALUE(
			(byte) (AbstractTextParams.TEXT_TRANSFORM_UPPERCASE | AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH)),

	LOWERCASE_FULL_WIDTH_VALUE(
			(byte) (AbstractTextParams.TEXT_TRANSFORM_LOWERCASE | AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH));

	private final byte textTransform;

	private TextTransformValue(byte textTransform) {
		this.textTransform = textTransform;
	}

	/** 大文字・小文字の変換と {@code full-width} の印を合わせた値の値です。 */
	public static TextTransformValue of(final byte textTransform) {
		for (final TextTransformValue value : values()) {
			if (value.textTransform == textTransform) {
				return value;
			}
		}
		throw new IllegalArgumentException(String.valueOf(textTransform));
	}

	public byte getTextTransform() {
		return this.textTransform;
	}

	public String toString() {
		final String cased = switch (this.textTransform & AbstractTextParams.TEXT_TRANSFORM_CASE_MASK) {
		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE -> "capitalize";
		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE -> "uppercase";
		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE -> "lowercase";
		default -> null;
		};
		if ((this.textTransform & AbstractTextParams.TEXT_TRANSFORM_FULL_WIDTH) == 0) {
			return cased == null ? "none" : cased;
		}
		return cased == null ? "full-width" : cased + " full-width";
	}
}
