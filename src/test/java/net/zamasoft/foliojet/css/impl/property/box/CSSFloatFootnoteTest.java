package net.zamasoft.foliojet.css.impl.property.box;

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
import net.zamasoft.foliojet.css.value.CSSFloatValue;
import net.zamasoft.foliojet.css.value.Value;

/**
 * {@code float: footnote}の解析テストです(脚注F0、
 * consult-codex-2026-07-31-footnote.txt §5)。レイアウト配線(F3)までは
 * 構文層のみ——通常フローへの退避はStyleBoxEmitterの分岐が守る。
 */
public class CSSFloatFootnoteTest extends TestCase {

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

	private static Value parse(final String value) throws PropertyException {
		return ((CSSFloat) CSSFloat.INFO).parseValue(tokens("float: " + value), null, null);
	}

	public void testFootnoteKeyword() throws Exception {
		assertSame(CSSFloatValue.FOOTNOTE_VALUE, parse("footnote"));
		assertEquals(CSSFloatValue.FOOTNOTE, CSSFloatValue.FOOTNOTE_VALUE.getFloat());
		assertEquals("footnote", CSSFloatValue.FOOTNOTE_VALUE.toString());
	}

	public void testExistingKeywordsUnchanged() throws Exception {
		assertSame(CSSFloatValue.NONE_VALUE, parse("none"));
		assertSame(CSSFloatValue.LEFT_VALUE, parse("left"));
		assertSame(CSSFloatValue.END_VALUE, parse("end"));
	}

	public void testUnknownKeywordRejected() {
		try {
			parse("bogus");
			fail("unknown float keyword must be rejected");
		} catch (PropertyException e) {
			// expected
		}
	}
}
