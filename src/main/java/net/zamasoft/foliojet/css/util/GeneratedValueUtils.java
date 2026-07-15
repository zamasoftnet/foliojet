package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.value.ListStylePositionValue;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.impl.css.part.CircleImage;
import net.zamasoft.foliojet.impl.css.part.DiscImage;
import net.zamasoft.foliojet.impl.css.part.SquareImage;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Color;

/**
 * @author MIYABE Tatsuhiko
 */
public final class GeneratedValueUtils {
	private GeneratedValueUtils() {
		// unused
	}

	/**
	 * &lt;list-style-type&gt; を値に変換します。
	 * 
	 * @param ident
	 * @return
	 */
	public static ListStyleTypeValue toListStyleType(String ident) {
		ident = ident.toLowerCase();
		if (ident.equals("disc")) {
			return ListStyleTypeValue.DISC_VALUE;
		} else if (ident.equals("circle")) {
			return ListStyleTypeValue.CIRCLE_VALUE;
		} else if (ident.equals("square")) {
			return ListStyleTypeValue.SQUARE_VALUE;
		} else if (ident.equals("decimal")) {
			return ListStyleTypeValue.DECIMAL_VALUE;
		} else if (ident.equals("decimal-leading-zero")) {
			return ListStyleTypeValue.DECIMAL_LEADING_ZERO_VALUE;
		} else if (ident.equals("lower-roman")) {
			return ListStyleTypeValue.LOWER_ROMAN_VALUE;
		} else if (ident.equals("upper-roman")) {
			return ListStyleTypeValue.UPPER_ROMAN_VALUE;
		} else if (ident.equals("lower-greek")) {
			return ListStyleTypeValue.LOWER_GREEK_VALUE;
		} else if (ident.equals("lower-alpha")) {
			return ListStyleTypeValue.LOWER_ALPHA_VALUE;
		} else if (ident.equals("lower-latin")) {
			return ListStyleTypeValue.LOWER_LATIN_VALUE;
		} else if (ident.equals("upper-alpha")) {
			return ListStyleTypeValue.UPPER_ALPHA_VALUE;
		} else if (ident.equals("upper-latin")) {
			return ListStyleTypeValue.UPPER_LATIN_VALUE;
		} else if (ident.equals("hebrew")) {
			return ListStyleTypeValue.DECIMAL_VALUE;
		} else if (ident.equals("armenian")) {
			return ListStyleTypeValue.ARMENIAN_VALUE;
		} else if (ident.equals("georgian")) {
			return ListStyleTypeValue.GEORGIAN_VALUE;
		} else if (ident.equals("cjk-ideographic")) {
			return ListStyleTypeValue.CJK_IDEOGRAPHIC_VALUE;
		} else if (ident.equals("hiragana")) {
			return ListStyleTypeValue.HIRAGANA_VALUE;
		} else if (ident.equals("katakana")) {
			return ListStyleTypeValue.KATAKANA_VALUE;
		} else if (ident.equals("hiragana-iroha")) {
			return ListStyleTypeValue.HIRAGANA_IROHA_VALUE;
		} else if (ident.equals("katakana-iroha")) {
			return ListStyleTypeValue.KATAKANA_IROHA_VALUE;
		} else if (ident.equals("-cssj-full-width-decimal") || ident.equals("-cssj-decimal-full-width")) {
			return ListStyleTypeValue._CSSJ_FULL_WIDTH_DECIMAL_VALUE;
		} else if (ident.equals("-cssj-cjk-decimal")) {
			return ListStyleTypeValue._CSSJ_CJK_DECIMAL_VALUE;
		} else if (ident.equals("none")) {
			return ListStyleTypeValue.NONE_VALUE;
		}
		return null;
	}

	/**
	 * &lt;list-style-position&gt; を値に変換します。
	 * 
	 * @param ident
	 * @return
	 */
	public static ListStylePositionValue toListStylePosition(String ident) {
		ident = ident.toLowerCase();
		if (ident.equals("inside")) {
			return ListStylePositionValue.INSIDE_VALUE;
		} else if (ident.equals("outside")) {
			return ListStylePositionValue.OUTSIDE_VALUE;
		}
		return null;
	}

	public static Image format(short listStyleType, Color color, FontStyle fontStyle) {
		switch (listStyleType) {
		case ListStyleTypeValue.DISC: {
			return new DiscImage(fontStyle, color);
		}

		case ListStyleTypeValue.CIRCLE: {
			return new CircleImage(fontStyle, color);
		}

		case ListStyleTypeValue.SQUARE: {
			return new SquareImage(fontStyle, color);
		}

		case ListStyleTypeValue.DECIMAL:
		case ListStyleTypeValue.DECIMAL_LEADING_ZERO:
		case ListStyleTypeValue.LOWER_ALPHA:
		case ListStyleTypeValue.UPPER_ALPHA:
		case ListStyleTypeValue.LOWER_ROMAN:
		case ListStyleTypeValue.UPPER_ROMAN:
		case ListStyleTypeValue.LOWER_LATIN:
		case ListStyleTypeValue.UPPER_LATIN:
		case ListStyleTypeValue.LOWER_GREEK:
		case ListStyleTypeValue.HEBREW:
		case ListStyleTypeValue.ARMENIAN:
		case ListStyleTypeValue.GEORGIAN:
		case ListStyleTypeValue.CJK_IDEOGRAPHIC:
		case ListStyleTypeValue.HIRAGANA:
		case ListStyleTypeValue.KATAKANA:
		case ListStyleTypeValue.HIRAGANA_IROHA:
		case ListStyleTypeValue.KATAKANA_IROHA:
		case ListStyleTypeValue._CSSJ_FULL_WIDTH_DECIMAL:
		case ListStyleTypeValue._CSSJ_CJK_DECIMAL:
		case ListStyleTypeValue.NONE:
			return null;
		default:
			throw new IllegalArgumentException(String.valueOf(listStyleType));
		}
	}

	/**
	 * カウンタおよび番号付リストをフォーマットします。
	 * 
	 * SPEC CSS2 12.6.2
	 * 
	 * @param number
	 * @param listStyleType
	 * @return
	 */
	public static String format(int number, short listStyleType) {
		switch (listStyleType) {
		case ListStyleTypeValue.NONE:
		case ListStyleTypeValue.DISC:
		case ListStyleTypeValue.CIRCLE:
		case ListStyleTypeValue.SQUARE:
			return null;

		case ListStyleTypeValue.HEBREW:
		case ListStyleTypeValue.ARMENIAN:
		case ListStyleTypeValue.GEORGIAN:

		case ListStyleTypeValue.DECIMAL: {
			return String.valueOf(number);
		}

		case ListStyleTypeValue.DECIMAL_LEADING_ZERO: {
			String str = String.valueOf(number);
			if (str.length() == 1) {
				return "0" + str;
			}
			return str;
		}

		case ListStyleTypeValue.LOWER_ALPHA:
		case ListStyleTypeValue.LOWER_LATIN: {
			return formatByChars(number, LOWER_ALPHA);
		}

		case ListStyleTypeValue.UPPER_ALPHA:
		case ListStyleTypeValue.UPPER_LATIN: {
			return formatByChars(number, UPPER_ALPHA);
		}

		case ListStyleTypeValue.LOWER_ROMAN: {
			return toRoman(number).toLowerCase();
		}

		case ListStyleTypeValue.UPPER_ROMAN: {
			return toRoman(number);
		}

		case ListStyleTypeValue.LOWER_GREEK: {
			return formatByChars(number, LOWER_GREEK);
		}
		case ListStyleTypeValue.CJK_IDEOGRAPHIC: {
			return toKansuji(number);
		}

		case ListStyleTypeValue.HIRAGANA: {
			return formatByChars(number, HIRAGANA);
		}

		case ListStyleTypeValue.KATAKANA: {
			return formatByChars(number, KATAKANA);
		}

		case ListStyleTypeValue.HIRAGANA_IROHA: {
			return formatByChars(number, HIRAGANA_IROHA);
		}

		case ListStyleTypeValue.KATAKANA_IROHA: {
			return formatByChars(number, KATAKANA_IROHA);
		}

		case ListStyleTypeValue._CSSJ_FULL_WIDTH_DECIMAL: {
			char[] ch = String.valueOf(number).toCharArray();
			for (int i = 0; i < ch.length; ++i) {
				ch[i] += 0xFEE0;
			}
			return new String(ch);
		}

		case ListStyleTypeValue._CSSJ_CJK_DECIMAL: {
			char[] ch = String.valueOf(number).toCharArray();
			for (int i = 0; i < ch.length; ++i) {
				switch (ch[i]) {
				case '0':
					ch[i] = '〇';
					break;
				case '1':
					ch[i] = '一';
					break;
				case '2':
					ch[i] = '二';
					break;
				case '3':
					ch[i] = '三';
					break;
				case '4':
					ch[i] = '四';
					break;
				case '5':
					ch[i] = '五';
					break;
				case '6':
					ch[i] = '六';
					break;
				case '7':
					ch[i] = '七';
					break;
				case '8':
					ch[i] = '八';
					break;
				case '9':
					ch[i] = '九';
					break;
				}
			}
			return new String(ch);
		}
		default:
			throw new IllegalArgumentException();
		}
	}

	private static final String LOWER_ALPHA = " abcdefghijklmnopqrstuvwxyz";

	private static final String UPPER_ALPHA = " ABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private static final String LOWER_GREEK = " αβγδεζηθικλμνξοπρστυφχψω";

	private static final String HIRAGANA = "　あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん";

	private static final String KATAKANA = "　アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン";

	private static final String HIRAGANA_IROHA = "　いろはにほへとちりぬるをわかよたれそつねならむうゐのおくやまけふこえてあさきゆめみしゑひもせすん";

	private static final String KATAKANA_IROHA = "　イロハニホヘトチリヌルヲワカヨタレソツネナラムウヰノオクヤマケフコエテアサキユメミシヱヒモセスン";

	private static String formatByChars(int number, String chars) {
		if (number >= 0 && number < chars.length()) {
			return String.valueOf(chars.charAt(number));
		}
		return String.valueOf(chars.charAt(0));
	}

	private static final String[][] ROMAN_TABLE = { { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX" },
			{ "", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC" },
			{ "", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM" }, { "", "M", "MM", "MMM" } };

	/**
	 * ローマ数字。
	 * 
	 * @param number
	 * @return
	 */
	private static String toRoman(int number) {
		if (number > 3999) {
			return "overflow";
		} else if (number < 1) {
			return "underflow";
		}

		String numstr = String.valueOf(number);
		StringBuilder result = new StringBuilder();
		// generation
		for (int i = 0; i < numstr.length(); i++) {
			result.append(ROMAN_TABLE[numstr.length() - i - 1][Integer.parseInt(String.valueOf(numstr.charAt(i)))]);
		}

		return result.toString();
	}

	private static final String[][] KANSUJI_TABLE = { { "", "一", "二", "三", "四", "五", "六", "七", "八", "九" },
			{ "", "十", "二十", "三十", "四十", "五十", "六十", "七十", "八十", "九十" },
			{ "", "百", "二百", "三百", "四百", "五百", "六百", "七百", "八百", "九百" },
			{ "", "千", "二千", "三千", "四千", "五千", "六千", "七千", "八千", "九千" },
			{ "", "一万", "二万", "三万", "四万", "五万", "六万", "七万", "八万", "九万" },
			{ "", "十", "二十", "三十", "四十", "五十", "六十", "七十", "八十", "九十" },
			{ "", "百", "二百", "三百", "四百", "五百", "六百", "七百", "八百", "九百" },
			{ "", "一千", "二千", "三千", "四千", "五千", "六千", "七千", "八千", "九千" }, };

	/**
	 * 漢数字
	 * 
	 * @param number
	 * @return
	 */
	private static String toKansuji(int number) {
		if (number > 99999999) {
			return "overflow";
		} else if (number < 1) {
			return "零";
		}

		String numstr = String.valueOf(number);
		StringBuilder result = new StringBuilder();
		// generation
		for (int i = 0; i < numstr.length(); i++) {
			result.append(KANSUJI_TABLE[numstr.length() - i - 1][Integer.parseInt(String.valueOf(numstr.charAt(i)))]);
		}

		return result.toString();
	}

	/**
	 * カウンタおよび番号付リストの装飾の終了記号を返します。
	 * 
	 * @param listStyleType
	 * @return
	 */
	public static String period(short listStyleType) {
		switch (listStyleType) {
		case ListStyleTypeValue.NONE:
		case ListStyleTypeValue.DISC:
		case ListStyleTypeValue.CIRCLE:
		case ListStyleTypeValue.SQUARE: {
			return null;
		}

		case ListStyleTypeValue.DECIMAL:
		case ListStyleTypeValue.DECIMAL_LEADING_ZERO:
		case ListStyleTypeValue.LOWER_ALPHA:
		case ListStyleTypeValue.UPPER_ALPHA:
		case ListStyleTypeValue.LOWER_ROMAN:
		case ListStyleTypeValue.UPPER_ROMAN:
		case ListStyleTypeValue.LOWER_LATIN:
		case ListStyleTypeValue.UPPER_LATIN:
		case ListStyleTypeValue.LOWER_GREEK:
		case ListStyleTypeValue.HEBREW:
		case ListStyleTypeValue.ARMENIAN:
		case ListStyleTypeValue.GEORGIAN: {
			return ".";
		}

		case ListStyleTypeValue._CSSJ_FULL_WIDTH_DECIMAL: {
			return "．";
		}

		case ListStyleTypeValue._CSSJ_CJK_DECIMAL:
		case ListStyleTypeValue.CJK_IDEOGRAPHIC:
		case ListStyleTypeValue.HIRAGANA:
		case ListStyleTypeValue.KATAKANA:
		case ListStyleTypeValue.HIRAGANA_IROHA:
		case ListStyleTypeValue.KATAKANA_IROHA: {
			return "、";
		}

		default:
			throw new IllegalArgumentException();
		}
	}
}
