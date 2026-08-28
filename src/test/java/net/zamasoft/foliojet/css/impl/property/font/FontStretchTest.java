package net.zamasoft.foliojet.css.impl.property.font;

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
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;

/**
 * {@code font-stretch}の解析と、割合→OS/2 usWidthClass(1..9)の丸め、
 * {@link FontStyleImpl}への搬送を固定します(2026-08-29)。
 */
public class FontStretchTest extends TestCase {

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

	private static int widthClass(final String value) throws PropertyException {
		final PercentageValue v = (PercentageValue) ((FontStretch) FontStretch.INFO)
				.parseValue(tokens("font-stretch: " + value), null, null);
		return FontStretch.toWidthClass(v.getPercentage());
	}

	public void testKeywordsMapToWidthClasses() throws Exception {
		assertEquals(1, widthClass("ultra-condensed"));
		assertEquals(2, widthClass("extra-condensed"));
		assertEquals(3, widthClass("condensed"));
		assertEquals(4, widthClass("semi-condensed"));
		assertEquals(5, widthClass("normal"));
		assertEquals(6, widthClass("semi-expanded"));
		assertEquals(7, widthClass("expanded"));
		assertEquals(8, widthClass("extra-expanded"));
		assertEquals(9, widthClass("ultra-expanded"));
	}

	public void testPercentagesRoundToNearestClass() throws Exception {
		assertEquals(3, widthClass("75%"));
		assertEquals(3, widthClass("80%"));
		assertEquals(5, widthClass("100%"));
		assertEquals(7, widthClass("130%"));
		assertEquals(9, widthClass("300%"));
	}

	public void testInvalidValuesAreRejected() {
		for (final String bad : new String[] { "-10%", "narrow", "condensed expanded" }) {
			try {
				widthClass(bad);
				fail("受理してはいけない: " + bad);
			} catch (final PropertyException e) {
				// expected
			}
		}
	}

	public void testFontStyleCarriesWidthClass() {
		final FontPolicyList policy = new FontPolicyList(
				new FontPolicyList.FontPolicy[] { FontPolicyList.FontPolicy.EMBEDDED });
		final FontStyle condensed = new FontStyleImpl(FontFamilyList.create("serif"), 12, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_400, FontStyle.Direction.LTR, policy, null, true, true,
				FontStyle.TextOrientation.MIXED, FontStretch.toWidthClass(75));
		assertEquals(3, condensed.getWidthClass());
		final FontStyle plain = new FontStyleImpl(FontFamilyList.create("serif"), 12, FontStyle.Style.NORMAL,
				FontStyle.Weight.W_400, FontStyle.Direction.LTR, policy);
		assertEquals(FontStretch.NORMAL_WIDTH_CLASS, plain.getWidthClass());
	}
}
