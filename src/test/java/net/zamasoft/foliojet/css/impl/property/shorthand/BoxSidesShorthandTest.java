package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.CompositeProperty;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;

/**
 * Fix four-side shorthand distribution (2026-10-04, duplicates in six classes merged into {@link
 * BoxSidesShorthand}).
 */
public class BoxSidesShorthandTest extends TestCase {
	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull(declaration, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		return new TokenStream(Tokens.fromExpression(all.get(0).getExpression()));
	}

	/** Return values in top, right, bottom, left order. */
	private static String sides(final ShorthandPropertyInfo info, final String name, final String value)
			throws PropertyException {
		final CompositeProperty p = (CompositeProperty) info.parse(tokens(name + ": " + value), null, null, false);
		final StringBuilder out = new StringBuilder();
		for (final String side : new String[] { "top", "right", "bottom", "left" }) {
			for (final CompositeProperty.Entry e : p.getEntries()) {
				if (e.getPrimitivePropertyInfo().getName().contains(side)) {
					out.append(out.length() == 0 ? "" : " ").append(e.getValue());
				}
			}
		}
		return out.toString();
	}

	public void testOneToFourValues() throws Exception {
		assertEquals("1.0pt 1.0pt 1.0pt 1.0pt", sides(BoxSidesShorthand.MARGIN, "margin", "1pt"));
		assertEquals("1.0pt 2.0pt 1.0pt 2.0pt", sides(BoxSidesShorthand.MARGIN, "margin", "1pt 2pt"));
		assertEquals("1.0pt 2.0pt 3.0pt 2.0pt", sides(BoxSidesShorthand.PADDING, "padding", "1pt 2pt 3pt"));
		assertEquals("1.0pt 2.0pt 3.0pt 4.0pt", sides(BoxSidesShorthand.INSET, "inset", "1pt 2pt 3pt 4pt"));
	}

	public void testGlobalKeywordAndInvalidValues() throws Exception {
		assertEquals("inherit inherit inherit inherit", sides(BoxSidesShorthand.BORDER_COLOR, "border-color", "inherit"));
		for (final String bad : new String[] { "1pt 2pt 3pt 4pt 5pt", "-1pt", "1pt inherit" }) {
			try {
				sides(BoxSidesShorthand.PADDING, "padding", bad);
				fail("拒否されるべき: " + bad);
			} catch (final PropertyException e) {
				// As expected.
			}
		}
	}
}
