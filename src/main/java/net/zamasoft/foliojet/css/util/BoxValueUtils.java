package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Offset;
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
		Value calc = CalcValueUtils.toCalc(ua, token);
		if (calc != null) {
			// <length-percentage>文脈なので単位なし数値のcalc()結果(例: calc(1 + 2))は無効
			return calc instanceof RealValue ? null : calc;
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
	 * Length/Dimension/Insets/Offsetの主フィールド(getLength()/getWidth()等)に
	 * 格納すべき値を返します。意味はtypeに依存する(Length.create/createMixedと
	 * 同じ規約): ABSOLUTE→絶対長さそのもの、RELATIVE→割合そのもの、
	 * MIXED→絶対成分(割合成分は{@link #extraRatioPart}が別途持つ)。
	 */
	private static double primaryPart(Value value) {
		if (value instanceof CalcLengthValue calc) {
			return calc.getAbsolute();
		}
		if (value instanceof AbsoluteLengthValue length) {
			return length.getLength();
		}
		if (value instanceof PercentageValue percentage) {
			return percentage.getRatio();
		}
		return 0;
	}

	/**
	 * MIXED(calc()の絶対+割合混在)の場合のみ意味を持つ、主フィールドとは別枠の
	 * 割合成分を返します。RELATIVE単体の場合は割合が既に{@link #primaryPart}に
	 * 入っているため、ここは常に0です。
	 */
	private static double extraRatioPart(Value value) {
		if (value instanceof CalcLengthValue calc) {
			return calc.getRatio();
		}
		return 0;
	}

	/**
	 * ValueからDimensionとして取得します。
	 */
	public static Dimension toDimension(Value widthValue, Value heightValue) {
		return Dimension.create(primaryPart(widthValue), extraRatioPart(widthValue), primaryPart(heightValue),
				extraRatioPart(heightValue), lengthType(widthValue), lengthType(heightValue));
	}

	/**
	 * Value(AbsoluteLengthValue/PercentageValue/CalcLengthValue/AUTO系キーワード)から
	 * 対応するLengthTypeを求めます。Dimension/Insets/Offsetのtype判定で共用します。
	 */
	private static LengthType lengthType(Value value) {
		if (value instanceof CalcLengthValue) {
			return LengthType.MIXED;
		}
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
		if (value instanceof CalcLengthValue calc) {
			return Length.createMixed(calc.getAbsolute(), calc.getRatio());
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
		Value calc = CalcValueUtils.toCalc(ua, token);
		QuantityValue value;
		if (calc instanceof RealValue) {
			// <length-percentage>文脈なので単位なし数値のcalc()結果(例: calc(1 + 2))は無効
			return null;
		} else if (calc != null) {
			value = (QuantityValue) calc;
		} else if (token instanceof CssToken.Percent percent) {
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
		final Value lineHeight = CalcValueUtils.toCalc(ua, token);
		if (lineHeight != null) {
			return ((QuantityValue) lineHeight).isNegative() ? null : lineHeight;
		}
		final Value plain;
		if (token instanceof CssToken.Num) {
			plain = ValueUtils.toReal(token);
			if (plain == null || ((RealValue) plain).isNegative()) {
				return null;
			}
		} else if (token instanceof CssToken.Percent) {
			plain = ValueUtils.toPercentage(token);
			if (plain == null || ((PercentageValue) plain).isNegative()) {
				return null;
			}
		} else {
			plain = ValueUtils.toLength(ua, token);
			if (plain == null || ((LengthValue) plain).isNegative()) {
				return null;
			}
		}
		return plain;
	}

	public static Insets toInsets(Value top, Value right, Value bottom, Value left) {
		return Insets.create(primaryPart(top), extraRatioPart(top), primaryPart(right), extraRatioPart(right),
				primaryPart(bottom), extraRatioPart(bottom), primaryPart(left), extraRatioPart(left), lengthType(top),
				lengthType(right), lengthType(bottom), lengthType(left));
	}

	public static Offset toOffset(Value xValue, Value yValue) {
		return Offset.create(primaryPart(xValue), extraRatioPart(xValue), primaryPart(yValue), extraRatioPart(yValue),
				lengthType(xValue), lengthType(yValue));
	}
}