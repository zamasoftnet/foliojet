package net.zamasoft.foliojet.impl.css.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.DirectionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.impl.css.property.text.BlockFlow;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
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
		switch (BlockFlow.get(style)) {
		case AbstractTextParams.FLOW_TB:
			// 横書き
			switch (Direction.get(style)) {
			case AbstractTextParams.DIRECTION_LTR:
				return FontStyle.Direction.LTR;
			case AbstractTextParams.DIRECTION_RTL:
				return FontStyle.Direction.RTL;
			default:
				throw new IllegalStateException();
			}
		case AbstractTextParams.FLOW_RL:
		case AbstractTextParams.FLOW_LR:
			// 縦書き
			return FontStyle.Direction.TB;
		default:
			throw new IllegalStateException();
		}
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
