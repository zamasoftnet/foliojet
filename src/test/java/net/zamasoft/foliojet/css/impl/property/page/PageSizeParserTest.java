package net.zamasoft.foliojet.css.impl.property.page;

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
import net.zamasoft.foliojet.css.value.PageSizeValue;

/** {@code @page size}(N3/N4)の解析テストです。 */
public class PageSizeParserTest extends TestCase {

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
			return ((PageSize) PageSize.INFO).parseValue(tokens("size: " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAccepted() throws Exception {
		assertEquals(PageSizeValue.AUTO, parse("auto"));
		final PageSizeValue landscape = (PageSizeValue) parse("landscape");
		assertEquals(PageSizeValue.ORIENTATION_LANDSCAPE, landscape.orientation);
		final double[] l = landscape.resolve(595, 842);
		assertEquals(842.0, l[0], 0.1);
		assertEquals(595.0, l[1], 0.1);

		final PageSizeValue a4l = (PageSizeValue) parse("A4 landscape");
		final double[] r = a4l.resolve(100, 100);
		assertEquals(841.89, r[0], 0.01);
		assertEquals(595.28, r[1], 0.01);

		final PageSizeValue two = (PageSizeValue) parse("200pt 300pt");
		assertEquals(200.0, two.width, 0.01);
		assertEquals(300.0, two.height, 0.01);
		final PageSizeValue one = (PageSizeValue) parse("200pt");
		assertEquals(200.0, one.height, 0.01);
	}

	public void testRejected() {
		assertTrue(parse("bogus") instanceof PropertyException);
		assertTrue(parse("200pt landscape") instanceof PropertyException);
		assertTrue(parse("2em") instanceof PropertyException);
	}
}
