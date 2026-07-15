package net.zamasoft.foliojet.css.value;

import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;

/**
 * @author MIYABE Tatsuhiko
 */
public enum FontStyleValue implements Value {
	NORMAL_VALUE(Style.NORMAL),

	ITALIC_VALUE(Style.ITALIC),

	OBLIQUE_VALUE(Style.OBLIQUE);

	private final Style fontStyle;

	private FontStyleValue(Style fontStyle) {
		this.fontStyle = fontStyle;
	}

	/**
	 * スタイルコードを返します。
	 * 
	 * @return
	 */
	public Style getFontStyle() {
		return this.fontStyle;
	}

	public String toString() {
		switch (this.fontStyle) {
		case NORMAL:
			return "normal";

		case ITALIC:
			return "italic";

		case OBLIQUE:
			return "oblique";

		default:
			throw new IllegalStateException();
		}
	}
}
