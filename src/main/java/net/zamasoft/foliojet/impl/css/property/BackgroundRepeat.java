package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.css3.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.BackgroundImage;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundRepeat.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundRepeat extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BackgroundRepeat();

	public static byte get(CSSStyle style) {
		byte repeat = ((BackgroundRepeatValue) style.get(INFO)).getBackgroundRepeat();
		switch (CSSJDirectionMode.get(style)) {
		case CSSJDirectionModeValue.HORIZONTAL_TB:
			// 縦書き
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_RL:
			case AbstractTextParams.FLOW_LR:
				switch (repeat) {
				case BackgroundImage.REPEAT_X:
					repeat = BackgroundImage.REPEAT_Y;
					break;
				case BackgroundImage.REPEAT_Y:
					repeat = BackgroundImage.REPEAT_X;
					break;
				}
				break;
			default:
				break;
			}
		case CSSJDirectionModeValue.VERTICAL_RL:
			// 縦書き
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_TB:
				switch (repeat) {
				case BackgroundImage.REPEAT_X:
					repeat = BackgroundImage.REPEAT_Y;
					break;
				case BackgroundImage.REPEAT_Y:
					repeat = BackgroundImage.REPEAT_X;
					break;
				}
				break;
			default:
				break;
			}
		default:
			break;
		}

		return repeat;
	}

	protected BackgroundRepeat() {
		super("background-repeat");
	}

	public Value getDefault(CSSStyle style) {
		return BackgroundRepeatValue.REPEAT_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = ColorValueUtils.toBackgroundRepeat(lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}