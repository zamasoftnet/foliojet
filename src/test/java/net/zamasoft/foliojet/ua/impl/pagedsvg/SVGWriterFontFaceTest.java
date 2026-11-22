package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.io.StringWriter;

import junit.framework.TestCase;

/**
 * The {@code @font-face} rules of a page SVG are CSS inside XML: the family and the source are CSS strings, and
 * the source begins with {@code output.paged-svg.base-uri}, which is the user's. An apostrophe in it ended the
 * string, so no subset font of the page was loaded.
 */
public class SVGWriterFontFaceTest extends TestCase {
	private static String defs(final String base) throws Exception {
		final SVGWriter writer = new SVGWriter(new StringWriter());
		writer.setFontSrc(uri -> base + uri);
		writer.addFontFace("cssj1", "assets/fonts/1.woff2");
		final StringWriter defs = new StringWriter();
		writer.writeDefs(defs);
		return defs.toString();
	}

	public void testPlainBaseUriIsWrittenAsItIs() throws Exception {
		final String defs = defs("../");
		assertTrue(defs, defs.contains("@font-face{font-family:'cssj1';src:url('../assets/fonts/1.woff2') format('woff2');"));
	}

	public void testApostropheInBaseUriIsEscaped() throws Exception {
		final String defs = defs("https://example.test/O'Reilly/");
		assertTrue(defs, defs.contains("src:url('https://example.test/O\\'Reilly/assets/fonts/1.woff2') format('woff2');"));
	}

	/** A backslash and a line break are escaped as well, and the markup characters once more for XML. */
	public void testBackslashLineBreakAndMarkup() throws Exception {
		final String defs = defs("a\\b\nc&d/");
		assertTrue(defs, defs.contains("src:url('a\\\\b\\a c&amp;d/assets/fonts/1.woff2') format('woff2');"));
	}
}
