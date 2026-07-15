package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BorderRadiusValue;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.RectBorder.Radius;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderTopRightRadius.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderTopRightRadius extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderTopRightRadius();

	public static Radius get(CSSStyle style) {
		final PrimitivePropertyInfo info;
		// 回転
		switch (CSSJDirectionMode.get(style)) {
		case CSSJDirectionModeValue.PHYSICAL:
			info = INFO;
			break;
		case CSSJDirectionModeValue.HORIZONTAL_TB:
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_RL:
				info = BorderTopLeftRadius.INFO;
				break;
			case AbstractTextParams.FLOW_LR:
				info = BorderTopRightRadius.INFO;
				break;
			default:
				info = INFO;
				break;
			}
			break;
		case CSSJDirectionModeValue.VERTICAL_RL:
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_TB:
				info = BorderBottomRightRadius.INFO;
				break;
			default:
				info = INFO;
				break;
			}
			break;
		default:
			throw new IllegalStateException();
		}
		final BorderRadiusValue r = (BorderRadiusValue) style.get(info);
		return Radius.create(((AbsoluteLengthValue) r.hr).getLength(), ((AbsoluteLengthValue) r.vr).getLength());
	}

	protected BorderTopRightRadius() {
		super("border-top-right-radius");
	}

	public Value getDefault(CSSStyle style) {
		return BorderRadiusValue.ZERO_RADIUS;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final BorderRadiusValue r = (BorderRadiusValue) value;
		final AbsoluteLengthValue hr = (AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(r.hr, style);
		final AbsoluteLengthValue vr = (AbsoluteLengthValue) ValueUtils.emExToAbsoluteLength(r.vr, style);
		return BorderRadiusValue.create(hr, vr);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final BorderRadiusValue value = BorderValueUtils.toBorderRadius(ua, tokens);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}