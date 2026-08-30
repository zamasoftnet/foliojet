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
import net.zamasoft.foliojet.css.impl.property.background.BackgroundOrigin;
import net.zamasoft.foliojet.css.impl.property.border.LogicalBorder;
import net.zamasoft.foliojet.css.impl.property.border.LogicalBorder.Aspect;
import net.zamasoft.foliojet.css.impl.property.box.LogicalSide;
import net.zamasoft.foliojet.css.impl.property.box.MaskClip;
import net.zamasoft.foliojet.css.impl.property.box.MaskComposite;
import net.zamasoft.foliojet.css.impl.property.box.MaskMode;
import net.zamasoft.foliojet.css.impl.property.box.MaskOrigin;
import net.zamasoft.foliojet.css.impl.property.box.Overflow;
import net.zamasoft.foliojet.css.impl.property.font.FontPalette;
import net.zamasoft.foliojet.css.impl.property.font.FontSynthesisSmallCaps;
import net.zamasoft.foliojet.css.impl.property.font.FontVariantAlternates;
import net.zamasoft.foliojet.css.impl.property.font.FontVariantCaps;
import net.zamasoft.foliojet.css.impl.property.font.FontVariantLigatures;
import net.zamasoft.foliojet.css.impl.property.text.TextEmphasisPosition;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.FontPaletteValue;
import net.zamasoft.foliojet.css.value.FontVariantAlternatesValue;
import net.zamasoft.foliojet.css.value.FontVariantLigaturesValue;
import net.zamasoft.foliojet.css.value.FontVariantValue;
import net.zamasoft.foliojet.css.value.TextEmphasisPositionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BackgroundOriginValue;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 2026-08-30に実装したプロパティ群の受理と写しを固定します——マスクの
 * ロングハンド4件、{@code font-variant-*}3件と{@code font-palette}、
 * 論理境界の短縮形、{@code overflow-block}/{@code -inline}、
 * {@code background-origin}、{@code text-emphasis-position}。
 *
 * <p>
 * どれも「受理されるか」より<b>「受理されて、正しいロングハンドへ、正しい値で
 * 落ちるか」</b>が要点である。受理だけを見ると、短縮形が展開を取りこぼしていても
 * 緑になってしまう。
 *
 * <p>
 * 描画まで届いていない項目({@code mask-mode}/{@code mask-composite}/
 * {@code font-palette}など)は<b>意図的に受理・保持だけ</b>なので、ここでも
 * 値が保たれることまでを見る。近似で見た目を変えないという判断そのものが
 * 仕様なので、それを崩す変更はこのテストで落ちてほしい。
 */
public class BaselineCssPropertyTest extends TestCase {

	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(BaselineCssPropertyTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
					case "getFontSize":
						return 12.0;
					case "getFontMagnification":
						return 1.0;
					case "getDefaultFontFamily":
						// font 短縮形は既定ファミリを暗黙に足す
						// ([[copperpdf4-font-family-css-asymmetry]])
						return net.zamasoft.foliojet.css.value.FontFamilyValue.SERIF;
					case "getDocumentContext":
						return new DocumentContext();
					case "getProperty":
						return null;
					case "message":
						this.warnings.add(String.valueOf(args[0]) + ":" + java.util.Arrays.toString(args));
						return null;
					case "toString":
						return "BaselineCssPropertyTest.UserAgent";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}

	/** {@code url()}の解決に使う基底URI。nullだと mask 等の短縮形が落ちる。 */
	private static final java.net.URI BASE_URI = java.net.URI.create("file:///dev/null/");

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
				this.ua(), BASE_URI, false);
		assertNotNull(name + ": " + value + " が無効になった " + this.warnings, property);
		assertTrue(name + ": " + value + " で警告 " + this.warnings, this.warnings.isEmpty());
		assertTrue(property instanceof CompositeProperty);
		return ((CompositeProperty) property).getEntries();
	}

	private Value single(final String name, final String value) {
		final Entry[] entries = this.parse(name, value);
		assertEquals(name + ": " + value + " の構成要素数", 1, entries.length);
		return entries[0].getValue();
	}

	/** 短縮形の中から、指定ロングハンドへ落ちた値を取り出します。 */
	private Value longhand(final String name, final String value, final PrimitivePropertyInfo info) {
		for (final Entry e : this.parse(name, value)) {
			if (e.getPrimitivePropertyInfo() == info) {
				return e.getValue();
			}
		}
		fail(name + ": " + value + " が " + info.getName() + " を設定していない");
		return null;
	}

	private void assertInvalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), BASE_URI, false);
		assertTrue(name + ": " + value + " が黙って受理された",
				property == null || !this.warnings.isEmpty());
	}

	/** 値が受理され、警告なしで通ることだけを確かめます。 */
	private void accepted(final String name, final String... values) {
		for (final String value : values) {
			assertNotNull(name + ": " + value, this.single(name, value));
		}
	}

	private static double pt(final Value value) {
		assertTrue("長さでない: " + value, value instanceof AbsoluteLengthValue);
		return ((AbsoluteLengthValue) value).getLength();
	}

	// ---- 1. background-origin

	public void testBackgroundOrigin() {
		assertEquals(BackgroundOriginValue.BORDER_BOX,
				((BackgroundOriginValue) this.single("background-origin", "border-box")).getBackgroundOrigin());
		assertEquals(BackgroundOriginValue.PADDING_BOX,
				((BackgroundOriginValue) this.single("background-origin", "padding-box")).getBackgroundOrigin());
		assertEquals(BackgroundOriginValue.CONTENT_BOX,
				((BackgroundOriginValue) this.single("background-origin", "content-box")).getBackgroundOrigin());
		// 多層
		assertNotNull(this.single("background-origin", "border-box, content-box"));
		this.assertInvalid("background-origin", "margin-box");
	}

	public void testBackgroundShorthandSetsOrigin() {
		// background 短縮形が origin も設定すること(短縮形は初期化も担う)
		assertNotNull(this.longhand("background", "red content-box", BackgroundOrigin.INFO));
	}

	// ---- 2. background-position-x / -y

	public void testBackgroundPositionAxis() {
		this.accepted("background-position-x", "left", "center", "right", "10pt", "25%");
		this.accepted("background-position-y", "top", "center", "bottom", "10pt", "25%");
		assertNotNull(this.single("background-position-x", "10pt, 20pt"));
		// 軸違いのキーワードは拒否する(-x に top は無い)
		this.assertInvalid("background-position-x", "top");
		this.assertInvalid("background-position-y", "left");
	}

	// ---- 3. background-blend-mode

	public void testBackgroundBlendMode() {
		this.accepted("background-blend-mode", "normal", "multiply", "screen", "overlay", "darken", "lighten",
				"color-dodge", "color-burn", "hard-light", "soft-light", "difference", "exclusion", "hue",
				"saturation", "color", "luminosity");
		assertNotNull(this.single("background-blend-mode", "multiply, screen"));
		this.assertInvalid("background-blend-mode", "no-such-mode");
	}

	// ---- 4. overflow-block / overflow-inline

	public void testOverflowLogical() {
		this.accepted("overflow-block", "visible", "hidden", "clip", "scroll", "auto");
		this.accepted("overflow-inline", "visible", "hidden", "clip", "scroll", "auto");
		this.assertInvalid("overflow-block", "no-such-value");
	}

	public void testOverflowLogicalIsSeparateFromPhysical() {
		// 論理版は物理版とは別のロングハンドへ落ちる。同じ枠へ潰していると
		// 書き分けたときにどちらかが消えるので、別であることを固定する
		assertNotNull(this.longhand("overflow-block", "hidden", Overflow.INFO_BLOCK));
		assertNotNull(this.longhand("overflow-inline", "hidden", Overflow.INFO_INLINE));
		assertNotNull(this.longhand("overflow-x", "hidden", Overflow.INFO_X));
		assertNotNull(this.longhand("overflow-y", "hidden", Overflow.INFO_Y));
	}

	// ---- 5. マスクのロングハンド4件

	public void testMaskOriginAndClip() {
		this.accepted("mask-origin", "border-box", "padding-box", "content-box", "fill-box", "stroke-box",
				"view-box");
		this.accepted("mask-clip", "border-box", "padding-box", "content-box", "fill-box", "stroke-box", "view-box",
				"no-clip");
		// no-clip は mask-clip だけの値
		this.assertInvalid("mask-origin", "no-clip");
		this.assertInvalid("mask-clip", "margin-box");
		// 多層
		assertNotNull(this.single("mask-origin", "border-box, content-box"));
		assertNotNull(this.single("mask-clip", "border-box, no-clip"));
	}

	public void testMaskMode() {
		assertSame(MaskMode.ModeValue.ALPHA, this.single("mask-mode", "alpha"));
		assertSame(MaskMode.ModeValue.LUMINANCE, this.single("mask-mode", "luminance"));
		assertSame(MaskMode.ModeValue.MATCH_SOURCE, this.single("mask-mode", "match-source"));
		final Value layers = this.single("mask-mode", "alpha, luminance");
		assertTrue(layers instanceof MaskMode.LayersValue);
		assertEquals(2, ((MaskMode.LayersValue) layers).layers().length);
		this.assertInvalid("mask-mode", "no-such-mode");
	}

	public void testMaskComposite() {
		this.accepted("mask-composite", "add", "subtract", "intersect", "exclude");
		assertNotNull(this.single("mask-composite", "add, subtract"));
		this.assertInvalid("mask-composite", "no-such-op");
	}

	public void testMaskShorthandInitialisesNewLonghands() {
		// 短縮形は自分が触らないロングハンドも初期値へ戻す。ここが抜けていると
		// 直前の規則の mask-mode 等が残って効き続ける
		final Entry[] entries = this.parse("mask", "url(a.png)");
		boolean origin = false, clip = false, mode = false, composite = false;
		for (final Entry e : entries) {
			origin |= e.getPrimitivePropertyInfo() == MaskOrigin.INFO;
			clip |= e.getPrimitivePropertyInfo() == MaskClip.INFO;
			mode |= e.getPrimitivePropertyInfo() == MaskMode.INFO;
			composite |= e.getPrimitivePropertyInfo() == MaskComposite.INFO;
		}
		assertTrue("mask が mask-origin を初期化していない", origin);
		assertTrue("mask が mask-clip を初期化していない", clip);
		assertTrue("mask が mask-mode を初期化していない", mode);
		assertTrue("mask が mask-composite を初期化していない", composite);
	}

	// ---- 6. font-variant-caps

	public void testFontVariantCaps() {
		assertSame(FontVariantValue.NORMAL_VALUE, this.single("font-variant-caps", "normal"));
		assertSame(FontVariantValue.SMALL_CAPS_VALUE, this.single("font-variant-caps", "small-caps"));
		assertSame(FontVariantValue.ALL_SMALL_CAPS_VALUE, this.single("font-variant-caps", "all-small-caps"));
		assertSame(FontVariantValue.PETITE_CAPS_VALUE, this.single("font-variant-caps", "petite-caps"));
		assertSame(FontVariantValue.ALL_PETITE_CAPS_VALUE, this.single("font-variant-caps", "all-petite-caps"));
		assertSame(FontVariantValue.UNICASE_VALUE, this.single("font-variant-caps", "unicase"));
		assertSame(FontVariantValue.TITLING_CAPS_VALUE, this.single("font-variant-caps", "titling-caps"));
		this.assertInvalid("font-variant-caps", "no-such-caps");
	}

	public void testFontVariantCapsFeatureTags() {
		// OpenType featureへの写しまで見る。ここが空だと「受理はされたが
		// 何も起きない」状態に退行しても気づけない
		assertTrue(FontVariantValue.SMALL_CAPS_VALUE.featureSet().toString().contains("smcp"));
		final String all = FontVariantValue.ALL_SMALL_CAPS_VALUE.featureSet().toString();
		assertTrue(all.contains("smcp"));
		assertTrue(all.contains("c2sc"));
	}

	public void testFontVariantShorthandReachesCaps() {
		// 既存挙動の回帰防止: font-variant と font 短縮形が caps へ届くこと
		assertSame(FontVariantValue.SMALL_CAPS_VALUE,
				this.longhand("font-variant", "small-caps", FontVariantCaps.INFO));
		assertSame(FontVariantValue.SMALL_CAPS_VALUE,
				this.longhand("font", "small-caps 12pt serif", FontVariantCaps.INFO));
		assertSame(FontVariantValue.NORMAL_VALUE, this.longhand("font", "12pt serif", FontVariantCaps.INFO));
	}

	// ---- 7. font-variant-ligatures

	public void testFontVariantLigatures() {
		assertSame(FontVariantLigaturesValue.NORMAL_VALUE, this.single("font-variant-ligatures", "normal"));
		assertSame(FontVariantLigaturesValue.NONE_VALUE, this.single("font-variant-ligatures", "none"));
		this.accepted("font-variant-ligatures", "common-ligatures", "no-common-ligatures",
				"discretionary-ligatures", "no-discretionary-ligatures", "historical-ligatures",
				"no-historical-ligatures", "contextual", "no-contextual");
		// 組み合わせ(順序自由)
		assertNotNull(this.single("font-variant-ligatures", "no-common-ligatures discretionary-ligatures"));
		assertNotNull(this.single("font-variant-ligatures", "contextual historical-ligatures"));
		// 同じ対の両方は書けない
		this.assertInvalid("font-variant-ligatures", "common-ligatures no-common-ligatures");
		// none と個別値は併記できない
		this.assertInvalid("font-variant-ligatures", "none contextual");
		this.assertInvalid("font-variant-ligatures", "no-such-ligature");
	}

	public void testFontVariantLigaturesIsNotNormalWhenSpecified() {
		final FontVariantLigaturesValue value = (FontVariantLigaturesValue) this
				.single("font-variant-ligatures", "no-common-ligatures");
		assertFalse(value.isNormal());
		assertTrue(FontVariantLigaturesValue.NORMAL_VALUE.isNormal());
	}

	// ---- 8. font-variant-alternates

	public void testFontVariantAlternates() {
		assertSame(FontVariantAlternatesValue.NORMAL_VALUE, this.single("font-variant-alternates", "normal"));
		final FontVariantAlternatesValue historical = (FontVariantAlternatesValue) this
				.single("font-variant-alternates", "historical-forms");
		assertTrue(historical.hasHistoricalForms());
		// 関数形式は @font-feature-values の名前解決が無いので保持のみ。
		// それでも「受理して保持する」ことは固定しておく
		final FontVariantAlternatesValue styled = (FontVariantAlternatesValue) this
				.single("font-variant-alternates", "stylistic(alt-a)");
		assertEquals(1, styled.getAlternates().size());
		assertEquals("stylistic", styled.getAlternates().get(0).function());
		assertNotNull(this.single("font-variant-alternates", "swash(fancy) ornaments(dingbat)"));
		this.assertInvalid("font-variant-alternates", "no-such-alternate");
	}

	// ---- 9. font-palette

	public void testFontPalette() {
		assertSame(FontPaletteValue.NORMAL_VALUE, this.single("font-palette", "normal"));
		assertSame(FontPaletteValue.LIGHT_VALUE, this.single("font-palette", "light"));
		assertSame(FontPaletteValue.DARK_VALUE, this.single("font-palette", "dark"));
		final FontPaletteValue named = (FontPaletteValue) this.single("font-palette", "--my-palette");
		assertEquals(FontPaletteValue.Kind.IDENTIFIER, named.getKind());
		assertEquals("--my-palette", named.getIdentifier());
	}

	public void testFontShorthandResetsPalette() {
		// font 短縮形は font-palette も初期値へ戻す(css-fonts-4)
		assertSame(FontPaletteValue.NORMAL_VALUE, this.longhand("font", "12pt serif", FontPalette.INFO));
	}

	// ---- 10. font-synthesis-small-caps

	public void testFontSynthesisSmallCaps() {
		this.accepted("font-synthesis-small-caps", "auto", "none");
		this.assertInvalid("font-synthesis-small-caps", "no-such-value");
		// font-synthesis 短縮形は weight / style / small-caps の3ロングハンドへ
		// 展開される(書かなかった成分は none へ落とす)ので single では取れない
		assertNotNull(this.longhand("font-synthesis", "small-caps", FontSynthesisSmallCaps.INFO));
		assertNotNull(this.longhand("font-synthesis", "weight style small-caps", FontSynthesisSmallCaps.INFO));
		assertNotNull(this.longhand("font-synthesis", "none", FontSynthesisSmallCaps.INFO));
	}

	// ---- 11. 論理境界の短縮形8件

	public void testLogicalBorderAxisShorthands() {
		// border-block は block 軸の start / end 両側へ配る。物理側ではなく
		// 論理ロングハンド(LogicalBorder)へ落ちるのが正しい——物理へ直接
		// 書くと writing-mode で向きが変わったときに追随できない
		final Entry[] block = this.parse("border-block", "1pt solid red");
		assertEquals(1.0, pt(find(block, LogicalBorder.of(Aspect.WIDTH, LogicalSide.BLOCK_START))), 1e-9);
		assertEquals(1.0, pt(find(block, LogicalBorder.of(Aspect.WIDTH, LogicalSide.BLOCK_END))), 1e-9);
		assertNotNull(find(block, LogicalBorder.of(Aspect.STYLE, LogicalSide.BLOCK_START)));
		assertTrue(find(block, LogicalBorder.of(Aspect.COLOR, LogicalSide.BLOCK_START)) instanceof ColorValue);
		// border-inline は inline 軸へ
		final Entry[] inline = this.parse("border-inline", "2pt dashed blue");
		assertEquals(2.0, pt(find(inline, LogicalBorder.of(Aspect.WIDTH, LogicalSide.INLINE_START))), 1e-9);
		assertEquals(2.0, pt(find(inline, LogicalBorder.of(Aspect.WIDTH, LogicalSide.INLINE_END))), 1e-9);
	}

	public void testLogicalBorderAxisComponentShorthands() {
		// -width/-style/-color は1値なら両側、2値なら start・end の順
		final Entry[] one = this.parse("border-block-width", "3pt");
		assertEquals(3.0, pt(find(one, LogicalBorder.of(Aspect.WIDTH, LogicalSide.BLOCK_START))), 1e-9);
		assertEquals(3.0, pt(find(one, LogicalBorder.of(Aspect.WIDTH, LogicalSide.BLOCK_END))), 1e-9);
		final Entry[] two = this.parse("border-block-width", "3pt 5pt");
		assertEquals(3.0, pt(find(two, LogicalBorder.of(Aspect.WIDTH, LogicalSide.BLOCK_START))), 1e-9);
		assertEquals(5.0, pt(find(two, LogicalBorder.of(Aspect.WIDTH, LogicalSide.BLOCK_END))), 1e-9);
		final Entry[] inlineTwo = this.parse("border-inline-width", "3pt 5pt");
		assertEquals(3.0, pt(find(inlineTwo, LogicalBorder.of(Aspect.WIDTH, LogicalSide.INLINE_START))), 1e-9);
		assertEquals(5.0, pt(find(inlineTwo, LogicalBorder.of(Aspect.WIDTH, LogicalSide.INLINE_END))), 1e-9);
		// スタイルと色も同様に通ること
		assertNotNull(this.parse("border-block-style", "solid dashed"));
		assertNotNull(this.parse("border-inline-style", "solid"));
		assertNotNull(this.parse("border-block-color", "red blue"));
		assertNotNull(this.parse("border-inline-color", "red"));
		// 3値以上は無い
		this.assertInvalid("border-block-width", "1pt 2pt 3pt");
	}

	private static Value find(final Entry[] entries, final PrimitivePropertyInfo info) {
		for (final Entry e : entries) {
			if (e.getPrimitivePropertyInfo() == info) {
				return e.getValue();
			}
		}
		fail("構成要素が無い: " + info.getName());
		return null;
	}

	// ---- 12. text-emphasis-position

	public void testTextEmphasisPosition() {
		assertSame(TextEmphasisPositionValue.OVER_RIGHT, this.single("text-emphasis-position", "over right"));
		assertSame(TextEmphasisPositionValue.OVER_LEFT, this.single("text-emphasis-position", "over left"));
		assertSame(TextEmphasisPositionValue.UNDER_RIGHT, this.single("text-emphasis-position", "under right"));
		assertSame(TextEmphasisPositionValue.UNDER_LEFT, this.single("text-emphasis-position", "under left"));
		// 順序は自由
		assertSame(TextEmphasisPositionValue.UNDER_LEFT, this.single("text-emphasis-position", "left under"));
		// 片方だけでも書ける(もう片方は既定)
		assertSame(TextEmphasisPositionValue.OVER_RIGHT, this.single("text-emphasis-position", "over"));
		this.assertInvalid("text-emphasis-position", "over under");
		this.assertInvalid("text-emphasis-position", "no-such-position");
	}

	public void testTextEmphasisShorthandDoesNotTouchPosition() {
		// SPEC css-text-decor-3 §8.5: text-emphasis は style と color だけの
		// 短縮形で、position は<b>意図的に含まない</b>(縦横で書き分けた
		// position を短縮形が消してしまわないため)。含めてしまう退行を防ぐ
		for (final Entry e : this.parse("text-emphasis", "dot red")) {
			assertNotSame("text-emphasis が text-emphasis-position を触っている",
					TextEmphasisPosition.INFO, e.getPrimitivePropertyInfo());
		}
	}

	// ---- 13. hyphenate-character

	public void testHyphenateCharacter() {
		assertNotNull(this.single("hyphenate-character", "auto"));
		assertNotNull(this.single("hyphenate-character", "\"-\""));
		assertNotNull(this.single("hyphenate-character", "\"\\2010\""));
		this.assertInvalid("hyphenate-character", "1pt");
	}

	// ---- 14. font-variant-ligatures / -alternates が font 短縮形で戻ること

	public void testFontShorthandResetsVariantLonghands() {
		assertSame(FontVariantLigaturesValue.NORMAL_VALUE,
				this.longhand("font", "12pt serif", FontVariantLigatures.INFO));
		assertSame(FontVariantAlternatesValue.NORMAL_VALUE,
				this.longhand("font", "12pt serif", FontVariantAlternates.INFO));
	}
}
