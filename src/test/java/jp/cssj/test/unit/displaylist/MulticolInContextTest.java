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
import java.util.Comparator;
import java.util.List;
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
 * Multi-column containers taller than the page and multi-column containers of floats alone (2026-11-02).
 *
 * <p>
 * A multi-column container in a cell, a float or a grid item is balanced where the page is not known, to one height that
 * can pass the page. A page break cuts the last column only, so what the earlier columns held past the cut ran off the
 * paper: 63 words of 120 reached it. Its remainder in a cell was laid out in one column, with no page context to resume
 * in.
 * </p>
 *
 * <p>
 * Floats in columns are placed in the coordinates of one band, and only a line below them reaches a break. Without such a
 * line a multi-column container of floats stayed on its page, and balancing, which measures flows, put them all back in
 * the first column.
 * </p>
 */
public class MulticolInContextTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final double PAGE_WIDTH = 300, PAGE_HEIGHT = 200;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\" asc=");

	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TOKEN = Pattern.compile("T(\\d+)Z");

	private static final String WORDS = " word".repeat(18);

	private static String paragraphs(final int count) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			s.append("<p>T").append(i).append('Z').append(WORDS).append("</p>");
		}
		return s.toString();
	}

	private static String floats(final int count, final String width) {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < count; ++i) {
			s.append("<div style='float:left;width:").append(width).append("'><p>T").append(i).append('Z').append(WORDS)
					.append("</p></div>");
		}
		return s.toString();
	}

	private static String document(final String style, final String body) {
		return "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\"><head><meta charset=\"UTF-8\"/><style>"
				+ "@page { size: 300pt 200pt; margin: 0 } body { margin: 0; font: 10pt/12pt sans-serif } p { margin: 0 }"
				+ "td { padding: 0 } table { border-spacing: 0; width: 100% }" + style + "</style></head><body>" + body
				+ "</body></html>";
	}

	private static final String MULTICOL = "<div style='columns:2;column-gap:0'>";

	private static final String[][] HOSTS = { //
			{ "cell", "<table><tr><td>", "</td></tr></table>" }, //
			{ "cell-after-text", "<p>Lead</p><table><tr><td>", "</td></tr></table>" }, //
			{ "cell-with-rows", "<table><tr><td>Row</td></tr><tr><td>", "</td></tr><tr><td>Row</td></tr></table>" }, //
			{ "cell-with-head", "<table><thead><tr><th>Head</th></tr></thead><tbody><tr><td>", "</td></tr></tbody></table>" }, //
			{ "grid", "<div style='display:grid'>", "</div>" }, //
			{ "grid-two-tracks", "<div style='display:grid;grid-template-columns:0 1fr'><div></div>", "</div>" }, //
			{ "flex-two-items", "<div style='display:flex'><div style='width:0'></div><div style='flex:1'>", "</div></div>" }, //
			{ "float", "<div style='float:left;width:100%'>", "</div>" } };

	public void testLongMulticolStaysOnThePaperInReadingOrder() throws Exception {
		for (final String[] host : HOSTS) {
			final String name = "long-" + host[0];
			final List<String> pages = convertPages(name, document("", host[1] + MULTICOL + paragraphs(60) + "</div>" + host[2]));
			assertTrue(name + ": 頁をまたがない", pages.size() >= 3);
			assertReadingOrder(name, pages, 60, 2);
		}
	}

	public void testThreeColumnsStayOnThePaperInReadingOrder() throws Exception {
		final String name = "three-columns";
		final String html = document("", "<table><tr><td><div style='columns:3;column-gap:0'>" + paragraphs(90)
				+ "</div></td></tr></table>");
		assertReadingOrder(name, convertPages(name, html), 90, 3);
	}

	/** One paragraph has no block boundary to cut at: the columns are cut between its lines. */
	public void testOneLongParagraph() throws Exception {
		final String name = "one-paragraph";
		final StringBuilder p = new StringBuilder("<p>");
		for (int i = 0; i < 60; ++i) {
			p.append('T').append(i).append('Z').append(WORDS).append(' ');
		}
		final String html = document("", "<table><tr><td>" + MULTICOL + p + "</p></div></td></tr></table>");
		final List<String> pages = convertPages(name, html);
		assertEquals(name + ": 語の数", 60, tokens(name, pages, 2).size());
	}

	/** The box around the columns keeps one frame, cut at the pages, and what follows the table stays after it. */
	public void testFrameAndWhatFollows() throws Exception {
		final String name = "frame";
		final String html = document("", "<table><tr><td><div style='columns:2;column-gap:0;border:1pt solid;background:#eee'>"
				+ paragraphs(60) + "</div></td></tr></table><p>T60Z</p>");
		final List<String> pages = convertPages(name, html);
		final List<double[]> found = tokens(name, pages, 2);
		assertEquals(name + ": 語の数", 61, found.size());
		double columnsEnd = 0;
		double after = 0;
		for (final double[] token : found) {
			if (token[3] == 60) {
				assertEquals(name + ": 表の後の段落の頁", pages.size() - 1.0, token[2]);
				after = token[1];
			} else if (token[2] == pages.size() - 1) {
				columnsEnd = Math.max(columnsEnd, token[1]);
			}
		}
		assertTrue(name + ": 表の後の段落が段に重なる: " + after + " / " + columnsEnd, after > columnsEnd);
	}

	/** The rest of a multi-column container in a cell is laid out with no page context, and in columns all the same. */
	public void testRestOfACellKeepsItsColumns() throws Exception {
		final String name = "rest-of-cell";
		final List<String> pages = convertPages(name,
				document("", "<table><tr><td>" + MULTICOL + paragraphs(60) + "</div></td></tr></table>"));
		final String last = pages.get(pages.size() - 1);
		final Matcher m = TEXT.matcher(last);
		boolean first = false, second = false;
		while (m.find()) {
			if (TOKEN.matcher(m.group(3)).find()) {
				first |= Double.parseDouble(m.group(1)) < PAGE_WIDTH / 2;
				second |= Double.parseDouble(m.group(1)) >= PAGE_WIDTH / 2;
			}
		}
		assertTrue(name + ": 最後の頁が 2 段に組まれていない: " + first + " " + second, first && second);
	}

	public void testFloatsAloneBreakThePage() throws Exception {
		final String[][] forms = { //
				{ "floats-body", "body { columns: 2; column-gap: 0 }", floats(40, "90%") }, //
				{ "floats-block", "", MULTICOL + floats(40, "90%") + "</div>" }, //
				{ "floats-heading", "body { columns: 2; column-gap: 0 }", "<p>Heading</p>" + floats(40, "90%") }, //
				{ "floats-after-text", "", "<p>Lead</p>" + MULTICOL + floats(40, "90%") + "</div>" }, //
				{ "floats-in-a-wrapper", "", MULTICOL + "<div>" + floats(40, "90%") + "</div></div>" }, //
				{ "one-tall-float", "", MULTICOL + "<div style='float:left;width:90%'>" + paragraphs(40) + "</div></div>" } };
		for (final String[] form : forms) {
			final List<String> pages = convertPages(form[0], document(form[1], form[2]));
			assertTrue(form[0] + ": 頁をまたがない", pages.size() >= 3);
			assertEquals(form[0] + ": 語の数", 40, tokens(form[0], pages, 2).size());
		}
	}

	/** Columns of floats stay where the page cut them: laid out again they all went back to the first column. */
	public void testFloatsLeftInSecondColumnStayThere() throws Exception {
		final String name = "floats-second-column";
		final StringBuilder floats = new StringBuilder();
		for (int i = 0; i < 3; ++i) {
			floats.append("<div style='float:left;width:90%;height:120pt'>T").append(i).append("Z</div>");
		}
		final List<String> pages = convertPages(name, document("", MULTICOL + floats + "</div>"));
		assertEquals(name + ": 語の数", 3, tokens(name, pages, 2).size());
	}

	/** What follows columns of floats goes below them: at no height it went beside them. */
	public void testBoxHoldsFloatsAlone() throws Exception {
		final String name = "floats-held";
		final String html = document("", MULTICOL + floats(2, "90%") + "</div><p>T2Z</p>");
		final List<String> pages = convertPages(name, html);
		assertEquals(name + ": 頁の数", 1, pages.size());
		final List<double[]> found = tokens(name, pages, 2);
		assertEquals(name + ": 語の数", 3, found.size());
		double floatsEnd = 0;
		double after = 0;
		for (final double[] token : found) {
			if (token[3] == 2) {
				after = token[1];
			} else {
				floatsEnd = Math.max(floatsEnd, token[1]);
			}
		}
		assertTrue(name + ": 後の段落が float の下に来ない: " + after + " / " + floatsEnd, after > floatsEnd);
	}

	/** A column is the containing block of a float in it. */
	public void testPercentageOfAFloatIsOfTheColumn() throws Exception {
		final String name = "float-percent";
		final String html = document("", "<div style='columns:3;column-gap:0'><div style='float:left;width:90%;background:#fdd'>"
				+ "Box</div>" + paragraphs(6) + "</div>");
		final List<String> pages = convertPages(name, html);
		final Matcher m = FRAME.matcher(pages.get(0));
		assertTrue(name + ": float の枠が無い", m.find());
		assertEquals(name + ": float の幅", PAGE_WIDTH / 3 * 0.9, Double.parseDouble(m.group(1)), 0.01);
	}

	/** Each numbered token once, on the paper, in order of pages, columns and lines. */
	private static void assertReadingOrder(final String name, final List<String> pages, final int count, final int columns) {
		final List<double[]> found = tokens(name, pages, columns);
		final List<Integer> numbers = new ArrayList<>();
		for (final double[] token : found) {
			numbers.add((int) token[3]);
		}
		final List<Integer> expected = new ArrayList<>();
		for (int i = 0; i < count; ++i) {
			expected.add(i);
		}
		assertEquals(name + ": 語の順", expected, numbers);
	}

	/** {x, y, page, number} of each numbered token on the paper, by page, column and line. */
	private static List<double[]> tokens(final String name, final List<String> pages, final int columns) {
		final List<double[]> found = new ArrayList<>();
		for (int p = 0; p < pages.size(); ++p) {
			final Matcher m = TEXT.matcher(pages.get(p));
			while (m.find()) {
				final Matcher token = TOKEN.matcher(m.group(3));
				while (token.find()) {
					final double x = Double.parseDouble(m.group(1));
					final double y = Double.parseDouble(m.group(2));
					assertTrue(name + ": 紙の外: " + token.group() + " x=" + x + " y=" + y,
							x >= 0 && x < PAGE_WIDTH && y >= 0 && y < PAGE_HEIGHT);
					found.add(new double[] { x, y, p, Integer.parseInt(token.group(1)) });
				}
			}
		}
		final double column = PAGE_WIDTH / columns;
		found.sort(Comparator.<double[]>comparingDouble(t -> t[2]).thenComparingDouble(t -> Math.floor(t[0] / column))
				.thenComparingDouble(t -> t[1]));
		return found;
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/multicol-in-context/" + name);
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
		}, "multicol-in-context-" + name, 64L * 1024 * 1024);
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
