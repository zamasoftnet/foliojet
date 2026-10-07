package net.zamasoft.foliojet.css.util;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Tests for CSS Color 3/4 color functions (2026-08-02, third priority in PLAN §2:
 * input compatibility with Tailwind v4's oklch default and color-mix(in oklab, C, transparent)).
 * Fix sRGB conversion values, alpha, and premultiplied interpolation.
 */
public class Color4ParserTest extends TestCase {

	private static CssToken token(final String value) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString("color: " + value, settings);
		assertNotNull("宣言のパースに失敗: " + value, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		assertEquals("単一トークンであること: " + value, 1, ts.size());
		return ts.get(0);
	}

	private static UserAgent ua() {
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(Color4ParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}

	private static ColorValue color(final String value) {
		return ColorValueUtils.toColor(ua(), token(value));
	}

	private static void assertRGB(final String value, final double r, final double g, final double b,
			final double tolerance) {
		final ColorValue c = color(value);
		assertNotNull(value, c);
		assertEquals(value + " R", r, c.getRed(), tolerance);
		assertEquals(value + " G", g, c.getGreen(), tolerance);
		assertEquals(value + " B", b, c.getBlue(), tolerance);
	}

	public void testHSL() {
		assertRGB("hsl(120, 100%, 25%)", 0, 0.502, 0, 0.005);
		assertRGB("hsl(0 100% 50%)", 1, 0, 0, 0.005);
		assertRGB("hsl(240deg 100% 50%)", 0, 0, 1, 0.005);
		final ColorValue half = color("hsl(0 100% 50% / 0.5)");
		assertEquals(0.5, half.getAlpha(), 0.005);
	}

	/** oklch→sRGB (reference values from CSS Color 4 samples and browser measurements). */
	public void testOKLCH() {
		assertRGB("oklch(0.6279 0.2577 29.23)", 1, 0, 0, 0.02);
		assertRGB("oklch(62.79% 0.2577 29.23deg)", 1, 0, 0, 0.02);
		assertRGB("oklch(1 0 0)", 1, 1, 1, 0.005);
		assertRGB("oklch(0 0 0)", 0, 0, 0, 0.005);
		final ColorValue withAlpha = color("oklch(0.5 0.1 180 / 40%)");
		assertNotNull(withAlpha);
		assertEquals(0.4, withAlpha.getAlpha(), 0.005);
	}

	public void testOKLab() {
		assertRGB("oklab(0.6279 0.2249 0.1258)", 1, 0, 0, 0.02);
	}

	public void testColorMix() {
		assertRGB("color-mix(in srgb, red, blue)", 0.5, 0, 0.5, 0.005);
		assertRGB("color-mix(in srgb, red 25%, blue)", 0.25, 0, 0.75, 0.005);
		// Tailwind v4 opacity-utility form: premultiplied interpolation
		// preserves color components and halves only alpha.
		final ColorValue faded = color("color-mix(in oklab, red, transparent)");
		assertNotNull(faded);
		assertEquals(0.5, faded.getAlpha(), 0.005);
		assertEquals(1, faded.getRed(), 0.02);
		assertEquals(0, faded.getGreen(), 0.02);
	}

	public void testLightDark() {
		assertRGB("light-dark(green, red)", 0, 0.502, 0, 0.005);
	}

	public void testInvalid() {
		assertNull(color("oklch(0.5)"));
		assertNull(color("color-mix(red, blue)"));
	}
}
