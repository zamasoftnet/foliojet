package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import junit.framework.TestCase;

/**
 * {@code :empty} of a preceding sibling is known with one pass: the sibling has closed (2026-11-26). The National
 * Diet Library's top page has {@code #main-overlay:not(:empty)+.layouts-global-main{display:none}} and an overlay
 * holding only a comment; with one pass {@code :empty} never matched, so {@code :not(:empty)} always did and the
 * whole page was hidden (copperpdf4 docs/history/2026-11-25-realweb-test.md section 8).
 */
public class EmptyPrecedingSiblingTest extends TestCase {
	private static Map<String, double[]> texts(final String name) throws Exception {
		final File dir = new File("local/empty-preceding-sibling-in");
		dir.mkdirs();
		final File input = new File(dir, name + ".html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
				+ "#a:not(:empty)+p{display:none} #b:empty+p{display:none} #c:empty+p{display:none}"
				+ " #d:empty+p{display:none} #e:empty~p.e{display:none}</style></head><body>"
				+ "<div id='a'><!----></div><p>SHOWN-A</p>" // a comment is no content
				+ "<div id='b'><b>x</b></div><p>SHOWN-B</p>" // an element is content
				+ "<div id='c'></div><p>HIDDEN-C</p>" // nothing
				+ "<div id='d'>\n  </div><p>HIDDEN-D</p>" // white space only, as the two-pass structure scan has it
				+ "<div id='e'><!-- x --></div><p>q</p><p class='e'>HIDDEN-E</p>" // a general sibling
				+ "<p>END</p></body></html>", StandardCharsets.UTF_8);
		return EndTagReopenTest.texts(input.getPath(), "empty-preceding-sibling-" + name);
	}

	public void testClosedSiblingIsKnown() throws Exception {
		final Map<String, double[]> texts = texts("one-pass");
		assertTrue(texts.keySet().toString(), texts.containsKey("SHOWN-A"));
		assertTrue(texts.keySet().toString(), texts.containsKey("SHOWN-B"));
		assertFalse(texts.keySet().toString(), texts.containsKey("HIDDEN-C"));
		assertFalse(texts.keySet().toString(), texts.containsKey("HIDDEN-D"));
		assertFalse(texts.keySet().toString(), texts.containsKey("HIDDEN-E"));
		assertTrue(texts.keySet().toString(), texts.containsKey("END"));
	}
}
