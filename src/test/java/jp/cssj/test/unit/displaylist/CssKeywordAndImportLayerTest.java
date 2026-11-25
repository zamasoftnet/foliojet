package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.parser.AtRulePreludeRewriter;
import net.zamasoft.foliojet.css.parser.AttributeValueQuoter;

/**
 * Two kinds of stylesheet that ph-css 8.2.1 cannot read, found in a test with real web pages (2026-11-25, copperpdf4
 * docs/history/2026-11-25-realweb-test.md §5).
 *
 * <ul>
 * <li>An unquoted attribute selector value that ph-css lexes as a keyword ({@code true}, {@code false},
 * {@code inherit} ...): the rule was dropped, and inside {@code @media} the rules after it left the block. Le Monde
 * hides its full-page menu with {@code .ds-popin[data-active=false]{display:none}}.</li>
 * <li>{@code @import} with {@code layer(name)} or {@code supports(...)}: the whole stylesheet was dropped, and with a
 * bare {@code layer} the import. arXiv's HTML papers import all of their style with
 * {@code @import "ar5iv.css" layer(ar5iv)}.</li>
 * </ul>
 */
public class CssKeywordAndImportLayerTest extends TestCase {
	public void testQuoteKeywordValues() {
		assertEquals("[x=\"true\"]", AttributeValueQuoter.quote("[x=true]"));
		assertEquals("a[ x = \"FALSE\" i ]{}", AttributeValueQuoter.quote("a[ x = FALSE i ]{}"));
		assertEquals("[x|=\"to\"][*|y~=\"and\"][z^=\"not\"][w$=\"only\"][v*=\"from\"][u=\"or\"][t=\"inherit\"]",
				AttributeValueQuoter.quote("[x|=to][*|y~=and][z^=not][w$=only][v*=from][u=or][t=inherit]"));
		assertEquals(":not([aria-hidden=\"true\"]) .a{}", AttributeValueQuoter.quote(":not([aria-hidden=true]) .a{}"));
		// Values ph-css reads, strings, comments, escapes and brackets that are not attribute selectors are left alone,
		// and a stylesheet without such values is the same instance.
		final String untouched = "[x=truex][x=\"true\"][x=abc][x=-true][x=true\\2d]{content:\"[x=true]\"}"
				+ " /* [x=false] */ .g{grid-template-columns:[from] 1fr [to]}"
				// an unquoted URL, and a string continued over a CRLF
				+ " .u{background:url(img[x=true].png)} .s{content:\"a\\\r\n[x=true]\"}";
		assertSame(untouched, AttributeValueQuoter.quote(untouched));
	}

	public void testRewriteImport() {
		assertEquals("@import \"a.css\" (-foliojet-import-layer: x62617365);",
				AtRulePreludeRewriter.rewrite("@import \"a.css\" layer(base);", s -> false));
		assertEquals("@import url(a.css) screen, (-foliojet-import-layer: x);",
				AtRulePreludeRewriter.rewrite("@import url(a.css) layer screen;", s -> false));
		assertEquals("@import 'a.css' (-foliojet-import-layer: x612e62) and (-foliojet-import-supports: x"
				+ "646973706c61793a67726964);",
				AtRulePreludeRewriter.rewrite("@import 'a.css' LAYER(a.b) supports(display:grid);", s -> false));
		// comments between and inside the parts
		assertEquals("@import \"a.css\" (-foliojet-import-layer: x78) and (-foliojet-import-supports: x"
				+ "646973706c61793a67726964);", AtRulePreludeRewriter
						.rewrite("@import \"a.css\" layer(/*l*/x) /*c*/ supports(/*(*/display:grid);", s -> false));
		assertEquals("a.b", AtRulePreludeRewriter.decodeImportValue("x612e62"));
		assertEquals("", AtRulePreludeRewriter.decodeImportValue("x"));
		// Imports without them, and an at-rule that only starts with "layer", are left alone
		final String untouched = "@import \"a.css\" screen; @import url(b.css); @import \"c.css\" layers;";
		assertSame(untouched, AtRulePreludeRewriter.rewrite(untouched, s -> false));
	}

	public void testKeywordValuesSelect() throws Exception {
		final Map<String, double[]> texts = texts("css-keyword-values", Map.of("input.html",
				"<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
						+ "@page{margin:0} body{margin:0;font:16px/20px sans-serif} p{margin:0}"
						+ ".a[data-active=false]{display:none}"
						+ "@media print{.b[aria-hidden=TRUE]{display:none} .c{display:none}}"
						+ ".d:not([data-x=inherit]){display:none}"
						+ ".e[data-x=to i]{margin-left:40px}"
						+ "</style></head><body><p>BODY</p><p class='a' data-active='false'>HIDDEN-A</p>"
						+ "<p class='b' aria-hidden='TRUE'>HIDDEN-B</p><p class='c'>HIDDEN-C</p>"
						+ "<p class='d' data-x='y'>HIDDEN-D</p><p class='e' data-x='TO'>E</p></body></html>"));
		assertTrue(texts.keySet().toString(), texts.containsKey("BODY"));
		for (final String hidden : new String[] { "HIDDEN-A", "HIDDEN-B", "HIDDEN-C", "HIDDEN-D" }) {
			assertFalse(hidden + " is drawn", texts.containsKey(hidden));
		}
		assertEquals("E x", 30, texts.get("E")[0], 0.5);
	}

	public void testImportLayerAndSupports() throws Exception {
		final Map<String, double[]> texts = texts("css-import-layer", Map.of(
				// the imports, then rules that must survive them; the base layer comes before theme, and unlayered
				// rules beat both
				"input.html", "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
						// a statement before the imports fixes the order: a2 before b2, though b2 is imported first
						+ "@layer a2, b2; @import 'b2.css' layer(b2); @import 'a2.css' layer(a2);"
						+ "@import 'base.css' layer(base); @import \"theme.css\" layer(theme);"
						+ "@import url(anon.css) layer; @import 'grid.css' supports(display:grid);"
						+ "@import 'nogrid.css' supports(not (display:grid)); @import 'sub.css' layer(x.y) print;"
						+ "@layer base, theme;"
						// (an unlayered p{margin:0} would beat every layered margin)
						+ "@page{margin:0} body{margin:0;font:16px/20px sans-serif} p{margin-top:0;margin-bottom:0}"
						+ ".after{display:none} .u{margin-left:20px}"
						+ "</style></head><body><p>BODY</p><p class='base'>HIDDEN-BASE</p><p class='anon'>HIDDEN-ANON</p>"
						+ "<p class='grid'>HIDDEN-GRID</p><p class='nogrid'>NOGRID</p><p class='sub'>HIDDEN-SUB</p>"
						+ "<p class='after'>HIDDEN-AFTER</p><p class='t'>T</p><p class='u'>U</p><p class='v'>V</p>"
						+ "</body></html>",
				"a2.css", ".v{margin-left:60px}",
				"b2.css", ".v{margin-left:30px}",
				"base.css", ".base{display:none} .t{margin-left:70px} .u{margin-left:90px}",
				"theme.css", ".t{margin-left:50px}",
				"anon.css", ".anon{display:none}",
				"grid.css", ".grid{display:none}",
				"nogrid.css", ".nogrid{display:none}",
				"sub.css", ".sub{display:none}"));
		assertTrue(texts.keySet().toString(), texts.containsKey("BODY"));
		for (final String hidden : new String[] { "HIDDEN-BASE", "HIDDEN-ANON", "HIDDEN-GRID", "HIDDEN-SUB",
				"HIDDEN-AFTER" }) {
			assertFalse(hidden + " is drawn", texts.containsKey(hidden));
		}
		assertTrue("supports(not (display:grid)) is false", texts.containsKey("NOGRID"));
		// theme comes after base
		assertEquals("T x", 50 * 0.75, texts.get("T")[0], 0.5);
		// unlayered rules beat the layers
		assertEquals("U x", 20 * 0.75, texts.get("U")[0], 0.5);
		// b2 comes after a2, as the statement before the imports says
		assertEquals("V x", 30 * 0.75, texts.get("V")[0], 0.5);
	}

	/** Writes the files into local/<name>-in and returns the texts of input.html as drawn. */
	private static Map<String, double[]> texts(final String name, final Map<String, String> files) throws Exception {
		final File dir = new File("local/" + name + "-in");
		dir.mkdirs();
		for (final Map.Entry<String, String> e : files.entrySet()) {
			Files.writeString(new File(dir, e.getKey()).toPath(), e.getValue(), StandardCharsets.UTF_8);
		}
		return EndTagReopenTest.texts(new File(dir, "input.html").getPath(), name);
	}
}
