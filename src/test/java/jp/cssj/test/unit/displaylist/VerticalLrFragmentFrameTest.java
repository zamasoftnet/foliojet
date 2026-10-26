package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verifies which frame edges a vertical-lr block keeps when a page break cuts it (2026-10-26, sweep seeds
 * 42567575, 42609033, 42637010, 42666929, 42679512 and 42695074: text of the next block drawn over the text of a
 * continued one).
 *
 * <p>
 * The page axis of vertical-lr runs left to right, so the part before the break keeps its left (start) frame and the
 * continuation its right (end) frame. The cut dropped the left edges in both vertical modes, as is right only for
 * vertical-rl: the first part lost its start margin and border and showed its end border at the cut, and the
 * continuation drew its start margins again while the following block was placed as if it had none. Table cells,
 * cut by their own code, had the same edges.
 * </p>
 */
public class VerticalLrFragmentFrameTest extends TestCase {
	private static final Pattern TEXT = Pattern
			.compile("x=([-0-9.]+) y=[-0-9.]+ Text\\[\"([^\"]+)\"[^\\]]*\\] w=([-0-9.]+)");

	/** Text runs per page: token to {x, width}, from the page's display list. */
	private static Map<String, double[]>[] layout(final String mode, final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
				+ "@page{size:100pt 100pt;margin:5pt} body{margin:0;font:10pt/1.2 serif;writing-mode:" + mode + "}"
				+ "p{margin:0}</style></head><body>" + body + "</body></html>";
		final Path dir = Files.createTempDirectory("vertical-lr-fragment");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString());
				AutoCloseable g = DisplayListDumper.scopedDetailedGeometry(true)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), URI.create("file:///a.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		assertFalse("more than two pages", Files.exists(dir.resolve("page-0003.txt")));
		@SuppressWarnings("unchecked")
		final Map<String, double[]>[] pages = new Map[2];
		for (int i = 0; i < pages.length; ++i) {
			pages[i] = new HashMap<>();
			final Path page = dir.resolve(String.format("page-%04d.txt", i + 1));
			assertTrue("page " + (i + 1) + " is missing", Files.exists(page));
			final Matcher m = TEXT.matcher(Files.readString(page));
			while (m.find()) {
				assertNull(m.group(2) + " is drawn twice on page " + (i + 1), pages[i].put(m.group(2),
						new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(3)) }));
			}
		}
		return pages;
	}

	private static double x(final Map<String, double[]> page, final String token) {
		final double[] run = page.get(token);
		assertNotNull(token + " is not on the page " + page.keySet(), run);
		return run[0];
	}

	private static final String LINES = "<p>A1</p><p>A2</p><p>A3</p><p>A4</p><p>A5</p><p>A6</p><p>A7</p><p>A8</p>";

	/** The start margin stays before the break, the end margin goes after the continuation. */
	public void testMarginsOfACutBox() throws Exception {
		final Map<String, double[]>[] pages = layout("vertical-lr",
				"<div style='margin:10pt'>" + LINES + "</div><p>B1</p>");
		assertEquals("the start margin before the break", 10, x(pages[0], "A1"), 0.01);
		assertEquals("no start margin after the break", 0, x(pages[1], "A7"), 0.01);
		assertEquals("the end margin after the continuation", x(pages[1], "A8") + 12 + 10, x(pages[1], "B1"), 0.01);
	}

	/** Border and padding: the same edges as the margins. */
	public void testBorderOfACutBox() throws Exception {
		final Map<String, double[]>[] pages = layout("vertical-lr",
				"<div style='border:2pt solid black;padding:3pt'>" + LINES + "</div><p>B1</p>");
		assertEquals("the start border before the break", 5, x(pages[0], "A1"), 0.01);
		assertEquals("no start border after the break", 0, x(pages[1], "A8"), 0.01);
		assertEquals("the end border after the continuation", 12 + 5, x(pages[1], "B1"), 0.01);
	}

	/** The block after a continued nest of boxes does not overlap its last line (the sweep's overlap). */
	public void testNextBlockAfterNestedContinuation() throws Exception {
		final Map<String, double[]>[] pages = layout("vertical-lr",
				"<div style='margin:3pt'><div style='margin:5pt'><div style='margin:5pt'>" + LINES
						+ "</div></div></div><p>B1</p>");
		final double[] last = pages[1].get("A8");
		assertNotNull(pages[1].keySet().toString(), last);
		assertEquals("no start margins after the break", 0, last[0], 0.01);
		// The end margins collapse into 5pt.
		assertEquals(last[0] + last[1] + 5, x(pages[1], "B1"), 0.01);
	}

	/** A table cell cut by the break: the same edges, through the table's own cut. */
	public void testCellOfACutTable() throws Exception {
		final Map<String, double[]>[] pages = layout("vertical-lr",
				"<table style='border:2pt solid black;border-spacing:1.5pt'><tr>"
						+ "<td style='border:1pt solid black;padding:2pt 8pt'>C1<br/>C2<br/>C3<br/>C4<br/>C5<br/>C6<br/>C7"
						+ "<br/>C8<br/>C9</td></tr></table><p>Z3</p>");
		// The table's border (2pt) and spacing (1.5pt), the cell's border (1pt) and padding (8pt)
		assertEquals("the start edges before the break", 12.5, x(pages[0], "C1"), 0.01);
		assertEquals("no start edges after the break", 0, x(pages[1], "C7"), 0.01);
		assertEquals("the end edges after the continuation", x(pages[1], "C9") + 12 + 12.5, x(pages[1], "Z3"), 0.01);
	}

	/** vertical-rl, which the cut already handled, is unchanged. */
	public void testVerticalRlUnchanged() throws Exception {
		final Map<String, double[]>[] pages = layout("vertical-rl",
				"<div style='margin:10pt'>" + LINES + "</div><p>B1</p>");
		assertEquals(68, x(pages[0], "A1"), 0.01);
		assertEquals(78, x(pages[1], "A7"), 0.01);
		assertEquals(44, x(pages[1], "B1"), 0.01);
	}
}
