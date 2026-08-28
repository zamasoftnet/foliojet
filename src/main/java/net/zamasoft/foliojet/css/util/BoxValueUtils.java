package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.TypedAttrValue;
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
import net.zamasoft.foliojet.css.value.FitContentValue;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.layout.box.params.IntrinsicSize;

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
		// 型付き attr()(2026-08-03)。属性はその要素のものなので、解決は計算値の
		// 段階(ValueUtils.emExToAbsoluteLength)で行う
		Value attr = AttrValueUtils.toTypedAttr(ua, token, TypedAttrValue.Kind.LENGTH);
		if (attr != null) {
			return attr;
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
	 * min-width/min-heightのValueからLengthを生成します(2026-08-29)。
	 * 固有寸法キーワードはmin側の既定と同じ0({@link #toMinDimension}と同じ理由)。
	 */
	public static Length toMinLength(Value value) {
		return isIntrinsic(value) ? Length.ZERO_LENGTH : toLength(value);
	}

	/**
	 * min-width/min-heightのValueからDimensionとして取得します(2026-08-29)。
	 * 固有寸法キーワードは{@link #toDimension}だとAUTOになるが、min-*で
	 * AUTO型は従来あり得ず(初期値は0)、firstPassLayout等がNONE扱いして
	 * 寸法がNONEに化ける。min側の既定と同じ0にしておき、実体は
	 * BlockParams.intrinsicMinLineが運ぶ。
	 */
	public static Dimension toMinDimension(Value widthValue, Value heightValue) {
		return toDimension(isIntrinsic(widthValue) ? AbsoluteLengthValue.ZERO : widthValue,
				isIntrinsic(heightValue) ? AbsoluteLengthValue.ZERO : heightValue);
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
		if (value == KeywordValue.NONE || value == KeywordValue.AUTO || isIntrinsic(value)) {
			// 固有寸法キーワードはDimension上ではAUTO(2026-08-29)。実体は
			// BlockParams.intrinsicLine等が別枠で運び、shrinkToFitで解く。
			// 行方向以外・ブロック以外の消費者はautoとして扱えばよい
			return LengthType.AUTO;
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	/**
	 * 固有寸法キーワード(max-content/min-content/fit-content/
	 * fit-content(L))であればtrueを返します(2026-08-29)。
	 */
	public static boolean isIntrinsic(Value value) {
		return value == KeywordValue.MAX_CONTENT || value == KeywordValue.MIN_CONTENT
				|| value == KeywordValue.FIT_CONTENT || value instanceof FitContentValue;
	}

	/**
	 * 固有寸法キーワードをレイアウト側の{@link IntrinsicSize}へ変換します
	 * (2026-08-29)。キーワードでなければnull。
	 */
	public static IntrinsicSize toIntrinsicSize(Value value) {
		if (value == KeywordValue.MAX_CONTENT) {
			return IntrinsicSize.MAX_CONTENT;
		}
		if (value == KeywordValue.MIN_CONTENT) {
			return IntrinsicSize.MIN_CONTENT;
		}
		if (value == KeywordValue.FIT_CONTENT) {
			return IntrinsicSize.FIT_CONTENT;
		}
		if (value instanceof FitContentValue fit) {
			final Value argument = fit.argument();
			// attr()の解決失敗等で長さでなくなった引数は引数無しと同じ
			final Length bound = (argument instanceof AbsoluteLengthValue || argument instanceof PercentageValue
					|| argument instanceof CalcLengthValue) ? toLength(argument) : Length.AUTO_LENGTH;
			return IntrinsicSize.fitContent(bound);
		}
		return null;
	}

	/**
	 * 固有寸法キーワードを解析します(css-sizing-3、2026-08-29)。
	 * {@code max-content}/{@code min-content}/{@code fit-content}/
	 * {@code fit-content(<length-percentage>)}。該当しなければnull
	 * (呼び出し側が通常の長さ解析へ進む)。
	 *
	 * @param ua    ユーザーエージェント
	 * @param token トークン
	 * @return キーワード値。該当しなければnull
	 * @throws PropertyException fit-content()の引数が不正
	 */
	public static Value toIntrinsicSize(UserAgent ua, CssToken token) throws PropertyException {
		if (ValueUtils.isKeyword(token, "max-content")) {
			return KeywordValue.MAX_CONTENT;
		}
		if (ValueUtils.isKeyword(token, "min-content")) {
			return KeywordValue.MIN_CONTENT;
		}
		if (ValueUtils.isKeyword(token, "fit-content")) {
			return KeywordValue.FIT_CONTENT;
		}
		if (token instanceof CssToken.Func func && func.is("fit-content")) {
			final TokenStream args = func.argStream();
			if (!args.hasNext()) {
				throw new PropertyException();
			}
			final QuantityValue bound = toPositiveLength(ua, args.next());
			if (bound == null || args.hasNext()) {
				throw new PropertyException();
			}
			return new FitContentValue(bound);
		}
		return null;
	}

	/**
	 * ValueからLengthを生成します。
	 */
	public static Length toLength(Value value) {
		if (value == KeywordValue.NONE || value == KeywordValue.AUTO || isIntrinsic(value)) {
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
		Value attr = AttrValueUtils.toTypedAttr(ua, token, TypedAttrValue.Kind.LENGTH);
		if (attr != null) {
			return (QuantityValue) attr;
		}
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