package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * Verify that <b>column balancing (column-fill: balance) gives a column at least one line</b> when the content has
 * fewer lines than columns (introduced 2026-10-22, sweep seed 30896161).
 *
 * <p>
 * {@code ColumnBalancer} went on from the proposed position wherever its cut oracle found no boundary, as if the
 * content could be cut there. A line cannot be cut, yet one line (13.2 pt) in four columns got a quarter of its
 * height (3.3 pt): in horizontal columns the next paragraph overlapped the line, and in {@code vertical-rl} columns
 * inside {@code vertical-lr} text the line was drawn off the paper (x=−9.9; Chrome 0). The balancing oracle
 * ({@code Container.getBalanceCutPointBelow}) now tells when a column cannot hold even the line that starts it, and
 * the balancer takes that column as not fitting. Where the oracle does not see the boundaries (inside a table, a float
 * or a flex item next to an empty one) it still answers with the proposed position, so such content is still spread
 * over the columns (the first version of the fix put it all in the first column; found by codex review).
 * </p>
 */
public class MulticolShortContentTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** Comparisons in layout treat differences below 0.5 pt as equal, so a column may end that much early. */
	private static final double TOLERANCE = 0.5;

	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(T\\d+)\"");

	public MulticolShortContentTest(String name) {
		super(name);
	}

	private static String document(final String bodyWritingMode, final String body) {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="120pt"?>
				<?jp.cssj.property name="output.page-height" value="400pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{margin:0;font:normal 11pt/1.2 serif;writing-mode:%s}
				div,p{margin:0;padding:0}
				td{border:1pt solid black;padding:0}
				</style></head><body>
				%s
				</body></html>
				""".formatted(bodyWritingMode, body);
	}

	/** Two columns 125 pt wide on 280x220 pt paper, lines 20 pt high (the documents of the codex review). */
	private static String twoColumns(final String style, final String body) {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="280pt"?>
				<?jp.cssj.property name="output.page-height" value="220pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{margin:0;font:10pt/20pt serif}
				p{margin:0}
				.cols{column-count:2;column-gap:10pt}
				%s
				</style></head><body>
				%s
				</body></html>
				""".formatted(style, body);
	}

	/** One line in horizontal columns: the next paragraph starts after the line (Chrome 13.2 pt). */
	public void testNextParagraphDoesNotOverlapOneLine() throws Exception {
		for (final int count : new int[] { 2, 4 }) {
			final String html = document("horizontal-tb",
					"<div style=\"column-count:" + count + "\"><div>T0</div></div><p>T1</p>");
			final double y = y(convert("horizontal-c" + count, html), "T1");
			assertTrue(count + " 段: T1 が T0 の行(高さ 13.2pt)に重なっている: y=" + y, y >= 13.2 - TOLERANCE);
		}
	}

	/** One line in vertical-rl columns inside vertical-lr text stays on the paper (Chrome x=0). */
	public void testVerticalColumnsKeepOneLineOnThePaper() throws Exception {
		final String html = document("vertical-lr",
				"<div style=\"writing-mode:vertical-rl\"><div style=\"column-count:4\"><div>T0</div></div></div>");
		final double x = x(convert("vertical-c4", html), "T0");
		assertEquals("T0 の x(Chrome は 0)", 0.0, x, TOLERANCE + 0.01);
	}

	/** Original document that stopped the sweep. Chrome gives x=1.2 for the glyph, which sits in a line from x=0. */
	public void testSweepSeed30896161() throws Exception {
		final String html = document("vertical-lr", """
				<div style="writing-mode:vertical-rl">
				<div style="column-count:4;column-gap:5pt">
				<ol style="list-style-position:outside;list-style-type:none">
				<li>T0</li>
				</ol>
				</div>
				</div>""");
		final double x = x(convert("seed-30896161", html), "T0");
		assertTrue("T0 が紙の外: x=" + x, x >= -TOLERANCE - 0.01);
	}

	/** A table, whose rows the cut oracle does not search, is still spread over the columns. */
	public void testTableIsStillSpreadOverTheColumns() throws Exception {
		final StringBuilder rows = new StringBuilder();
		for (int i = 0; i < 10; ++i) {
			rows.append("<tr><td>T").append(i).append("</td></tr>");
		}
		final String html = document("horizontal-tb",
				"<div style=\"column-count:2;column-gap:5pt\"><table>" + rows + "</table></div><p>T99</p>");
		final String dump = convert("table-c2", html);
		assertTrue("T9 が 2 段目に行っていない: x=" + x(dump, "T9"), x(dump, "T9") > 50);
		assertTrue("表が段で分かれず縦に伸びた: T99 y=" + y(dump, "T99"), y(dump, "T99") < 120);
	}

	/**
	 * Content whose boundaries the oracle does not see is still spread over the two columns, as before the fix: a table
	 * after a paragraph, a flex row whose first item is empty, a float beside the text and a float above the text.
	 */
	public void testUnseenBoundariesStillSpreadOverTheColumns() throws Exception {
		final String[][] cases = {
				{ "table{border-spacing:0} td{padding:0;font:30pt/40pt serif}",
						"<div class=\"cols\"><p>T0</p><table><tr><td>T1</td></tr><tr><td>T2</td></tr></table></div>", "T2" },
				{ ".row{display:flex;align-items:stretch} .empty{flex:0 0 20pt} .text{flex:1;min-width:0}",
						"<div class=\"cols\"><div class=\"row\"><div class=\"empty\"></div>"
								+ "<div class=\"text\">T0<br>T1<br>T2<br>T3<br>T4<br>T5</div></div></div>", "T3" },
				{ ".f{float:left;width:30pt}", "<div class=\"cols\"><div class=\"f\">T10<br>T11<br>T12<br>T13<br>T14<br>T15</div>"
						+ "<div>T0<br>T1<br>T2<br>T3<br>T4<br>T5</div></div>", "T13" },
				{ ".f{float:left;width:100%}", "<div class=\"cols\"><div class=\"f\">T10<br>T11<br>T12<br>T13<br>T14</div>T0</div>",
						"T13" } };
		for (int i = 0; i < cases.length; ++i) {
			final String dump = convert("unseen-" + i, twoColumns(cases[i][0], cases[i][1] + "<div>T9</div>"));
			assertTrue(i + ": " + cases[i][2] + " が 2 段目に行っていない: x=" + x(dump, cases[i][2]), x(dump, cases[i][2]) > 130);
		}
	}

	/**
	 * A table of one row starting a column is not cut inside its first line (2026-10-28, sweep mirror check seed
	 * 45251237): the row of one line went into columns of 12pt for its 17pt, the line ran out of the cell and the cell
	 * went on empty in the next column. A cell of the other writing axis, not cut with its row, likewise.
	 */
	public void testTableRowStartingAColumnKeepsItsLine() throws Exception {
		final String[] cells = { "<td style=\"border-style:solid\">T0</td><td>T1</td>",
				"<td style=\"writing-mode:vertical-lr;border-style:solid\">T0</td><td>T1</td>" };
		for (int i = 0; i < cells.length; ++i) {
			final String html = """
					<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
					<?jp.cssj.property name="output.page-width" value="355pt"?>
					<?jp.cssj.property name="output.page-height" value="407pt"?>
					<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
					<style>
					@page{margin:5pt 5pt 0 0}
					body{margin:0;font:9pt/11pt serif;text-orientation:sideways}
					</style></head><body>
					<div style="column-count:3"><table style="border-spacing:3pt 6pt;margin-top:5pt"><tr>%s</tr></table></div>
					</body></html>
					""".formatted(cells[i]);
			final String dump = convert("table-row-" + i, html);
			assertEquals(i + ": セルが次の段に続いた\n" + dump, 1, dump.split("BackgroundBorder", -1).length - 1);
			assertInCell(i + "", dump, "T0", "T0");
		}
	}

	/**
	 * A block of the other writing axis in a cell is not cut either (2026-10-28, sweep mirror check seed 45251398): the
	 * cut point of the cell's content was the end of the first glyph of the vertical line, the columns came out shorter
	 * than the line and its last glyph ran out of the cell.
	 */
	public void testVerticalBlockInACellKeepsItsLine() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="293pt"?>
				<?jp.cssj.property name="output.page-height" value="298pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:5pt}
				body{margin:0;font:10pt/15pt serif;text-orientation:sideways}
				td{border:1pt solid}
				</style></head><body>
				<div style="column-count:3"><table><tr><td><div style="writing-mode:vertical-lr">T0 T1 T2 T3</div></td>
				<td>T4<br>T5</td></tr><tr><td>T6</td><td>T7</td></tr><tr><td>T8</td><td>T9</td></tr></table></div>
				</body></html>
				""";
		assertInCell("", convert("vertical-block-in-cell", html), "T0", "T3");
	}

	/**
	 * The first piece of a row is still small enough to spread the table (2026-10-28, codex review of the fix above): a
	 * bottom-aligned cell beside a cell of six lines (the space above it is given up at a cut), a nested table at the
	 * start of the only cell (its first row, not the whole table), and a table with a footer, which every fragment
	 * keeps (the first column must hold it besides the first row).
	 */
	public void testTableFirstPieceStillSpreads() throws Exception {
		final String[][] cases = {
				{ ".cols{column-count:3} table{border-spacing:0} td{padding:0}",
						"<div class=\"cols\"><table><tr><td>T0<br>T1<br>T2<br>T3<br>T4<br>T5</td>"
								+ "<td style=\"vertical-align:bottom\">T6</td></tr></table></div>",
						"T3", "60" },
				{ "table{border-spacing:0} td{padding:0}", "<div class=\"cols\"><table><tr><td><table>"
						+ "<tr><td>T0</td></tr><tr><td>T1</td></tr><tr><td>T2</td></tr><tr><td>T3</td></tr><tr><td>T4</td></tr>"
						+ "<tr><td>T5</td></tr><tr><td>T6</td></tr><tr><td>T7</td></tr><tr><td>T8</td></tr><tr><td>T9</td></tr>"
						+ "</table></td></tr></table></div>", "T9", "130" } };
		for (int i = 0; i < cases.length; ++i) {
			final String dump = convert("first-piece-" + i, twoColumns(cases[i][0], cases[i][1] + "<div>T99</div>"));
			assertTrue(i + ": " + cases[i][2] + " が次の段に行っていない: x=" + x(dump, cases[i][2]),
					x(dump, cases[i][2]) > Double.parseDouble(cases[i][3]));
		}
		// The footer: no text of the table runs into the paragraph after the columns
		final String dump = convert("first-piece-footer", twoColumns("table{border-spacing:0} td{padding:0}",
				"<div class=\"cols\"><table><tfoot><tr><td>T9</td></tr></tfoot><tr><td>T0</td></tr><tr><td>T1</td></tr>"
						+ "</table></div><div>T99</div>"));
		for (final String token : new String[] { "T0", "T1", "T9" }) {
			assertTrue("tfoot: " + token + " が後ろの段落 T99 に重なる\n" + dump, y(dump, token) + 20 <= y(dump, "T99") + TOLERANCE);
		}
	}

	/** The bottom of {@code last} is in the smallest frame around the top of {@code first}. */
	private static void assertInCell(final String name, final String dump, final String first, final String last) {
		final double[] start = box(dump, first), end = box(dump, last);
		double[] frame = null;
		final Matcher m = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) BackgroundBorder\\[w=([\\d.]+) h=([\\d.]+)")
				.matcher(dump);
		while (m.find()) {
			final double[] f = { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
			if (f[0] <= start[0] + TOLERANCE && f[1] <= start[1] + TOLERANCE && start[0] < f[0] + f[2]
					&& start[1] < f[1] + f[3] && (frame == null || f[2] * f[3] < frame[2] * frame[3])) {
				frame = f;
			}
		}
		assertNotNull(name + ": " + first + " を囲む枠が無い\n" + dump, frame);
		assertTrue(name + ": " + last + " の下 " + (end[1] + end[3]) + " がセルの枠の下 " + (frame[1] + frame[3]) + " より下\n"
				+ dump, end[1] + end[3] <= frame[1] + frame[3] + TOLERANCE);
	}

	/** A token's text box (x, y, w, h). */
	private static double[] box(final String dump, final String token) {
		final Matcher m = Pattern
				.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"" + token + "\"[^\\]]*\\] w=([\\d.]+) h=([\\d.]+)")
				.matcher(dump);
		assertTrue(token + " が描かれていない:\n" + dump, m.find());
		return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
				Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) };
	}

	private static double x(final String dump, final String token) {
		return position(dump, token)[0];
	}

	private static double y(final String dump, final String token) {
		return position(dump, token)[1];
	}

	private static double[] position(final String dump, final String token) {
		final Matcher m = TEXT_IN_DUMP.matcher(dump);
		while (m.find()) {
			if (m.group(3).equals(token)) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		throw new AssertionError(token + " が描かれていない:\n" + dump);
	}

	/** Convert a one-page document and return its page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/multicol-short-content/" + name);
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
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath());
					AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(true)) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-short-content-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertEquals(name + ": 頁数", 1, pages.length);
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
