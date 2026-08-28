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
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;

/** css-content-3のスラッシュ後代替文字列を視覚内容から分離する回帰。 */
public class ContentAlternativeTextParserTest extends TestCase {

	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		if (decls == null) {
			return null;
		}
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		if (all.size() != 1) {
			return null;
		}
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static Object parse(final String css) {
		try {
			final TokenStream tokens = tokens("content: " + css);
			if (tokens == null) {
				return new PropertyException();
			}
			return ((Content) Content.INFO).parseValue(tokens, null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAlternativeTextIsNotVisualContent() {
		final Value[] values = ((ValueListValue) parse("'\\200b' / ' (external)'")).getValues();
		assertEquals(1, values.length);
		assertEquals("\u200b", ((StringValue) values[0]).getString());
	}

	public void testSupportedAlternativeGrammar() {
		final Value[] values = ((ValueListValue) parse(
				"'Chapter ' counter(chapter) / 'Chapter ' counter(chapter) attr(data-alt)")).getValues();
		assertEquals(2, values.length);
		assertEquals("Chapter ", ((StringValue) values[0]).getString());
	}

	public void testInvalidAlternativeTextIsRejected() {
		assertTrue(parse("'x' /") instanceof PropertyException);
		assertTrue(parse("/ 'alt'") instanceof PropertyException);
		assertTrue(parse("'x' / url(alt.png)") instanceof PropertyException);
		assertTrue(parse("'x' / open-quote") instanceof PropertyException);
		assertTrue(parse("'x' / 'alt' / 'again'") instanceof PropertyException);
	}
}
