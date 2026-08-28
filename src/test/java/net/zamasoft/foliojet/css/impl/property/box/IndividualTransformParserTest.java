package net.zamasoft.foliojet.css.impl.property.box;

import java.awt.geom.AffineTransform;
import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.flex.LegacyFlexAlignmentAlias;
import net.zamasoft.foliojet.css.impl.property.font.FontStretch;
import net.zamasoft.foliojet.css.impl.property.text.LineBreak;
import net.zamasoft.foliojet.css.impl.property.text.TabSize;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.PropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.css3.LineBreakValue;
import net.zamasoft.foliojet.css.value.css3.TransformValue;

/**
 * 個別変換プロパティ{@code translate}/{@code rotate}/{@code scale}、
 * {@code zoom}、{@code line-break}、{@code tab-size}、
 * {@code font-stretch}、2011年版{@code -ms-flex-pack}系の解析テストです
 * (2026-08-29)。
 */
public class IndividualTransformParserTest extends TestCase {

	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull(decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	/** 96dpi(px→pt 0.75)だけを答えるUserAgent(ShapeOutsideParserTestと同型)。 */
	private static net.zamasoft.foliojet.ua.UserAgent ua() {
		return (net.zamasoft.foliojet.ua.UserAgent) java.lang.reflect.Proxy.newProxyInstance(
				IndividualTransformParserTest.class.getClassLoader(),
				new Class[] { net.zamasoft.foliojet.ua.UserAgent.class }, (p, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
					case "getDocumentContext":
						return new net.zamasoft.foliojet.ua.DocumentContext();
					case "toString":
						return "IndividualTransformParserTest.UserAgent";
					case "hashCode":
						return System.identityHashCode(p);
					case "equals":
						return p == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}

	private static Object parse(final PrimitivePropertyInfo info, final String css) {
		try {
			return ((AbstractPrimitivePropertyInfo) info).parseValue(tokens(info.getName() + ": " + css), ua(),
					null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	private static void assertMatrix(final Object v, final double... expected) {
		assertTrue(String.valueOf(v), v instanceof TransformValue);
		final double[] m = new double[6];
		((TransformValue) v).getTransform().getMatrix(m);
		for (int i = 0; i < 6; ++i) {
			assertEquals("m[" + i + "] of " + v, expected[i], m[i], 1e-9);
		}
	}

	public void testTranslate() {
		assertSame(KeywordValue.NONE, parse(Translate.INFO, "none"));
		// uaなしのpx→ptは0.75
		assertMatrix(parse(Translate.INFO, "20px"), 1, 0, 0, 1, 15, 0);
		assertMatrix(parse(Translate.INFO, "20px 40px"), 1, 0, 0, 1, 15, 30);
		// z成分は読み捨て
		assertMatrix(parse(Translate.INFO, "20px 40px 5px"), 1, 0, 0, 1, 15, 30);
		// 割合は係数(W→x, H→y)で運ぶ。交差成分は0
		final Object pct = parse(Translate.INFO, "50% -10%");
		assertMatrix(pct, 1, 0, 0, 1, 0, 0);
		assertEquals(0.5, ((TransformValue) pct).getTxRatio(), 1e-9);
		assertEquals(-0.1, ((TransformValue) pct).getTyRatio(), 1e-9);
		assertEquals(0.0, ((TransformValue) pct).getTxRatioH(), 1e-9);
		assertEquals(0.0, ((TransformValue) pct).getTyRatioW(), 1e-9);
		// 不正: 4値、キーワード、割合のz
		assertTrue(parse(Translate.INFO, "1px 2px 3px 4px") instanceof PropertyException);
		assertTrue(parse(Translate.INFO, "auto") instanceof PropertyException);
		assertTrue(parse(Translate.INFO, "1px 2px 50%") instanceof PropertyException);
	}

	public void testRotate() {
		assertSame(KeywordValue.NONE, parse(Rotate.INFO, "none"));
		final double c = Math.cos(Math.PI / 4), s = Math.sin(Math.PI / 4);
		assertMatrix(parse(Rotate.INFO, "45deg"), c, s, -s, c, 0, 0);
		assertMatrix(parse(Rotate.INFO, "0.125turn"), c, s, -s, c, 0, 0);
		assertMatrix(parse(Rotate.INFO, "50grad"), c, s, -s, c, 0, 0);
		assertMatrix(parse(Rotate.INFO, "0.785398163rad"), c, s, -s, c, 0, 0);
		// z軸は角度と順不同
		assertMatrix(parse(Rotate.INFO, "z 45deg"), c, s, -s, c, 0, 0);
		assertMatrix(parse(Rotate.INFO, "45deg z"), c, s, -s, c, 0, 0);
		assertMatrix(parse(Rotate.INFO, "0 0 1 45deg"), c, s, -s, c, 0, 0);
		assertMatrix(parse(Rotate.INFO, "45deg 0 0 1"), c, s, -s, c, 0, 0);
		// 負のz軸は逆回転
		assertMatrix(parse(Rotate.INFO, "0 0 -1 45deg"), c, -s, s, c, 0, 0);
		// x/y軸・傾いた軸は紙面に射影できないので恒等
		assertSame(TransformValue.IDENTITY_TRANSFORM_VALUE, parse(Rotate.INFO, "x 45deg"));
		assertSame(TransformValue.IDENTITY_TRANSFORM_VALUE, parse(Rotate.INFO, "y 45deg"));
		assertSame(TransformValue.IDENTITY_TRANSFORM_VALUE, parse(Rotate.INFO, "1 1 1 45deg"));
		// 不正
		assertTrue(parse(Rotate.INFO, "z") instanceof PropertyException);
		assertTrue(parse(Rotate.INFO, "45deg 30deg") instanceof PropertyException);
		assertTrue(parse(Rotate.INFO, "w 45deg") instanceof PropertyException);
		assertTrue(parse(Rotate.INFO, "45px") instanceof PropertyException);
	}

	public void testScale() {
		assertSame(KeywordValue.NONE, parse(Scale.INFO, "none"));
		assertMatrix(parse(Scale.INFO, "1.5"), 1.5, 0, 0, 1.5, 0, 0);
		assertMatrix(parse(Scale.INFO, "2 0.5"), 2, 0, 0, 0.5, 0, 0);
		assertMatrix(parse(Scale.INFO, "2 0.5 7"), 2, 0, 0, 0.5, 0, 0);
		assertMatrix(parse(Scale.INFO, "150% 50%"), 1.5, 0, 0, 0.5, 0, 0);
		assertTrue(parse(Scale.INFO, "1 2 3 4") instanceof PropertyException);
		assertTrue(parse(Scale.INFO, "1px") instanceof PropertyException);
	}

	public void testZoom() {
		assertEquals(1.5, ((RealValue) parse(Zoom.INFO, "1.5")).getReal(), 1e-9);
		assertEquals(2.0, ((RealValue) parse(Zoom.INFO, "200%")).getReal(), 1e-9);
		assertSame(RealValue.ONE, parse(Zoom.INFO, "normal"));
		assertSame(RealValue.ONE, parse(Zoom.INFO, "reset"));
		// 0は1(css-viewport)
		assertEquals(1.0, ((RealValue) parse(Zoom.INFO, "0")).getReal(), 1e-9);
		assertTrue(parse(Zoom.INFO, "-1") instanceof PropertyException);
		assertTrue(parse(Zoom.INFO, "1px") instanceof PropertyException);
	}

	public void testLineBreak() {
		assertSame(LineBreakValue.AUTO, parse(LineBreak.INFO, "auto"));
		assertSame(LineBreakValue.LOOSE, parse(LineBreak.INFO, "loose"));
		assertSame(LineBreakValue.NORMAL, parse(LineBreak.INFO, "normal"));
		assertSame(LineBreakValue.STRICT, parse(LineBreak.INFO, "Strict"));
		assertSame(LineBreakValue.ANYWHERE, parse(LineBreak.INFO, "anywhere"));
		assertTrue(parse(LineBreak.INFO, "break-all") instanceof PropertyException);
		assertTrue(parse(LineBreak.INFO, "loose strict") instanceof PropertyException);
		assertTrue(LineBreak.INFO.isInherited());
	}

	public void testTabSize() {
		assertEquals(4.0, ((RealValue) parse(TabSize.INFO, "4")).getReal(), 1e-9);
		assertEquals(0.0, ((RealValue) parse(TabSize.INFO, "0")).getReal(), 1e-9);
		assertEquals(30.0, ((AbsoluteLengthValue) parse(TabSize.INFO, "30pt")).getLength(), 1e-9);
		assertEquals(15.0, ((AbsoluteLengthValue) parse(TabSize.INFO, "20px")).getLength(), 1e-9);
		// emは計算値で解く(RelativeLengthValueのまま返る)
		assertFalse(parse(TabSize.INFO, "2em") instanceof PropertyException);
		assertTrue(parse(TabSize.INFO, "-1") instanceof PropertyException);
		assertTrue(parse(TabSize.INFO, "-1pt") instanceof PropertyException);
		assertTrue(parse(TabSize.INFO, "50%") instanceof PropertyException);
		assertTrue(parse(TabSize.INFO, "auto") instanceof PropertyException);
		assertTrue(TabSize.INFO.isInherited());
		assertEquals(8.0, ((RealValue) TabSize.INFO.getDefault(null)).getReal(), 1e-9);
	}

	public void testFontStretch() {
		assertEquals(100.0, ((PercentageValue) parse(FontStretch.INFO, "normal")).getPercentage(), 1e-9);
		assertEquals(50.0, ((PercentageValue) parse(FontStretch.INFO, "ultra-condensed")).getPercentage(), 1e-9);
		assertEquals(75.0, ((PercentageValue) parse(FontStretch.INFO, "condensed")).getPercentage(), 1e-9);
		assertEquals(200.0, ((PercentageValue) parse(FontStretch.INFO, "ultra-expanded")).getPercentage(), 1e-9);
		assertEquals(80.0, ((PercentageValue) parse(FontStretch.INFO, "80%")).getPercentage(), 1e-9);
		assertTrue(parse(FontStretch.INFO, "-10%") instanceof PropertyException);
		assertTrue(parse(FontStretch.INFO, "1.5") instanceof PropertyException);
		assertTrue(parse(FontStretch.INFO, "wide") instanceof PropertyException);
		// usWidthClassへの丸め
		assertEquals(1, FontStretch.toWidthClass(50));
		assertEquals(3, FontStretch.toWidthClass(75));
		assertEquals(3, FontStretch.toWidthClass(80));
		assertEquals(5, FontStretch.toWidthClass(100));
		assertEquals(9, FontStretch.toWidthClass(200));
		assertEquals(9, FontStretch.toWidthClass(300));
	}

	private static Object parseAlias(final PropertyInfo info, final String css) {
		try {
			return info.parse(tokens(info.getName() + ": " + css), ua(), null, false);
		} catch (final PropertyException e) {
			return e;
		}
	}

	private static BoxAlignmentValue aliasValue(final LegacyFlexAlignmentAlias alias, final String css) {
		final Object p = parseAlias(alias, css);
		assertTrue(String.valueOf(p), p instanceof CompositeProperty);
		final CompositeProperty.Entry[] entries = ((CompositeProperty) p).getEntries();
		assertEquals(1, entries.length);
		assertSame(alias.getTarget(), entries[0].getPrimitivePropertyInfo());
		return (BoxAlignmentValue) entries[0].getValue();
	}

	public void testLegacyFlexAlignment() {
		assertSame(BoxAlignmentValue.FLEX_START, aliasValue(LegacyFlexAlignmentAlias.FLEX_PACK, "start"));
		assertSame(BoxAlignmentValue.FLEX_END, aliasValue(LegacyFlexAlignmentAlias.FLEX_PACK, "end"));
		assertSame(BoxAlignmentValue.SPACE_BETWEEN, aliasValue(LegacyFlexAlignmentAlias.FLEX_PACK, "justify"));
		assertSame(BoxAlignmentValue.SPACE_AROUND, aliasValue(LegacyFlexAlignmentAlias.FLEX_PACK, "distribute"));
		assertSame(BoxAlignmentValue.CENTER, aliasValue(LegacyFlexAlignmentAlias.FLEX_PACK, "center"));
		assertSame(BoxAlignmentValue.CENTER, aliasValue(LegacyFlexAlignmentAlias.FLEX_ALIGN, "center"));
		assertSame(BoxAlignmentValue.STRETCH, aliasValue(LegacyFlexAlignmentAlias.FLEX_ALIGN, "stretch"));
		assertSame(BoxAlignmentValue.FLEX_END, aliasValue(LegacyFlexAlignmentAlias.FLEX_ITEM_ALIGN, "end"));
		assertSame(BoxAlignmentValue.AUTO, aliasValue(LegacyFlexAlignmentAlias.FLEX_ITEM_ALIGN, "auto"));
		assertSame(BoxAlignmentValue.SPACE_AROUND,
				aliasValue(LegacyFlexAlignmentAlias.FLEX_LINE_PACK, "distribute"));
		// items系にspace-*は無い(justifyは受理しない)
		assertTrue(parseAlias(LegacyFlexAlignmentAlias.FLEX_ALIGN, "justify") instanceof PropertyException);
		assertTrue(parseAlias(LegacyFlexAlignmentAlias.FLEX_PACK, "middle") instanceof PropertyException);
		// 全体キーワードは標準プロパティへ
		final Object inherit = parseAlias(LegacyFlexAlignmentAlias.FLEX_PACK, "inherit");
		assertTrue(inherit instanceof CompositeProperty);
		assertSame(KeywordValue.INHERIT, ((CompositeProperty) inherit).getEntries()[0].getValue());
	}

	/** 合成順 translate→rotate→scale→transform の行列検算(BoxStyleMapperと同式)。 */
	public void testComposeOrder() {
		final AffineTransform pre = ((TransformValue) parse(Translate.INFO, "30pt 7.5pt")).getTransform();
		pre.concatenate(((TransformValue) parse(Rotate.INFO, "90deg")).getTransform());
		pre.concatenate(((TransformValue) parse(Scale.INFO, "2 1")).getTransform());
		// 線形部は R90·S(2,1) = [0 -1; 2 0]、平行移動は先頭のtranslateそのまま
		assertEquals(0.0, pre.getScaleX(), 1e-9);
		assertEquals(2.0, pre.getShearY(), 1e-9);
		assertEquals(-1.0, pre.getShearX(), 1e-9);
		assertEquals(0.0, pre.getScaleY(), 1e-9);
		assertEquals(30.0, pre.getTranslateX(), 1e-9);
		assertEquals(7.5, pre.getTranslateY(), 1e-9);
	}
}
