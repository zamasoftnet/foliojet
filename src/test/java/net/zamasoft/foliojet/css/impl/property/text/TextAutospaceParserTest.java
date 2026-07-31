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
import net.zamasoft.foliojet.css.value.TextAutospaceValue;

/**
 * {@code text-autospace}(和文詰めA1)の解析テストです。受理集合と
 * サブセット外(auto/punctuation/insert/replace・重複)の宣言無効を
 * 固定する。
 */
public class TextAutospaceParserTest extends TestCase {

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

	private static Object parse(final String css) {
		try {
			return ((TextAutospace) TextAutospace.INFO).parseValue(tokens("text-autospace: " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAccepted() {
		assertEquals(TextAutospaceValue.NORMAL, parse("normal"));
		assertEquals(TextAutospaceValue.NO_AUTOSPACE, parse("no-autospace"));
		assertEquals(TextAutospaceValue.IDEOGRAPH_ALPHA, parse("ideograph-alpha"));
		assertEquals(TextAutospaceValue.IDEOGRAPH_NUMERIC, parse("ideograph-numeric"));
		assertEquals(TextAutospaceValue.IDEOGRAPH_ALPHA_NUMERIC, parse("ideograph-alpha ideograph-numeric"));
		assertEquals(TextAutospaceValue.IDEOGRAPH_ALPHA_NUMERIC, parse("ideograph-numeric ideograph-alpha"));
	}

	public void testFlags() {
		assertEquals(TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC, TextAutospaceValue.NORMAL.getFlags());
		assertEquals(0, TextAutospaceValue.NO_AUTOSPACE.getFlags());
		assertEquals(TextAutospaceValue.ALPHA, TextAutospaceValue.IDEOGRAPH_ALPHA.getFlags());
		assertEquals(TextAutospaceValue.NUMERIC, TextAutospaceValue.IDEOGRAPH_NUMERIC.getFlags());
	}

	public void testRejected() {
		assertTrue(parse("auto") instanceof PropertyException);
		assertTrue(parse("punctuation") instanceof PropertyException);
		assertTrue(parse("insert") instanceof PropertyException);
		assertTrue(parse("replace") instanceof PropertyException);
		assertTrue(parse("normal ideograph-alpha") instanceof PropertyException);
		assertTrue(parse("ideograph-alpha ideograph-alpha") instanceof PropertyException);
		assertTrue(parse("no-autospace normal") instanceof PropertyException);
	}
}
