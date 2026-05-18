package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJDirectionModeValue;
import net.zamasoft.foliojet.impl.css.property.css3.BlockFlow;
import net.zamasoft.foliojet.impl.css.property.ext.CSSJDirectionMode;
import net.zamasoft.foliojet.impl.css.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-height"> height 特性
 * </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Height.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Height extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Height();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo info;
		if (CSSJInternalImage.getImage(style) != null) {
			// 画像には回転を適用しない
			info = INFO;
		} else {
			// 回転
			switch (CSSJDirectionMode.get(style)) {
			case CSSJDirectionModeValue.PHYSICAL:
				info = INFO;
				break;
			case CSSJDirectionModeValue.HORIZONTAL_TB:
				switch (BlockFlow.get(style)) {
				case AbstractTextParams.FLOW_RL:
				case AbstractTextParams.FLOW_LR:
					info = Width.INFO;
					break;
				default:
					info = INFO;
					break;
				}
				break;
			case CSSJDirectionModeValue.VERTICAL_RL:
				switch (BlockFlow.get(style)) {
				case AbstractTextParams.FLOW_TB:
					info = Width.INFO;
					break;
				default:
					info = INFO;
					break;
				}
				break;
			default:
				throw new IllegalStateException();
			}
		}
		return style.get(info);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(Height.get(style));
	}

	protected Height() {
		super("height");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return AutoValue.AUTO_VALUE;
		}

		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	public int getPriority() {
		return 1;
	}
}