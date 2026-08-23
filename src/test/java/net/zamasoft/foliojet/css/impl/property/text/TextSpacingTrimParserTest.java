package net.zamasoft.foliojet.css.impl.property.text;

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
import net.zamasoft.foliojet.css.value.TextSpacingTrimValue;

/** CSS Text 4に合わせたtext-spacing-trimの受理値と意味を固定する。 */
public class TextSpacingTrimParserTest extends TestCase {

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

	private static Object parse(final String css) {
		try {
			return ((TextSpacingTrim) TextSpacingTrim.INFO)
					.parseValue(tokens("text-spacing-trim: " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAccepted() {
		assertEquals(TextSpacingTrimValue.NORMAL, parse("normal"));
		assertEquals(TextSpacingTrimValue.SPACE_ALL, parse("space-all"));
		assertEquals(TextSpacingTrimValue.SPACE_FIRST, parse("space-first"));
		assertEquals(TextSpacingTrimValue.TRIM_START, parse("trim-start"));
		assertEquals(TextSpacingTrimValue.TRIM_BOTH, parse("trim-both"));
		assertEquals(TextSpacingTrimValue.AUTO, parse("auto"));
	}

	public void testPolicies() {
		assertFalse(TextSpacingTrimValue.NORMAL.isSpaceAll());
		assertFalse(TextSpacingTrimValue.NORMAL.trimsLineStart());
		assertFalse(TextSpacingTrimValue.NORMAL.trimsLineEnd());
		assertFalse(TextSpacingTrimValue.NORMAL.spacesFirstLine());
		assertTrue(TextSpacingTrimValue.SPACE_ALL.isSpaceAll());
		assertFalse(TextSpacingTrimValue.SPACE_ALL.trimsLineStart());
		assertFalse(TextSpacingTrimValue.SPACE_ALL.trimsLineEnd());
		assertTrue(TextSpacingTrimValue.SPACE_FIRST.spacesFirstLine());
		assertFalse(TextSpacingTrimValue.SPACE_FIRST.trimsLineStart());
		assertFalse(TextSpacingTrimValue.TRIM_START.isSpaceAll());
		assertTrue(TextSpacingTrimValue.TRIM_START.trimsLineStart());
		assertFalse(TextSpacingTrimValue.TRIM_START.trimsLineEnd());
		assertTrue(TextSpacingTrimValue.TRIM_BOTH.trimsLineStart());
		assertTrue(TextSpacingTrimValue.TRIM_BOTH.trimsLineEnd());
		assertTrue(TextSpacingTrimValue.AUTO.trimsLineStart());
		assertTrue(TextSpacingTrimValue.AUTO.trimsLineEnd());
	}

	public void testRejected() {
		assertTrue(parse("trim-all") instanceof PropertyException);
		assertTrue(parse("normal trim-start") instanceof PropertyException);
	}
}
