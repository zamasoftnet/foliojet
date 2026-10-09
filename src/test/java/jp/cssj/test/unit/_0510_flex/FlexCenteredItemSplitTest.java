package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * A centered item of a flex line broken across pages is placed as if the line were not broken (2026-10-09, as
 * {@code GridEdgeCasesTest.testCenteredItemInSplitRow}): FlexBox.split cut every item at the same distance from the
 * line start, so an item {@code align-items: center} put below the rest of the page stayed whole on that page, below
 * the paper. The expected values are Chrome's.
 */
public class FlexCenteredItemSplitTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	private static final String STYLE = "@page { size: 300pt 300pt; margin: 0 } body { margin: 0; font: 10pt/15pt serif }"
			+ " p, ul { margin: 0 } .a { display: flex; align-items: center; gap: 10pt }";

	/** 50 lines (750pt) from the page top: TAGS at 367.5, page 2 at 67.5. */
	public void testItemBelowTheCut() throws Exception {
		final List<String> pages = convert("below", centeredLine(0, 50, "<li>TAGS</li>"));
		assertFalse("TAGS は 1 頁目に残らない:\n" + pages.get(0), pages.get(0).contains("Text[\"TAGS\""));
		assertEquals("TAGS は 2 頁目の行の中ほど", 67.5, y(pages.get(1), "TAGS"), 0.5);
		assertOnPaper(pages);
	}

	/** 40 lines (600pt): TAGS at 292.5 crosses the page bottom and starts page 2. */
	public void testItemAcrossTheCut() throws Exception {
		final List<String> pages = convert("across", centeredLine(0, 40, "<li>TAGS</li>"));
		assertFalse("TAGS は 1 頁目に残らない:\n" + pages.get(0), pages.get(0).contains("Text[\"TAGS\""));
		assertEquals("TAGS は 2 頁目の頭", 0, y(pages.get(1), "TAGS"), 0.5);
		assertOnPaper(pages);
	}

	/** 30 lines after 100pt, 8 tags (120pt) at 265: two lines fit on page 1, the rest start page 2. */
	public void testItemSplitAtTheCut() throws Exception {
		final List<String> pages = convert("split", centeredLine(100, 30,
				"<li>TAGS</li><li>T2</li><li>T3</li><li>T4</li><li>T5</li><li>T6</li><li>T7</li><li>T8</li>"));
		assertEquals("TAGS", 265, y(pages.get(0), "TAGS"), 0.5);
		assertEquals("T2", 280, y(pages.get(0), "T2"), 0.5);
		assertEquals("T3 は 2 頁目の頭", 0, y(pages.get(1), "T3"), 0.5);
		assertOnPaper(pages);
	}

	/** A line that starts in the middle of the page: TAGS at 532.5 goes to page 2 at 232.5. */
	public void testLineFromTheMiddleOfThePage() throws Exception {
		final List<String> pages = convert("middle", centeredLine(240, 40, "<li>TAGS</li>"));
		assertFalse("TAGS は 1 頁目に残らない:\n" + pages.get(0), pages.get(0).contains("Text[\"TAGS\""));
		assertEquals("TAGS は 2 頁目", 232.5, y(pages.get(1), "TAGS"), 0.5);
		assertOnPaper(pages);
	}

	private static String centeredLine(final int before, final int lines, final String tags) {
		final StringBuilder body = new StringBuilder();
		if (before > 0) {
			body.append("<div style=\"height: ").append(before).append("pt\">PRE</div>");
		}
		body.append("<div class=\"a\"><ul>").append(tags).append("</ul><div>");
		for (int i = 0; i < lines; ++i) {
			body.append("<p>D").append(i).append(" lorem ipsum dolor sit amet.</p>");
		}
		body.append("</div></div><p>END</p>");
		return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + STYLE + "</style></head><body>" + body
				+ "</body></html>";
	}

	private static double y(final String page, final String text) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(2));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	private static void assertOnPaper(final List<String> pages) {
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				assertTrue(m.group(3) + " が紙面の外:\n" + page, Double.parseDouble(m.group(2)) < 300);
			}
		}
	}

	private static List<String> convert(final String name, final String html) throws Exception {
		final File dir = Files.createTempDirectory("flex-centered-" + name).toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] files = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(name + ": no pages", files);
		Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
