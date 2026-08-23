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
import net.zamasoft.foliojet.css.value.TextOrientationValue;

/** CSS Writing Modesのtext-orientation受理値を固定する。 */
public class TextOrientationParserTest extends TestCase {
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
			return ((TextOrientation) TextOrientation.INFO)
					.parseValue(tokens("text-orientation: " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAccepted() {
		assertEquals(TextOrientationValue.MIXED, parse("mixed"));
		assertEquals(TextOrientationValue.UPRIGHT, parse("upright"));
		assertEquals(TextOrientationValue.SIDEWAYS, parse("sideways"));
	}

	public void testRejected() {
		assertTrue(parse("sideways-right") instanceof PropertyException);
		assertTrue(parse("mixed upright") instanceof PropertyException);
	}
}
