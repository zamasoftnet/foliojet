package net.zamasoft.foliojet.css.impl.property.content;

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
import net.zamasoft.foliojet.css.value.LeaderValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;

/**
 * Parsing tests for {@code content: leader(...)}
 * (consult-codex-2026-07-31-leader.txt L1).
 */
public class LeaderParserTest extends TestCase {

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
			return ((Content) Content.INFO).parseValue(tokens("content: " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	private static LeaderValue single(final String css) {
		final Value[] values = ((ValueListValue) parse(css)).getValues();
		assertEquals(1, values.length);
		return (LeaderValue) values[0];
	}

	public void testAccepted() {
		assertEquals(".", single("leader(dotted)").getPattern());
		assertEquals("_", single("leader(solid)").getPattern());
		assertEquals(" ", single("leader(space)").getPattern());
		assertEquals(". ", single("leader('. ')").getPattern());

		// Combined with target-counter() (standard table-of-contents form).
		final Value[] values = ((ValueListValue) parse("leader(dotted) target-counter(attr(href), page)")).getValues();
		assertEquals(2, values.length);
		assertTrue(values[0] instanceof LeaderValue);
	}

	public void testRejected() {
		assertTrue(parse("leader()") instanceof PropertyException);
		assertTrue(parse("leader(bogus)") instanceof PropertyException);
		assertTrue(parse("leader('')") instanceof PropertyException);
		// Only line breaks = empty after removal → syntax error to avoid infinite repetition with a zero period.
		assertTrue(parse("leader('\\A')") instanceof PropertyException);
		assertTrue(parse("leader(dotted solid)") instanceof PropertyException);
	}
}
