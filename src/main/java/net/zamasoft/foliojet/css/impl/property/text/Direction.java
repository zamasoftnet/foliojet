package net.zamasoft.foliojet.css.impl.property.text;

import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.KeywordPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.pdfg2d.gc.font.FontStyle;

/**
 * @author MIYABE Tatsuhiko
 */
public class Direction extends KeywordPropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Direction();

	public static FontStyle.Direction getFontDirection(CSSStyle style) {
		if (TypesettingMode.isHorizontal(BlockFlow.get(style), WritingModeVariant.get(style))) {
			// Horizontal writing (horizontal-tb and sideways-*).
			switch (Direction.get(style)) {
			case AbstractTextParams.DIRECTION_LTR:
				return FontStyle.Direction.LTR;
			case AbstractTextParams.DIRECTION_RTL:
				return FontStyle.Direction.RTL;
			default:
				throw new IllegalStateException();
			}
		}
		// Only ordinary vertical-* modes use vertical writing.
		return FontStyle.Direction.TB;
	}

	public static byte get(CSSStyle style) {
		DirectionValue value = (DirectionValue) style.get(INFO);
		return value.getDirection();
	}

	private Direction() {
		super("direction", true, DirectionValue.LTR_VALUE, Map.of("ltr", DirectionValue.LTR_VALUE, "rtl",
				DirectionValue.RTL_VALUE));
	}

}
