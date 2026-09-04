package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class Direction extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Direction();

	public static FontStyle.Direction getFontDirection(CSSStyle style) {
		if (TypesettingMode.isHorizontal(BlockFlow.get(style), WritingModeVariant.get(style))) {
			// 横組版(horizontal-tb と sideways-*)
			switch (Direction.get(style)) {
			case AbstractTextParams.DIRECTION_LTR:
				return FontStyle.Direction.LTR;
			case AbstractTextParams.DIRECTION_RTL:
				return FontStyle.Direction.RTL;
			default:
				throw new IllegalStateException();
			}
		}
		// 通常の vertical-* だけが縦組版。
		return FontStyle.Direction.TB;
	}

	public static byte get(CSSStyle style) {
		DirectionValue value = (DirectionValue) style.get(INFO);
		return value.getDirection();
	}

	private Direction() {
		super("direction");
	}

	public Value getDefault(CSSStyle style) {
		return DirectionValue.LTR_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("ltr")) {
				return DirectionValue.LTR_VALUE;
			} else if (ident.equals("rtl")) {
				return DirectionValue.RTL_VALUE;
			}
		}
		throw new PropertyException();
	}

}
