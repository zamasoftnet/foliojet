package net.zamasoft.foliojet.css.impl.property.font;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.FontFeatureSettingsValue;
import net.zamasoft.foliojet.css.value.FontVariantEastAsianValue;
import net.zamasoft.foliojet.css.value.FontVariantNumericValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;

/**
 * Tests for parsing {@code font-feature-settings}/{@code font-variant-east-asian}
 * and for {@link FontFeatureSet} canonical form and override rules
 * (increment ① = parsing and transport. consult-codex-2026-07-31-font-features.txt §5.3).
 */
public class FontFeatureSettingsTest extends TestCase {

	/** Obtain the declaration-value token sequence through the real ph-css pipeline. */
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

	private static FontFeatureSet parseSettings(final String value) throws PropertyException {
		final Value v = ((FontFeatureSettings) FontFeatureSettings.INFO)
				.parseValue(tokens("font-feature-settings: " + value), null, null);
		return ((FontFeatureSettingsValue) v).getFeatures();
	}

	private static FontVariantEastAsianValue parseEastAsian(final String value) throws PropertyException {
		return (FontVariantEastAsianValue) ((FontVariantEastAsian) FontVariantEastAsian.INFO)
				.parseValue(tokens("font-variant-east-asian: " + value), null, null);
	}

	private static FontVariantNumericValue parseNumeric(final String value) throws PropertyException {
		return (FontVariantNumericValue) ((FontVariantNumeric) FontVariantNumeric.INFO)
				.parseValue(tokens("font-variant-numeric: " + value), null, null);
	}

	private static void assertRejected(final String value) {
		try {
			parseSettings(value);
			fail("解析が拒否されるべき値: " + value);
		} catch (PropertyException e) {
			// expected
		}
	}

	private static void assertEastAsianRejected(final String value) {
		try {
			parseEastAsian(value);
			fail("解析が拒否されるべき値: " + value);
		} catch (PropertyException e) {
			// expected
		}
	}

	private static void assertNumericRejected(final String value) {
		try {
			parseNumeric(value);
			fail("解析が拒否されるべき値: " + value);
		} catch (PropertyException e) {
			// expected
		}
	}

	public void testFeatureSettingsList() throws Exception {
		final FontFeatureSet set = parseSettings("\"palt\" 1, \"jp78\" on, \"kern\" off");
		assertEquals(3, set.size());
		assertEquals(1, set.value(FontFeatureSet.packTag("palt")));
		assertEquals(1, set.value(FontFeatureSet.packTag("jp78")));
		assertEquals(0, set.value(FontFeatureSet.packTag("kern")));
		// Unspecified tags return -1 (distinguish unspecified from explicit 0).
		assertEquals(-1, set.value(FontFeatureSet.packTag("liga")));
	}

	public void testFeatureSettingsDefaultsAndNumbers() throws Exception {
		// An omitted value means 1; retain integer values unchanged (for future Alternate support).
		final FontFeatureSet set = parseSettings("\"liga\", \"salt\" 3");
		assertEquals(1, set.value(FontFeatureSet.packTag("liga")));
		assertEquals(3, set.value(FontFeatureSet.packTag("salt")));
	}

	public void testFeatureSettingsNormal() throws Exception {
		assertTrue(parseSettings("normal").isEmpty());
		assertSame(FontFeatureSet.EMPTY, parseSettings("normal"));
	}

	public void testFeatureSettingsDuplicateLastWins() throws Exception {
		final FontFeatureSet set = parseSettings("\"palt\" 1, \"palt\" 0");
		assertEquals(1, set.size());
		assertEquals(0, set.value(FontFeatureSet.packTag("palt")));
	}

	public void testFeatureSettingsCanonicalOrder() throws Exception {
		// Different enumeration order yields the same canonical form (identity as a cache key).
		assertEquals(parseSettings("\"palt\" 1, \"jp78\" 1"), parseSettings("\"jp78\" 1, \"palt\" 1"));
		assertEquals(parseSettings("\"palt\", \"jp78\"").hashCode(),
				parseSettings("\"jp78\" on, \"palt\" on").hashCode());
	}

	public void testFeatureSettingsRejections() {
		assertRejected("\"pal\" 1"); // Three-character tag
		assertRejected("\"palto\" 1"); // Five-character tag
		assertRejected("\"palt\" -1"); // Negative number
		assertRejected("\"palt\" 1.5"); // Non-integer
		assertRejected("\"palt\" 1 2"); // Extra token
		assertRejected("palt"); // Unquoted (identifier other than normal)
		assertRejected("\"palt\" on off"); // Extra keyword
	}

	public void testEastAsianKeywords() throws Exception {
		final FontVariantEastAsianValue v = parseEastAsian("jis78 proportional-width ruby");
		final FontFeatureSet set = v.featureSet();
		assertEquals(3, set.size());
		assertEquals(1, set.value(FontFeatureSet.packTag("jp78")));
		assertEquals(1, set.value(FontFeatureSet.packTag("pwid")));
		assertEquals(1, set.value(FontFeatureSet.packTag("ruby")));
	}

	public void testEastAsianOrderIndependent() throws Exception {
		assertEquals(parseEastAsian("ruby jis04").featureSet(), parseEastAsian("jis04 ruby").featureSet());
	}

	public void testEastAsianNormal() throws Exception {
		assertTrue(parseEastAsian("normal").isNormal());
		assertSame(FontFeatureSet.EMPTY, parseEastAsian("normal").featureSet());
	}

	public void testEastAsianRejections() {
		assertEastAsianRejected("jis78 jis83"); // Conflicting glyph variants
		assertEastAsianRejected("full-width proportional-width"); // Conflicting width variants
		assertEastAsianRejected("ruby ruby"); // Duplicate
		assertEastAsianRejected("bogus");
		assertEastAsianRejected("normal ruby"); // normal must stand alone.
	}

	public void testOverrideMerge() throws Exception {
		// Explicit tags from font-feature-settings override tags from font-variant-east-asian
		// (same composition as CSSStyle.getFontStyle).
		final FontFeatureSet eastAsian = parseEastAsian("jis78 ruby").featureSet();
		final FontFeatureSet merged = eastAsian.override(parseSettings("\"jp78\" 0, \"palt\" 1"));
		assertEquals(0, merged.value(FontFeatureSet.packTag("jp78")));
		assertEquals(1, merged.value(FontFeatureSet.packTag("ruby")));
		assertEquals(1, merged.value(FontFeatureSet.packTag("palt")));
		// font-feature-settings:normal (empty set) does not override.
		assertSame(eastAsian, eastAsian.override(FontFeatureSet.EMPTY));
	}

	public void testNumericKeywords() throws Exception {
		final FontFeatureSet set = parseNumeric(
				"tabular-nums lining-nums diagonal-fractions ordinal slashed-zero").featureSet();
		assertEquals(5, set.size());
		assertEquals(1, set.value(FontFeatureSet.packTag("tnum")));
		assertEquals(1, set.value(FontFeatureSet.packTag("lnum")));
		assertEquals(1, set.value(FontFeatureSet.packTag("frac")));
		assertEquals(1, set.value(FontFeatureSet.packTag("ordn")));
		assertEquals(1, set.value(FontFeatureSet.packTag("zero")));
	}

	public void testNumericNormalAndOrder() throws Exception {
		assertTrue(parseNumeric("normal").isNormal());
		assertSame(FontFeatureSet.EMPTY, parseNumeric("normal").featureSet());
		assertEquals(parseNumeric("oldstyle-nums proportional-nums").featureSet(),
				parseNumeric("proportional-nums oldstyle-nums").featureSet());
	}

	public void testNumericRejections() {
		assertNumericRejected("tabular-nums proportional-nums");
		assertNumericRejected("lining-nums oldstyle-nums");
		assertNumericRejected("diagonal-fractions stacked-fractions");
		assertNumericRejected("ordinal ordinal");
		assertNumericRejected("normal tabular-nums");
		assertNumericRejected("bogus");
	}

	public void testNumericMergePrecedence() throws Exception {
		final FontFeatureSet variants = parseEastAsian("jis78").featureSet()
				.override(parseNumeric("tabular-nums").featureSet());
		final FontFeatureSet merged = variants.override(parseSettings("\"tnum\" 0"));
		assertEquals(1, merged.value(FontFeatureSet.packTag("jp78")));
		assertEquals(0, merged.value(FontFeatureSet.packTag("tnum")));
	}
}
