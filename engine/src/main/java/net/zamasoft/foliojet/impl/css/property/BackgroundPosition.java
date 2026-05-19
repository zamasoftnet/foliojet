package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractCompositePrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.css3.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.Offset;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.parser.LexicalUnit;

/**
 * <a href="http://www.w3.org/TR/CSS21/colors.html#propdef-background-position">
 * backgropund-position 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundPosition.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BackgroundPosition extends AbstractCompositePrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_X = new BackgroundPosition();

	public static final PrimitivePropertyInfo INFO_Y = new BackgroundPosition();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_X, INFO_Y };

	public static Offset get(CSSStyle style) {
		Value xValue = style.get(INFO_X);
		Value yValue = style.get(INFO_Y);
		switch (CSSJDirectionMode.get(style)) {
		case CSSJDirectionModeValue.HORIZONTAL_TB:
			// 縦書き
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_RL:
				switch (yValue.getValueType()) {
				case Value.TYPE_PERCENTAGE:
					PercentageValue y = (PercentageValue) yValue;
					yValue = PercentageValue.create(100 - y.getPercentage());
					break;
				}
			case AbstractTextParams.FLOW_LR: {
				Value x = xValue;
				xValue = yValue;
				yValue = x;
			}
				break;
			default:
				break;
			}
		case CSSJDirectionModeValue.VERTICAL_RL:
			// 縦書き
			switch (BlockFlow.get(style)) {
			case AbstractTextParams.FLOW_TB:
				switch (yValue.getValueType()) {
				case Value.TYPE_PERCENTAGE:
					PercentageValue x = (PercentageValue) xValue;
					xValue = PercentageValue.create(100 - x.getPercentage());
					break;
				}
				Value x = xValue;
				xValue = yValue;
				yValue = x;
				break;
			default:
				break;
			}
		default:
			break;
		}

		return BoxValueUtils.toOffset(xValue, yValue);
	}

	protected BackgroundPosition() {
		super("background-position");
	}

	public Value getDefault(CSSStyle style) {
		return PercentageValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}

	/**
	 * 計算値はPercentageValueまたはAbsoluteLengthです。
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	protected Entry[] parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			return new Entry[] { new Entry(BackgroundPosition.INFO_X, InheritValue.INHERIT_VALUE),
					new Entry(BackgroundPosition.INFO_Y, InheritValue.INHERIT_VALUE) };
		}
		Value x, y;

		if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
			String kw1 = lu.getStringValue().toLowerCase();
			if (!(kw1.equals("top") || kw1.equals("bottom") || kw1.equals("center") || kw1.equals("left")
					|| kw1.equals("right"))) {
				throw new PropertyException();
			}
			String kw2;
			lu = lu.getNextLexicalUnit();
			if (lu == null || lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
				if (lu == null) {
					kw2 = null;
				} else {
					kw2 = lu.getStringValue().toLowerCase();
					if (!(kw2.equals("top") || kw2.equals("bottom") || kw2.equals("center") || kw2.equals("left")
							|| kw2.equals("right"))) {
						throw new PropertyException();
					}
				}

				if (("top".equals(kw1) && "left".equals(kw2)) || ("left".equals(kw1) && "top".equals(kw2))) {
					x = y = PercentageValue.ZERO;
				} else if (("top".equals(kw1) && kw2 == null) || ("top".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "top".equals(kw2))) {
					x = PercentageValue.HALF;
					y = PercentageValue.ZERO;
				} else if (("right".equals(kw1) && "top".equals(kw2)) || ("top".equals(kw1) && "right".equals(kw2))) {
					x = PercentageValue.FULL;
					y = PercentageValue.ZERO;
				} else if (("left".equals(kw1) && kw2 == null) || ("left".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "left".equals(kw2))) {
					x = PercentageValue.ZERO;
					y = PercentageValue.HALF;
				} else if (("center".equals(kw1) && kw2 == null) || ("center".equals(kw1) && "center".equals(kw2))) {
					x = y = PercentageValue.HALF;
				} else if (("right".equals(kw1) && kw2 == null) || ("right".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "right".equals(kw2))) {
					x = PercentageValue.FULL;
					y = PercentageValue.HALF;
				} else if (("left".equals(kw1) && "bottom".equals(kw2))
						|| ("bottom".equals(kw1) && "left".equals(kw2))) {
					x = PercentageValue.ZERO;
					y = PercentageValue.FULL;
				} else if (("bottom".equals(kw1) && kw2 == null) || ("bottom".equals(kw1) && "center".equals(kw2))
						|| ("center".equals(kw1) && "bottom".equals(kw2))) {
					x = PercentageValue.HALF;
					y = PercentageValue.FULL;
				} else if (("bottom".equals(kw1) && "right".equals(kw2))
						|| ("right".equals(kw1) && "bottom".equals(kw2))) {
					x = y = PercentageValue.FULL;
				} else {
					throw new PropertyException();
				}

				return new Entry[] { new Entry(BackgroundPosition.INFO_X, x), new Entry(BackgroundPosition.INFO_Y, y) };
			}

			y = ValueUtils.toPercentage(lu);
			if (y == null) {
				y = ValueUtils.toLength(ua, lu);
			}
			if (y == null) {
				throw new PropertyException();
			}
			if (kw1.equals("left")) {
				x = PercentageValue.ZERO;
			} else if (kw1.equals("center")) {
				x = PercentageValue.HALF;
			} else if (kw1.equals("right")) {
				x = PercentageValue.FULL;
			} else {
				throw new PropertyException();
			}

			return new Entry[] { new Entry(BackgroundPosition.INFO_X, x), new Entry(BackgroundPosition.INFO_Y, y) };
		}

		x = ValueUtils.toPercentage(lu);
		if (x == null) {
			x = ValueUtils.toLength(ua, lu);
		}
		if (x == null) {
			throw new PropertyException();
		}

		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			y = x;
			return new Entry[] { new Entry(BackgroundPosition.INFO_X, x), new Entry(BackgroundPosition.INFO_Y, y) };

		}

		if (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
			String kw2 = lu.getStringValue().toLowerCase();
			if (kw2.equals("top")) {
				y = PercentageValue.ZERO;
			} else if (kw2.equals("center")) {
				y = PercentageValue.HALF;
			} else if (kw2.equals("bottom")) {
				y = PercentageValue.FULL;
			} else {
				throw new PropertyException();
			}
		} else {
			y = ValueUtils.toPercentage(lu);
			if (y == null) {
				y = ValueUtils.toLength(ua, lu);
			}
			if (y == null) {
				throw new PropertyException();
			}
		}
		return new Entry[] { new Entry(BackgroundPosition.INFO_X, x), new Entry(BackgroundPosition.INFO_Y, y) };

	}

}