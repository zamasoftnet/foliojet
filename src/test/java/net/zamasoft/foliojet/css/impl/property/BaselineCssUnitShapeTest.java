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
import net.zamasoft.foliojet.css.impl.property.border.BorderImageOutset;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageRepeat;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageSlice;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageSource;
import net.zamasoft.foliojet.css.impl.property.border.BorderImageWidth;
import net.zamasoft.foliojet.css.impl.property.box.ClipPath;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.util.BasicShapes;
import net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Fix parsing of units, basic shapes, and {@code border-image} implemented on 2026-08-30.
 *
 * <p>
 * For units ({@code cap}/{@code ic}/{@code ric}/{@code rlh}), check only <b>the parsed unit</b> here.
 * Actual lengths need real-font cap-height and root-element line-height, so display-list baseline data
 * ({@code files/unittest/3020-VALUE/font-relative-units.html}) fixes those.
 *
 * <p>
 * For {@code rect()}/{@code xywh()}, check <b>the result normalized to {@code inset()}</b>.
 * The key is that right/bottom edges are distances from the top-left origin and must be inverted
 * to {@code 100% - value}. Parsing alone would pass even if this were reversed.
 */
public class BaselineCssUnitShapeTest extends TestCase {

	/** Base URI for resolving {@code url()}. */
	private static final java.net.URI BASE_URI = java.net.URI.create("file:///base/");

	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(BaselineCssUnitShapeTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
					case "getFontSize":
						return 12.0;
					case "getFontMagnification":
						return 1.0;
					case "getDocumentContext":
						return new DocumentContext();
					case "getProperty":
						return null;
					case "message":
						this.warnings.add(String.valueOf(args[0]) + ":" + java.util.Arrays.toString(args));
						return null;
					case "toString":
						return "BaselineCssUnitShapeTest.UserAgent";
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
		assertTrue(name + ": " + value + " が黙って受理された", property == null || !this.warnings.isEmpty());
	}

	// ---- 1. Units

	public void testQUnit() {
		// 1Q = 1/40 cm。10Q = 2.5mm
		assertEquals(2.5 / 25.4 * 72, ((AbsoluteLengthValue) this.single("width", "10Q")).getLength(), 1e-9);
	}

	public void testCapAndRlhUnits() {
		final RelativeLengthValue cap = (RelativeLengthValue) this.single("width", "10cap");
		assertEquals(Unit.CAP, cap.getUnit());
		assertEquals(10.0, cap.getValue(), 1e-9);
		final RelativeLengthValue rlh = (RelativeLengthValue) this.single("width", "2rlh");
		assertEquals(Unit.RLH, rlh.getUnit());
		assertEquals(2.0, rlh.getValue(), 1e-9);
	}

	public void testIcFoldsToEm() {
		// SPEC css-values-4: ic is the advance of ideograph U+6C34; the fallback when unavailable
		// is 1em. Full-width ideographs effectively have a 1em advance, so this implementation
		// always uses the fallback (intentional approximation). Likewise, normalize ric to rem.
		final RelativeLengthValue ic = (RelativeLengthValue) this.single("width", "10ic");
		assertEquals(Unit.EM, ic.getUnit());
		assertEquals(10.0, ic.getValue(), 1e-9);
		final RelativeLengthValue ric = (RelativeLengthValue) this.single("width", "10ric");
		assertEquals(Unit.REM, ric.getUnit());
		assertEquals(10.0, ric.getValue(), 1e-9);
		// The same notation using em / rem produces exactly the same value.
		final RelativeLengthValue em = (RelativeLengthValue) this.single("width", "10em");
		assertEquals(em.getUnit(), ic.getUnit());
		assertEquals(em.getValue(), ic.getValue(), 1e-9);
	}

	public void testCalcKeepsCapComponent() {
		// calc() carries font-relative components separately by unit through computed-value processing.
		// If a new unit is missing from the component array, it silently becomes 0 here.
		final Value value = this.single("width", "calc(5cap + 10pt)");
		assertTrue("CalcFontRelativeValue でない: " + value, value instanceof CalcFontRelativeValue);
		final int capIndex = CalcFontRelativeValue.indexOf(Unit.CAP);
		assertTrue("CAPが成分配列に無い", capIndex >= 0);
		// Components are private, so check with toString(), which prints each component.
		// (It lists "<coefficient><unit name>" in the same order as UNITS.)
		assertTrue("cap成分が保たれていない: " + value, value.toString().contains("5.0cap"));
		assertTrue("絶対成分が保たれていない: " + value, value.toString().contains("10.0pt"));
	}

	public void testCalcKeepsRlhComponent() {
		final Value value = this.single("width", "calc(1rlh + 1cap)");
		assertTrue(value instanceof CalcFontRelativeValue);
		assertTrue(value.toString().contains("1.0rlh"));
		assertTrue(value.toString().contains("1.0cap"));
	}

	// ---- 2. rect() / xywh()

	private ShapeSpec shape(final String value) {
		final Value v = this.single("clip-path", value);
		assertTrue("ClipPathValue でない: " + v, v instanceof ClipPath.ClipPathValue);
		return ((ClipPath.ClipPathValue) v).shape();
	}

	public void testRectParses() {
		final ShapeSpec spec = this.shape("rect(10pt 90pt 90pt 10pt)");
		assertTrue("Rect でない: " + spec, spec instanceof ShapeSpec.Rect);
		final ShapeSpec.Rect rect = (ShapeSpec.Rect) spec;
		assertEquals(10.0, ((AbsoluteLengthValue) rect.top()).getLength(), 1e-9);
		assertEquals(90.0, ((AbsoluteLengthValue) rect.right()).getLength(), 1e-9);
		// Retain auto as null.
		final ShapeSpec.Rect auto = (ShapeSpec.Rect) this.shape("rect(auto 90pt auto 10pt)");
		assertNull(auto.top());
		assertNull(auto.bottom());
		assertNotNull(auto.right());
		// Also accept round.
		assertNotNull(((ShapeSpec.Rect) this.shape("rect(10pt 90pt 90pt 10pt round 4pt)")).radii());
	}

	public void testXywhParses() {
		final ShapeSpec spec = this.shape("xywh(20pt 30pt 40pt 50pt)");
		assertTrue("Xywh でない: " + spec, spec instanceof ShapeSpec.Xywh);
		final ShapeSpec.Xywh xywh = (ShapeSpec.Xywh) spec;
		assertEquals(20.0, ((AbsoluteLengthValue) xywh.x()).getLength(), 1e-9);
		assertEquals(50.0, ((AbsoluteLengthValue) xywh.height()).getLength(), 1e-9);
		assertNotNull(((ShapeSpec.Xywh) this.shape("xywh(0 0 10pt 10pt round 2pt)")).radii());
	}

	public void testXywhRejects() {
		// xywh() has no auto.
		this.assertInvalid("clip-path", "xywh(auto 0 10pt 10pt)");
		// Width and height cannot be negative.
		this.assertInvalid("clip-path", "xywh(0 0 -10pt 10pt)");
		this.assertInvalid("clip-path", "xywh(0 0 10pt -10pt)");
		// Wrong number of values
		this.assertInvalid("clip-path", "xywh(0 0 10pt)");
		this.assertInvalid("clip-path", "rect(10pt 90pt 90pt)");
	}

	public void testRectFoldsToSameInsetAsInset() {
		// The key to this implementation: rect() right/bottom values are distances from the top-left origin,
		// so invert them with 100% - value to obtain inset() offsets. Reference-box dimensions are known
		// only at layout time, so carry these as percentage components of Length.
		// Parsing alone passes even without inversion, so compare resolved rectangles.
		final java.awt.geom.Rectangle2D fromRect = resolved("rect(10pt 90pt 90pt 10pt)");
		final java.awt.geom.Rectangle2D fromInset = resolved("inset(10pt)");
		assertEquals("x", fromInset.getX(), fromRect.getX(), 1e-9);
		assertEquals("y", fromInset.getY(), fromRect.getY(), 1e-9);
		assertEquals("幅", fromInset.getWidth(), fromRect.getWidth(), 1e-9);
		assertEquals("高さ", fromInset.getHeight(), fromRect.getHeight(), 1e-9);
		// For a 100 pt square box, expect an 80x80 rectangle from (10,10) to (90,90).
		assertEquals(10.0, fromRect.getX(), 1e-9);
		assertEquals(10.0, fromRect.getY(), 1e-9);
		assertEquals(80.0, fromRect.getWidth(), 1e-9);
		assertEquals(80.0, fromRect.getHeight(), 1e-9);
	}

	public void testRectAutoMeansBoxEdge() {
		// auto means the edge coincides with the reference-box edge = inset 0.
		final java.awt.geom.Rectangle2D r = resolved("rect(auto 90pt 90pt auto)");
		assertEquals(0.0, r.getX(), 1e-9);
		assertEquals(0.0, r.getY(), 1e-9);
		assertEquals(90.0, r.getWidth(), 1e-9);
		assertEquals(90.0, r.getHeight(), 1e-9);
	}

	public void testXywhFoldsToInset() {
		// xywh(20 30 40 50) is 40x50 from top-left (20,30).
		final java.awt.geom.Rectangle2D r = resolved("xywh(20pt 30pt 40pt 50pt)");
		assertEquals(20.0, r.getX(), 1e-9);
		assertEquals(30.0, r.getY(), 1e-9);
		assertEquals(40.0, r.getWidth(), 1e-9);
		assertEquals(50.0, r.getHeight(), 1e-9);
	}

	public void testRectPercentages() {
		// Percentages undergo the same inversion.
		final java.awt.geom.Rectangle2D r = resolved("rect(10% 90% 90% 10%)");
		assertEquals(10.0, r.getX(), 1e-9);
		assertEquals(80.0, r.getWidth(), 1e-9);
	}

	/** Apply to a 100 pt square box and return the clipping shape's bounding rectangle. */
	private java.awt.geom.Rectangle2D resolved(final String value) {
		final ClipPathShape shape = ClipPath.toShape(this.single("clip-path", value));
		assertTrue("Inset へ畳まれていない: " + shape, shape instanceof ClipPathShape.Inset);
		return shape.resolve(0, 0, 100, 100).getBounds2D();
	}

	public void testBasicShapesStillParsesInset() {
		// Adding rect()/xywh() does not break existing basic-shape forms.
		assertTrue(this.shape("inset(10pt)") instanceof ShapeSpec.Inset);
		assertTrue(this.shape("circle(50%)") instanceof ShapeSpec.Circle);
		assertTrue(this.shape("ellipse(40% 50%)") instanceof ShapeSpec.Ellipse);
		assertTrue(this.shape("polygon(0 0, 100% 0, 50% 100%)") instanceof ShapeSpec.Polygon);
		assertNotNull(BasicShapes.class);
	}

	// ---- 3. border-image parsing

	public void testBorderImageSource() {
		assertNotNull(this.single("border-image-source", "none"));
		assertNotNull(this.single("border-image-source", "url(frame.png)"));
		assertNotNull(this.single("border-image-source", "linear-gradient(red, blue)"));
		this.assertInvalid("border-image-source", "10pt");
	}

	public void testBorderImageSlice() {
		assertNotNull(this.single("border-image-slice", "30"));
		assertNotNull(this.single("border-image-slice", "30%"));
		assertNotNull(this.single("border-image-slice", "10 20 30 40"));
		assertNotNull(this.single("border-image-slice", "30 fill"));
		// fill may appear in any position.
		assertNotNull(this.single("border-image-slice", "fill 30"));
		// Negative values and five or more values are invalid.
		this.assertInvalid("border-image-slice", "-1");
		this.assertInvalid("border-image-slice", "1 2 3 4 5");
	}

	public void testBorderImageWidth() {
		assertNotNull(this.single("border-image-width", "1"));
		assertNotNull(this.single("border-image-width", "20pt"));
		assertNotNull(this.single("border-image-width", "25%"));
		assertNotNull(this.single("border-image-width", "auto"));
		assertNotNull(this.single("border-image-width", "1 2 3 4"));
		this.assertInvalid("border-image-width", "-1");
		this.assertInvalid("border-image-width", "1 2 3 4 5");
	}

	public void testBorderImageOutset() {
		assertNotNull(this.single("border-image-outset", "0"));
		assertNotNull(this.single("border-image-outset", "5pt"));
		assertNotNull(this.single("border-image-outset", "1 2"));
		this.assertInvalid("border-image-outset", "-1");
		// outset does not accept percentages.
		this.assertInvalid("border-image-outset", "10%");
	}

	public void testBorderImageRepeat() {
		assertNotNull(this.single("border-image-repeat", "stretch"));
		assertNotNull(this.single("border-image-repeat", "repeat"));
		assertNotNull(this.single("border-image-repeat", "round"));
		assertNotNull(this.single("border-image-repeat", "space"));
		assertNotNull(this.single("border-image-repeat", "repeat round"));
		this.assertInvalid("border-image-repeat", "no-such-repeat");
		this.assertInvalid("border-image-repeat", "repeat round space");
	}

	public void testBorderImageShorthand() {
		// Set all five longhands (shorthands also initialize values).
		for (final PrimitivePropertyInfo info : new PrimitivePropertyInfo[] { BorderImageSource.INFO,
				BorderImageSlice.INFO, BorderImageWidth.INFO, BorderImageOutset.INFO, BorderImageRepeat.INFO }) {
			assertNotNull(this.longhand("border-image", "url(frame.png) 30 stretch", info));
		}
	}

	public void testBorderImageShorthandSlashForms() {
		// Width follows /; outset follows //.
		assertNotNull(this.longhand("border-image", "url(a.png) 30 / 20pt", BorderImageWidth.INFO));
		assertNotNull(this.longhand("border-image", "url(a.png) 30 / 20pt / 5pt", BorderImageOutset.INFO));
		// The form `30 / / 5pt`, specifying only outset and omitting width, cannot be tested because
		// **the CSS parser (ph-css) cannot parse the declaration itself**, not because of this shorthand.
		// It practically never occurs in real documents, so do not pursue it (checked on 2026-08-30).
		// source may be omitted.
		assertNotNull(this.longhand("border-image", "30 stretch", BorderImageSlice.INFO));
		// Global keywords
		assertNotNull(this.longhand("border-image", "none", BorderImageSource.INFO));
	}

	public void testBorderImageShorthandResetsOmitted() {
		// Omitted components reset to their initial values. Without this, border-image-outset and
		// other values from the preceding rule would remain in effect.
		assertNotNull(this.longhand("border-image", "url(a.png)", BorderImageOutset.INFO));
		assertNotNull(this.longhand("border-image", "url(a.png)", BorderImageRepeat.INFO));
		assertNotNull(this.longhand("border-image", "url(a.png)", BorderImageWidth.INFO));
	}

}
