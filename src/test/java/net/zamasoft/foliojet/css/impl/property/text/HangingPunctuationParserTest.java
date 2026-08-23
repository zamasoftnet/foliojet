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
import net.zamasoft.foliojet.css.value.HangingPunctuationValue;

/** JLREQで使うhanging-punctuationの値と組合せを固定する。 */
public class HangingPunctuationParserTest extends TestCase {
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
			return ((HangingPunctuation) HangingPunctuation.INFO)
					.parseValue(tokens("hanging-punctuation: " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAccepted() {
		assertEquals(HangingPunctuationValue.NONE, parse("none"));
		assertEquals(HangingPunctuationValue.FIRST, parse("first"));
		assertEquals(HangingPunctuationValue.ALLOW_END, parse("allow-end"));
		assertEquals(HangingPunctuationValue.FORCE_END, parse("force-end"));
		assertEquals(HangingPunctuationValue.FIRST_ALLOW_END, parse("first allow-end"));
		assertEquals(HangingPunctuationValue.FIRST_ALLOW_END, parse("allow-end first"));
		assertEquals(HangingPunctuationValue.FIRST_FORCE_END, parse("force-end first"));
	}

	public void testRejected() {
		assertTrue(parse("last") instanceof PropertyException);
		assertTrue(parse("first first") instanceof PropertyException);
		assertTrue(parse("allow-end force-end") instanceof PropertyException);
		assertTrue(parse("none first") instanceof PropertyException);
	}
}
