package net.zamasoft.foliojet.css;

/**
 * Page margin box names (the 16 boxes in css-page-3 §7.1).
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
	 * Returns the box name for an at-rule symbol (@top-center, etc.;
	 * the @ is optional). Returns null for an unknown symbol.
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
