package net.zamasoft.foliojet.css.impl.property.box;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.impl.property.shorthand.OutlineShorthand;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.css3.BoxShadowValue;
import net.zamasoft.foliojet.css.value.css3.BoxShadowValue.Shadow;

/**
 * box-shadow と outline ショートハンドの構文解析のテストです(2026-08-29)。
 *
 * <p>
 * {@code <shadow> = inset? && <length>{2,4} && <color>?}の受理・拒否と、
 * outlineの{@code auto}/{@code invert}の写像を固定する。描画結果は
 * {@code jp.cssj.test.unit.displaylist.BoxDecorationTest}が画素で見る。
 * </p>
 */
public class BoxShadowTest extends TestCase {

	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull("宣言のパースに失敗: " + declaration, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static Shadow[] parse(final String value) throws PropertyException {
		return ((BoxShadowValue) ((BoxShadow) BoxShadow.INFO).parseValue(tokens("box-shadow: " + value), null, null)).getShadows();
	}

	private static double px(final net.zamasoft.foliojet.css.value.LengthValue v) {
		// UA無しで解析するのでpt換算(getLength)は使わずpxのまま比べる
		return ((AbsoluteLengthValue) v).getLength(net.zamasoft.foliojet.css.token.Unit.PX);
	}

	public void testNone() throws Exception {
		assertEquals(0, parse("none").length);
	}

	public void testCardShadow() throws Exception {
		final Shadow[] shadows = parse("0 2px 8px rgba(0,0,0,.15)");
		assertEquals(1, shadows.length);
		final Shadow s = shadows[0];
		assertFalse(s.inset);
		assertEquals(0.0, px(s.x), 1e-9);
		assertEquals(2, px(s.y), 1e-9);
		assertEquals(8, px(s.blur), 1e-9);
		assertNull("spread省略はnull", s.spread);
		assertNotNull(s.color);
		assertEquals(0.15f, s.color.getColor().getAlpha(), 1e-3f);
	}

	public void testInsetAndSpreadAnyOrder() throws Exception {
		final Shadow[] a = parse("inset 0 0 6px 2px red");
		final Shadow[] b = parse("red 0 0 6px 2px inset");
		for (final Shadow[] shadows : new Shadow[][] { a, b }) {
			assertEquals(1, shadows.length);
			assertTrue(shadows[0].inset);
			assertEquals(6, px(shadows[0].blur), 1e-9);
			assertEquals(2, px(shadows[0].spread), 1e-9);
			assertNotNull(shadows[0].color);
		}
	}

	public void testColorDefaultsToCurrentColor() throws Exception {
		final Shadow[] shadows = parse("1px 1px");
		assertEquals(1, shadows.length);
		assertNull("色省略はnull(使用値でcurrentColor)", shadows[0].color);
		assertNull(shadows[0].blur);
	}

	public void testMultipleShadowsKeepOrder() throws Exception {
		final Shadow[] shadows = parse("2px 2px red, 4px 4px 1px blue, inset 0 0 3px");
		assertEquals(3, shadows.length);
		assertEquals(2, px(shadows[0].x), 1e-9);
		assertEquals(4, px(shadows[1].x), 1e-9);
		assertEquals(1, px(shadows[1].blur), 1e-9);
		assertTrue(shadows[2].inset);
	}

	public void testTransparentShadowDropped() throws Exception {
		assertEquals(0, parse("2px 2px transparent").length);
		assertEquals(1, parse("2px 2px transparent, 1px 1px red").length);
	}

	public void testRejects() {
		for (final String bad : new String[] { "2px", "2px 2px -1px", "2px 2px 2px 2px 2px", "2px red 2px",
				"inset inset 1px 1px", "1px 1px red blue", "none 1px 1px" }) {
			try {
				parse(bad);
				fail("拒否されるべき: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}

	private static String outline(final String value) throws PropertyException {
		return ((OutlineShorthand) OutlineShorthand.INFO).parse(tokens("outline: " + value), null, null, false).toString();
	}

	public void testOutlineShorthand() throws Exception {
		// auto→solid、invert→DEFAULT(currentColor)。順不同
		final String a = outline("auto 2px invert");
		assertTrue(a, a.contains(" solid "));
		assertTrue(a, a.contains(" default "));
		final String b = outline("dashed red");
		assertTrue(b, b.contains(" dashed "));
		assertFalse(b, b.contains("solid"));
		for (final String bad : new String[] { "hidden", "1px 2px", "solid solid" }) {
			try {
				outline(bad);
				fail("拒否されるべき: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}
}
