package net.zamasoft.foliojet.css.property;

import java.awt.geom.AffineTransform;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.background.BackgroundPosition;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.MaskImage;
import net.zamasoft.foliojet.css.impl.property.box.MaskPosition;
import net.zamasoft.foliojet.css.impl.property.box.MaskRepeat;
import net.zamasoft.foliojet.css.impl.property.box.MaskSize;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.impl.property.box.Transform;
import net.zamasoft.foliojet.css.impl.property.font.FontKerning;
import net.zamasoft.foliojet.css.impl.property.page.PageBreakInside;
import net.zamasoft.foliojet.css.impl.property.text.UnicodeBidi;
import net.zamasoft.foliojet.css.impl.property.text.WordBreak;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.FontKerningValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PageBreakInsideValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TransformValue;
import net.zamasoft.foliojet.css.value.css3.WordBreakValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 実サイトの警告から拾ったCSSの穴(2026-08-29)を、宣言の解釈の段階で固定
 * するテストです。対象は{@code docs/PLAN.md} §5「実サイトの警告から拾った
 * 候補」の各項目——落とすと見た目が壊れる指定(割合translateと他関数の併用、
 * ショートハンドの全体キーワード、8桁hex)と、実装済み機能の穴埋め
 * (ベンダ別名、flow-root、break-word、avoid-column、isolate、font-kerning、
 * 論理ショートハンド、mask-*、backgroundの4値position、revert)。
 */
public class RealSiteCssGapsTest extends TestCase {
	/** 警告を集める疑似UA。長さの解決に必要な最小限だけ応答する。 */
	private static final class Probe {
		final List<String> warnings = new ArrayList<>();
		final UserAgent ua = (UserAgent) java.lang.reflect.Proxy.newProxyInstance(
				RealSiteCssGapsTest.class.getClassLoader(), new Class[] { UserAgent.class },
				(proxy, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
					case "message":
						this.warnings.add(Integer.toHexString((Short) args[0]) + ":" + java.util.Arrays.toString(
								(String[]) args[1]));
						return null;
					case "getFontSize":
						return 12.0;
					case "getDocumentContext":
						return new net.zamasoft.foliojet.ua.DocumentContext();
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

	/** 宣言を解釈し、展開結果(特性名→値)を返す。失敗時はnullで、警告はprobeに残る。 */
	private static Map<String, Value> parse(final Probe probe, final String declaration) {
		final int colon = declaration.indexOf(':');
		final String name = declaration.substring(0, colon).trim();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(declaration),
				probe.ua, URI.create("file:///test.css"), false);
		if (property == null) {
			return null;
		}
		final Map<String, Value> values = new java.util.LinkedHashMap<>();
		for (final CompositeProperty.Entry entry : ((CompositeProperty) property).getEntries()) {
			values.put(entry.getPrimitivePropertyInfo().getName(), entry.getValue());
		}
		return values;
	}

	private static Map<String, Value> parseOk(final String declaration) {
		final Probe probe = new Probe();
		final Map<String, Value> values = parse(probe, declaration);
		assertNotNull("解釈できるべき: " + declaration + " " + probe.warnings, values);
		assertTrue("警告なしで解釈できるべき: " + declaration + " " + probe.warnings, probe.warnings.isEmpty());
		return values;
	}

	public void testShorthandGlobalKeywords() {
		assertEquals(Map.of(Padding.TOP.getName(), KeywordValue.INHERIT, Padding.RIGHT.getName(),
				KeywordValue.INHERIT, Padding.BOTTOM.getName(), KeywordValue.INHERIT, Padding.LEFT.getName(),
				KeywordValue.INHERIT), parseOk("padding: inherit"));
		assertEquals(4, parseOk("margin: unset").size());
		assertEquals(KeywordValue.INITIAL, parseOk("inset: initial").get(Inset.TOP.getName()));
		assertEquals(2, parseOk("overflow: inherit").size());
		assertEquals(2, parseOk("columns: inherit").size());
		// 2026-09-04: 標準 `writing-mode` は BlockFlow+variant の 2 longhand(direction を変えない)、
		// legacy `-cssj-writing-mode` は Direction+BlockFlow+variant の 3(sideways-writing-mode-design.md 追補 A)
		assertEquals(2, parseOk("writing-mode: inherit").size());
		assertEquals(3, parseOk("-cssj-writing-mode: inherit").size());
		assertEquals(4, parseOk("border-style: inherit").size());
		assertEquals(4, parseOk("border-width: inherit").size());
		assertEquals(1, parseOk("text-wrap: inherit").size());
		// 通常の値は従来どおり
		assertEquals(4, parseOk("padding: 1px 2px").size());
	}

	public void testHexColorWithAlpha() {
		final Probe probe = new Probe();
		final ColorValue half = ColorValueUtils.toColor(probe.ua, tokens("color: #ff000080").get(0));
		assertNotNull(half);
		assertEquals(1.0, half.getRed(), 0.001);
		assertEquals(0.0, half.getGreen(), 0.001);
		assertEquals(128 / 255.0, half.getAlpha(), 0.005);
		final ColorValue nibble = ColorValueUtils.toColor(probe.ua, tokens("color: #f008").get(0));
		assertNotNull(nibble);
		assertEquals(0x88 / 255.0, nibble.getAlpha(), 0.005);
		assertNotNull(parseOk("background-color: #00000033"));
		// 属性値経路
		assertEquals(0x33 / 255.0, ColorValueUtils.parseRGBHexColor("00000033").getAlpha(), 0.005);
	}

	public void testVendorPrefixedColumns() {
		assertNotNull(parseOk("-webkit-column-count: 3"));
		assertNotNull(parseOk("-moz-column-width: 12em"));
		assertNotNull(parseOk("-webkit-columns: 2 auto"));
		assertNotNull(parseOk("-webkit-column-break-inside: avoid"));
	}

	public void testFlowRoot() {
		assertSame(DisplayValue.FLOW_ROOT_VALUE, parseOk("display: flow-root").get(Display.INFO.getName()));
		assertEquals(DisplayValue.BLOCK, DisplayValue.FLOW_ROOT_VALUE.getDisplay());
	}

	public void testWordBreakBreakWord() {
		assertSame(WordBreakValue.BREAK_WORD_VALUE, parseOk("word-break: break-word").get(WordBreak.INFO.getName()));
	}

	public void testBreakInsideAvoidColumn() {
		assertSame(PageBreakInsideValue.AVOID_VALUE,
				parseOk("break-inside: avoid-column").get(PageBreakInside.INFO.getName()));
		assertSame(PageBreakInsideValue.AVOID_VALUE,
				parseOk("page-break-inside: avoid-page").get(PageBreakInside.INFO.getName()));
	}

	public void testUnicodeBidiIsolate() {
		assertSame(UnicodeBidiValue.ISOLATE_VALUE, parseOk("unicode-bidi: isolate").get(UnicodeBidi.INFO.getName()));
		assertSame(UnicodeBidiValue.ISOLATE_OVERRIDE_VALUE,
				parseOk("unicode-bidi: isolate-override").get(UnicodeBidi.INFO.getName()));
		assertSame(UnicodeBidiValue.PLAINTEXT_VALUE, parseOk("unicode-bidi: plaintext").get(UnicodeBidi.INFO.getName()));
	}

	public void testFontKerning() {
		assertSame(FontKerningValue.NONE_VALUE, parseOk("font-kerning: none").get(FontKerning.INFO.getName()));
		assertSame(FontKerningValue.AUTO_VALUE, parseOk("font-kerning: auto").get(FontKerning.INFO.getName()));
		assertNull(parse(new Probe(), "font-kerning: sideways"));
	}

	public void testLogicalShorthands() {
		final Map<String, Value> margin = parseOk("margin-inline: 1em 2em");
		assertEquals(2, margin.size());
		assertNotNull(margin.get(Margin.INLINE_START.getName()));
		assertNotNull(margin.get(Margin.INLINE_END.getName()));
		final Map<String, Value> padding = parseOk("padding-block: 4px");
		assertEquals(padding.get(Padding.BLOCK_START.getName()), padding.get(Padding.BLOCK_END.getName()));
		final Map<String, Value> inset = parseOk("inset-inline: auto 0");
		assertSame(KeywordValue.AUTO, inset.get(Inset.INLINE_START.getName()));
		assertEquals(2, parseOk("margin-block: inherit").size());
		assertNull(parse(new Probe(), "padding-inline: 1px 2px 3px"));
	}

	public void testSvgPresentationPropertiesAreAcceptedSilently() {
		final Probe probe = new Probe();
		assertNull(parse(probe, "fill: currentColor"));
		assertNull(parse(probe, "stroke-width: 2px"));
		assertTrue("SVGのfill/strokeは警告しない: " + probe.warnings, probe.warnings.isEmpty());
	}

	public void testRevertIsDroppedSilently() {
		final Probe probe = new Probe();
		assertNull(parse(probe, "height: revert-layer"));
		assertNull(parse(probe, "color: revert"));
		assertTrue("revertは警告しない: " + probe.warnings, probe.warnings.isEmpty());
	}

	public void testPercentTranslateMixedWithScale() {
		final Map<String, Value> values = parseOk("transform: translate(-50%, -50%) scale(1.1)");
		final TransformValue t = (TransformValue) values.get(Transform.INFO.getName());
		assertEquals(-0.5, t.getTxRatio(), 1e-9);
		assertEquals(-0.5, t.getTyRatio(), 1e-9);
		assertEquals(0.0, t.getTxRatioH(), 1e-9);
		assertEquals(0.0, t.getTyRatioW(), 1e-9);
		assertEquals(1.1, t.getTransform().getScaleX(), 1e-6);

		// 前に拡大があると、割合は拡大された量になる(A_lin·v)
		final TransformValue scaled = (TransformValue) parseOk("transform: scale(2) translateX(50%)")
				.get(Transform.INFO.getName());
		assertEquals(1.0, scaled.getTxRatio(), 1e-9);
		assertEquals(0.0, scaled.getTyRatioW(), 1e-9);

		// 90度回転の後ろの横方向割合は縦方向へ移る(交差成分)
		final TransformValue rotated = (TransformValue) parseOk("transform: rotate(90deg) translateX(100%)")
				.get(Transform.INFO.getName());
		assertEquals(0.0, rotated.getTxRatio(), 1e-9);
		assertEquals(1.0, rotated.getTyRatioW(), 1e-9);

		// 3D関数は2Dへ縮退して受ける
		final TransformValue threeD = (TransformValue) parseOk("transform: translate3d(0, 10px, 0) translateZ(0)")
				.get(Transform.INFO.getName());
		final AffineTransform at = threeD.getTransform();
		assertEquals(0.0, at.getTranslateX(), 1e-9);
		assertEquals(7.5, at.getTranslateY(), 1e-9); // 10px = 7.5pt (96dpi)
	}

	public void testBackgroundFourValuePosition() {
		final Map<String, Value> values = parseOk("background: url(x.png) right 10px bottom 20px / cover no-repeat");
		assertNotNull(values.get(BackgroundPosition.INFO_X.getName()));
		assertNotSame(PercentageValue.ZERO, values.get(BackgroundPosition.INFO_X.getName()));
		assertNotSame(PercentageValue.ZERO, values.get(BackgroundPosition.INFO_Y.getName()));
		assertSame(KeywordValue.COVER, values.get("-cssj-background-size"));
		assertEquals(PercentageValue.HALF, parseOk("background: #fff center / 50% no-repeat")
				.get(BackgroundPosition.INFO_X.getName()));
	}

	public void testMaskProperties() {
		final Map<String, Value> mask = parseOk("-webkit-mask: url(icon.svg) no-repeat center / contain");
		assertNotNull(mask.get(MaskImage.INFO.getName()));
		assertSame(BackgroundRepeatValue.NO_REPEAT_VALUE, mask.get(MaskRepeat.INFO.getName()));
		assertSame(PercentageValue.HALF, mask.get(MaskPosition.INFO_X.getName()));
		assertSame(KeywordValue.CONTAIN, mask.get(MaskSize.INFO_WIDTH.getName()));
		assertNotNull(parseOk("mask-size: 24px"));
		assertNotNull(parseOk("-webkit-mask-position: 0 50%"));
		assertNotNull(parseOk("mask-repeat: repeat-x"));
		// mask-mode等のキーワードは読み飛ばす
		assertNotNull(parseOk("mask: url(a.svg) alpha border-box no-repeat"));
	}
}
