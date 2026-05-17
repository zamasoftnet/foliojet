package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.DirectionValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.css3.BlockFlow;
import jp.cssj.homare.style.box.params.AbstractTextParams;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Direction.java 1552 2018-04-26 01:43:24Z miyabe $
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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("ltr")) {
				return DirectionValue.LTR_VALUE;
			} else if (ident.equals("rtl")) {
				return DirectionValue.RTL_VALUE;
			}
		default:
			throw new PropertyException();
		}
	}

}
