package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.css3.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: MarginLeft.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class MarginLeft extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MarginLeft();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo info;
		// 回転
		switch (CSSJDirectionMode.get(style)) {
		case CSSJDirectionModeValue.PHYSICAL:
			info = INFO;
			break;
		case CSSJDirectionModeValue.HORIZONTAL_TB:
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_RL:
				info = MarginBottom.INFO;
				break;
			case AbstractTextParams.FLOW_LR:
				info = MarginTop.INFO;
				break;
			default:
				info = INFO;
				break;
			}
			break;
		case CSSJDirectionModeValue.VERTICAL_RL:
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_TB:
				info = MarginTop.INFO;
				break;
			default:
				info = INFO;
				break;
			}
			break;
		default:
			throw new IllegalStateException();
		}
		return style.get(info);
	}

	protected MarginLeft() {
		super("margin-left");
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = BoxValueUtils.toMarginWidth(ua, lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

	public int getPriority() {
		return 1;
	}
}