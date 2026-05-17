package jp.cssj.homare.css.util;

import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.AutoValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.NormalValue;
import jp.cssj.homare.css.value.PercentageValue;
import jp.cssj.homare.css.value.QuantityValue;
import jp.cssj.homare.css.value.RealValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.style.box.params.Dimension;
import jp.cssj.homare.style.box.params.Insets;
import jp.cssj.homare.style.box.params.Length;
import jp.cssj.homare.style.box.params.Offset;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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
	public static Value toMarginWidth(UserAgent ua, LexicalUnit lu) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("auto")) {
				return AutoValue.AUTO_VALUE;
			}
			break;

		case LexicalUnit.SAC_PERCENTAGE:
			return ValueUtils.toPercentage(lu);

		default:
			return ValueUtils.toLength(ua, lu);
		}

		return null;
	}

	/**
	 * top/right/left/bottom を値に変換します。
	 * 
	 * @param device
	 * @param lu
	 * @return
	 */
	public static Value toTRLB(UserAgent device, LexicalUnit lu) throws PropertyException {
		return toMarginWidth(device, lu);
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
	public static QuantityValue toPositiveLength(UserAgent ua, LexicalUnit lu) {
		QuantityValue value;
		short luType = lu.getLexicalUnitType();
		switch (luType) {

		case LexicalUnit.SAC_PERCENTAGE:
			value = ValueUtils.toPercentage(lu);
			break;

		default:
			value = ValueUtils.toLength(ua, lu);
			break;
		}
		if (value != null && value.isNegative()) {
			return null;
		}
		return value;
	}

	public static Value toLineHeight(UserAgent ua, LexicalUnit lu) {
		if (ValueUtils.isNormal(lu)) {
			return NormalValue.NORMAL_VALUE;
		}

		Value lineHeight;
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_INTEGER:
		case LexicalUnit.SAC_REAL: {
			lineHeight = ValueUtils.toReal(lu);
			if (lineHeight == null || ((RealValue) lineHeight).isNegative()) {
				return null;
			}
		}
			break;

		case LexicalUnit.SAC_PERCENTAGE: {
			lineHeight = ValueUtils.toPercentage(lu);
			if (lineHeight == null || ((PercentageValue) lineHeight).isNegative()) {
				return null;
			}
		}
			break;

		default: {
			lineHeight = ValueUtils.toLength(ua, lu);
			if (lineHeight == null || ((LengthValue) lineHeight).isNegative()) {
				return null;
			}
			break;
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