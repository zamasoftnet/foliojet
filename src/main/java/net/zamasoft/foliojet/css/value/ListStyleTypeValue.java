package net.zamasoft.foliojet.css.value;

/**
 * @author MIYABE Tatsuhiko
 */
public enum ListStyleTypeValue implements ListStyleTypeSource {
	NONE_VALUE(ListStyleTypeValue.NONE, "none"),

	DISC_VALUE(ListStyleTypeValue.DISC, "disc"),

	CIRCLE_VALUE(ListStyleTypeValue.CIRCLE, "circle"),

	SQUARE_VALUE(ListStyleTypeValue.SQUARE, "square"),

	DECIMAL_VALUE(ListStyleTypeValue.DECIMAL, "decimal"),

	DECIMAL_LEADING_ZERO_VALUE(ListStyleTypeValue.DECIMAL_LEADING_ZERO, "decimal-leading-zero"),

	LOWER_ROMAN_VALUE(ListStyleTypeValue.LOWER_ROMAN, "lower-roman"),

	UPPER_ROMAN_VALUE(ListStyleTypeValue.UPPER_ROMAN, "upper-roman"),

	LOWER_GREEK_VALUE(ListStyleTypeValue.LOWER_GREEK, "lower-greek"),

	LOWER_ALPHA_VALUE(ListStyleTypeValue.LOWER_ALPHA, "lower-alpha"),

	LOWER_LATIN_VALUE(ListStyleTypeValue.LOWER_LATIN, "lower-latin"),

	UPPER_ALPHA_VALUE(ListStyleTypeValue.UPPER_ALPHA, "upper-alpha"),

	UPPER_LATIN_VALUE(ListStyleTypeValue.UPPER_LATIN, "upper-latin"),

	HEBREW_VALUE(ListStyleTypeValue.HEBREW, "hebrew"),

	ARMENIAN_VALUE(ListStyleTypeValue.ARMENIAN, "armenian"),

	GEORGIAN_VALUE(ListStyleTypeValue.GEORGIAN, "georgian"),

	CJK_IDEOGRAPHIC_VALUE(ListStyleTypeValue.CJK_IDEOGRAPHIC, "cjk-ideographic"),

	HIRAGANA_VALUE(ListStyleTypeValue.HIRAGANA, "hiragana"),

	KATAKANA_VALUE(ListStyleTypeValue.KATAKANA, "katakana"),

	HIRAGANA_IROHA_VALUE(ListStyleTypeValue.HIRAGANA_IROHA, "hiragana-iroha"),

	KATAKANA_IROHA_VALUE(ListStyleTypeValue.KATAKANA_IROHA, "katakana-iroha"),

	_CSSJ_FULL_WIDTH_DECIMAL_VALUE(ListStyleTypeValue._CSSJ_FULL_WIDTH_DECIMAL, "-cssj-full-width-decimal", "-cssj-decimal-full-width"),

	_CSSJ_CJK_DECIMAL_VALUE(ListStyleTypeValue._CSSJ_CJK_DECIMAL, "cjk-decimal", "-cssj-cjk-decimal");

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

	/**
	 * 著者定義カウンタスタイル({@code @counter-style})に割り当てる
	 * コードの先頭です(2026-08-02)。これ以上のコードは
	 * {@code CounterStyles}(文書ごとの登録簿)が名前ごとに配る。
	 * 組み込みの追加余地を空けてある。
	 */
	public static final short FIRST_CUSTOM = 1000;

	private final short listStyleType;

	/** CSS の名前と別名。先頭が {@link #toString} の名前。 */
	private final String[] names;

	private ListStyleTypeValue(final short listStyleType, final String... names) {
		this.listStyleType = listStyleType;
		this.names = names;
	}

	public short getListStyleType() {
		return this.listStyleType;
	}

	/** CSS の名前です(解析の {@link #forName} と同じ表から。2026-10-04 まで別の switch で、upper-latin が抜けて例外になっていた)。 */
	public String toString() {
		return this.names[0];
	}

	/** CSS の名前(小文字)か別名に当たる値です。無ければ null。 */
	public static ListStyleTypeValue forName(final String name) {
		return ByName.MAP.get(name);
	}

	private static final class ByName {
		static final java.util.Map<String, ListStyleTypeValue> MAP = new java.util.HashMap<>();
		static {
			for (final ListStyleTypeValue value : values()) {
				for (final String name : value.names) {
					MAP.put(name, value);
				}
			}
		}
	}
}
