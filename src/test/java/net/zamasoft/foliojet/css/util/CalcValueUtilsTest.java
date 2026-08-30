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

	/** 実際のph-cssパイプライン(Tokens.fromExpression)を通して1個のcalc()系トークンを得る。 */
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

	// --- ph-cssの実パイプラインを通したRPN変換の確認 ---

	public void testCalcAdditionViaRealParser() {
		CssToken token = parseCalcToken("width: calc(1px + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(3.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcUnitlessZeroIsNeutralForAddition() {
		// 単位なしの0はどちらの側でも中立元として扱ってよい(calc(0 + 10px))
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
		// 単位なしの非0数値と長さの加算は無効(0のみが中立元として許容される)
		CssToken token = parseCalcToken("width: calc(1 + 10px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	public void testCalcOperatorPrecedenceViaRealParser() {
		// 1px + 2px * 3 = 1px + 6px = 7px (乗算が優先されることの確認)
		CssToken token = parseCalcToken("width: calc(1px + 2px * 3)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(7.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcParenthesesViaRealParser() {
		// (1px + 2px) * 3 = 9px (丸括弧による優先順位の上書きの確認)
		CssToken token = parseCalcToken("width: calc((1px + 2px) * 3)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof AbsoluteLengthValue);
		assertEquals(9.0, ((AbsoluteLengthValue) value).getLength(Unit.PX), DELTA);
	}

	public void testCalcMixedPercentAndAbsoluteViaRealParser() {
		// getAbsolute()はPT単位(AbsoluteLengthValue.getLength()と同じ規約)のため、
		// DPI換算を避けて素直に検証できるようptを使う。
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
		// pxと%の混在は基準値なしに静的比較できないため、現時点では非対応(無効値)
		CssToken token = parseCalcToken("width: min(10px, 50%)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	// --- 手組みトークンによる境界条件の確認(RPNスタックの直接検証) ---

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
	 * <b>フォント相対単位は係数として持ち越す</b>(2026-08-03)。
	 *
	 * <p>
	 * em/ex/rem/ch はCSSStyleが定まるまで解決できないが、<b>解けないことと
	 * 無効であることは違う</b>。2026-08-03まではここで無効値にしていたため、
	 * {@code left: calc(-1 * (3.5rem - 26px))}(W3C仕様書が自己リンク記号を
	 * 左余白へ出す書き方)のような指定が丸ごと捨てられていた。今は絶対成分・
	 * 割合成分と分けたまま計算値の段階まで運び、
	 * {@code ValueUtils.emExToAbsoluteLength}で解く。
	 */
	public void testCalcWithRelativeUnitKeepsComponents() {
		CssToken token = parseCalcToken("width: calc(1em + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue("フォント相対成分を持つ値になる: " + value, value instanceof CalcFontRelativeValue);
		// 2px は 1.5pt。em の係数は解決前なので値そのものが残る
		assertEquals("calc(1.5pt + 0.0% + 1.0em + 0.0ex + 0.0rem + 0.0ch + 0.0lh + 0.0cap + 0.0rlh)", value.toString());
	}

	/** 数との乗算はフォント相対成分にも効く(線形なので後で寸法を掛けても等価)。 */
	public void testCalcRelativeUnitScales() {
		CssToken token = parseCalcToken("width: calc(-1 * (3.5rem - 26px))");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertTrue(value instanceof CalcFontRelativeValue);
		assertEquals("calc(19.5pt + 0.0% + 0.0em + 0.0ex + -3.5rem + 0.0ch + 0.0lh + 0.0cap + 0.0rlh)", value.toString());
	}

	public void testCalcWithVarReturnsNull() {
		// var()はカスケード時解決が必要なため現時点は非対応(無効値)
		CssToken token = parseCalcToken("width: calc(var(--x) + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	// --- css-values-4 の数学関数12種(2026-08-30) ---

	/** {@code calc()}を通して長さ(pt)を取り出します。 */
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

	/** {@code calc()}を通して素の数値を取り出します。 */
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
		// 角度単位を取る。sin(90deg)=1、cos(0)=1、tan(45deg)=1
		assertEquals(10.0, lengthPt("width: calc(sin(90deg) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(cos(0) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(tan(45deg) * 10pt)"), 1e-9);
		// 単位違いでも同じ角度なら同じ値(0.25turn = 90deg = π/2 rad)
		assertEquals(10.0, lengthPt("width: calc(sin(0.25turn) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(sin(1.5707963267948966rad) * 10pt)"), 1e-9);
		// SPEC css-values-4: 裸の数値はラジアンとして扱う
		assertEquals(10.0, lengthPt("width: calc(sin(1.5707963267948966) * 10pt)"), 1e-9);
	}

	/** {@code calc()}を通して角度(度)を取り出します。 */
	private static double degrees(String declaration) {
		Value value = CalcValueUtils.toCalc(userAgent(), parseCalcToken(declaration));
		assertNotNull(declaration + " が無効になった", value);
		assertTrue(declaration + " が角度でない: " + value, value instanceof AngleValue);
		return ((AngleValue) value).getDegrees();
	}

	public void testInverseTrigReturnsAngles() {
		// 逆三角は<number>を取って<angle>を返す
		assertEquals(90.0, degrees("width: calc(asin(1))"), 1e-9);
		assertEquals(0.0, degrees("width: calc(acos(1))"), 1e-9);
		assertEquals(45.0, degrees("width: calc(atan(1))"), 1e-9);
		assertEquals(45.0, degrees("width: calc(atan2(1, 1))"), 1e-9);
		assertEquals(-45.0, degrees("width: calc(atan2(-1, 1))"), 1e-9);
		// 返した角度を三角関数へ食わせて往復できること
		assertEquals(10.0, lengthPt("width: calc(sin(asin(1)) * 10pt)"), 1e-9);
		assertEquals(10.0, lengthPt("width: calc(cos(acos(1)) * 10pt)"), 1e-9);
	}

	/**
	 * <b>角度÷角度は未対応</b>です(2026-08-30時点)。
	 *
	 * <p>
	 * SPEC css-values-4 では{@code calc(45deg / 1deg)}は無次元の1を返すが、
	 * この実装の除算は「数で割る」場合しか扱わない。実文書での出現がまず
     * 無いので追っていない——できないことを黙って忘れないための表明である。
	 */
	public void testAngleDividedByAngleIsNotSupported() {
		assertInvalidCalc("width: calc(atan2(1, 1) / 1deg)");
	}

	public void testMathFunctionsOutOfDomainAreInvalid() {
		// 定義域外は無効値。NaNを黙って通すと版面が壊れる
		assertInvalidCalc("width: calc(sqrt(-1) * 1pt)");
		assertInvalidCalc("width: calc(log(0) * 1pt)");
		assertInvalidCalc("width: calc(asin(2) * 1pt)");
		assertInvalidCalc("width: calc(acos(-2) * 1pt)");
	}

	public void testMathFunctionsNest() {
		// 入れ子と四則の混在
		assertEquals(5.0, lengthPt("width: calc(sqrt(pow(5, 2)) * 1pt)"), DELTA);
		assertEquals(13.0, lengthPt("width: calc((hypot(3, 4) + 8) * 1pt)"), DELTA);
		assertEquals(2.0, lengthPt("width: calc(max(sqrt(4), 1) * 1pt)"), DELTA);
	}
}
