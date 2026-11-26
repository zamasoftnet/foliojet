package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

/**
 * An element left open inside an inline svg or math ends with it (2026-11-26, batch 6 of the real-site tests,
 * Shopify's theme demo: an icon's {@code <path ...>} without "/" before {@code </symbol></svg>}). The balancer's
 * {@code </svg>} closed only the path, and the foreign content filter, counting the end tags as written, stayed one
 * deep: every element after the svg went into the SVG namespace, and nothing of the rest of the page was printed.
 */
public class SvgUnclosedChildTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	private static String texts(final String name, final String body) throws Exception {
		return String.join(" ", texts(pages(name, "<html>", body)));
	}

	private static List<String> pages(final String name, final String html, final String body) throws Exception {
		final File dir = new File("local/svg-unclosed-child-in");
		dir.mkdirs();
		final File input = new File(dir, name + ".html");
		Files.writeString(input.toPath(), "<!DOCTYPE html>" + html + "<body><p>BEFORE</p>" + body
				+ "<p>LAST</p></body></html>", StandardCharsets.UTF_8);
		return DisplayListPages.of(input.getPath(), "svg-unclosed-child-" + name);
	}

	private static List<String> texts(final List<String> pages) {
		final List<String> found = new ArrayList<>();
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				found.add(m.group(1));
			}
		}
		return found;
	}

	public void testPathLeftOpenBeforeSvgEnd() throws Exception {
		final String t = texts("svg", "<svg width='20' height='20'><path d='M0 0L10 10'></svg><div>AFTER</div>");
		assertTrue(t, t.contains("AFTER"));
		assertTrue(t, t.contains("LAST"));
	}

	public void testPathLeftOpenBeforeSymbolEnd() throws Exception {
		final String t = texts("symbol", "<svg class='hidden' width='0' height='0'><symbol id='i' viewBox='0 0 18 17'>"
				+ "<path d='M.8 15.9l7.4-7.4' fill='currentColor'></symbol></svg><div>AFTER</div>");
		assertTrue(t, t.contains("AFTER"));
		assertTrue(t, t.contains("LAST"));
	}

	/**
	 * With the svg's xmlns written out, as Shopify's theme writes it: NekoHTML's namespace binder kept the svg's default
	 * namespace in force after it, by the same miscount, and gave it to every element that followed.
	 */
	public void testPathLeftOpenUnderAnXmlns() throws Exception {
		final String t = texts("xmlns", "<svg xmlns='http://www.w3.org/2000/svg' class='hidden' width='0' height='0'>"
				+ "<symbol id='i' viewBox='0 0 18 17'><path d='M.8 15.9l7.4-7.4' fill='currentColor'></symbol></svg>"
				+ "<div>AFTER</div><svg width='20' height='20'><rect width='20' height='20' fill='red'/></svg>");
		assertTrue(t, t.contains("AFTER"));
		assertTrue(t, t.contains("LAST"));
	}

	public void testHtmlEndTagClosesTheSvg() throws Exception {
		final String t = texts("html-end", "<div><svg width='20' height='20'><g><path d='M0 0L10 10'></div><p>AFTER</p>");
		assertTrue(t, t.contains("AFTER"));
		assertTrue(t, t.contains("LAST"));
	}

	/**
	 * A math element after such an svg is a MathML one (codex, 2026-11-26): NekoHTML's binder gave it the SVG namespace
	 * left in force, and it went to the SVG reader, which did not take it.
	 */
	public void testMathAfterSvgLeftOpenUnderAnXmlns() throws Exception {
		final String all = String.join("\n", pages("math-after", "<html>", "<svg xmlns='http://www.w3.org/2000/svg'"
				+ " width='20' height='20'><path d='M0 0L10 10'></svg><math><mfrac><mn>7</mn><mn>9</mn></mfrac></math>"));
		// the svg (20px = 15pt) and the fraction, each an object of its own
		assertEquals(all, 2, all.split("AbsoluteRectFrame\\[", -1).length - 1);
		assertTrue(all, all.contains("AbsoluteRectFrame[w=15.00 h=15.00]"));
		assertTrue(all, all.contains("Text[\"LAST\""));
	}

	/**
	 * An svg in a document of {@code <html xmlns="http://www.w3.org/1999/xhtml">} is an SVG one and is drawn (codex,
	 * 2026-11-26): NekoHTML gave it and its children the XHTML namespace of the html element. Before 2026-11-26 it was
	 * not drawn at all; when the svg alone was given the SVG namespace, its end event still had the XHTML one.
	 */
	public void testSvgUnderXhtmlNamespace() throws Exception {
		final String all = String.join("\n", pages("xhtml-ns", "<html xmlns='http://www.w3.org/1999/xhtml'>",
				"<svg width='40' height='40'><rect width='40' height='40' fill='red'/></svg>"));
		// 40px = 30pt
		assertTrue(all, all.contains("AbsoluteRectFrame[w=30.00 h=30.00]"));
		assertTrue(all, all.contains("Text[\"LAST\""));
	}

	public void testMathLeftOpen() throws Exception {
		final String t = texts("math", "<math><mrow><mi>x</math><div>AFTER</div>");
		assertTrue(t, t.contains("AFTER"));
		assertTrue(t, t.contains("LAST"));
	}
}
