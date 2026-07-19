package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * @author MIYABE Tatsuhiko
 */
public final class TextValueUtils {
	private TextValueUtils() {
		// unused
	}

	/**
	 * &lt;text-align&gt; を値に変換します。
	 * 
	 * @param ident
	 * @return
	 */
	public static TextAlignValue toTextAlign(String ident) {
		if (ident.equals("left")) {
			return TextAlignValue.LEFT_VALUE;
		} else if (ident.equals("right")) {
			return TextAlignValue.RIGHT_VALUE;
		} else if (ident.equals("center")) {
			return TextAlignValue.CENTER_VALUE;
		} else if (ident.equals("justify")) {
			return TextAlignValue.JUSTIFY_VALUE;
		} else if (ident.equals("start")) {
			return TextAlignValue.START_VALUE;
		} else if (ident.equals("end")) {
			return TextAlignValue.END_VALUE;
		} else if (ident.equals("-cssj-justify-center")) {
			return TextAlignValue.X_JUSTIFY_CENTER_VALUE;
		}
		return null;
	}

	public static byte toTextAlignParam(TextAlignValue value, CSSStyle style) {
		byte textAlign = value.getTextAlign();
		switch (textAlign) {
		case TextAlignValue.LEFT:
			if (Direction.get(style) == AbstractTextParams.DIRECTION_RTL) {
				return AbstractLineParams.TEXT_ALIGN_END;
			}
			return AbstractLineParams.TEXT_ALIGN_START;
		case TextAlignValue.RIGHT:
			if (Direction.get(style) == AbstractTextParams.DIRECTION_RTL) {
				return AbstractLineParams.TEXT_ALIGN_START;
			}
			return AbstractLineParams.TEXT_ALIGN_END;
		case TextAlignValue.CENTER:
			return AbstractLineParams.TEXT_ALIGN_CENTER;
		case TextAlignValue.JUSTIFY:
			return AbstractLineParams.TEXT_ALIGN_JUSTIFY;
		case TextAlignValue.START:
			return AbstractLineParams.TEXT_ALIGN_START;
		case TextAlignValue.END:
			return AbstractLineParams.TEXT_ALIGN_END;
		case TextAlignValue.X_JUSTIFY_CENTER:
			return AbstractLineParams.TEXT_ALIGN_X_JUSTIFY_CENTER;
		default:
			throw new IllegalStateException();
		}
	}
}
