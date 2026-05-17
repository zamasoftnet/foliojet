package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ColorValueUtils;
import jp.cssj.homare.css.value.BackgroundRepeatValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.ext.CSSJDirectionModeValue;
import jp.cssj.homare.impl.css.property.css3.BlockFlow;
import jp.cssj.homare.impl.css.property.ext.CSSJDirectionMode;
import jp.cssj.homare.style.box.params.AbstractTextParams;
import jp.cssj.homare.style.box.params.BackgroundImage;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = ColorValueUtils.toBackgroundRepeat(lu);
		if (value != null) {
			return value;
		}
		throw new PropertyException();
	}

}