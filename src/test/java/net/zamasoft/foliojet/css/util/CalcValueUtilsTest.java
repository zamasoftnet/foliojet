package net.zamasoft.foliojet.css.util;

import java.lang.reflect.Proxy;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AngleValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

public class CalcValueUtilsTest extends TestCase {
	private static final double DELTA = 1e-9;

	private static UserAgent userAgent() {
		return (UserAgent) Proxy.newProxyInstance(CalcValueUtilsTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "CalcValueUtilsTest.UserAgent";
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}

	/** Obtain one calc()-family token through the real ph-css pipeline (Tokens.fromExpression). */
	private static CssToken parseCalcToken(String declaration) {
		CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull("宣言のパースに失敗: " + declaration, decls);
		List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		CSSDeclaration decl = all.get(0);
		List<CssToken> tokens = Tokens.fromExpression(decl.getExpression());
		assertEquals(1, tokens.size());
		return tokens.get(0);
	}

	// --- Verify RPN conversion through the real ph-css pipeline ---

	public void testCalcAdditionViaRealParser() {
		CssToken token = parseCalcToken("width: calc(1px + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(3.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcUnitlessZeroIsNeutralForAddition() {
		// Unitless 0 may be treated as the identity on either side (calc(0 + 10px)).
		CssToken token = parseCalcToken("width: calc(0 + 10px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(10.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcUnitlessZeroIsNeutralForSubtraction() {
		// calc(10px - 0)
		CssToken token = parseCalcToken("width: calc(10px - 0)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(10.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcNonZeroNumberPlusLengthIsInvalid() {
		// Adding a nonzero unitless number to a length is invalid (only 0 is allowed as the identity).
		CssToken token = parseCalcToken("width: calc(1 + 10px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	public void testCalcOperatorPrecedenceViaRealParser() {
		// 1px + 2px * 3 = 1px + 6px = 7px (verify multiplication precedence).
		CssToken token = parseCalcToken("width: calc(1px + 2px * 3)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(7.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcParenthesesViaRealParser() {
		// (1px + 2px) * 3 = 9px (verify parentheses override precedence).
		CssToken token = parseCalcToken("width: calc((1px + 2px) * 3)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(9.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcMixedPercentAndAbsoluteViaRealParser() {
		// getAbsolute() uses PT units (the same convention as AbsoluteLengthValue.getLength()),
		// so use pt for straightforward checks without DPI conversion.
		CssToken token = parseCalcToken("width: calc(50% + 10pt)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof CalcLengthValue);
		CalcLengthValue mixed = (CalcLengthValue) value;
		assertEquals(10.0, mixed.getAbsolute(), DELTA);
		assertEquals(0.5, mixed.getRatio(), DELTA);
	}

	public void testCalcPurePercentCollapsesToPercentageValue() {
		CssToken token = parseCalcToken("width: calc(50% + 10%)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof PercentageValue);
		assertEquals(60.0, ((PercentageValue) value).getPercentage(), DELTA);
	}

	public void testCalcNestedFunctionViaRealParser() {
		// calc(min(10px, 20px) + 5px) = 10px + 5px = 15px
		CssToken token = parseCalcToken("width: calc(min(10px, 20px) + 5px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(15.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcDivisionAndMultiplication() {
		CssToken div = parseCalcToken("width: calc(10px / 2)");
		Value divValue = CalcValueUtils.toCalc(userAgent(), div);
		assertTrue(divValue instanceof AbsoluteLengthValue);
		assertEquals(5.0, ((AbsoluteLengthValue) divValue).getLength(Unit.PX), DELTA);

		CssToken mul = parseCalcToken("width: calc(2 * 3)");
		Value mulValue = CalcValueUtils.toCalc(userAgent(), mul);
		assertTrue(mulValue instanceof RealValue);
		assertEquals(6.0, ((RealValue) mulValue).getReal(), DELTA);
	}

	public void testCalcLengthTimesLengthIsInvalid() {
		CssToken token = parseCalcToken("width: calc(10px * 20px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	public void testCalcDivisionByLengthIsInvalid() {
		CssToken token = parseCalcToken("width: calc(10px / 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	public void testCalcDivisionByZeroIsInvalid() {
		CssToken token = parseCalcToken("width: calc(10px / 0)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	// --- min/max/clamp ---

	public void testMin() {
		CssToken token = parseCalcToken("width: min(10px, 20px, 5px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(5.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testMax() {
		CssToken token = parseCalcToken("width: max(10px, 20px, 5px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(20.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testClampWithinRange() {
		CssToken token = parseCalcToken("width: clamp(10px, 15px, 20px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(15.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testClampBelowMin() {
		CssToken token = parseCalcToken("width: clamp(10px, 5px, 20px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(10.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testClampAboveMax() {
		CssToken token = parseCalcToken("width: clamp(10px, 25px, 20px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(20.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testMinMaxIncomparableMixedUnitsFails() {
		// Mixed px and % need a reference value for static comparison, so are currently unsupported (invalid value).
		CssToken token = parseCalcToken("width: min(10px, 50%)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
		assertNull(CalcValueUtils.toCalc(userAgent(), parseCalcToken("width: clamp(5mm, 10%, 20mm)")));
		assertNull(CalcValueUtils.toCalc(userAgent(), parseCalcToken("width: max(1em, 50%)")));
	}

	/**
	 * Defer comparison between absolute lengths and font-relative units until computed-value time,
	 * when font dimensions are known (2026-10-04).
	 * Verify values through layout in jp.cssj.test.unit.displaylist.MinMaxFontRelativeTest.
	 */
	public void testMinMaxWithFontRelativeUnitIsDeferred() {
		for (final String declaration : new String[] { "width: min(10mm, 3em)", "width: max(1em, 1px)",
				"width: clamp(1rem, 50pt, 2rem)", "width: calc(min(10pt, 1em) * 2 + 1em)" }) {
			final Value value = CalcValueUtils.toCalc(userAgent(), parseCalcToken(declaration));
			assertTrue(declaration + ": " + value, value instanceof CalcFontRelativeValue);
			assertFalse(declaration, ((CalcFontRelativeValue) value).isZero());
			assertFalse(declaration, ((CalcFontRelativeValue) value).isNegative());
		}
	}

	// --- Check boundary conditions with hand-built tokens (direct RPN stack verification) ---

	public void testNonFunctionTokenReturnsNull() {
		assertNull(CalcValueUtils.toCalc(userAgent(), new CssToken.Dim(1, net.zamasoft.foliojet.css.token.Unit.PX,
				"px")));
	}

	public void testUnknownFunctionReturnsNull() {
		CssToken token = new CssToken.Func("unknown-fn", List.of(new CssToken.Num(1, true)));
		assertNull(CalcValueUtils.toCalc(userAgent(), token));
	}

	public void testEmptyCalcReturnsNull() {
		CssToken token = new CssToken.Func("calc", List.of());
		assertNull(CalcValueUtils.toCalc(userAgent(), token));
	}

	/**
	 * <b>Carry font-relative units as coefficients</b> (2026-08-03).
	 *
	 * <p>
	 * em/ex/rem/ch cannot be resolved until CSSStyle is known, but <b>unresolvable and invalid are
	 * different</b>. Until 2026-08-03, they were invalidated here, discarding entire declarations such as
	 * {@code left: calc(-1 * (3.5rem - 26px))} (used by W3C specifications to place self-link symbols
	 * in the left margin). Now carry them separately from absolute and percentage components until
	 * computed-value time, then resolve them in {@code ValueUtils.emExToAbsoluteLength}.
	 */
	public void testCalcWithRelativeUnitKeepsComponents() {
		CssToken token = parseCalcToken("width: calc(1em + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue("フォント相対成分を持つ値になる: " + value, value instanceof CalcFontRelativeValue);
		// 2 px is 1.5 pt. The em coefficient is unresolved, so its value remains unchanged.
		assertEquals("calc(1.5pt + 0.0% + 1.0em + 0.0ex + 0.0rem + 0.0ch + 0.0lh + 0.0cap + 0.0rlh)", value.toString());
	}

	/**
	 * Multiplication by a number also affects font-relative components (linearity permits multiplying
	 * dimensions later).
	 */
	public void testCalcRelativeUnitScales() {
		CssToken token = parseCalcToken("width: calc(-1 * (3.5rem - 26px))");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof CalcFontRelativeValue);
		assertEquals("calc(19.5pt + 0.0% + 0.0em + 0.0ex + -3.5rem + 0.0ch + 0.0lh + 0.0cap + 0.0rlh)", value.toString());
	}

	public void testCalcWithVarReturnsNull() {
		// var() needs cascade-time resolution, so it is currently unsupported (invalid value).
		CssToken token = parseCalcToken("width: calc(var(--x) + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	// --- Twelve css-values-4 math functions (2026-08-30) ---

	/** Extract a length (pt) through {@code calc()}. */
	private static double lengthPt(String declaration) {
		Value value = CalcValueUtils.toCalc(userAgent(), parseCalcToken(declaration));
		assertNotNull(declaration + " が無効になった", value);
		if (value instanceof AbsoluteLengthValue abs) {
			return abs.getLength();
		}
		assertTrue(declaration + " が長さでない: " + value, value instanceof CalcLengthValue);
		CalcLengthValue calc = (CalcLengthValue) value;
		assertEquals("割合成分が残っている: " + value, 0.0, calc.getRatio(), DELTA);
		return calc.getAbsolute();
	}

	/** Extract a unitless number through {@code calc()}. */
	private static double number(String declaration) {
		Value value = CalcValueUtils.toCalc(userAgent(), parseCalcToken(declaration));
		assertNotNull(declaration + " が無効になった", value);
		assertTrue(declaration + " が数値でない: " + value, value instanceof RealValue);
		return ((RealValue) value).getReal();
	}

	private static void assertInvalidCalc(String declaration) {
		assertNull(declaration + " が受理された",
				CalcValueUtils.toCalc(userAgent(), parseCalcToken(declaration)));
	}

	public void testSqrtExpPowLogHypot() {
		assertEquals(4.0, lengthPt("width: calc(sqrt(16) * 1pt)"), DELTA);
		assertEquals(1024.0, lengthPt("width: calc(pow(2, 10) * 1pt)"), DELTA);
		assertEquals(5.0, lengthPt("width: calc(hypot(3, 4) * 1pt)"), DELTA);
		// exp(0)=1、log(e)=1、log(8,2)=3
		assertEquals(1.0, lengthPt("width: calc(exp(0) * 1pt)"), DELTA);
		assertEquals(3.0, lengthPt("width: calc(log(8, 2) * 1pt)"), DELTA);
		assertEquals(Math.log(10), lengthPt("width: calc(log(10) * 1pt)"), 1e-9);
	}

	public void testTrigTakesAngles() {
		// Accept angle units. sin(90deg)=1, cos(0)=1, tan(45deg)=1.
		assertEquals(10.0, lengthPt("width: calc(sin(90deg) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(cos(0) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(tan(45deg) * 10pt)"), 1e-9);
		// The same angle in different units gives the same value (0.25turn = 90deg = π/2 rad).
		assertEquals(10.0, lengthPt("width: calc(sin(0.25turn) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(sin(1.5707963267948966rad) * 10pt)"), 1e-9);
		// SPEC css-values-4: treat bare numbers as radians.
		assertEquals(10.0, lengthPt("width: calc(sin(1.5707963267948966) * 10pt)"), 1e-9);
	}

	/** Extract an angle (degrees) through {@code calc()}. */
	private static double degrees(String declaration) {
		Value value = CalcValueUtils.toCalc(userAgent(), parseCalcToken(declaration));
		assertNotNull(declaration + " が無効になった", value);
		assertTrue(declaration + " が角度でない: " + value, value instanceof AngleValue);
		return ((AngleValue) value).getDegrees();
	}

	public void testInverseTrigReturnsAngles() {
		// Inverse trigonometric functions take <number> and return <angle>.
		assertEquals(90.0, degrees("width: calc(asin(1))"), 1e-9);
		assertEquals(0.0, degrees("width: calc(acos(1))"), 1e-9);
		assertEquals(45.0, degrees("width: calc(atan(1))"), 1e-9);
		assertEquals(45.0, degrees("width: calc(atan2(1, 1))"), 1e-9);
		assertEquals(-45.0, degrees("width: calc(atan2(-1, 1))"), 1e-9);
		// Passing the returned angle to a trigonometric function completes a round trip.
		assertEquals(10.0, lengthPt("width: calc(sin(asin(1)) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(cos(acos(1)) * 10pt)"), 1e-9);
	}

	/**
	 * <b>Angle ÷ angle is unsupported</b> (as of 2026-08-30).
	 *
	 * <p>
	 * SPEC css-values-4 says {@code calc(45deg / 1deg)} returns dimensionless 1, but division
	 * in this implementation handles only division by a number. This is practically absent from real
	 * documents, so it is not pursued; this assertion prevents the limitation from being silently forgotten.
	 */
	public void testAngleDividedByAngleIsNotSupported() {
		assertInvalidCalc("width: calc(atan2(1, 1) / 1deg)");
	}

	public void testMathFunctionsOutOfDomainAreInvalid() {
		// Out-of-domain input is invalid. Silently accepting NaN would break the type area.
		assertInvalidCalc("width: calc(sqrt(-1) * 1pt)");
		assertInvalidCalc("width: calc(log(0) * 1pt)");
		assertInvalidCalc("width: calc(asin(2) * 1pt)");
		assertInvalidCalc("width: calc(acos(-2) * 1pt)");
	}

	public void testMathFunctionsNest() {
		// Nesting mixed with arithmetic
		assertEquals(5.0, lengthPt("width: calc(sqrt(pow(5, 2)) * 1pt)"), DELTA);
		assertEquals(13.0, lengthPt("width: calc((hypot(3, 4) + 8) * 1pt)"), DELTA);
		assertEquals(2.0, lengthPt("width: calc(max(sqrt(4), 1) * 1pt)"), DELTA);
	}
}
