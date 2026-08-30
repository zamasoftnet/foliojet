package net.zamasoft.foliojet.css.impl.property;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.ConicGradientValue;
import net.zamasoft.foliojet.css.value.css3.LinearGradientValue;
import net.zamasoft.foliojet.css.value.css3.RadialGradientValue;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 2026-08-30に実装した色の値を固定します——{@code rebeccapurple}・
 * {@code hwb()}・{@code lab()}・{@code lch()}・{@code color()}・
 * {@code hsl()}の{@code turn}、およびグラデーションの補間色空間指定。
 *
 * <p>
 * この製品の出力はPDFなので、どの色空間で書かれてもsRGB(かCMYK)へ落ちる。
 * したがってここで見るのは<b>変換後のsRGB成分</b>である。期待値は実際に
 * 変換したPDFの塗り演算子から採り、CSS Color 4の変換行列で手計算して
 * 一致を確かめたもの([[docs/history/2026-08-30-baseline-css-gap-implementation.md]])。
 *
 * <p>
 * <b>色域外は単純クランプする</b>のがこの実装の方針で、色域マッピングはしない
 * (印刷用途では凝る必要がない)。{@code display-p3}や{@code rec2020}の
 * ケースはその方針そのものを固定している。
 */
public class BaselineCssColorTest extends TestCase {

	/** sRGB成分の許容誤差。floatで持つので1e-3もあれば十分に厳しい。 */
	private static final float EPS = 1e-3f;

	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(BaselineCssColorTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
					case "getFontSize":
						return 12.0;
					case "getDocumentContext":
						return new DocumentContext();
					case "getProperty":
						return null;
					case "message":
						this.warnings.add(String.valueOf(args[0]) + ":" + java.util.Arrays.toString(args));
						return null;
					case "toString":
						return "BaselineCssColorTest.UserAgent";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}

	private static List<CssToken> tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull("宣言のパースに失敗: " + declaration, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		return Tokens.fromExpression(all.get(0).getExpression());
	}

	/** 宣言を解析し、警告が出ていないことを確かめて構成要素を返します。 */
	private Entry[] parse(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), null, false);
		assertNotNull(name + ": " + value + " が無効になった " + this.warnings, property);
		assertTrue(name + ": " + value + " で警告 " + this.warnings, this.warnings.isEmpty());
		assertTrue(property instanceof CompositeProperty);
		return ((CompositeProperty) property).getEntries();
	}

	private Value single(final String name, final String value) {
		final Entry[] entries = this.parse(name, value);
		assertEquals(1, entries.length);
		return entries[0].getValue();
	}

	private void assertInvalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), null, false);
		assertNull(name + ": " + value + " が受理された", property);
		assertFalse("警告が出ていない: " + name + ": " + value, this.warnings.isEmpty());
	}

	/** {@code color}に書いた値をsRGB成分で確かめます。 */
	private void assertColor(final String value, final float red, final float green, final float blue,
			final float eps) {
		final ColorValue color = (ColorValue) this.single("color", value);
		assertEquals(value + " の赤", red, color.getColor().getRed(), eps);
		assertEquals(value + " の緑", green, color.getColor().getGreen(), eps);
		assertEquals(value + " の青", blue, color.getColor().getBlue(), eps);
	}

	private void assertColor(final String value, final float red, final float green, final float blue) {
		this.assertColor(value, red, green, blue, EPS);
	}

	// ---- 1. 名前付き色

	public void testRebeccapurple() {
		// CSS Color 4 で追加された唯一の新しい名前付き色 #663399
		this.assertColor("rebeccapurple", 0x66 / 255f, 0x33 / 255f, 0x99 / 255f);
	}

	// ---- 2. hwb()

	public void testHwb() {
		// 色相0(赤)に白25%・黒25%。残り50%が純色ぶん
		this.assertColor("hwb(0 25% 25%)", 0.75f, 0.25f, 0.25f);
		this.assertColor("hwb(0 0% 0%)", 1f, 0f, 0f);
		this.assertColor("hwb(120 0% 0%)", 0f, 1f, 0f);
		// SPEC css-color-4 §7: 白+黒が100%を超えたら比で正規化する。
		// 60%:60% は 50%:50% と等しくなり、色相によらず中間グレー
		this.assertColor("hwb(0 60% 60%)", 0.5f, 0.5f, 0.5f);
		this.assertColor("hwb(240 60% 60%)", 0.5f, 0.5f, 0.5f);
		// アルファ
		final ColorValue alpha = (ColorValue) this.single("color", "hwb(0 0% 0% / 0.25)");
		assertEquals(0.25f, alpha.getColor().getAlpha(), EPS);
	}

	// ---- 3. lab() / lch()

	public void testLab() {
		// L*=100 は白、L*=0 は黒(a*=b*=0)
		this.assertColor("lab(100% 0 0)", 1f, 1f, 1f);
		this.assertColor("lab(0% 0 0)", 0f, 0f, 0f);
		// CSS Color 4 の例。赤橙になる
		this.assertColor("lab(50% 40 59.5)", 0.75f, 0.34f, 0f, 5e-3f);
	}

	public void testLch() {
		this.assertColor("lch(52.2% 72.2 50)", 0.81f, 0.34f, 0.10f, 5e-3f);
		// 彩度0は無彩色。L*=50 の sRGB は 0.4663 —— ここが合っていることが
		// Lab→XYZ→sRGB の変換全体が正しいことの要になるので、誤差を締める
		this.assertColor("lch(50% 0 0)", 0.4663f, 0.4663f, 0.4663f, 5e-3f);
		this.assertColor("lch(100% 0 0)", 1f, 1f, 1f);
	}

	// ---- 4. color() 関数記法

	public void testColorFunctionSrgb() {
		// sRGBはそのまま
		this.assertColor("color(srgb 1 0.5 0)", 1f, 0.5f, 0f, 1e-5f);
		this.assertColor("color(srgb 0 0 0)", 0f, 0f, 0f, 1e-5f);
		final ColorValue alpha = (ColorValue) this.single("color", "color(srgb 0 0 0 / 0.5)");
		assertEquals(0.5f, alpha.getColor().getAlpha(), EPS);
	}

	public void testColorFunctionWideGamut() {
		// display-p3 の (1, 0.5, 0) は sRGB では赤と青が色域外。
		// 線形化0.21404 → XYZ(0.5434, 0.3770, 0.0097) → 線形sRGB
		// (1.1768, 0.1810, -0.0365) → クランプしてガンマ符号化で
		// (1, 0.4626, 0)
		this.assertColor("color(display-p3 1 0.5 0)", 1f, 0.4626f, 0f, 5e-3f);
		// rec2020 の緑は sRGB の色域を大きく外れる。全成分がクランプされる
		this.assertColor("color(rec2020 0 1 0)", 0f, 1f, 0f, 1e-5f);
	}

	public void testColorFunctionXyz() {
		this.assertColor("color(xyz 0.4 0.2 0.1)", 0.9727f, 0f, 0.3267f, 5e-3f);
		// xyz は xyz-d65 の別名
		final ColorValue xyz = (ColorValue) this.single("color", "color(xyz 0.4 0.2 0.1)");
		final ColorValue d65 = (ColorValue) this.single("color", "color(xyz-d65 0.4 0.2 0.1)");
		assertEquals(xyz.getColor().getRed(), d65.getColor().getRed(), 1e-6f);
		assertEquals(xyz.getColor().getBlue(), d65.getColor().getBlue(), 1e-6f);
		// D50 は色順応を挟むので D65 とは違う色になる
		final ColorValue d50 = (ColorValue) this.single("color", "color(xyz-d50 0.4 0.2 0.1)");
		assertNotNull(d50);
	}

	public void testColorFunctionAllSpacesAccepted() {
		// 変換先の値までは見ないが、仕様の定義済み色空間がすべて通ること
		for (final String space : new String[] { "srgb", "srgb-linear", "display-p3", "a98-rgb", "prophoto-rgb",
				"rec2020", "xyz", "xyz-d50", "xyz-d65" }) {
			assertTrue(space + " が色にならない",
					this.single("color", "color(" + space + " 0.5 0.5 0.5)") instanceof ColorValue);
		}
	}

	public void testColorFunctionRejects() {
		this.assertInvalid("color", "color(no-such-space 1 1 1)");
		// 成分が足りない
		this.assertInvalid("color", "color(srgb 1 1)");
		// 成分が多い
		this.assertInvalid("color", "color(srgb 1 1 1 1)");
	}

	// ---- 5. hsl() の turn

	public void testHslTurn() {
		// 0.5turn = 180deg。角度単位が色相に効くこと
		final ColorValue turn = (ColorValue) this.single("color", "hsl(0.5turn 100% 50%)");
		final ColorValue deg = (ColorValue) this.single("color", "hsl(180 100% 50%)");
		assertEquals(deg.getColor().getRed(), turn.getColor().getRed(), 1e-5f);
		assertEquals(deg.getColor().getGreen(), turn.getColor().getGreen(), 1e-5f);
		assertEquals(deg.getColor().getBlue(), turn.getColor().getBlue(), 1e-5f);
		// 180deg のシアン
		this.assertColor("hsl(0.5turn 100% 50%)", 0f, 1f, 1f, 1e-5f);
	}

	// ---- 6. グラデーションの補間色空間

	public void testGradientInterpolationAccepted() {
		// SPEC css-images-4 の <color-interpolation-method>。指定色空間での
		// 補間は未実装で既存のsRGB補間へ落とすが、2026-08-30以前は
		// 「in」があるだけで宣言ごと無効になっていた
		assertTrue(this.single("background-image", "linear-gradient(in oklab, red, blue)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "linear-gradient(in srgb, red, blue)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "linear-gradient(in oklch, red, blue)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "radial-gradient(in oklch longer hue, red, blue)")
				instanceof RadialGradientValue);
		assertTrue(this.single("background-image", "conic-gradient(in oklab, red, blue)")
				instanceof ConicGradientValue);
		// 向きの指定と併記できること
		assertTrue(this.single("background-image", "linear-gradient(to right in oklab, red, blue)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "linear-gradient(45deg in oklab, red, blue)")
				instanceof LinearGradientValue);
	}

	public void testGradientInterpolationDoesNotAlterStops() {
		// 補間指定を足しても色停止点の色そのものは変わらないこと。
		// 「受理はされたが色が壊れた」を防ぐための表明
		final LinearGradientValue plain = (LinearGradientValue) this.single("background-image",
				"linear-gradient(red, blue)");
		final LinearGradientValue in = (LinearGradientValue) this.single("background-image",
				"linear-gradient(in oklab, red, blue)");
		assertEquals(plain.toString(), in.toString());
	}
}
