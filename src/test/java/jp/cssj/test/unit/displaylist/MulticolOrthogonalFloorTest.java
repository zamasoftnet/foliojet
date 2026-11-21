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
 * Verify that <b>column balancing (column-fill: balance) never makes columns smaller
 * than children with an orthogonal writing direction</b> (introduced 2026-10-03, sweep seed 11587843).
 *
 * <p>
 * Orthogonal children (horizontal writing in vertical columns or vertical writing in horizontal columns)
 * are atomic under the pagination contract and cannot split at column boundaries.
 * However, capacity search ({@code Container.getCutPointBelow}) returned the child's <b>line boundaries</b>
 * on a different axis as cut points. {@code ColumnBalancer} then chose a column capacity
 * smaller than the child and used it directly as the box size:
 * </p>
 * <ul>
 * <li>Vertical columns (vertical-rl) became <b>narrower</b> than the child. The child aligned
 * to the column's right edge (block start) and overflowed leftward. With vertical-lr body text,
 * the columns sat at the paper's left edge, drawing text outside (x&lt;0): x=−26.90 with two columns
 * and x=−62.09 with four (Chrome: 0 for both).</li>
 * <li>Horizontal columns became <b>shorter</b> than the child, so the following paragraph overlapped it
 * (after an 88 pt-tall child, the paragraph was at y=73.42; without columns, 100.32).</li>
 * </ul>
 *
 * <p>
 * Fix in {@code FlowContainer.balancePageSizeFloor}: extend the floor, previously limited to
 * same-axis opposite-progression children (RL⇄LR, 2026-08-22), to all children with a writing direction
 * different from the columns, traversing inside same-direction children too.
 * </p>
 *
 * <p>
 * 2026-11-21 (sweep seed 147257079): the floor is the child's extent, not where it ends in the single stack (that kept
 * everything before an orthogonal child in the first column), balancing does not cut inside such a child, and the
 * column builder breaks before one that runs past the balanced column end.
 * </p>
 */
public class MulticolOrthogonalFloorTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(T\\d+)\"");

	public MulticolOrthogonalFloorTest(String name) {
		super(name);
	}

	private static String document(final String bodyWritingMode, final String body) {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="595pt"?>
				<?jp.cssj.property name="output.page-height" value="842pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:0pt}
				body{margin:0;font:normal 11pt/1.2 serif;writing-mode:%s}
				div,p{margin:0;padding:0}
				</style></head><body>
				%s
				</body></html>
				""".formatted(bodyWritingMode, body);
	}

	private static final String HORIZONTAL_CHILD = "<div style=\"writing-mode:horizontal-tb;width:88pt\">T0<br>T1</div>";

	/**
	 * Horizontal-writing child inside vertical columns. Text starts at the paper's left edge (x=0) regardless of
	 * column count.
	 */
	public void testVerticalColumnsAreNotNarrowerThanHorizontalChild() throws Exception {
		for (final int count : new int[] { 2, 3, 4 }) {
			final String html = document("vertical-lr", "<div style=\"writing-mode:vertical-rl\"><div style=\"column-count:"
					+ count + "\">" + HORIZONTAL_CHILD + "</div></div>");
			final String dump = convert("vertical-c" + count, html);
			assertEquals(count + " 段: T0 の x(Chrome は 0)", 0.0, x(dump, "T0"), 0.01);
			assertEquals(count + " 段: T1 の x", 0.0, x(dump, "T1"), 0.01);
		}
	}

	/** The same holds with an extra div wrapper sharing the writing direction. */
	public void testNestedHorizontalChildIsFound() throws Exception {
		final String html = document("vertical-lr", "<div style=\"writing-mode:vertical-rl\"><div style=\"column-count:2\"><div>"
				+ HORIZONTAL_CHILD + "</div></div></div>");
		assertEquals("T0 の x(Chrome は 0)", 0.0, x(convert("vertical-nested", html), "T0"), 0.01);
	}

	/** Original document that stopped the sweep (all 930 unreduced bytes). Chrome gives x=30.0. */
	public void testSweepSeed11587843() throws Exception {
		final String html = document("vertical-lr", """
				<div style="display:list-item;position:static;float:left;writing-mode:vertical-rl;">
				<div style="column-count:4;column-gap:4pt">
				<div style="writing-mode:horizontal-tb;width:min-content;min-width:8em;max-width:90%;">
				<ul style="list-style-position:outside;list-style-type:none">
				<li>T0</li>
				<li>T1</li>
				</ul>
				</div>
				</div>
				</div>""");
		final String dump = convert("seed-11587843", html);
		assertEquals("T0 の x(Chrome は 30.0)", 30.0, x(dump, "T0"), 0.01);
	}

	/** Vertical-writing child inside horizontal columns (88 pt tall). The following paragraph does not overlap it. */
	public void testHorizontalColumnsAreNotShorterThanVerticalChild() throws Exception {
		final String html = document("horizontal-tb", "<div style=\"column-count:2\">"
				+ "<div style=\"writing-mode:vertical-rl;height:88pt\">T2<br>T3</div></div><p>T5</p>");
		final double y = y(convert("horizontal-c2", html), "T5");
		assertTrue("T5 が縦書きの子(高さ 88pt)に重なっている: y=" + y, y >= 88);
	}

	/** Four columns 144.25pt wide (595pt paper, gap 6pt): the second starts at x=150.25. */
	private static final double SECOND_HORIZONTAL_COLUMN = 150.25;

	/**
	 * A vertical-writing block last in horizontal columns goes into a later column with the text before it spread over
	 * the columns (2026-11-21, sweep seed 147257079). The floor was where the block ends in the single stack, all the
	 * content: everything stayed in the first column. Chrome puts the text in the first two columns and the block in the
	 * third.
	 */
	public void testOrthogonalBlockLastDoesNotKeepEverythingInTheFirstColumn() throws Exception {
		final StringBuilder text = new StringBuilder();
		for (int i = 10; i < 60; ++i) {
			text.append('T').append(i).append(' ');
		}
		final String html = document("horizontal-tb", "<div style=\"column-count:4;column-gap:6pt\"><p>" + text
				+ "</p><div style=\"writing-mode:vertical-rl\"><p>T2 T3</p></div></div>");
		final String dump = convert("orthogonal-last", html);
		assertTrue("T59 が 1 段目のまま: x=" + x(dump, "T59"), x(dump, "T59") >= SECOND_HORIZONTAL_COLUMN - 0.01);
		assertTrue("縦書きの箱が 1 段目のまま: x=" + x(dump, "T2"), x(dump, "T2") >= SECOND_HORIZONTAL_COLUMN - 0.01);
	}

	/**
	 * A vertical-writing block after a line as long as the balanced column goes to the next column (2026-11-21). Laid out
	 * again for balancing it is placed whole, and nothing after it checked it against the column end: it stayed below
	 * the line in the first column. Chrome puts it at the top of the second.
	 */
	public void testOrthogonalBlockAfterAFullColumnGoesToTheNextColumn() throws Exception {
		final String html = document("horizontal-tb", "<div style=\"column-count:4;column-gap:6pt\"><div><p>"
				+ "<span style=\"display:inline-block;height:150pt;width:34pt\">T0</span></p></div>"
				+ "<div style=\"writing-mode:vertical-rl\"><p>T2 T3</p></div></div>");
		final String dump = convert("orthogonal-after-full", html);
		assertEquals("T2 の x(2 段目の始まり)", SECOND_HORIZONTAL_COLUMN, x(dump, "T2"), 0.01);
		assertEquals("T2 の y", 0.0, y(dump, "T2"), 0.01);
	}

	/** The same in vertical-rl columns with a horizontal-writing block: it goes below, to the second column. */
	public void testHorizontalBlockAfterAFullVerticalColumnGoesToTheNextColumn() throws Exception {
		final String html = document("vertical-rl", "<div style=\"column-count:4;column-gap:6pt\"><div><p>"
				+ "<span style=\"display:inline-block;width:150pt;height:34pt\">T0</span></p></div>"
				+ "<div style=\"writing-mode:horizontal-tb\"><p>T2 T3</p></div></div>");
		final String dump = convert("horizontal-after-full", html);
		// Four columns 206pt long (842pt paper, gap 6pt): the second starts at y=212
		assertEquals("T2 の y(2 段目の始まり)", 212.0, y(dump, "T2"), 0.01);
		assertTrue("T2 が紙の右の外: x=" + x(dump, "T2"), x(dump, "T2") < 595);
	}

	/**
	 * Balancing does not cut inside a block of the other axis (2026-11-21). Its vertical lines were taken for cut points
	 * along the column, the columns came out shorter than the paragraph, the block and the paragraph after it, and the
	 * paragraph after the block ran past the multicol onto the one that follows it.
	 */
	public void testBalancingDoesNotCutInsideAnOrthogonalBlock() throws Exception {
		final String html = document("horizontal-tb", "<div style=\"column-count:2;column-gap:6pt\"><p>T0</p>"
				+ "<div style=\"writing-mode:vertical-rl;height:100pt\">T2<br>T3<br>T4</div><p>T9</p></div><p>T20</p>");
		final String dump = convert("orthogonal-not-cut", html);
		assertTrue("T9(y=" + y(dump, "T9") + ")が段組の後の T20(y=" + y(dump, "T20") + ")に重なっている",
				y(dump, "T9") + 13.2 <= y(dump, "T20") + 0.01);
	}

	/**
	 * Document that stopped the sweep (seed 147257079, all 1,265 bytes). A horizontal-writing block after a grid wider
	 * than the vertical-rl box stayed in the first column beyond the grid, left of the paper; Chrome puts it in the second
	 * column at x=76.75.
	 */
	public void testSweepSeed147257079() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="595pt"?>
				<?jp.cssj.property name="output.page-height" value="842pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<title>fuzz v2 1444370305</title>
				<meta name="foliojet-fuzz-generator" content="2" />
				<style>
				@page{margin:10pt}
				body{margin:0;font:normal 9pt/1.2 serif;writing-mode:vertical-lr}
				p,div,td{margin:0;padding:0}
				table{border-collapse:collapse;table-layout:fixed}
				td{border:1pt solid black}
				</style></head><body data-fuzz-generator="2">
				<div style="writing-mode:vertical-rl">
				<div style="display:list-item;width:123pt;">
				<div style="column-count:4;column-gap:6pt">
				<div style="display:grid;position:static;writing-mode:vertical-lr;grid-template-columns:fit-content(48pt) minmax(12pt,1fr);gap:calc(1pt + .25em);">
				<p><span style="display:inline-block;width:192pt;height:34pt">T0</span></p>
				<div data-fuzz-role="layout-item" style="width:max-content;min-width:8em;max-width:90%;">
				<div style="position:static;float:right;writing-mode:vertical-lr;">
				<p id="p1">T1</p>
				</div>
				</div>
				</div>
				<div style="position:static;writing-mode:horizontal-tb;">
				<p id="p2">T2 T3 T4 T5 </p>
				</div>
				</div>
				</div>
				</div>

				</body></html>
				""";
		final String dump = convert("seed-147257079", html);
		for (final String token : new String[] { "T2", "T3", "T4", "T5" }) {
			assertTrue(token + " が紙の左の外: x=" + x(dump, token), x(dump, token) >= 0);
		}
		assertEquals("T2 の x(Chrome は 76.75)", 76.25, x(dump, "T2"), 0.01);
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
		final File dir = new File("local/multicol-orthogonal-floor/" + name);
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
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-orthogonal-floor-" + name, 64L * 1024 * 1024);
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
