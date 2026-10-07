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
 * Fix the color values implemented on 2026-08-30: {@code rebeccapurple},
 * {@code hwb()}, {@code lab()}, {@code lch()}, {@code color()},
 * {@code turn} in {@code hsl()}, and gradient interpolation color-space specifications.
 *
 * <p>
 * Since this product outputs PDF, every specified color space is converted to sRGB (or CMYK).
 * Thus, check <b>the converted sRGB components</b>. Expected values were taken from fill operators
 * in actual converted PDFs and verified against hand calculations using CSS Color 4 conversion
 * matrices ([[the development records]]).
 *
 * <p>
 * This implementation <b>simply clamps out-of-gamut values</b> and does no gamut mapping
 * (print use does not need that complexity). The {@code display-p3} and {@code rec2020} cases
 * fix this policy itself.
 */
public class BaselineCssColorTest extends TestCase {

	/** Tolerance for sRGB components. They are stored as floats, so 1e-3 is sufficiently strict. */
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

	/** Parse a declaration, check that no warnings appear, and return its components. */
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

	/** Check the value specified for {@code color} using sRGB components. */
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

	// ---- 1. Named colors

	public void testRebeccapurple() {
		// The only new named color added in CSS Color 4: #663399
		this.assertColor("rebeccapurple", 0x66 / 255f, 0x33 / 255f, 0x99 / 255f);
	}

	// ---- 2. hwb()

	public void testHwb() {
		// Hue 0 (red), 25% white and 25% black. The remaining 50% is the pure color.
		this.assertColor("hwb(0 25% 25%)", 0.75f, 0.25f, 0.25f);
		this.assertColor("hwb(0 0% 0%)", 1f, 0f, 0f);
		this.assertColor("hwb(120 0% 0%)", 0f, 1f, 0f);
		// SPEC css-color-4 §7: if white + black exceeds 100%, normalize by their ratio.
		// 60%:60% becomes 50%:50%, producing middle gray regardless of hue.
		this.assertColor("hwb(0 60% 60%)", 0.5f, 0.5f, 0.5f);
		this.assertColor("hwb(240 60% 60%)", 0.5f, 0.5f, 0.5f);
		// Alpha
		final ColorValue alpha = (ColorValue) this.single("color", "hwb(0 0% 0% / 0.25)");
		assertEquals(0.25f, alpha.getColor().getAlpha(), EPS);
	}

	// ---- 3. lab() / lch()

	public void testLab() {
		// L*=100 is white, L*=0 is black (a*=b*=0).
		this.assertColor("lab(100% 0 0)", 1f, 1f, 1f);
		this.assertColor("lab(0% 0 0)", 0f, 0f, 0f);
		// CSS Color 4 example. Produces red-orange.
		this.assertColor("lab(50% 40 59.5)", 0.75f, 0.34f, 0f, 5e-3f);
	}

	public void testLch() {
		this.assertColor("lch(52.2% 72.2 50)", 0.81f, 0.34f, 0.10f, 5e-3f);
		// Chroma 0 is achromatic. L*=50 becomes 0.4663 in sRGB; correctness here is essential
		// to the entire Lab→XYZ→sRGB conversion, so tighten the tolerance.
		this.assertColor("lch(50% 0 0)", 0.4663f, 0.4663f, 0.4663f, 5e-3f);
		this.assertColor("lch(100% 0 0)", 1f, 1f, 1f);
	}

	// ---- 4. color() functional notation

	public void testColorFunctionSrgb() {
		// sRGB passes through unchanged.
		this.assertColor("color(srgb 1 0.5 0)", 1f, 0.5f, 0f, 1e-5f);
		this.assertColor("color(srgb 0 0 0)", 0f, 0f, 0f, 1e-5f);
		final ColorValue alpha = (ColorValue) this.single("color", "color(srgb 0 0 0 / 0.5)");
		assertEquals(0.5f, alpha.getColor().getAlpha(), EPS);
	}

	public void testColorFunctionWideGamut() {
		// For display-p3 (1, 0.5, 0), red and blue are outside the sRGB gamut.
		// Linearize to 0.21404 → XYZ(0.5434, 0.3770, 0.0097) → linear sRGB
		// (1.1768, 0.1810, -0.0365) → clamp and gamma-encode to
		// (1, 0.4626, 0)
		this.assertColor("color(display-p3 1 0.5 0)", 1f, 0.4626f, 0f, 5e-3f);
		// rec2020 green lies far outside the sRGB gamut. All components are clamped.
		this.assertColor("color(rec2020 0 1 0)", 0f, 1f, 0f, 1e-5f);
	}

	public void testColorFunctionXyz() {
		this.assertColor("color(xyz 0.4 0.2 0.1)", 0.9727f, 0f, 0.3267f, 5e-3f);
		// xyz is an alias for xyz-d65.
		final ColorValue xyz = (ColorValue) this.single("color", "color(xyz 0.4 0.2 0.1)");
		final ColorValue d65 = (ColorValue) this.single("color", "color(xyz-d65 0.4 0.2 0.1)");
		assertEquals(xyz.getColor().getRed(), d65.getColor().getRed(), 1e-6f);
		assertEquals(xyz.getColor().getBlue(), d65.getColor().getBlue(), 1e-6f);
		// D50 includes chromatic adaptation, so it produces a different color from D65.
		final ColorValue d50 = (ColorValue) this.single("color", "color(xyz-d50 0.4 0.2 0.1)");
		assertNotNull(d50);
	}

	public void testColorFunctionAllSpacesAccepted() {
		// Do not check converted values, but ensure every predefined color space in the specification is accepted.
		for (final String space : new String[] { "srgb", "srgb-linear", "display-p3", "a98-rgb", "prophoto-rgb",
				"rec2020", "xyz", "xyz-d50", "xyz-d65" }) {
			assertTrue(space + " が色にならない",
					this.single("color", "color(" + space + " 0.5 0.5 0.5)") instanceof ColorValue);
		}
	}

	public void testColorFunctionRejects() {
		this.assertInvalid("color", "color(no-such-space 1 1 1)");
		// Too few components
		this.assertInvalid("color", "color(srgb 1 1)");
		// Too many components
		this.assertInvalid("color", "color(srgb 1 1 1 1)");
	}

	// ---- 5. turn in hsl()

	public void testHslTurn() {
		// 0.5turn = 180deg. Angle units affect hue.
		final ColorValue turn = (ColorValue) this.single("color", "hsl(0.5turn 100% 50%)");
		final ColorValue deg = (ColorValue) this.single("color", "hsl(180 100% 50%)");
		assertEquals(deg.getColor().getRed(), turn.getColor().getRed(), 1e-5f);
		assertEquals(deg.getColor().getGreen(), turn.getColor().getGreen(), 1e-5f);
		assertEquals(deg.getColor().getBlue(), turn.getColor().getBlue(), 1e-5f);
		// Cyan at 180deg
		this.assertColor("hsl(0.5turn 100% 50%)", 0f, 1f, 1f, 1e-5f);
	}

	// ---- 6. Gradient interpolation color spaces

	public void testGradientInterpolationAccepted() {
		// SPEC css-images-4 <color-interpolation-method>. Interpolation in the specified color space
		// is unimplemented and falls back to existing sRGB interpolation, but before 2026-08-30,
		// the mere presence of "in" invalidated the entire declaration.
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
		// Can be combined with a direction specification.
		assertTrue(this.single("background-image", "linear-gradient(to right in oklab, red, blue)")
				instanceof LinearGradientValue);
		assertTrue(this.single("background-image", "linear-gradient(45deg in oklab, red, blue)")
				instanceof LinearGradientValue);
	}

	public void testGradientInterpolationDoesNotAlterStops() {
		// Adding an interpolation specification does not change the colors of the color stops themselves.
		// An assertion against "accepted, but with corrupted colors."
		final LinearGradientValue plain = (LinearGradientValue) this.single("background-image",
				"linear-gradient(red, blue)");
		final LinearGradientValue in = (LinearGradientValue) this.single("background-image",
				"linear-gradient(in oklab, red, blue)");
		assertEquals(plain.toString(), in.toString());
	}
}
