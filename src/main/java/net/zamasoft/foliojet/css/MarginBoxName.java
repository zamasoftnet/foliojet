package net.zamasoft.foliojet.css;

/**
 * ページマージンボックスの名前です(css-page-3 §7.1 の16ボックス)。
 *
 * @author MIYABE Tatsuhiko
 */
public enum MarginBoxName {
	TOP_LEFT_CORNER("top-left-corner"),

	TOP_LEFT("top-left"),

	TOP_CENTER("top-center"),

	TOP_RIGHT("top-right"),

	TOP_RIGHT_CORNER("top-right-corner"),

	BOTTOM_LEFT_CORNER("bottom-left-corner"),

	BOTTOM_LEFT("bottom-left"),

	BOTTOM_CENTER("bottom-center"),

	BOTTOM_RIGHT("bottom-right"),

	BOTTOM_RIGHT_CORNER("bottom-right-corner"),

	LEFT_TOP("left-top"),

	LEFT_MIDDLE("left-middle"),

	LEFT_BOTTOM("left-bottom"),

	RIGHT_TOP("right-top"),

	RIGHT_MIDDLE("right-middle"),

	RIGHT_BOTTOM("right-bottom");

	private final String symbol;

	private MarginBoxName(String symbol) {
		this.symbol = symbol;
	}

	/**
	 * at-rule の記号(先頭の @ を除く小文字名)を返します。
	 */
	public String getSymbol() {
		return this.symbol;
	}

	/**
	 * at-rule の記号(@top-center 等。@ はあってもなくてもよい)から
	 * ボックス名を返します。未知の記号なら null を返します。
	 */
	public static MarginBoxName fromSymbol(String symbol) {
		String name = symbol.startsWith("@") ? symbol.substring(1) : symbol;
		for (MarginBoxName box : values()) {
			if (box.symbol.equalsIgnoreCase(name)) {
				return box;
			}
		}
		return null;
	}

	public String toString() {
		return this.symbol;
	}
}
