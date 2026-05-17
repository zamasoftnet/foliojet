package jp.cssj.homare.css.value;

import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: FontStyleValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FontStyleValue implements Value {
	public static final FontStyleValue NORMAL_VALUE = new FontStyleValue(Style.NORMAL);

	public static final FontStyleValue ITALIC_VALUE = new FontStyleValue(Style.ITALIC);

	public static final FontStyleValue OBLIQUE_VALUE = new FontStyleValue(Style.OBLIQUE);

	private final Style fontStyle;

	private FontStyleValue(Style fontStyle) {
		this.fontStyle = fontStyle;
	}

	public short getValueType() {
		return TYPE_FONT_STYLE;
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
