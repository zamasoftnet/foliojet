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
	 * Converts &lt;margin-width&gt; to a value.
	 *
	 * @param ua
	 * @param lu
	 * @return
	 */
	public static Value toMarginWidth(UserAgent ua, CssToken token) throws PropertyException {
		if (token instanceof CssToken.Ident ident) {
			return ident.is("auto") ? KeywordValue.AUTO : null;
		}
		// Typed attr() (2026-08-03). Attributes belong to the element, so resolve at the computed-value
		// stage (ValueUtils.emExToAbsoluteLength).
		Value attr = AttrValueUtils.toTypedAttr(ua, token, TypedAttrValue.Kind.LENGTH);
		if (attr != null) {
			return attr;
		}
		Value calc = CalcValueUtils.toCalc(ua, token);
		if (calc != null) {
			// A <length-percentage> context rejects unitless numeric calc() results (e.g. calc(1 + 2))
			return calc instanceof RealValue ? null : calc;
		}
		if (token instanceof CssToken.Percent percent) {
			return ValueUtils.toPercentage(percent);
		}
		return ValueUtils.toLength(ua, token);
	}

	/**
	 * Returns the value to store in the primary field of Length/Dimension/Insets/Offset
	 * (getLength()/getWidth(), etc.). Meaning depends on type (same contract as
	 * Length.create/createMixed): ABSOLUTE→absolute length, RELATIVE→ratio,
	 * MIXED→absolute component (the ratio component is held separately by {@link #extraRatioPart}).
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
	 * Returns the ratio component stored separately from the primary field, meaningful
	 * only for MIXED (absolute + percentage in calc()). For plain RELATIVE, the ratio is
	 * already in {@link #primaryPart}, so this always returns 0.
	 */
	private static double extraRatioPart(Value value) {
		if (value instanceof CalcLengthValue calc) {
			return calc.getRatio();
		}
		return 0;
	}

	/**
	 * Obtains a Dimension from a Value.
	 */
	public static Dimension toDimension(Value widthValue, Value heightValue) {
		return Dimension.create(primaryPart(widthValue), extraRatioPart(widthValue), primaryPart(heightValue),
				extraRatioPart(heightValue), lengthType(widthValue), lengthType(heightValue));
	}

	/**
	 * Creates a Length from a min-width/min-height Value (2026-08-29).
	 * Intrinsic size keywords become 0, the min-side default (same reason as {@link #toMinDimension}).
	 */
	public static Length toMinLength(Value value) {
		return isIntrinsic(value) ? Length.ZERO_LENGTH : toLength(value);
	}

	/**
	 * Obtains a Dimension from a min-width/min-height Value (2026-08-29).
	 * {@link #toDimension} converts intrinsic size keywords to AUTO, but min-* previously
	 * never had type AUTO (initial value 0). firstPassLayout, etc. treat it as NONE, turning
	 * the dimension into NONE. Use 0, the min-side default, and carry the actual value in
	 * BlockParams.intrinsicMinLine.
	 */
	public static Dimension toMinDimension(Value widthValue, Value heightValue) {
		return toDimension(isIntrinsic(widthValue) ? AbsoluteLengthValue.ZERO : widthValue,
				isIntrinsic(heightValue) ? AbsoluteLengthValue.ZERO : heightValue);
	}

	/**
	 * Determines the LengthType corresponding to a Value
	 * (AbsoluteLengthValue/PercentageValue/CalcLengthValue/AUTO keywords).
	 * Shared by Dimension/Insets/Offset type checks.
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
			// Intrinsic size keywords are AUTO in Dimension (2026-08-29). BlockParams.intrinsicLine,
			// etc. carry the actual values separately, resolved by shrinkToFit.
			// Consumers outside the inline axis or outside blocks can treat them as auto.
			return LengthType.AUTO;
		}
		throw new IllegalStateException(String.valueOf(value));
	}

	/**
	 * Returns true for intrinsic size keywords (max-content/min-content/fit-content/
	 * fit-content(L)) (2026-08-29).
	 */
	public static boolean isIntrinsic(Value value) {
		return value == KeywordValue.MAX_CONTENT || value == KeywordValue.MIN_CONTENT
				|| value == KeywordValue.FIT_CONTENT || value instanceof FitContentValue;
	}

	/**
	 * Converts an intrinsic size keyword to the layout-side {@link IntrinsicSize}
	 * (2026-08-29). Returns null for other values.
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
			// An argument no longer representing a length, e.g. after failed attr() resolution, means no argument
			final Length bound = (argument instanceof AbsoluteLengthValue || argument instanceof PercentageValue
					|| argument instanceof CalcLengthValue) ? toLength(argument) : Length.AUTO_LENGTH;
			return IntrinsicSize.fitContent(bound);
		}
		return null;
	}

	/**
	 * Parses intrinsic size keywords (css-sizing-3, 2026-08-29):
	 * {@code max-content}/{@code min-content}/{@code fit-content}/
	 * {@code fit-content(<length-percentage>)}. Returns null if none matches
	 * (the caller proceeds with normal length parsing).
	 *
	 * @param ua    user agent
	 * @param token token
	 * @return keyword value, or null if none matches
	 * @throws PropertyException if the fit-content() argument is invalid
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
	 * Creates a Length from a Value.
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
	 * Returns a positive percentage or Length.
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
			// A <length-percentage> context rejects unitless numeric calc() results (e.g. calc(1 + 2))
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