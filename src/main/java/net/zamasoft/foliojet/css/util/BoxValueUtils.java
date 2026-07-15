package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.style.box.params.LengthType;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.Insets;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.style.box.params.Offset;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
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
			return ident.is("auto") ? KeywordValue.AUTO : null;
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
	 * 絶対長さ・パーセントの数値部分を返します(キーワードは0)。
	 */
	private static double quantity(Value value) {
		if (value instanceof AbsoluteLengthValue length) {
			return length.getLength();
		}
		if (value instanceof PercentageValue percentage) {
			return percentage.getRatio();
		}
		return 0;
	}

	/**
	 * ValueからDimensionとして取得します。
	 */
	public static Dimension toDimension(Value widthValue, Value heightValue) {
		return Dimension.create(quantity(widthValue), quantity(heightValue), dimensionType(widthValue),
				dimensionType(heightValue));
	}

	private static LengthType dimensionType(Value value) {
		if (value instanceof AbsoluteLengthValue) {
			return LengthType.ABSOLUTE;
		}
		if (value instanceof PercentageValue) {
			return LengthType.RELATIVE;
		}
		if (value == KeywordValue.NONE || value == KeywordValue.AUTO) {
			return LengthType.AUTO;
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	/**
	 * ValueからLengthを生成します。
	 */
	public static Length toLength(Value value) {
		if (value == KeywordValue.NONE || value == KeywordValue.AUTO) {
			return Length.AUTO_LENGTH;
		}
		if (value instanceof PercentageValue percentage) {
			return Length.create(percentage.getRatio(), LengthType.RELATIVE);
		}
		if (value instanceof AbsoluteLengthValue length) {
			return Length.create(length.getLength(), LengthType.ABSOLUTE);
		}
		throw new IllegalStateException(String.valueOf(value));
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
			return KeywordValue.NORMAL;
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
		return Insets.create(quantity(top), quantity(right), quantity(bottom), quantity(left), insetsType(top),
				insetsType(right), insetsType(bottom), insetsType(left));
	}

	private static LengthType insetsType(Value value) {
		if (value instanceof AbsoluteLengthValue) {
			return LengthType.ABSOLUTE;
		}
		if (value instanceof PercentageValue) {
			return LengthType.RELATIVE;
		}
		if (value == KeywordValue.AUTO) {
			return LengthType.AUTO;
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	public static Offset toOffset(Value xValue, Value yValue) {
		return Offset.create(quantity(xValue), quantity(yValue), offsetType(xValue), offsetType(yValue));
	}

	private static LengthType offsetType(Value value) {
		if (value instanceof AbsoluteLengthValue) {
			return LengthType.ABSOLUTE;
		}
		if (value instanceof PercentageValue) {
			return LengthType.RELATIVE;
		}
		if (value == KeywordValue.AUTO) {
			return LengthType.AUTO;
		}
		throw new IllegalStateException(String.valueOf(value));
	}
}