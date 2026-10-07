package net.zamasoft.foliojet.css.impl.property.box;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.util.BasicShapes.ShapeSpec;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.ClipPathShape;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Parsing tests for {@code shape-outside}, {@code shape-margin}, and {@code shape-image-threshold}
 * (css-shapes-1, introduced on 2026-08-29). Fix acceptance/rejection and the default reference box
 * when only a basic-shape is specified (margin-box, unlike {@code clip-path}'s border-box).
 * The parser shares {@code BasicShapes} with {@code clip-path}, so existing {@code clip-path} tests
 * cover shape syntax. Here, check only shared wiring and property-specific rules.
 */
public class ShapeOutsideParserTest extends TestCase {

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

	private static Object proxy(final Class<?> type, final String name) {
		return java.lang.reflect.Proxy.newProxyInstance(ShapeOutsideParserTest.class.getClassLoader(),
				new Class[] { type }, (p, method, args) -> {
					switch (method.getName()) {
					case "getPixelsPerInch":
						return 96.0;
					case "getDocumentContext":
						return new net.zamasoft.foliojet.ua.DocumentContext();
					case "toString":
						return "ShapeOutsideParserTest." + name;
					case "hashCode":
						return System.identityHashCode(p);
					case "equals":
						return p == args[0];
					default:
						throw new UnsupportedOperationException(method.toString());
					}
				});
	}

	private static UserAgent ua() {
		return (UserAgent) proxy(UserAgent.class, "UserAgent");
	}

	private static Value parse(final PrimitivePropertyInfo info, final String value) throws PropertyException {
		return ((net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo) info)
				.parseValue(tokens(info.getName() + ": " + value), ua(), java.net.URI.create("file:///base/"));
	}

	private static void assertInvalid(final PrimitivePropertyInfo info, final String value) {
		try {
			parse(info, value);
			fail(info.getName() + ": " + value + " が受理された");
		} catch (final PropertyException e) {
			// As expected.
		}
	}

	public void testNone() throws Exception {
		assertSame(KeywordValue.NONE, parse(ShapeOutside.INFO, "none"));
	}

	public void testBasicShapeDefaultsToMarginBox() throws Exception {
		final ShapeOutside.ShapeOutsideValue v = (ShapeOutside.ShapeOutsideValue) parse(ShapeOutside.INFO,
				"circle(50%)");
		assertTrue(v.shape() instanceof ShapeSpec.Circle);
		assertEquals(ClipPathShape.ReferenceBox.MARGIN_BOX, v.box());
		assertNull(v.image());
		final ShapeSpec.Circle c = (ShapeSpec.Circle) v.shape();
		assertEquals(50.0, ((PercentageValue) c.radius()).getPercentage(), 0);
	}

	public void testShapeBoxInEitherOrder() throws Exception {
		ShapeOutside.ShapeOutsideValue v = (ShapeOutside.ShapeOutsideValue) parse(ShapeOutside.INFO,
				"polygon(0 0, 100% 0, 100% 100%) border-box");
		assertTrue(v.shape() instanceof ShapeSpec.Polygon);
		assertEquals(ClipPathShape.ReferenceBox.BORDER_BOX, v.box());
		v = (ShapeOutside.ShapeOutsideValue) parse(ShapeOutside.INFO, "content-box ellipse(40% 30% at center)");
		assertTrue(v.shape() instanceof ShapeSpec.Ellipse);
		assertEquals(ClipPathShape.ReferenceBox.CONTENT_BOX, v.box());
		v = (ShapeOutside.ShapeOutsideValue) parse(ShapeOutside.INFO, "inset(10px round 5px) padding-box");
		assertTrue(v.shape() instanceof ShapeSpec.Inset);
		assertEquals(ClipPathShape.ReferenceBox.PADDING_BOX, v.box());
	}

	public void testShapeBoxOnly() throws Exception {
		final ShapeOutside.ShapeOutsideValue v = (ShapeOutside.ShapeOutsideValue) parse(ShapeOutside.INFO,
				"margin-box");
		assertNull(v.shape());
		assertEquals(ClipPathShape.ReferenceBox.MARGIN_BOX, v.box());
	}

	public void testImage() throws Exception {
		final ShapeOutside.ShapeOutsideValue v = (ShapeOutside.ShapeOutsideValue) parse(ShapeOutside.INFO,
				"url(shape.png)");
		assertNull(v.shape());
		assertNull(v.box());
		assertEquals("file:///base/shape.png", v.image().getURI().toString());
	}

	public void testShapeOutsideRejects() {
		assertInvalid(ShapeOutside.INFO, "circle(50%) circle(50%)");
		assertInvalid(ShapeOutside.INFO, "border-box padding-box");
		assertInvalid(ShapeOutside.INFO, "url(a.png) margin-box");
		assertInvalid(ShapeOutside.INFO, "none margin-box");
		assertInvalid(ShapeOutside.INFO, "foo");
		// path() was implemented in BasicShapes on 2026-08-29, so shape-outside accepts it too.
		assertInvalid(ShapeOutside.INFO, "linear-gradient(red, blue)");
	}

	public void testShapeMargin() throws Exception {
		assertEquals(7.5, ((AbsoluteLengthValue) parse(ShapeMargin.INFO, "10px")).getLength(), 1e-6);
		assertEquals(5.0, ((PercentageValue) parse(ShapeMargin.INFO, "5%")).getPercentage(), 0);
		assertInvalid(ShapeMargin.INFO, "-1px");
		assertInvalid(ShapeMargin.INFO, "auto");
		assertInvalid(ShapeMargin.INFO, "1px 2px");
	}

	public void testShapeImageThresholdClamps() throws Exception {
		assertEquals(0.5, ((RealValue) parse(ShapeImageThreshold.INFO, "0.5")).getReal(), 0);
		assertEquals(1.0, ((RealValue) parse(ShapeImageThreshold.INFO, "2")).getReal(), 0);
		assertEquals(0.0, ((RealValue) parse(ShapeImageThreshold.INFO, "-1")).getReal(), 0);
		assertInvalid(ShapeImageThreshold.INFO, "abc");
		assertInvalid(ShapeImageThreshold.INFO, "50%");
		assertInvalid(ShapeImageThreshold.INFO, "0.5 0.5");
	}
}
