package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum ListStyleTypeValue implements Value {
	NONE_VALUE(ListStyleTypeValue.NONE),

	DISC_VALUE(ListStyleTypeValue.DISC),

	CIRCLE_VALUE(ListStyleTypeValue.CIRCLE),

	SQUARE_VALUE(ListStyleTypeValue.SQUARE),

	DECIMAL_VALUE(ListStyleTypeValue.DECIMAL),

	DECIMAL_LEADING_ZERO_VALUE(ListStyleTypeValue.DECIMAL_LEADING_ZERO),

	LOWER_ROMAN_VALUE(ListStyleTypeValue.LOWER_ROMAN),

	UPPER_ROMAN_VALUE(ListStyleTypeValue.UPPER_ROMAN),

	LOWER_GREEK_VALUE(ListStyleTypeValue.LOWER_GREEK),

	LOWER_ALPHA_VALUE(ListStyleTypeValue.LOWER_ALPHA),

	LOWER_LATIN_VALUE(ListStyleTypeValue.LOWER_LATIN),

	UPPER_ALPHA_VALUE(ListStyleTypeValue.UPPER_ALPHA),

	UPPER_LATIN_VALUE(ListStyleTypeValue.UPPER_LATIN),

	HEBREW_VALUE(ListStyleTypeValue.HEBREW),

	ARMENIAN_VALUE(ListStyleTypeValue.ARMENIAN),

	GEORGIAN_VALUE(ListStyleTypeValue.GEORGIAN),

	CJK_IDEOGRAPHIC_VALUE(ListStyleTypeValue.CJK_IDEOGRAPHIC),

	HIRAGANA_VALUE(ListStyleTypeValue.HIRAGANA),

	KATAKANA_VALUE(ListStyleTypeValue.KATAKANA),

	HIRAGANA_IROHA_VALUE(ListStyleTypeValue.HIRAGANA_IROHA),

	KATAKANA_IROHA_VALUE(ListStyleTypeValue.KATAKANA_IROHA),

	_CSSJ_FULL_WIDTH_DECIMAL_VALUE(
			ListStyleTypeValue._CSSJ_FULL_WIDTH_DECIMAL),

	_CSSJ_CJK_DECIMAL_VALUE(ListStyleTypeValue._CSSJ_CJK_DECIMAL);
	public static final short NONE = 0;

	public static final short DISC = 1;

	public static final short CIRCLE = 2;

	public static final short SQUARE = 3;

	public static final short DECIMAL = 4;

	public static final short DECIMAL_LEADING_ZERO = 5;

	public static final short LOWER_ROMAN = 6;

	public static final short UPPER_ROMAN = 7;

	public static final short LOWER_GREEK = 8;

	public static final short LOWER_ALPHA = 9;

	public static final short LOWER_LATIN = 10;

	public static final short UPPER_ALPHA = 11;

	public static final short UPPER_LATIN = 12;

	public static final short HEBREW = 13;

	public static final short ARMENIAN = 14;

	public static final short GEORGIAN = 15;

	public static final short CJK_IDEOGRAPHIC = 16;

	public static final short HIRAGANA = 17;

	public static final short KATAKANA = 18;

	public static final short HIRAGANA_IROHA = 19;

	public static final short KATAKANA_IROHA = 20;

	public static final short _CSSJ_FULL_WIDTH_DECIMAL = 21;

	public static final short _CSSJ_CJK_DECIMAL = 22;

	private final short listStyleType;

	private ListStyleTypeValue(short listStyleType) {
		this.listStyleType = listStyleType;
	}

	public short getListStyleType() {
		return this.listStyleType;
	}

	public String toString() {
		switch (this.listStyleType) {
		case DISC:
			return "disc";

		case CIRCLE:
			return "circle";

		case SQUARE:
			return "square";

		case DECIMAL:
			return "decimal";

		case DECIMAL_LEADING_ZERO:
			return "decimal-leading-zero";

		case LOWER_ROMAN:
			return "lower-roman";

		case UPPER_ROMAN:
			return "upper-roman";

		case LOWER_GREEK:
			return "lower-greek";

		case LOWER_ALPHA:
			return "lower-alpha";

		case LOWER_LATIN:
			return "lower-latin";

		case UPPER_ALPHA:
			return "upper-alpha";

		case HEBREW:
			return "hebrew";

		case ARMENIAN:
			return "armenian";

		case GEORGIAN:
			return "georgian";

		case CJK_IDEOGRAPHIC:
			return "cjk-ideographic";

		case HIRAGANA:
			return "hiragana";

		case KATAKANA:
			return "katakana";

		case HIRAGANA_IROHA:
			return "hiragana-iroha";

		case KATAKANA_IROHA:
			return "katakana-iroha";

		case _CSSJ_FULL_WIDTH_DECIMAL:
			return "-cssj-full-width-decimal";

		case _CSSJ_CJK_DECIMAL:
			return "-cssj-cjk-decimal";

		case NONE:
			return "none";

		default:
			throw new IllegalStateException();
		}
	}
}