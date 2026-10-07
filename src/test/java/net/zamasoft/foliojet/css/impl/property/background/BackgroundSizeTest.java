package net.zamasoft.foliojet.css.impl.property.background;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * Tests for the {@code contain}/{@code cover} keyword forms of background-size
 * (2026-08-06).
 *
 * <p>
 * Previously, only the two-value {@code <length>|<percentage>|auto} syntax was supported.
 * {@code contain}/{@code cover} threw {@link PropertyException} and fell back entirely to
 * the default {@code auto auto} (the image's original size). For icons fitting a larger image
 * (such as a sprite sheet) into a small box, this showed only a clipped, tiny part of the
 * original-size image (discovered in yahoo.co.jp sidebar icons).
 * </p>
 */
public class BackgroundSizeTest extends TestCase {

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

	private static Entry[] parse(final String value) throws PropertyException {
		BackgroundSize info = (BackgroundSize) BackgroundSize.INFO_WIDTH;
		return info.parseValues(tokens("background-size: " + value), null, null);
	}

	public void testContainKeyword() throws Exception {
		Entry[] entries = parse("contain");
		assertEquals(2, entries.length);
		assertSame(KeywordValue.CONTAIN, entries[0].getValue());
		assertSame(KeywordValue.CONTAIN, entries[1].getValue());
	}

	public void testCoverKeyword() throws Exception {
		Entry[] entries = parse("cover");
		assertEquals(2, entries.length);
		assertSame(KeywordValue.COVER, entries[0].getValue());
		assertSame(KeywordValue.COVER, entries[1].getValue());
	}

	public void testContainRejectsTrailingValue() {
		try {
			parse("contain 10px");
			fail("contain must be the sole value");
		} catch (PropertyException e) {
			// expected
		}
	}

	public void testCoverRejectsTrailingValue() {
		try {
			parse("cover 10px");
			fail("cover must be the sole value");
		} catch (PropertyException e) {
			// expected
		}
	}

	/** The existing <length>|<percentage>|auto syntax does not regress. */
	public void testExistingLengthSyntaxUnchanged() throws Exception {
		Entry[] entries = parse("10px 20px");
		assertEquals(2, entries.length);
		assertTrue(entries[0].getValue() instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue);
		assertTrue(entries[1].getValue() instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue);
	}

	public void testExistingAutoSyntaxUnchanged() throws Exception {
		Entry[] entries = parse("auto");
		assertEquals(2, entries.length);
		assertSame(KeywordValue.AUTO, entries[0].getValue());
		assertSame(KeywordValue.AUTO, entries[1].getValue());
	}
}
