package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.TextAlignValue;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.text.Direction;
import net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant;
import net.zamasoft.foliojet.layout.box.params.AbstractLineParams;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;

/**
 * @author MIYABE Tatsuhiko
 */
public final class TextValueUtils {
	private TextValueUtils() {
		// unused
	}

	/**
	 * Converts &lt;text-align&gt; to a value.
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
		} else if (ident.equals("match-parent")) {
			return TextAlignValue.MATCH_PARENT_VALUE;
		} else if (ident.equals("-cssj-justify-center")) {
			return TextAlignValue.X_JUSTIFY_CENTER_VALUE;
		}
		return null;
	}

	/**
	 * True if layout uses the traditional direction-based start/end swap.
	 * sideways lays out logical coordinates in the same direction as LTR and reverses only
	 * when converting to physical coordinates.
	 */
	public static boolean usesLegacyRtlAlignment(CSSStyle style) {
		return Direction.get(style) == AbstractTextParams.DIRECTION_RTL
				&& !TypesettingMode.usesSidewaysInlineAxis(BlockFlow.get(style), WritingModeVariant.get(style));
	}

	public static byte toTextAlignParam(TextAlignValue value, CSSStyle style) {
		byte textAlign = value.getTextAlign();
		switch (textAlign) {
		case TextAlignValue.LEFT:
			if (usesLegacyRtlAlignment(style)) {
				return AbstractLineParams.TEXT_ALIGN_END;
			}
			return AbstractLineParams.TEXT_ALIGN_START;
		case TextAlignValue.RIGHT:
			if (usesLegacyRtlAlignment(style)) {
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
		case TextAlignValue.X_CENTER_OVERHANG:
			return AbstractLineParams.TEXT_ALIGN_X_CENTER_OVERHANG;
		default:
			throw new IllegalStateException();
		}
	}
}
