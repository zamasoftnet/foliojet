package net.zamasoft.foliojet.css.impl.property.ext;

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
import net.zamasoft.foliojet.css.value.ext.CSSJWarichuValue;

/** 標準CSSに対応する機能がない割注拡張の受理値を固定する。 */
public class CSSJWarichuParserTest extends TestCase {
	private static TokenStream tokens(final String value) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList
				.readFromString("-cssj-warichu: " + value, settings);
		assertNotNull(decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static Object parse(final String value) {
		try {
			return ((CSSJWarichu) CSSJWarichu.INFO).parseValue(tokens(value), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAccepted() {
		assertEquals(CSSJWarichuValue.NONE, parse("none"));
		assertEquals(CSSJWarichuValue.AUTO, parse("auto"));
	}

	public void testRejected() {
		assertTrue(parse("always") instanceof PropertyException);
		assertTrue(parse("auto none") instanceof PropertyException);
	}
}
