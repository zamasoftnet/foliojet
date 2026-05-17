package jp.cssj.homare.css.value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ListStyleTypeValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ListStyleTypeValue implements Value {
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

	public static final ListStyleTypeValue NONE_VALUE = new ListStyleTypeValue(NONE);

	public static final ListStyleTypeValue DISC_VALUE = new ListStyleTypeValue(DISC);

	public static final ListStyleTypeValue CIRCLE_VALUE = new ListStyleTypeValue(CIRCLE);

	public static final ListStyleTypeValue SQUARE_VALUE = new ListStyleTypeValue(SQUARE);

	public static final ListStyleTypeValue DECIMAL_VALUE = new ListStyleTypeValue(DECIMAL);

	public static final ListStyleTypeValue DECIMAL_LEADING_ZERO_VALUE = new ListStyleTypeValue(DECIMAL_LEADING_ZERO);

	public static final ListStyleTypeValue LOWER_ROMAN_VALUE = new ListStyleTypeValue(LOWER_ROMAN);

	public static final ListStyleTypeValue UPPER_ROMAN_VALUE = new ListStyleTypeValue(UPPER_ROMAN);

	public static final ListStyleTypeValue LOWER_GREEK_VALUE = new ListStyleTypeValue(LOWER_GREEK);

	public static final ListStyleTypeValue LOWER_ALPHA_VALUE = new ListStyleTypeValue(LOWER_ALPHA);

	public static final ListStyleTypeValue LOWER_LATIN_VALUE = new ListStyleTypeValue(LOWER_LATIN);

	public static final ListStyleTypeValue UPPER_ALPHA_VALUE = new ListStyleTypeValue(UPPER_ALPHA);

	public static final ListStyleTypeValue UPPER_LATIN_VALUE = new ListStyleTypeValue(UPPER_LATIN);

	public static final ListStyleTypeValue HEBREW_VALUE = new ListStyleTypeValue(HEBREW);

	public static final ListStyleTypeValue ARMENIAN_VALUE = new ListStyleTypeValue(ARMENIAN);

	public static final ListStyleTypeValue GEORGIAN_VALUE = new ListStyleTypeValue(GEORGIAN);

	public static final ListStyleTypeValue CJK_IDEOGRAPHIC_VALUE = new ListStyleTypeValue(CJK_IDEOGRAPHIC);

	public static final ListStyleTypeValue HIRAGANA_VALUE = new ListStyleTypeValue(HIRAGANA);

	public static final ListStyleTypeValue KATAKANA_VALUE = new ListStyleTypeValue(KATAKANA);

	public static final ListStyleTypeValue HIRAGANA_IROHA_VALUE = new ListStyleTypeValue(HIRAGANA_IROHA);

	public static final ListStyleTypeValue KATAKANA_IROHA_VALUE = new ListStyleTypeValue(KATAKANA_IROHA);

	public static final ListStyleTypeValue _CSSJ_FULL_WIDTH_DECIMAL_VALUE = new ListStyleTypeValue(
			_CSSJ_FULL_WIDTH_DECIMAL);

	public static final ListStyleTypeValue _CSSJ_CJK_DECIMAL_VALUE = new ListStyleTypeValue(_CSSJ_CJK_DECIMAL);

	private final short listStyleType;

	private ListStyleTypeValue(short listStyleType) {
		this.listStyleType = listStyleType;
	}

	public short getValueType() {
		return TYPE_LIST_STYLE_TYPE;
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