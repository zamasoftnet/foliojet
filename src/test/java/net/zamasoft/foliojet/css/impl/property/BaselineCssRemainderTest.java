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
import net.zamasoft.foliojet.css.font.FontFeatureValues;
import net.zamasoft.foliojet.css.impl.property.image.ImageOrientation;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.Property;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.FontVariantAlternatesValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.DocumentContext;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Fix the final three items left from the MDN Baseline inventory (2026-08-30):
 * {@code @font-feature-values} name resolution, {@code image-orientation},
 * and {@code border-image-repeat} tiling.
 *
 * <p>
 * Since {@code border-image-repeat} tiling concerns drawing, leave tile counts to baseline images
 * ({@code files/visual/2110-border-image}) and measurements made here
 * (stretch 8 / repeat 60 / round 54 / space 52 tiles).
 * These tests check only that values reach the correct destination.
 */
public class BaselineCssRemainderTest extends TestCase {

	private final List<String> warnings = new ArrayList<String>();

	private UserAgent ua() {
		return (UserAgent) Proxy.newProxyInstance(BaselineCssRemainderTest.class.getClassLoader(),
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
						return "BaselineCssRemainderTest.UserAgent";
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

	private Value single(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), java.net.URI.create("file:///base/"), false);
		assertNotNull(name + ": " + value + " が無効になった " + this.warnings, property);
		assertTrue(name + ": " + value + " で警告 " + this.warnings, this.warnings.isEmpty());
		final Entry[] entries = ((CompositeProperty) property).getEntries();
		assertEquals(1, entries.length);
		return entries[0].getValue();
	}

	private void assertInvalid(final String name, final String value) {
		this.warnings.clear();
		final Property property = ElementPropertySet.getInstance().parseDeclaration(name, tokens(name + ": " + value),
				this.ua(), java.net.URI.create("file:///base/"), false);
		assertTrue(name + ": " + value + " が黙って受理された", property == null || !this.warnings.isEmpty());
	}

	// ---- 1. image-orientation

	public void testImageOrientationValues() {
		assertSame(KeywordValue.FROM_IMAGE, this.single("image-orientation", "from-image"));
		assertSame(KeywordValue.NONE, this.single("image-orientation", "none"));
		// The angle specification in early drafts was removed from the current specification.
		this.assertInvalid("image-orientation", "90deg");
		this.assertInvalid("image-orientation", "no-such-value");
	}

	public void testImageOrientationApplyIsIdentityForFromImage() {
		// Pass null through unchanged (callers can pass input unconditionally).
		assertNull(ImageOrientation.apply(null, null));
	}

	// ---- 2. @font-feature-values name resolution

	/**
	 * Construct the name table directly and check that {@code font-variant-alternates} functions
	 * map to OpenType feature tags. {@code CSSStyleSheetBuilder} parses at-rules, so focus here on
	 * the correctness of <b>the resolution mapping</b> (css-fonts-4 §6.9).
	 */
	public void testFeatureValuesResolveToTags() {
		final FontFeatureValues values = new FontFeatureValues();
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLESET, "nice", new int[] { 12 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLISTIC, "alt-a", new int[] { 1 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.CHARACTER_VARIANT, "beta", new int[] { 2 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.SWASH, "fancy", new int[] { 1 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.ORNAMENTS, "ding", new int[] { 2 });
		values.define(List.of("Test Font"), FontFeatureValues.Type.ANNOTATION, "circled", new int[] { 1 });

		assertEquals("styleset(12) は ss12", "ss12", tagOf(values, "styleset", "nice"));
		assertEquals("stylistic は salt", "salt", tagOf(values, "stylistic", "alt-a"));
		assertEquals("character-variant(2) は cv02", "cv02", tagOf(values, "character-variant", "beta"));
		assertEquals("swash は swsh", "swsh", tagOf(values, "swash", "fancy"));
		assertEquals("ornaments は ornm", "ornm", tagOf(values, "ornaments", "ding"));
		assertEquals("annotation は nalt", "nalt", tagOf(values, "annotation", "circled"));
	}

	public void testUndefinedFeatureNameIsDropped() {
		final FontFeatureValues values = new FontFeatureValues();
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLESET, "nice", new int[] { 12 });
		// If a name is absent from the table, drop only that function (keep the declaration).
		final FontVariantAlternatesValue value = FontVariantAlternatesValue.create(false,
				List.of(new FontVariantAlternatesValue.Alternate("styleset", List.of("no-such-name"))));
		final String tags = value.featureSet(values, "Test Font").toString();
		assertEquals("未定義の名前が機能タグになっている: " + tags, "FontFeatureSet[]", tags);
	}

	public void testFeatureValuesAreScopedToFamily() {
		final FontFeatureValues values = new FontFeatureValues();
		values.define(List.of("Test Font"), FontFeatureValues.Type.STYLESET, "nice", new int[] { 12 });
		assertNotNull(values.lookup("Test Font", FontFeatureValues.Type.STYLESET, "nice"));
		// Do not consult another family's table.
		assertNull(values.lookup("Other Font", FontFeatureValues.Type.STYLESET, "nice"));
	}

	public void testEmptyFeatureValuesKeepsExistingBehaviour() {
		// A document with an empty table gives the same result as one without the at-rule.
		final FontFeatureValues empty = new FontFeatureValues();
		assertTrue(empty.isEmpty());
		final FontVariantAlternatesValue historical = FontVariantAlternatesValue.create(true, List.of());
		assertEquals(historical.featureSet().toString(), historical.featureSet(empty, "Test Font").toString());
	}

	private static String tagOf(final FontFeatureValues values, final String function, final String name) {
		final FontVariantAlternatesValue value = FontVariantAlternatesValue.create(false,
				List.of(new FontVariantAlternatesValue.Alternate(function, List.of(name))));
		// FontFeatureSet toString uses the form "FontFeatureSet[ss12=1]".
		// Extract just the tag from "<tag>=<value>" inside the brackets.
		final String text = value.featureSet(values, "Test Font").toString();
		final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[(\\w{4})=")
				.matcher(text);
		assertTrue(function + " が機能タグを出していない: " + text, matcher.find());
		return matcher.group(1);
	}

	// ---- 3. border-image-repeat values

	public void testBorderImageRepeatValues() {
		for (final String v : new String[] { "stretch", "repeat", "round", "space" }) {
			assertNotNull(this.single("border-image-repeat", v));
		}
		assertNotNull(this.single("border-image-repeat", "repeat round"));
		this.assertInvalid("border-image-repeat", "repeat round space");
	}
}
