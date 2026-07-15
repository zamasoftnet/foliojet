package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.NormalValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.Insets;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.style.box.params.Offset;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BoxValueUtils.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public final class BoxValueUtils {
	private BoxValueUtils() {
		// unused
	}

	/**
	 * &lt;margin-width&gt; を値に変換します。
	 * 
	 * @param ua
	 * @param lu
	 * @return
	 */
	public static Value toMarginWidth(UserAgent ua, CssToken token) throws PropertyException {
		if (token instanceof CssToken.Ident ident) {
			return ident.is("auto") ? AutoValue.AUTO_VALUE : null;
		}
		if (token instanceof CssToken.Percent percent) {
			return ValueUtils.toPercentage(percent);
		}
		return ValueUtils.toLength(ua, token);
	}

	/**
	 * top/right/left/bottom を値に変換します。
	 * 
	 * @param device
	 * @param lu
	 * @return
	 */
	public static Value toTRLB(UserAgent device, CssToken token) throws PropertyException {
		return toMarginWidth(device, token);
	}

	/**
	 * ValueからDimensionとして取得します。
	 * 
	 * @param widthValue
	 * @param heightValue
	 * @return
	 */
	public static Dimension toDimension(Value widthValue, Value heightValue) {
		byte widthType;
		double width;
		switch (widthValue.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			widthType = Dimension.TYPE_ABSOLUTE;
			width = ((AbsoluteLengthValue) widthValue).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			widthType = Dimension.TYPE_RELATIVE;
			width = ((PercentageValue) widthValue).getRatio();
			break;
		case Value.TYPE_NONE:
		case Value.TYPE_AUTO:
			widthType = Dimension.TYPE_AUTO;
			width = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		byte heightType;
		double height;
		switch (heightValue.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			heightType = Dimension.TYPE_ABSOLUTE;
			height = ((AbsoluteLengthValue) heightValue).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			heightType = Dimension.TYPE_RELATIVE;
			height = ((PercentageValue) heightValue).getRatio();
			break;
		case Value.TYPE_NONE:
		case Value.TYPE_AUTO:
			heightType = Dimension.TYPE_AUTO;
			height = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		return Dimension.create(width, height, widthType, heightType);
	}

	/**
	 * ValueからLengthを生成します。
	 * 
	 * @param value
	 * @return
	 */
	public static Length toLength(Value value) {
		switch (value.getValueType()) {
		case Value.TYPE_NONE:
		case Value.TYPE_AUTO:
			return Length.AUTO_LENGTH;
		case Value.TYPE_PERCENTAGE:
			return Length.create(((PercentageValue) value).getRatio(), Length.TYPE_RELATIVE);
		case Value.TYPE_ABSOLUTE_LENGTH:
			return Length.create(((AbsoluteLengthValue) value).getLength(), Length.TYPE_ABSOLUTE);
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * 正のパーセント値またはLengthを返します。
	 * 
	 * @param ua
	 * @param lu
	 * @return
	 */
	public static QuantityValue toPositiveLength(UserAgent ua, CssToken token) {
		QuantityValue value;
		if (token instanceof CssToken.Percent percent) {
			value = ValueUtils.toPercentage(percent);
		} else {
			value = ValueUtils.toLength(ua, token);
		}
		if (value != null && value.isNegative()) {
			return null;
		}
		return value;
	}

	public static Value toLineHeight(UserAgent ua, CssToken token) {
		if (ValueUtils.isNormal(token)) {
			return NormalValue.NORMAL_VALUE;
		}
		final Value lineHeight;
		if (token instanceof CssToken.Num) {
			lineHeight = ValueUtils.toReal(token);
			if (lineHeight == null || ((RealValue) lineHeight).isNegative()) {
				return null;
			}
		} else if (token instanceof CssToken.Percent) {
			lineHeight = ValueUtils.toPercentage(token);
			if (lineHeight == null || ((PercentageValue) lineHeight).isNegative()) {
				return null;
			}
		} else {
			lineHeight = ValueUtils.toLength(ua, token);
			if (lineHeight == null || ((LengthValue) lineHeight).isNegative()) {
				return null;
			}
		}
		return lineHeight;
	}

	public static Insets toInsets(Value top, Value right, Value bottom, Value left) {
		double topWidth, rightWidth, bottomWidth, leftWidth;
		short topType, rightType, bottomType, leftType;

		switch (top.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			topType = Insets.TYPE_ABSOLUTE;
			topWidth = ((AbsoluteLengthValue) top).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			topType = Insets.TYPE_RELATIVE;
			topWidth = ((PercentageValue) top).getRatio();
			break;
		case Value.TYPE_AUTO:
			topType = Insets.TYPE_AUTO;
			topWidth = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (right.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			rightType = Insets.TYPE_ABSOLUTE;
			rightWidth = ((AbsoluteLengthValue) right).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			rightType = Insets.TYPE_RELATIVE;
			rightWidth = ((PercentageValue) right).getRatio();
			break;
		case Value.TYPE_AUTO:
			rightType = Insets.TYPE_AUTO;
			rightWidth = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (bottom.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			bottomType = Insets.TYPE_ABSOLUTE;
			bottomWidth = ((AbsoluteLengthValue) bottom).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			bottomType = Insets.TYPE_RELATIVE;
			bottomWidth = ((PercentageValue) bottom).getRatio();
			break;
		case Value.TYPE_AUTO:
			bottomType = Insets.TYPE_AUTO;
			bottomWidth = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		switch (left.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			leftType = Insets.TYPE_ABSOLUTE;
			leftWidth = ((AbsoluteLengthValue) left).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			leftType = Insets.TYPE_RELATIVE;
			leftWidth = ((PercentageValue) left).getRatio();
			break;
		case Value.TYPE_AUTO:
			leftType = Insets.TYPE_AUTO;
			leftWidth = 0;
			break;
		default:
			throw new IllegalStateException();
		}

		return Insets.create(topWidth, rightWidth, bottomWidth, leftWidth, topType, rightType, bottomType, leftType);
	}

	public static Offset toOffset(Value xValue, Value yValue) {
		short xType;
		double x;
		switch (xValue.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			xType = Offset.TYPE_ABSOLUTE;
			x = ((AbsoluteLengthValue) xValue).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			xType = Offset.TYPE_RELATIVE;
			x = ((PercentageValue) xValue).getRatio();
			break;
		case Value.TYPE_AUTO:
			xType = Offset.TYPE_AUTO;
			x = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		short yType;
		double y;
		switch (yValue.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			yType = Offset.TYPE_ABSOLUTE;
			y = ((AbsoluteLengthValue) yValue).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			yType = Offset.TYPE_RELATIVE;
			y = ((PercentageValue) yValue).getRatio();
			break;
		case Value.TYPE_AUTO:
			yType = Offset.TYPE_AUTO;
			y = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		return Offset.create(x, y, xType, yType);
	}
}