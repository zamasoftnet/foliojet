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
