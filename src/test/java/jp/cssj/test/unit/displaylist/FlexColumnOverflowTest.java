package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import junit.framework.TestCase;

/**
 * What overflows an item of definite height in a column flex container of definite height goes on to the next page,
 * as Chrome prints it (2026-11-26, batch 6 of the real-site tests, defect 38: BBC Sport's league table, in an app shell
 * of {@code display:flex; flex-direction:column; height:100vh}, lost its rows past the first page). The line ledger of
 * a column has one line per item, as long as the item's box: no line crossed the cut line, and the whole container was
 * kept on the page with its overflow below the paper. Rows are 24pt; the page area is A4's. A row is drawn as "row" and
 * its number.
 */
public class FlexColumnOverflowTest extends TestCase {
	private static String rows(final int n) {
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < n; ++i) {
			b.append("<p style='margin:0;line-height:24pt'>row ").append(i).append("</p>");
		}
		return b.toString();
	}

	private static List<String> pages(final String name, final String body) throws Exception {
		final File dir = new File("local/flex-column-overflow-in");
		dir.mkdirs();
		final File input = new File(dir, name + ".html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><body style='margin:0'>" + body + "<p>AFTER</p></body></html>",
				StandardCharsets.UTF_8);
		return DisplayListPages.of(input.getPath(), "flex-column-overflow-" + name);
	}

	public void testOverflowOfAPageHighContainerGoesOn() throws Exception {
		final List<String> pages = pages("vh", "<div style='display:flex;flex-direction:column;height:100vh'>"
				+ "<div style='height:300pt'>" + rows(40) + "</div></div>");
		assertNotNull("row 39: " + pages, DisplayListPages.find(pages, "39"));
		assertTrue(DisplayListPages.find(pages, "39")[0] >= 2);
		assertNotNull(DisplayListPages.find(pages, "AFTER"));
	}

	public void testOverflowOfATallerContainerGoesOn() throws Exception {
		final List<String> pages = pages("tall", "<div style='display:flex;flex-direction:column;height:2000pt'>"
				+ "<div style='height:300pt'>" + rows(80) + "</div></div>");
		assertNotNull("row 79: " + pages, DisplayListPages.find(pages, "79"));
	}

	/**
	 * overflow: hidden clips it, as before: nothing of it goes on to the next page (a clipped text stays in the display
	 * list of its page, so only the page is asked).
	 */
	public void testHiddenOverflowStaysClipped() throws Exception {
		final List<String> pages = pages("hidden", "<div style='display:flex;flex-direction:column;height:100vh'>"
				+ "<div style='height:300pt;overflow:hidden'>" + rows(40) + "</div></div>");
		final double[] last = DisplayListPages.find(pages, "39");
		assertTrue(last == null || last[0] == 1);
		assertNotNull(DisplayListPages.find(pages, "AFTER"));
	}
}
