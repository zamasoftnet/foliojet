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
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
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

	public void testCalcWithRelativeUnitReturnsNull() {
		// em/ex/rem/chはCSSStyleが定まるまで解決できないため現時点は非対応(無効値)
		CssToken token = parseCalcToken("width: calc(1em + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}

	public void testCalcWithVarReturnsNull() {
		// var()はカスケード時解決が必要なため現時点は非対応(無効値)
		CssToken token = parseCalcToken("width: calc(var(--x) + 2px)");
		Value value = CalcValueUtils.toCalc(userAgent(), token);
		assertNull(value);
	}
}
