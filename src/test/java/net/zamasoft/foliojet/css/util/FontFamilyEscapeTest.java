package net.zamasoft.foliojet.css.util;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamily;

/**
 * An unquoted font family name is what its identifiers spell after their escapes are decoded, as a quoted name is
 * (2026-11-26: Font Awesome 7 declares {@code @font-face{font-family:Font Awesome\ 7 Free}} and refers to
 * {@code "Font Awesome 7 Free"}; every icon on pypi.org was a missing glyph). {@code @font-face} and the
 * {@code font-family} property both read the name here.
 */
public class FontFamilyEscapeTest extends TestCase {
	private static List<String> names(final String value) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString("font-family:" + value, settings);
		final UserAgent ua = (UserAgent) Proxy.newProxyInstance(FontFamilyEscapeTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if (method.getName().equals("getDefaultFontFamily")) {
						return new FontFamilyValue(new FontFamily[0]);
					}
					throw new UnsupportedOperationException(method.getName());
				});
		final FontFamilyValue families = FontValueUtils.toFontFamily(ua,
				new TokenStream(Tokens.fromExpression(decls.getAllDeclarations().get(0).getExpression())));
		final List<String> names = new ArrayList<String>();
		for (int i = 0; i < families.getLength(); ++i) {
			names.add(families.get(i).name());
		}
		return names;
	}

	public void testUnquotedNamesAreUnescaped() {
		assertEquals(List.of("Font Awesome 7 Free", "Baz Qux", "foo"),
				names("Font Awesome\\ 7 Free, Baz\\ Qux, \\66 oo"));
	}

	public void testQuotedAndPlainNamesAreUnchanged() {
		assertEquals(List.of("Font Awesome 7 Free", "Zilla Slab"), names("\"Font Awesome 7 Free\", Zilla Slab"));
	}
}
