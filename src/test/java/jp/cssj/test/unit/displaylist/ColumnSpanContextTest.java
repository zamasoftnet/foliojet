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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code column-span: all} and the boxes around it (2026-11-01).
 *
 * <p>
 * A flex or grid container between a box and its multi-column container leaves the box in one column. An item was first
 * laid out by a builder of its own, which sees no multi-column box, and its remainder by the page's builder, which does,
 * so a heading spanned on some pages only and overprinted the item's lines.
 * </p>
 *
 * <p>
 * A multi-column container inside an item or a cell keeps its spanners, and what follows a spanner is laid out once. The
 * fragment closed before the spanner has a source range running to the end of the element; laid out from a record, where
 * that range is complete, balancing replayed all of it and printed the rest of the element twice.
 * </p>
 */
public class ColumnSpanContextTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final double PAGE_WIDTH = 300;

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TOKEN = Pattern.compile("[TH]\\d+Z");

	private static final String WORDS = " word".repeat(24);

	/** A paragraph, a heading with a background (the only frames drawn) and a paragraph, each with a numbered token. */
	private static String section(final int i) {
		return "<p>T" + i + "Z" + WORDS + "</p><h1>H" + i + "Z</h1><p>T" + (1000 + i) + "Z" + WORDS + "</p>";
	}

	private static String sections(final int from, final int count, final String open, final String close) {
		final StringBuilder s = new StringBuilder();
		for (int i = from; i < from + count; ++i) {
			s.append(open).append(section(i)).append(close);
		}
		return s.toString();
	}

	private static String document(final String style, final String body) {
		return "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\"><head><meta charset=\"UTF-8\"/><style>"
				+ "@page { size: 300pt 200pt; margin: 0 } body { margin: 0; font: 10pt/12pt sans-serif } p { margin: 0 }"
				+ "h1 { margin: 0; font: inherit; column-span: all; background: #f00 }" + style + "</style></head><body>" + body
				+ "</body></html>";
	}

	private static final String MULTICOL_BODY = "body { columns: 2; column-gap: 0 }";

	public void testHeadingInAFlexItemStaysInItsColumn() throws Exception {
		final String html = document(MULTICOL_BODY,
				"<div style='display:flex;flex-wrap:wrap'>" + sections(0, 12, "<div style='width:45%'>", "</div>") + "</div>");
		assertInColumns("flex", html, 12);
	}

	public void testHeadingInAGridItemStaysInItsColumn() throws Exception {
		final String html = document(MULTICOL_BODY, "<div style='display:grid;grid-template-columns:1fr 1fr'>"
				+ sections(0, 12, "<div>", "</div>") + "</div>");
		assertInColumns("grid", html, 12);
	}

	public void testHeadingInAColumnFlexItemStaysInItsColumn() throws Exception {
		final String html = document(MULTICOL_BODY,
				"<div style='display:flex;flex-direction:column'>" + sections(0, 12, "<div>", "</div>") + "</div>");
		assertInColumns("flex-column", html, 12);
	}

	public void testHeadingDeepInAnItemStaysInItsColumn() throws Exception {
		final String html = document(MULTICOL_BODY, "<div style='display:flex;flex-wrap:wrap'>"
				+ sections(0, 12, "<div style='width:45%'><div><section>", "</section></div></div>") + "</div>");
		assertInColumns("flex-deep", html, 12);
	}

	private static void assertInColumns(final String name, final String html, final int count) throws Exception {
		final List<String> pages = convertPages(name, html);
		assertTrue(name + ": 頁をまたがない", pages.size() >= 2);
		final List<Double> widths = frameWidths(pages);
		assertEquals(name + ": 見出しの数", count, widths.size());
		for (final double width : widths) {
			assertTrue(name + ": 見出しが段をまたいだ: " + widths, width <= PAGE_WIDTH / 2 + 0.01);
		}
		assertWhole(name, pages, count);
	}

	/** The control: blocks and positioned blocks do not stop a heading from spanning. */
	public void testHeadingInABlockSpans() throws Exception {
		for (final String[] wrapper : new String[][] { { "div", "<div>" }, { "relative", "<div style='position:relative'>" } }) {
			final List<String> pages = convertPages("block-" + wrapper[0],
					document(MULTICOL_BODY, sections(0, 6, wrapper[1], "</div>")));
			final List<Double> widths = frameWidths(pages);
			assertEquals(wrapper[0] + ": 見出しの数", 6, widths.size());
			for (final double width : widths) {
				assertEquals(wrapper[0] + ": 見出しが段をまたがない", PAGE_WIDTH, width, 0.01);
			}
			assertWhole(wrapper[0], pages, 6);
		}
	}

	/** A heading spans the multi-column container inside the item. */
	public void testHeadingSpansTheMulticolInsideAnItem() throws Exception {
		final String item = "<div style='width:100%'><div style='columns:2;column-gap:0'>";
		final String body = "<div style='display:flex;flex-wrap:wrap'>" + sections(0, 2, item, "</div></div>") + "</div>";
		final List<String> pages = convertPages("inner", document("", body));
		final List<Double> widths = frameWidths(pages);
		assertEquals("見出しの数", 2, widths.size());
		for (final double width : widths) {
			assertEquals("見出しが内側の段をまたがない", PAGE_WIDTH, width, 0.01);
		}
		assertWhole("inner", pages, 2);
		// Columns around the flex container as well: a remainder laid out without the inner box open finds the outer one
		// and must not span it
		final List<String> nested = convertPages("inner-outer", document(MULTICOL_BODY, body));
		for (final double width : frameWidths(nested)) {
			assertTrue("見出しが外側の段をまたいだ", width <= PAGE_WIDTH / 2 + 0.01);
		}
		assertWhole("inner-outer", nested, 2);
	}

	/** Every box that lays the multi-column container out from a record, short enough to stay on the page. */
	private static final String[][] HOSTS = { //
			{ "cell", "<table style='width:100%'><tr><td>", "</td></tr></table>" }, //
			{ "grid", "<div style='display:grid'><div>", "</div></div>" }, //
			{ "flex", "<div style='display:flex'><div style='width:100%'>", "</div></div>" }, //
			{ "flex-wrap", "<div style='display:flex;flex-wrap:wrap'><div style='width:100%'>", "</div></div>" }, //
			{ "float", "<div style='float:left;width:100%'>", "</div>" }, //
			{ "inline-block", "<div style='display:inline-block;width:100%'>", "</div>" }, //
			{ "block", "<div>", "</div>" } };

	public void testWhatFollowsAHeadingIsLaidOutOnce() throws Exception {
		for (final String[] host : HOSTS) {
			for (final int count : new int[] { 1, 2 }) {
				final String name = "once-" + host[0] + "-" + count;
				final String html = document("@page { size: 300pt 400pt; margin: 0 }",
						host[1] + sections(0, count, "<div style='columns:2;column-gap:0'>", "</div>") + host[2]);
				final List<String> pages = convertPages(name, html);
				assertEquals(name + ": 1 頁に収まる", 1, pages.size());
				assertEquals(name + ": 見出しの数", count, frameWidths(pages).size());
				assertWhole(name, pages, count);
			}
		}
	}

	/** Two headings in one multi-column container: both fragments before them are closed by a span. */
	public void testTwoHeadingsInOneMulticol() throws Exception {
		for (final String[] host : HOSTS) {
			final String name = "two-" + host[0];
			final String html = document("@page { size: 300pt 400pt; margin: 0 }", host[1]
					+ "<div style='columns:2;column-gap:0'><section>" + section(0) + "</section>" + section(1) + "</div>"
					+ host[2]);
			final List<String> pages = convertPages(name, html);
			assertEquals(name + ": 1 頁に収まる", 1, pages.size());
			assertWhole(name, pages, 2);
		}
	}

	/** Each numbered token exactly once, and no two lines of a column on top of each other. */
	private static void assertWhole(final String name, final List<String> pages, final int count) {
		final Map<String, Integer> seen = new HashMap<>();
		for (final String page : pages) {
			final List<double[]> lines = new ArrayList<>();
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				final double extent = Double.parseDouble(m.group(4)) + Double.parseDouble(m.group(5));
				lines.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), extent });
				final Matcher token = TOKEN.matcher(m.group(3));
				while (token.find()) {
					seen.merge(token.group(), 1, Integer::sum);
				}
			}
			for (int i = 0; i < lines.size(); ++i) {
				for (int j = i + 1; j < lines.size(); ++j) {
					final double[] a = lines.get(i);
					final double[] b = lines.get(j);
					assertFalse(name + ": 行が重なった: x=" + a[0] + " y=" + a[1] + " と y=" + b[1],
							Math.abs(a[0] - b[0]) < 0.5 && Math.abs(a[1] - b[1]) < 0.8 * Math.min(a[2], b[2]));
				}
			}
		}
		for (int i = 0; i < count; ++i) {
			for (final String token : new String[] { "T" + i + "Z", "H" + i + "Z", "T" + (1000 + i) + "Z" }) {
				assertEquals(name + ": " + token + " の回数", Integer.valueOf(1), seen.get(token));
			}
		}
	}

	private static List<Double> frameWidths(final List<String> pages) {
		final List<Double> widths = new ArrayList<>();
		for (final String page : pages) {
			final Matcher m = FRAME.matcher(page);
			while (m.find()) {
				widths.add(Double.parseDouble(m.group(1)));
			}
		}
		return widths;
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/column-span-context/" + name);
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
		}, "column-span-context-" + name, 64L * 1024 * 1024);
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
