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
import net.zamasoft.foliojet.css.impl.property.background.BackgroundColor;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundImage;
import net.zamasoft.foliojet.css.impl.property.border.BorderColor;
import net.zamasoft.foliojet.css.impl.property.text.TextDecoration;
import net.zamasoft.foliojet.css.impl.property.text.TextDecorationAux;
import net.zamasoft.foliojet.css.impl.property.text.TextDecorationColor;
import net.zamasoft.foliojet.css.impl.property.text.TextStrokeColor;
import net.zamasoft.foliojet.css.impl.property.text.WhiteSpace;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PositionValue;
import net.zamasoft.foliojet.css.value.TextDecorationValue;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.WhiteSpaceValue;
import net.zamasoft.foliojet.css.value.css3.ConicGradientValue;
import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.css.value.css3.LinearGradientValue;
import net.zamasoft.foliojet.css.value.css3.RadialGradientValue;
import net.zamasoft.foliojet.css.value.css3.TextShadowValue;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 実サイト50件の変換で「invalid value」警告になっていたCSS値の対応
 * (2026-08-29)を、宣言解析の窓口({@code ElementPropertySet.parseDeclaration})
 * を通して固定します——受理されること、警告が出ないこと、期待どおりの
 * 値へ写ること。
 *
 * <ul>
 * <li>display の接頭辞別名({@code -webkit-flex}等)</li>
 * <li>ビューポート単位(vw/vh/vmin/vmax/svh/dvw/vi/vb、calc()/min()内も)</li>
 * <li>{@code env()}(safe-area-inset-*・フォールバック・calc()内)</li>
 * <li>{@code currentColor}(color/border/background/text-stroke/text-decoration)</li>
 * <li>グラデーション(rgb()新構文・接頭辞旧構文・-webkit-gradient()・
 * calc()位置・放射の単色近似・多層の先頭レイヤ)</li>
 * <li>text-shadow(ぼかし半径・色先行・複数影)</li>
 * <li>整列キーワード(baseline・self-*・left/right・safe/unsafe)</li>
 * <li>min-width/min-height: auto、text-decoration短縮形と個別指定</li>
 * <li>white-space/text-wrap/position/float/unicode-bidi/grid-templateの小物</li>
 * </ul>
 */
public class ModernCssValuesTest extends TestCase {

	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(ModernCssValuesTest.class.getClassLoader(),
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
						return "ModernCssValuesTest.UserAgent";
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

	/** 宣言が不受理(無効化か警告)になることを確かめます。 */
	private void rejected(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), null, false);
		assertTrue(name + ": " + value + " が受理された", property == null || !this.warnings.isEmpty());
	}

	private Value single(final String name, final String value) {
		final Entry[] entries = this.parse(name, value);
		assertEquals(1, entries.length);
		return entries[0].getValue();
	}

	private static Value entry(final Entry[] entries, final PrimitivePropertyInfo info) {
		for (final Entry e : entries) {
			if (e.getPrimitivePropertyInfo() == info) {
				return e.getValue();
			}
		}
		fail("構成要素が無い: " + info.getName());
		return null;
	}

	private void assertInvalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), null, false);
		assertNull(name + ": " + value + " が受理された", property);
		assertFalse("警告が出ていない: " + name + ": " + value, this.warnings.isEmpty());
	}

	/** 長さ(pt)。calc()の結果(割合成分0)も受ける。 */
	private static double pt(final Value value) {
		if (value instanceof AbsoluteLengthValue abs) {
			return abs.getLength();
		}
		if (value instanceof CalcLengthValue calc) {
			assertEquals(0.0, calc.getRatio(), 0.0);
			return calc.getAbsolute();
		}
		fail("長さでない: " + value);
		return Double.NaN;
	}

	private static double mm(final double mm) {
		return mm / 25.4 * 72;
	}

	// ---- 1. display の別名

	public void testDisplayAliases() {
		assertSame(DisplayValue.FLEX_VALUE, this.single("display", "-webkit-flex"));
		assertSame(DisplayValue.FLEX_VALUE, this.single("display", "-moz-flex"));
		assertSame(DisplayValue.FLEX_VALUE, this.single("display", "-ms-flexbox"));
		assertSame(DisplayValue.FLEX_VALUE, this.single("display", "-webkit-inline-flex"));
		assertSame(DisplayValue.FLEX_VALUE, this.single("display", "-ms-inline-flexbox"));
		assertSame(DisplayValue.GRID_VALUE, this.single("display", "-ms-grid"));
		// 2009年版boxはブロック(line-clamp慣用句を壊さない)
		assertSame(DisplayValue.BLOCK_VALUE, this.single("display", "-webkit-box"));
		assertSame(DisplayValue.BLOCK_VALUE, this.single("display", "-moz-box"));
		assertSame(DisplayValue.BLOCK_VALUE, this.single("display", "-webkit-inline-box"));
		this.assertInvalid("display", "-webkit-unknown");
	}

	// ---- 2. ビューポート単位(既定A4・余白12.7mmの版面: 184.6mm × 271.6mm)

	public void testViewportUnits() {
		final double vw = mm(210 - 25.4) / 100;
		final double vh = mm(297 - 25.4) / 100;
		assertEquals(100 * vw, pt(this.single("width", "100vw")), 1e-6);
		assertEquals(10 * vh, pt(this.single("height", "10vh")), 1e-6);
		assertEquals(50 * vw, pt(this.single("width", "50vmin")), 1e-6);
		assertEquals(50 * vh, pt(this.single("width", "50vmax")), 1e-6);
		// 新しい変種は印刷では同じ値
		assertEquals(10 * vh, pt(this.single("height", "10svh")), 1e-6);
		assertEquals(10 * vh, pt(this.single("height", "10lvh")), 1e-6);
		assertEquals(10 * vh, pt(this.single("height", "10dvh")), 1e-6);
		assertEquals(10 * vw, pt(this.single("width", "10dvw")), 1e-6);
		assertEquals(10 * vw, pt(this.single("width", "10vi")), 1e-6);
		assertEquals(10 * vh, pt(this.single("height", "10vb")), 1e-6);
	}

	public void testViewportUnitsInsideMath() {
		final double vw = mm(210 - 25.4) / 100;
		// min(192px, 100vh) = 144pt(192px)
		assertEquals(144, pt(this.single("width", "min(192px, 100vh)")), 1e-6);
		assertEquals(100 * vw - 20, pt(this.single("width", "calc(100vw - 20pt)")), 1e-6);
		assertEquals(100 * vw, pt(this.single("width", "max(100vw, 10px)")), 1e-6);
		assertEquals(50 * vw, pt(this.single("width", "clamp(10px, 50vw, 1000pt)")), 1e-6);
		// マージン・パディングでも同じ経路
		final Entry[] margin = this.parse("margin", "0 auto 2vh");
		assertEquals(4, margin.length);
	}

	// ---- 3. env()

	public void testEnvKnownNames() {
		assertEquals(0, pt(this.single("padding-left", "env(safe-area-inset-left)")), 0);
		assertEquals(0, pt(this.single("padding-top", "env(titlebar-area-height)")), 0);
		assertEquals(0, pt(this.single("padding-top", "env(safe-area-inset-top, 20px)")), 0);
		// calc()/max()の中
		assertEquals(12, pt(this.single("padding-left", "calc(env(safe-area-inset-top) + 16px)")), 1e-6);
		assertEquals(12, pt(this.single("padding-right", "max(16px, env(safe-area-inset-right))")), 1e-6);
		// 4値の短縮形の中でも各値へ展開される
		final Entry[] padding = this.parse("padding", "env(safe-area-inset-top) 10pt");
		assertEquals(4, padding.length);
	}

	public void testEnvUnknownName() {
		assertEquals(100, pt(this.single("width", "env(unknown-thing, 100pt)")), 0);
		// フォールバック無しの未知名は宣言無効(仕様)
		this.assertInvalid("width", "env(unknown-thing)");
	}

	// ---- 4. currentColor

	public void testCurrentColor() {
		assertSame(KeywordValue.INHERIT, this.single("color", "currentColor"));
		assertSame(KeywordValue.INHERIT, this.single("color", "currentcolor"));
		final Entry[] borderColor = this.parse("border-color", "currentColor");
		assertEquals(4, borderColor.length);
		for (final Entry e : borderColor) {
			assertSame(KeywordValue.DEFAULT, e.getValue());
		}
		assertSame(KeywordValue.DEFAULT, entry(this.parse("border", "1px solid currentColor"), BorderColor.TOP));
		assertSame(KeywordValue.DEFAULT, entry(this.parse("border-top", "1px solid currentcolor"), BorderColor.TOP));
		assertSame(KeywordValue.DEFAULT, this.single("border-top-color", "currentColor"));
		assertSame(KeywordValue.DEFAULT, this.single("border-inline-start-color", "currentColor"));
		assertSame(KeywordValue.DEFAULT, this.single("background-color", "currentColor"));
		assertSame(KeywordValue.DEFAULT, entry(this.parse("background", "currentColor"), BackgroundColor.INFO));
		assertSame(KeywordValue.DEFAULT,
				entry(this.parse("-webkit-text-stroke", "1px currentColor"), TextStrokeColor.INFO));
		assertSame(KeywordValue.DEFAULT,
				entry(this.parse("text-decoration", "underline currentColor"), TextDecorationColor.INFO));
		assertSame(KeywordValue.DEFAULT, this.single("column-rule-color", "currentColor"));
		// text-shadowのcurrentColorは「色なし=文字色」
		final TextShadowValue shadow = (TextShadowValue) this.single("text-shadow", "currentColor 0 1px");
		assertEquals(1, shadow.getShadows().length);
		assertNull(shadow.getShadows()[0].color);
	}

	// ---- 5. グラデーションと新しい色構文

	public void testModernRgbSyntax() {
		final ColorValue half = (ColorValue) this.single("color", "rgb(0 0 0 / 50%)");
		assertEquals(0.5f, half.getColor().getAlpha(), 1e-6f);
		final ColorValue opaque = (ColorValue) this.single("color", "rgb(255 255 255)");
		assertEquals(1f, opaque.getColor().getAlpha(), 1e-6f);
		final ColorValue legacy = (ColorValue) this.single("color", "rgba(0,0,0,.3)");
		assertEquals(0.3f, legacy.getColor().getAlpha(), 1e-6f);
		final ColorValue slash = (ColorValue) this.single("color", "rgba(0 0 0 / .25)");
		assertEquals(0.25f, slash.getColor().getAlpha(), 1e-6f);
	}

	public void testGradients() {
		assertTrue(entry(this.parse("background",
				"linear-gradient(to right, rgb(0 0 0 / 50%), rgba(0,0,0,.3), rgb(255 255 255))"),
				BackgroundImage.INFO) instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "linear-gradient(180deg, #fff 0%, #000 100%)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "linear-gradient(0.25turn, #fff, #000)")
				instanceof LinearGradientValue);
		// 接頭辞つき旧構文(向きは開始辺、角度は反時計回り)
		assertTrue(this.single("background-image", "-webkit-linear-gradient(top, #fff, #000)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "-moz-linear-gradient(left, #fff 0%, #000 100%)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "-o-linear-gradient(45deg, #fff, #000)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "-webkit-linear-gradient(to bottom, #fff, #000)")
				instanceof LinearGradientValue);
		// 2008年版WebKit構文
		assertTrue(this.single("background-image",
				"-webkit-gradient(linear, left top, right top, from(#fff), color-stop(50%, #888), to(#000))")
				instanceof LinearGradientValue);
		// calc()位置・長さ位置
		assertTrue(this.single("background-image", "linear-gradient(#fff, transparent calc(100% - 1px))")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "linear-gradient(to bottom, #fff 10px, #000 40px)")
				instanceof LinearGradientValue);
		// repeating-は1回分として近似
		assertTrue(this.single("background-image", "repeating-linear-gradient(45deg, #fff, #000 10px)")
				instanceof LinearGradientValue);
	}

	public void testRadialConicRepeatingGradients() {
		// 放射(2026-08-29に本実装)。形状・寸法・位置と色停止を持つ
		final Value radial = this.single("background-image", "radial-gradient(circle at center, #fff, #000)");
		assertTrue(radial instanceof RadialGradientValue);
		assertTrue(((RadialGradientValue) radial).isCircle());
		assertEquals(RadialGradientValue.Size.FARTHEST_CORNER, ((RadialGradientValue) radial).getSize());
		final RadialGradientValue sized = (RadialGradientValue) this.single("background-image",
				"radial-gradient(ellipse closest-side at 20% 80%, red 10%, blue 90%)");
		assertFalse(sized.isCircle());
		assertEquals(RadialGradientValue.Size.CLOSEST_SIDE, sized.getSize());
		final RadialGradientValue explicit = (RadialGradientValue) this.single("background-image",
				"radial-gradient(40px 20px at 10px 10px, #fff, #000)");
		assertEquals(RadialGradientValue.Size.EXPLICIT, explicit.getSize());
		assertFalse(explicit.isCircle());
		assertTrue(((RadialGradientValue) this.single("background-image", "radial-gradient(30px, #fff, #000)"))
				.isCircle());
		assertTrue(this.single("background-image", "radial-gradient(farthest-side circle, #fff, #000)")
				instanceof RadialGradientValue);
		assertTrue(this.single("background-image", "radial-gradient(#fff, #000)") instanceof RadialGradientValue);
		// 旧構文: 位置, 形状 寸法(contain/cover)
		final RadialGradientValue legacy = (RadialGradientValue) this.single("background-image",
				"-webkit-radial-gradient(center, ellipse cover, #fff, #123)");
		assertFalse(legacy.isCircle());
		assertEquals(RadialGradientValue.Size.FARTHEST_CORNER, legacy.getSize());
		assertTrue(this.single("background-image", "-moz-radial-gradient(circle closest-side, #fff, #000)")
				instanceof RadialGradientValue);
		assertTrue(this.single("background-image",
				"-webkit-gradient(radial, center center, 0, center center, 100, from(#fff), to(#000))")
				instanceof RadialGradientValue);
		// 円錐
		final Value conic = this.single("background-image", "conic-gradient(from 90deg, red, blue)");
		assertTrue(conic instanceof ConicGradientValue);
		assertEquals(Math.PI / 2, ((ConicGradientValue) conic).getFromAngle(), 1e-9);
		assertTrue(this.single("background-image",
				"conic-gradient(from 0.25turn at 25% 75%, red 0deg 90deg, lime 90deg 180deg, blue)")
				instanceof ConicGradientValue);
		assertTrue(this.single("background-image", "conic-gradient(red 25%, blue 75%)") instanceof ConicGradientValue);
		// 繰り返し
		assertTrue(((LinearGradientValue) this.single("background-image",
				"repeating-linear-gradient(45deg, #fff, #000 10px)")).isRepeating());
		assertTrue(((RadialGradientValue) this.single("background-image",
				"repeating-radial-gradient(circle, #fff 0 4px, #000 4px 8px)")).isRepeating());
		assertTrue(((ConicGradientValue) this.single("background-image",
				"repeating-conic-gradient(red 0 15deg, blue 15deg 30deg)")).isRepeating());
		// 色停止の長さは保持される(周期に要る)
		assertEquals("#ffffff,#000000 10.00pt", ((LinearGradientValue) this.single("background-image",
				"linear-gradient(#fff, #000 10pt)")).getStops().toString());
		// 不正: 円の半径に%
		this.rejected("background-image", "radial-gradient(circle 50%, #fff, #000)");
	}

	public void testFilter() {
		assertTrue(this.single("filter", "none") instanceof FilterValue);
		assertTrue(((FilterValue) this.single("filter", "none")).isNone());
		final FilterValue gray = (FilterValue) this.single("filter", "grayscale(100%)");
		assertNotNull(gray.matrix);
		assertNull(gray.shadow);
		final FilterValue multi = (FilterValue) this.single("filter",
				"blur(2px) brightness(1.2) contrast(80%) drop-shadow(2px 4px 6px rgba(0,0,0,.5)) opacity(50%) "
						+ "saturate(2) sepia(1) hue-rotate(90deg) invert(1)");
		assertEquals(0.5f, multi.opacity, 1e-6f);
		assertTrue(multi.blur > 0);
		assertNotNull(multi.shadow);
		assertEquals(6 * 0.75, multi.shadow.blur(), 1e-6);
		assertNotNull(multi.matrix);
		assertTrue(((FilterValue) this.single("filter", "drop-shadow(#f00 1px 1px)")).shadow.color().getRed() > 0.9f);
		// url()は受理して無視
		assertTrue(this.single("filter", "url(#blur)") instanceof FilterValue);
		this.rejected("filter", "foo(1)");
		this.rejected("filter", "grayscale(-1)");
		this.rejected("filter", "drop-shadow(1px)");
	}

	public void testMultiLayerBackgroundTakesFirstLayer() {
		final Entry[] entries = this.parse("background", "linear-gradient(red, blue), #fff");
		assertTrue(entry(entries, BackgroundImage.INFO) instanceof LinearGradientValue);
		final ColorValue color = (ColorValue) entry(entries, BackgroundColor.INFO);
		assertEquals(1f, color.getColor().getRed(), 1e-6f);
		// グラデーションと色の併記(単層)
		final Entry[] mixed = this.parse("background", "#000 linear-gradient(red, blue) no-repeat");
		assertTrue(entry(mixed, BackgroundImage.INFO) instanceof LinearGradientValue);
		assertTrue(entry(mixed, BackgroundColor.INFO) instanceof ColorValue);
	}

	// ---- 6. text-shadow

	public void testTextShadow() {
		assertEquals(1, ((TextShadowValue) this.single("text-shadow", "0 -1px 0 rgba(0,0,0,.3)")).getShadows().length);
		assertEquals(1, ((TextShadowValue) this.single("text-shadow", "0 1px 0 rgb(255 255 255)")).getShadows().length);
		assertEquals(1, ((TextShadowValue) this.single("text-shadow", "0 1px 4px rgb(0 0 0)")).getShadows().length);
		final TextShadowValue two = (TextShadowValue) this.single("text-shadow",
				"0 1px 0 rgb(0 0 0 / 30%), 0 -1px 2px #fff");
		assertEquals(2, two.getShadows().length);
		assertEquals(0.3f, two.getShadows()[0].color.getColor().getAlpha(), 1e-6f);
		// 色が先
		final TextShadowValue first = (TextShadowValue) this.single("text-shadow", "red 1px 1px");
		assertNotNull(first.getShadows()[0].color);
		// 従来の形は退行しない
		assertEquals(1, ((TextShadowValue) this.single("text-shadow", "2pt 3pt red")).getShadows().length);
		assertSame(TextShadowValue.EMPTY_TEXT_SHADOW, this.single("text-shadow", "none"));
		this.assertInvalid("text-shadow", "red");
		this.assertInvalid("text-shadow", "1px 1px 1px 1px red");
	}

	// ---- 7. 整列キーワード

	public void testAlignmentKeywords() {
		assertSame(BoxAlignmentValue.FLEX_START, this.single("align-items", "baseline"));
		assertSame(BoxAlignmentValue.FLEX_START, this.single("align-items", "first baseline"));
		assertSame(BoxAlignmentValue.FLEX_START, this.single("align-self", "last baseline"));
		assertSame(BoxAlignmentValue.FLEX_START, this.single("align-content", "baseline"));
		assertSame(BoxAlignmentValue.START, this.single("justify-self", "self-start"));
		assertSame(BoxAlignmentValue.END, this.single("align-self", "self-end"));
		assertSame(BoxAlignmentValue.START, this.single("justify-content", "left"));
		assertSame(BoxAlignmentValue.END, this.single("justify-items", "right"));
		assertSame(BoxAlignmentValue.CENTER, this.single("align-items", "safe center"));
		assertSame(BoxAlignmentValue.FLEX_END, this.single("justify-content", "unsafe flex-end"));
		// place-*短縮形にも効く
		assertEquals(2, this.parse("place-items", "baseline center").length);
		// align-*にleft/rightは無い
		this.assertInvalid("align-items", "left");
		this.assertInvalid("align-items", "first");
	}

	// ---- 8. min-*: auto、text-decoration

	public void testMinSizeAuto() {
		assertSame(AbsoluteLengthValue.ZERO, this.single("min-width", "auto"));
		assertSame(AbsoluteLengthValue.ZERO, this.single("min-height", "auto"));
		assertSame(AbsoluteLengthValue.ZERO, this.single("min-inline-size", "auto"));
		assertSame(AbsoluteLengthValue.ZERO, this.single("min-block-size", "auto"));
	}

	public void testTextDecorationShorthand() {
		Entry[] entries = this.parse("text-decoration", "underline dotted");
		assertEquals(TextDecorationValue.UNDERLINE, ((TextDecorationValue) entry(entries, TextDecoration.INFO)).getFlags());
		assertEquals("dotted", entry(entries, TextDecorationAux.STYLE).toString());
		assertSame(KeywordValue.DEFAULT, entry(entries, TextDecorationColor.INFO));

		entries = this.parse("text-decoration", "underline wavy #999 2px");
		assertEquals(TextDecorationValue.UNDERLINE, ((TextDecorationValue) entry(entries, TextDecoration.INFO)).getFlags());
		assertTrue(entry(entries, TextDecorationColor.INFO) instanceof ColorValue);
		assertEquals(1.5, pt(entry(entries, TextDecorationAux.THICKNESS)), 1e-6);

		entries = this.parse("text-decoration", "red underline overline");
		assertEquals(TextDecorationValue.UNDERLINE | TextDecorationValue.OVERLINE,
				((TextDecorationValue) entry(entries, TextDecoration.INFO)).getFlags());

		entries = this.parse("text-decoration", "none");
		assertEquals(0, ((TextDecorationValue) entry(entries, TextDecoration.INFO)).getFlags());
		// 色だけの指定も短縮形として正当(線種はnone)
		entries = this.parse("text-decoration", "red");
		assertEquals(0, ((TextDecorationValue) entry(entries, TextDecoration.INFO)).getFlags());
		this.assertInvalid("text-decoration", "underline none");
		this.assertInvalid("text-decoration", "bogus");
	}

	public void testTextDecorationLonghands() {
		assertEquals(TextDecorationValue.LINE_THROUGH,
				((TextDecorationValue) this.single("text-decoration-line", "line-through")).getFlags());
		assertTrue(this.single("text-decoration-color", "#999") instanceof ColorValue);
		assertSame(KeywordValue.DEFAULT, this.single("text-decoration-color", "currentColor"));
		assertEquals("wavy", this.single("text-decoration-style", "wavy").toString());
		assertEquals(1.5, pt(this.single("text-decoration-thickness", "2px")), 1e-6);
		assertSame(KeywordValue.NORMAL, this.single("text-decoration-thickness", "from-font"));
		assertSame(KeywordValue.AUTO, this.single("text-underline-offset", "auto"));
		assertNotNull(this.single("text-underline-offset", "0.2em"));
		assertNotNull(this.single("text-underline-offset", "-1px"));
		assertSame(KeywordValue.DEFAULT, this.single("-webkit-text-decoration-color", "currentColor"));
		this.assertInvalid("text-decoration-style", "bogus");
		this.assertInvalid("text-decoration-line", "dotted");
	}

	// ---- 9. 小物

	public void testWhiteSpaceAndTextWrap() {
		assertSame(WhiteSpaceValue.PRE_WRAP_VALUE, this.single("white-space", "break-spaces"));
		assertSame(WhiteSpaceValue.PRE_WRAP_VALUE, this.single("white-space", "-moz-pre-wrap"));
		assertSame(WhiteSpaceValue.PRE_WRAP_VALUE, this.single("white-space", "-pre-wrap"));
		assertSame(WhiteSpaceValue.PRE_WRAP_VALUE, this.single("white-space", "-o-pre-wrap"));
		assertSame(WhiteSpaceValue.NORMAL_VALUE, this.single("white-space", "wrap"));
		assertSame(WhiteSpaceValue.NOWRAP_VALUE, entry(this.parse("text-wrap", "nowrap"), WhiteSpace.INFO));
		assertEquals(0, this.parse("text-wrap", "wrap").length);
		assertEquals(1, this.parse("text-wrap", "pretty").length);
		assertEquals(2, this.parse("text-wrap", "nowrap balance").length);
		this.assertInvalid("text-wrap", "bogus");
	}

	public void testPositionFloatBidi() {
		assertSame(PositionValue.STICKY_VALUE, this.single("position", "-webkit-sticky"));
		assertSame(CSSFloatValue.START_VALUE, this.single("float", "inline-start"));
		assertSame(CSSFloatValue.END_VALUE, this.single("float", "inline-end"));
		// 2026-09-04: isolate系は値のまま保つ(段落単位UBAのflag OFFではレイアウト側が従来どおり扱う)
		assertSame(UnicodeBidiValue.ISOLATE_VALUE, this.single("unicode-bidi", "isolate"));
		assertSame(UnicodeBidiValue.ISOLATE_VALUE, this.single("unicode-bidi", "-moz-isolate"));
		assertSame(UnicodeBidiValue.ISOLATE_VALUE, this.single("unicode-bidi", "-webkit-isolate"));
		assertSame(UnicodeBidiValue.ISOLATE_OVERRIDE_VALUE, this.single("unicode-bidi", "-webkit-isolate-override"));
		assertSame(UnicodeBidiValue.PLAINTEXT_VALUE, this.single("unicode-bidi", "-webkit-plaintext"));
		assertEquals("isolate", UnicodeBidiValue.ISOLATE_VALUE.toString());
		assertEquals("plaintext", UnicodeBidiValue.PLAINTEXT_VALUE.toString());
	}

	public void testGridTracks() {
		assertNotNull(this.single("grid-template-columns", "100%"));
		assertNotNull(this.single("grid-template-columns", "repeat(4, 25%)"));
		assertNotNull(this.single("grid-template-columns", "min-content max-content auto"));
		assertNotNull(this.single("grid-template-columns", "minmax(0, auto) 1fr"));
		assertNotNull(this.single("grid-template-columns", "fit-content(200px) 1fr"));
		assertNotNull(this.single("grid-template-rows", "subgrid"));
		assertNotNull(this.single("grid-template-columns", "minmax(200px, 30%)"));
		this.assertInvalid("grid-template-columns", "-10%");
	}
}
