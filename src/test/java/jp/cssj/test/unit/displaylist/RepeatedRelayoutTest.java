package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.box.content.RowSplitContainer;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * What remains of a table cell, a float or a grid broken across pages is not laid out again at each page when that can
 * give nothing new (2026-10-31, performance audit #4 and #8): a paragraph of plain text in a cell keeps its lines
 * ({@link TextBlockBox#keepLines}), and the rows past the page wait ({@code RowSplitContainer}). Everything that a repeat
 * of the layout does change goes on being laid out: these tests pin both sides, and that the text stays whole and in
 * order.
 */
public class RepeatedRelayoutTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\"");

	private static String paragraphs(final int count, final String extra) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			s.append("<p>T").append(i).append('Z').append(extra).append("</p>");
		}
		return s.toString();
	}

	private static String items(final int count, final String extra) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			s.append("<div>T").append(i).append('Z').append(extra).append("</div>");
		}
		return s.toString();
	}

	private static String document(final String style, final String body) {
		return "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\"><head><meta charset=\"UTF-8\"/><style>"
				+ "@page { size: 200pt 100pt; margin: 0 } body { margin: 0; font: 10pt/12pt sans-serif } p { margin: 0 }"
				+ style + "</style></head><body>" + body + "</body></html>";
	}

	public void testCellKeepsLinesOfPlainText() throws Exception {
		final long before = TextBlockBox.LINES_KEPT.get();
		final List<String> pages = convertPages("cell", document("", "<table><tr><td>" + paragraphs(40, "") + "</td></tr></table>"));
		assertTrue("頁数 " + pages.size(), pages.size() >= 4);
		assertTrue("行が持ち越されていない", TextBlockBox.LINES_KEPT.get() > before);
		assertOrder(pages, 40);
	}

	public void testFloatKeepsLinesOfPlainText() throws Exception {
		final long before = TextBlockBox.LINES_KEPT.get();
		final List<String> pages = convertPages("float", document(".f { float: left; width: 50% }",
				"<div class=\"f\">" + paragraphs(40, "") + "</div>"));
		assertTrue("頁数 " + pages.size(), pages.size() >= 4);
		assertTrue("行が持ち越されていない", TextBlockBox.LINES_KEPT.get() > before);
		assertOrder(pages, 40);
	}

	/** Floats beside the text, inline images and the rest stay out of it: only their first layout is known not to move. */
	public void testCellLaysOutAgainWhatDoesNotRepeat() throws Exception {
		for (final String extra : new String[] { " <img src=\"data:,\" style=\"width: 10pt; height: 10pt\"/>",
				" <span style=\"vertical-align: top; font-size: 20pt\">x</span>",
				" <span style=\"display: inline-block\">x</span>" }) {
			final long before = TextBlockBox.LINES_KEPT.get();
			final List<String> pages = convertPages("cell-not-plain",
					document("", "<table><tr><td>" + paragraphs(40, extra) + "</td></tr></table>"));
			assertTrue("頁数 " + pages.size(), pages.size() >= 4);
			assertEquals(extra, before, TextBlockBox.LINES_KEPT.get());
			assertOrder(pages, 40);
		}
	}

	public void testGridHoldsRowsPastThePage() throws Exception {
		final long before = RowSplitContainer.ROWS_HELD.get();
		final List<String> pages = convertPages("grid", document(".g { display: grid; grid-template-columns: 1fr 1fr }",
				"<div class=\"g\">" + items(80, "") + "</div>"));
		assertTrue("頁数 " + pages.size(), pages.size() >= 4);
		assertTrue("行が待たされていない", RowSplitContainer.ROWS_HELD.get() > before);
		assertOrder(pages, 80);
		// Every page but the last holds as many rows, at the same places, as the first
		assertEquals(ys(pages.get(0)), ys(pages.get(1)));
		assertEquals(ys(pages.get(0)), ys(pages.get(pages.size() - 2)));
	}

	public void testFlexHoldsRowsPastThePage() throws Exception {
		final long before = RowSplitContainer.ROWS_HELD.get();
		final List<String> pages = convertPages("flex", document(".f { display: flex; flex-wrap: wrap } .f > div { width: 50% }",
				"<div class=\"f\">" + items(80, "") + "</div>"));
		assertTrue("頁数 " + pages.size(), pages.size() >= 4);
		assertTrue("行が待たされていない", RowSplitContainer.ROWS_HELD.get() > before);
		assertOrder(pages, 80);
		assertEquals(ys(pages.get(0)), ys(pages.get(1)));
	}

	/** Only the rows after the last item that is not plain wait: laying a table out has effects wherever it is. */
	public void testRowsBeforeAnItemThatIsNotPlainAreLaidOutAgain() throws Exception {
		final long before = RowSplitContainer.ROWS_HELD.get();
		final List<String> pages = convertPages("grid-table", document(".g { display: grid; grid-template-columns: 1fr 1fr }",
				"<div class=\"g\">" + items(80, "") + "<div><table><tr><td>L</td></tr></table></div></div>"));
		assertTrue("頁数 " + pages.size(), pages.size() >= 4);
		assertEquals(before, RowSplitContainer.ROWS_HELD.get());
		assertOrder(pages, 80);
	}

	/**
	 * A table in a row before them breaks the page during the restyle, on a first page tall enough for the rows to wait.
	 * They go on after the break with the rest of the items (codex, 2026-11-01): put back into the box of the page already
	 * finished, every one of them was lost, with the tests above and 170,000 documents of the sweep green.
	 */
	public void testRowsThatWaitFollowABreakDuringTheRestyle() throws Exception {
		final StringBuilder rows = new StringBuilder();
		for (int i = 0; i < 10; ++i) {
			rows.append("<tr><td>R</td></tr>");
		}
		final long before = RowSplitContainer.ROWS_HELD.get();
		final List<String> pages = convertPages("grid-break-in-restyle", document(
				"@page :first { size: 200pt 500pt; margin: 0 } .g { display: grid; height: 2000pt; align-content: start }"
						+ ".g > div { height: 20pt } .g > .a { height: 450pt } .g > .b { height: auto; break-inside: avoid }"
						+ "table { table-layout: fixed; width: 100%; border-collapse: collapse } td { height: 40pt; padding: 0 }",
				"<div class=\"g\"><div class=\"a\">A</div><div class=\"b\"><table><tbody>" + rows + "</tbody></table></div>" + items(20, "")
						+ "</div>"));
		assertTrue("行が待たされていない", RowSplitContainer.ROWS_HELD.get() > before);
		assertOrder(pages, 20);
	}

	private static final String FLEX_ROW = ".r { display: flex } .r > aside { flex: none; width: 50pt } .r > main { flex: 1; min-width: 0 }";

	private static final String GRID_ROW = ".r { display: grid; grid-template-columns: 50pt 1fr }";

	/** A body beside a side item: the side item stops it from flowing, so all that remains of it is stacked at each page. */
	private static String row(final String body) {
		return "<div class=\"r\"><aside>S</aside><main>" + body + "</main></div>";
	}

	/** It stays boxes rather than being replayed from source, which made such a document quadratic (2026-11-01). */
	public void testRowItemKeepsLinesOfPlainText() throws Exception {
		for (final String style : new String[] { FLEX_ROW, GRID_ROW }) {
			final long before = TextBlockBox.LINES_KEPT.get();
			final List<String> pages = convertPages("row-item", document(style, row(paragraphs(40, ""))));
			assertTrue("頁数 " + pages.size(), pages.size() >= 4);
			assertTrue("行が持ち越されていない", TextBlockBox.LINES_KEPT.get() > before);
			assertOrder(pages, 40);
		}
	}

	public void testRowItemLaysOutAgainWhatDoesNotRepeat() throws Exception {
		for (final String extra : new String[] { " <img src=\"data:,\" style=\"width: 10pt; height: 10pt\"/>",
				" <span style=\"vertical-align: top; font-size: 20pt\">x</span>",
				" <span style=\"display: inline-block\">x</span>" }) {
			final long before = TextBlockBox.LINES_KEPT.get();
			final List<String> pages = convertPages("row-item-not-plain", document(FLEX_ROW, row(paragraphs(40, extra))));
			assertTrue("頁数 " + pages.size(), pages.size() >= 4);
			assertEquals(extra, before, TextBlockBox.LINES_KEPT.get());
			assertOrder(pages, 40);
		}
		final long before = TextBlockBox.LINES_KEPT.get();
		final List<String> pages = convertPages("row-item-pretty", document(FLEX_ROW + " p { text-wrap: pretty }", row(paragraphs(40, ""))));
		assertEquals("text-wrap: pretty", before, TextBlockBox.LINES_KEPT.get());
		assertOrder(pages, 40);
	}

	/** The lines past a line clamp are gone from the box, and with them what told it to end in an ellipsis (codex, 2026-11-01). */
	public void testRowItemKeepsTheEllipsisOfALineClamp() throws Exception {
		final String clamped = "<p class=\"c\">" + "clamped words past the end of the second line ".repeat(6) + "</p>";
		final List<String> pages = convertPages("row-item-clamp",
				document(FLEX_ROW + " p.c { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden }",
						row(paragraphs(30, "") + clamped + paragraphs(10, ""))));
		assertTrue("頁数 " + pages.size(), pages.size() >= 3);
		assertTrue("省略記号が無い", String.join("", pages).contains("…"));
	}

	/** A block without text has no lines that tell of a replay, and a replay measures one sized by its content (codex, 2026-11-01). */
	public void testRowItemMeasuresABlockSizedByItsContentAgain() throws Exception {
		final String body = paragraphs(30, "") + "<div class=\"i\"></div>" + "<p>T30Z</p>";
		final List<String> fit = convertPages("row-item-fit",
				document(FLEX_ROW + " .i { width: fit-content; min-width: 20pt; aspect-ratio: 1 }", row(body)));
		final List<String> fixed = convertPages("row-item-fixed", document(FLEX_ROW + " .i { width: 20pt; height: 20pt }", row(body)));
		assertEquals(fixed.size(), fit.size());
		for (int i = 0; i < fit.size(); ++i) {
			assertEquals("頁 " + i, ys(fixed.get(i)), ys(fit.get(i)));
		}
	}

	/**
	 * A table that breaks the page during the restyle takes the barrier with it: what follows in the item is laid out with
	 * the breaks on, and goes by the forced breaks in it. Laid out alone, it went by them no more (2026-11-01).
	 */
	public void testForcedBreaksFollowATableInARowItem() throws Exception {
		final StringBuilder body = new StringBuilder(paragraphs(8, " words words words words words words words words words words"));
		body.append("<table><tbody>");
		for (int i = 0; i < 30; ++i) {
			body.append("<tr><td>R</td></tr>");
		}
		body.append("</tbody></table>");
		for (int i = 8; i < 40; ++i) {
			body.append(i % 7 == 3 ? "<p class=\"b\">T" : "<p>T").append(i).append("Z words words words words words words words words</p>");
		}
		final List<String> pages = convertPages("row-item-forced",
				document(GRID_ROW + " table { width: 100%; table-layout: fixed } td { height: 12pt } p.b { break-after: page }", row(body.toString())));
		assertOrder(pages, 40);
		for (int i = 10; i < 39; i += 7) {
			for (final String page : pages) {
				assertFalse("T" + i + "Z の後で改頁していない", page.contains("T" + i + "Z") && page.contains("T" + (i + 1) + "Z"));
			}
		}
	}

	/**
	 * An item that paints less than it takes, on a page it does not fit (2026-11-01). What was left of its line found
	 * nothing to split, so the box stayed whole on that page with every line after it, and the rest of the document ran off
	 * the paper. The sweep did not see it: a document with an explicit size over the page is left out of that check.
	 */
	public void testLinesAfterATallItemStayOnThePaper() throws Exception {
		final String[] boxes = { ".g { display: flex; flex-wrap: wrap } .g > div { width: 100% }", ".g { display: grid }" };
		final String[] firstItems = { "<div style=\"height: 150pt\">A</div>", "<div style=\"height: 500pt\">A</div>",
				"<div style=\"padding-bottom: 300pt\">A</div>", "<div style=\"margin-bottom: 300pt\">A</div>",
				"<div><div style=\"min-height: 300pt\">A</div></div>",
				"<div><div style=\"float: left; width: 50pt; height: 300pt\">A</div>B</div>" };
		for (final String box : boxes) {
			for (final String first : firstItems) {
				final List<String> pages = convertPages("tall-item",
						document(box, "<div class=\"g\">" + first + items(30, "") + "</div>"));
				assertOrder(pages, 30);
				assertOnThePaper(box + " " + first, pages, 100);
			}
		}
	}

	/** The blank of such an item ends on the page where it ends: it is no page of its own (2026-11-01, as Chrome). */
	public void testItemsFollowTheBlankOfATallItem() throws Exception {
		final List<String> flex = convertPages("tall-item-flex", document(
				".g { display: flex; flex-wrap: wrap } .g > div { width: 100% }",
				"<div class=\"g\"><div style=\"height: 150pt\">A</div>" + items(30, "") + "</div>"));
		assertEquals(50.0, firstY(flex.get(1), "T0Z"));
		final List<String> grid = convertPages("tall-item-grid", document(".g { display: grid }",
				"<div class=\"g\"><div style=\"height: 250pt\">A</div>" + items(30, "") + "</div>"));
		// The page wholly blank in between is not output
		assertEquals(50.0, firstY(grid.get(1), "T0Z"));
	}

	/**
	 * A line the ledger holds taller than the page though what is left of its items fits (2026-11-01): the lower bound of a
	 * remainder line, here 569pt on a page of 538pt. Nothing split, and all the items after it stayed on page 9, down to
	 * y=4970.
	 */
	public void testLinesAfterARemainderLineTallerThanThePageStayOnThePaper() throws Exception {
		final String source = java.nio.file.Files.readString(
				new File("files/unittest/0190-vertical-align/text-top-bottom.html").toPath(), StandardCharsets.ISO_8859_1);
		final String body = source.substring(source.indexOf("<body>") + 6, source.indexOf("</body>"));
		final List<String> pages = convertPages("remainder-line", document(
				"@page { size: 419.53pt 595.28pt; margin: 10mm } body { font: 24pt serif } p { margin: 1em 0 }"
						+ "span.large { font-size: 2em } span.small { font-size: 0.5em }"
						+ ".f { display: flex; flex-wrap: wrap } .f > div { width: 45% }",
				"<div class=\"f\">" + ("<div>" + body + "</div>").repeat(18) + "</div>"));
		assertTrue("頁数 " + pages.size(), pages.size() >= 10);
		assertOnThePaper("items", pages, 538.6);
	}

	/**
	 * A line that broke at a soft hyphen keeps the soft hyphen (2026-11-01). Laying the line out again leaves the hyphen out,
	 * and each layout used up the breaks it took: in a cell of many pages the hyphens were gone by page 6, and from there on
	 * long words ran 16pt out of their column into the next.
	 */
	public void testHyphensSurviveLayingOutAgain() throws Exception {
		final List<String> pages = convertPages("hyphens", document(
				"@page { size: 220pt 160pt; margin: 10pt } p { hyphens: auto }"
						+ "table { table-layout: fixed; width: 200pt; border-collapse: collapse } td { padding: 0; vertical-align: top }",
				"<table><colgroup><col style=\"width: 70pt\"/><col/></colgroup><tbody><tr><td>"
						+ paragraphs(40, " internationalization representativeness incomprehensibilities")
						+ "</td><td>R</td></tr></tbody></table>"));
		assertTrue("頁数 " + pages.size(), pages.size() >= 20);
		assertTrue("最後から 2 頁目にハイフンが無い: " + pages.get(pages.size() - 2), pages.get(pages.size() - 2).contains("-\""));
		assertOrder(pages, 40);
	}

	/** A hyphen made for the end of a line is not in the text that string() repeats: "ab-" and "cd" are "abcd" (codex, 2026-11-01). */
	public void testStringLeavesTheLineEndHyphenOut() throws Exception {
		final List<String> pages = convertPages("hyphen-string", document(
				"@page { size: 200pt 100pt; margin: 0 } body { font: 10pt/12pt monospace; hyphens: manual }"
						+ "#source { width: 3.1ch; string-set: title content() } #copy { width: 4.1ch; break-before: page }"
						+ "#copy::before { content: string(title) } #wide { break-before: page } #wide::before { content: string(title) }",
				"<div id=\"source\">ab­cd</div><div id=\"copy\"></div><div id=\"wide\"></div>"));
		assertEquals("頁数", 3, pages.size());
		assertFalse("ハイフンが重なった: " + pages.get(1), pages.get(1).contains("--"));
		assertFalse("語の途中にハイフン: " + pages.get(2), pages.get(2).contains("-"));
	}

	private static double firstY(final String page, final String token) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).contains(token)) {
				return Double.parseDouble(m.group(2));
			}
		}
		fail(token + " が無い: " + page);
		return 0;
	}

	private static void assertOnThePaper(final String what, final List<String> pages, final double height) {
		for (int i = 0; i < pages.size(); ++i) {
			for (final double y : ys(pages.get(i))) {
				assertTrue(what + ": " + (i + 1) + " 頁の y=" + y + " は紙の外", y < height);
			}
		}
	}

	private static List<Double> ys(final String page) {
		final List<Double> ys = new ArrayList<>();
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			ys.add(Double.parseDouble(m.group(2)));
		}
		return ys;
	}

	/** T0Z to T{count-1}Z each once, in order of their numbers from page to page. */
	private static void assertOrder(final List<String> pages, final int count) {
		int next = 0;
		final int[] seen = new int[count];
		final Pattern token = Pattern.compile("T(\\d+)Z");
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			final List<Integer> numbers = new ArrayList<>();
			while (m.find()) {
				final Matcher t = token.matcher(m.group(3));
				if (t.find()) {
					numbers.add(Integer.parseInt(t.group(1)));
				}
			}
			for (final int n : numbers) {
				++seen[n];
				assertTrue(n + " は " + next + " より前のはず", n >= next - 1);
				next = Math.max(next, n);
			}
		}
		for (int i = 0; i < count; ++i) {
			assertEquals("T" + i + "Z の回数", 1, seen[i]);
		}
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/repeated-relayout/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "repeated-relayout-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		Arrays.sort(pages);
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
