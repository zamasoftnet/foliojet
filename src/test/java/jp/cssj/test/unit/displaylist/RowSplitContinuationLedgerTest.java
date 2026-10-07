package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
 * Verify that <b>a grid/flex with one item spanning multiple pages paginates to the end</b>
 * (added 2026-08-17).
 *
 * <p>
 * Forced boundary-row splitting (crosses+anySplit) in {@code GridBox.split}/{@code FlexBox.split}
 * wrote row heights in the continuation fragment's row ledger using <b>measurements of the remainder
 * immediately after splitting</b>. At that point the remainder is not laid out (anchors are not restored),
 * so {@code getPageExtent} returns almost zero. For grid, the next split's boundary search
 * (direct {@code Row.start} comparison) incorrectly concluded that "all rows fit before the cut line"
 * and returned an <b>empty continuation fragment</b>, leaving the remainder in the head fragment
 * to be painted outside the paper.
 * </p>
 *
 * <p>
 * Real example: the eLife article (`files/realworld/elife-art`) wraps the entire body in
 * {@code display:grid}; 95 pages of content piled up on page 3
 * (4,081,661 overlapping character pairs; 51,339 pt outside the paper). Of 235 real-corpus documents,
 * six exhibited the same failure (pandoc-doc, qiita-article, godoc-pkg,
 * elife-art, mathjax-docs, and rtd-theme).
 * </p>
 *
 * <p>
 * The fix is a geometric lower bound: "a continuation row's height must be at least
 * <b>the original row height minus the amount consumed on this page</b>" (both GridBox and FlexBox).
 * Flex uses cumulative sums for boundary searches, so actual damage has not been confirmed,
 * but the same ledger error exists; protect it with the same lower bound.
 * </p>
 */
public class RowSplitContinuationLedgerTest extends TestCase {
	/** Timeout. Measured runtime is under 5 seconds per case. */
	private static final long WATCHDOG_MS = 120_000L;

	/** Paragraph count. More than 50 pages of content (180 pt content area per page). */
	private static final int PARAGRAPHS = 300;

	public RowSplitContinuationLedgerTest(final String name) {
		super(name);
	}

	/** Same structure as eLife: 12-column grid, two rows with a small nav and a huge item. Before the fix, content ended at page 3. */
	public void testGridWithMultiPageItemPaginatesToTheEnd() throws Exception {
		final int pages = convert("grid-multipage-item",
				".wrap{display:grid;grid-template-columns:repeat(12,1fr);grid-column-gap:8px}\n"
						+ ".nav{grid-column:1/13}.main{grid-column:2/12}");
		assertTrue("gridの巨大itemが最後まで組まれていない(ページ数=" + pages + ")", pages >= 40);
	}

	/**
	 * Flex counterpart (column direction). The ledger error is identical; actual damage is unconfirmed
	 * because boundary search uses cumulative sums. Pin down the guard.
	 */
	public void testFlexColumnWithMultiPageItemPaginatesToTheEnd() throws Exception {
		final int pages = convert("flex-multipage-item",
				".wrap{display:flex;flex-direction:column}\n.nav{}.main{}");
		assertTrue("flexの巨大itemが最後まで組まれていない(ページ数=" + pages + ")", pages >= 40);
	}

	/**
	 * <b>A flex container at the document end paginates regardless of the preceding content</b>
	 * (2026-08-17, root fix for the pandoc manual).
	 *
	 * <p>
	 * Flex/grid contents are laid out through TwoPass recording and take the early return in
	 * {@code addBound}, so they close without setting {@code interflowBreak}. If the container is
	 * the last child, the overflow check at the end of {@code endFlowBlock} is the only opportunity
	 * for an automatic page break. When the preceding nav (inline-flex) left the flag false,
	 * the check was skipped and the entire body piled up on one page
	 * (measured: 130,000 pt for the pandoc manual). The fix always enables the check
	 * when closing a PageAtomicBox ({@code BreakableBuilder.endFlowBlock}).
	 * </p>
	 */
	public void testTrailingFlexAfterInlineFlexNavPaginates() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0}
				.container{display:flex}
				nav > ul{display:inline-flex;flex-wrap:wrap}
				</style></head><body>
				<nav><ul><li><a href="#">A</a></li></ul></nav>
				<div class="container">
				<main>
				""");
		for (int i = 0; i < PARAGRAPHS; ++i) {
			html.append("<p>Paragraph ").append(i).append(" text that wraps a bit more here.</p>\n");
		}
		// Add no subsequent content: the container must be the last child to reproduce this.
		html.append("</main>\n<div>SIDE</div>\n</div>\n</body></html>\n");

		final File dir = new File("local/row-split-ledger/trailing-flex");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
		}
		final int pages = convertFile("trailing-flex", dir, input);
		assertTrue("末尾のflexコンテナが改ページされていない(ページ数=" + pages + ")", pages >= 8);
	}

	/**
	 * <b>Pagination reaches the end even when body itself is a column flex</b>
	 * (2026-08-17, root fix for godoc-pkg).
	 *
	 * <p>
	 * Column-direction flex has no row ledger and is atomic. Even if rescue splitting ran once,
	 * the previous {@code endFlowBlock} overflow check ran <b>only once</b>, leaving the remainder
	 * on page 2 without rechecking it, and finishing with overflow
	 * (measured: a pkg.go.dev document had 2 pages and 10.88 million overlapping pairs). The fix
	 * repeats the check until the content fits, only when closing a PageAtomicBox
	 * (an unconditional loop breaks blank-page suppression and existing fuzz behavior).
	 * </p>
	 */
	public void testBodyAsColumnFlexPaginatesToTheEnd() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0;display:flex;flex-direction:column}
				</style></head><body>
				<header>HEAD</header>
				<main>
				""");
		for (int i = 0; i < PARAGRAPHS; ++i) {
			html.append("<p>Paragraph ").append(i).append(" text that wraps a bit more here.</p>\n");
		}
		html.append("</main>\n<footer>FOOT</footer>\n</body></html>\n");

		final File dir = new File("local/row-split-ledger/body-column-flex");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
		}
		final int pages = convertFile("body-column-flex", dir, input);
		// At the time, rescue splitting produced 35 pages (strips cut through lines). Since F0
		// de-atomization on 2026-08-18 (FlexBox.isPageAtomicNow), normal line splitting handles layout,
		// increasing the page count further. Use a lower bound that holds for both paths.
		assertTrue("bodyのcolumn flexが最後まで組まれていない(ページ数=" + pages + ")", pages >= 20);
	}

	/**
	 * <b>When the retained side of a boundary row ends before the cut line, the next row does not overlap the remainder</b>
	 * (2026-08-19, root fix for smolcss).
	 *
	 * <p>
	 * Forced row splitting moves indivisible content (e.g., a {@code page-break-inside:avoid} block)
	 * entirely to the remainder, so actual content on the retained side can end before the cut line.
	 * Previously, moved/retained fragment dimensions used the cut line, so the remainder's actual content
	 * (= original row height - actual consumption &gt; original row height - distance to the cut line)
	 * overlapped the next row's start, fixed at "old geometry - distance to the cut line"
	 * (smolcss: the next article's body overlapped the preceding article's footer).
	 * The fix uses the retained side's actual painted end ({@code paintedPageExtent}).
	 * </p>
	 */
	public void testKeptSideEndingEarlyDoesNotOverlapNextRow() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0}
				.wrap{display:grid}
				.atomic{page-break-inside:avoid}
				</style></head><body><div class="wrap">
				<article>
				""");
		// Paragraph on the retained side (ends before the cut line).
		for (int i = 0; i < 10; ++i) {
			html.append("<p>Alpha paragraph ").append(i).append(" fills the kept side of row A.</p>\n");
		}
		// Indivisible block (crosses the cut line, so moves entirely to the remainder).
		html.append("<div class=\"atomic\">");
		for (int i = 0; i < 8; ++i) {
			html.append("<p>Atomic line ").append(i).append("</p>");
		}
		html.append("</div>\n<p>ATAIL marks the end of row A.</p>\n</article>\n<article>\n");
		html.append("<p>BHEAD starts row B here.</p>\n");
		for (int i = 0; i < 6; ++i) {
			html.append("<p>Bravo paragraph ").append(i).append(".</p>\n");
		}
		html.append("</article>\n</div></body></html>\n");

		final File dir = new File("local/row-split-ledger/kept-early-end");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
		}
		final int pages = convertFile("kept-early-end", dir, input);
		assertTrue("行分割が起きていない(ページ数=" + pages + ")", pages >= 2);
		// If ATAIL (end of row A) and BHEAD (start of row B) appear on the same page,
		// BHEAD must be below ATAIL.
		boolean checked = false;
		for (int p = 1; p <= pages; ++p) {
			final java.util.List<String> lines = java.nio.file.Files.readAllLines(
					new File(dir, String.format("page-%04d.txt", p)).toPath(), StandardCharsets.UTF_8);
			double atail = Double.NaN, bhead = Double.NaN;
			for (final String line : lines) {
				final java.util.regex.Matcher m = java.util.regex.Pattern
						.compile("y=([0-9.-]+) (?:artifact )?Text\\[\"(ATAIL|BHEAD)\"").matcher(line);
				if (m.find()) {
					if ("ATAIL".equals(m.group(2))) {
						atail = Double.parseDouble(m.group(1));
					} else {
						bhead = Double.parseDouble(m.group(1));
					}
				}
			}
			if (!Double.isNaN(atail) && !Double.isNaN(bhead)) {
				checked = true;
				assertTrue("p" + p + "で行Bの先頭(y=" + bhead + ")が行Aの残余(y=" + atail + ")に重なっています",
						bhead > atail);
			}
		}
		assertTrue("ATAILとBHEADが同一ページに現れず、重なり検査ができていません(フィクスチャ要調整)", checked);
	}

	/**
	 * Splitting a flex row with no painted content on a tiny page must not repeatedly split
	 * the same row with zero consumption and exponentially expand coordinates (extreme sweep STRICT seed 189).
	 */
	public void testEmptyVerticalFlexRowMakesProgressAcrossTableFragments() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="60pt"?>
				<?jp.cssj.property name="output.page-height" value="60pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt} body{font:normal 12pt/1.2 serif;writing-mode:vertical-lr}</style>
				</head><body><table>
				<td><div style="display:flex;flex-direction:row-reverse;flex-wrap:wrap-reverse;width:8em">
				<ul></ul><input type="checkbox" /><div style="width:78%"></div>
				<ol style="list-style-position:inside"><li></li></ol>
				</div></td><td></td><tfoot><td>T256</td></tfoot>
				</table></body></html>
				""";
		final File dir = new File(System.getProperty("java.io.tmpdir"),
				"row-split-ledger/empty-vertical-flex-row");
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
		final int pages = convertFile("empty-vertical-flex-row", dir, input);
		assertTrue("空flex行の分割が進んでいない(ページ数=" + pages + ")", pages <= 10);
	}

	/**
	 * Reflow only the first row being split; do not repeatedly add percentage dimensions and
	 * frame-inclusive outer dimensions of subsequent rows carried over intact on each generation
	 * (second minimal condition for extreme STRICT seed 189).
	 * The old implementation diverged to an x coordinate of 4.97e8 pt in 26 pages.
	 */
	public void testLaterVerticalFlexRowsKeepExtentsAcrossFragments() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="60pt"?>
				<?jp.cssj.property name="output.page-height" value="60pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt}body{margin:0;font:normal 12pt/1.2 serif;writing-mode:vertical-lr}</style>
				</head><body><table>
				<thead><th>T181</th></thead>
				<td>T223<div style="display:flex;flex-direction:row-reverse;flex-wrap:wrap-reverse;width:8em">
				<div style="flex:0 0 calc(25% + 0pt);min-width:8em"><input /><input value="x" /></div>
				<div style="width:78%"><table><td>T230</td></table></div>
				<ol></ol>
				</div></td>
				<tfoot><td>T256</td></tfoot>
				</table></body></html>
				""";
		final File dir = new File(System.getProperty("java.io.tmpdir"),
				"row-split-ledger/later-vertical-flex-rows");
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
		final int pages = convertFile("later-vertical-flex-rows", dir, input);
		assertTrue("後続行の寸法が増幅している(ページ数=" + pages + ")", pages <= 30);
		final java.util.regex.Pattern xPattern = java.util.regex.Pattern.compile("\\bx=(-?[0-9.E+-]+)");
		final StringBuilder displayLists = new StringBuilder();
		for (int p = 1; p <= pages; ++p) {
			final String dl = java.nio.file.Files.readString(
					new File(dir, String.format("page-%04d.txt", p)).toPath(), StandardCharsets.UTF_8);
			displayLists.append(dl);
			final java.util.regex.Matcher matcher = xPattern.matcher(dl);
			while (matcher.find()) {
				final double x = Double.parseDouble(matcher.group(1));
				assertTrue("後続行のx座標が増幅しています: " + x + " (page=" + p + ")", Math.abs(x) < 500);
			}
		}
		for (final String token : new String[] { "T181", "T223", "T230", "T256" }) {
			assertTrue("内容が欠落しています: " + token, displayLists.indexOf(token) >= 0);
		}
	}

	private static int convertFile(final String name, final File dir, final File input) throws Exception {
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
		}, name, 64L * 1024 * 1024);
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
		return pages.length;
	}

	private static int convert(final String name, final String wrapCss) throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0}
				""").append(wrapCss).append("""
				</style></head><body>
				<div class="wrap">
				<div class="nav">NAV</div>
				<div class="main">
				""");
		for (int i = 0; i < PARAGRAPHS; ++i) {
			html.append("<div><p>Paragraph ").append(i)
					.append(" with some text that wraps a little bit more.</p></div>\n");
		}
		html.append("</div>\n</div>\n<p>AFTER</p>\n</body></html>\n");

		final File dir = new File("local/row-split-ledger/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
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
		}, name, 64L * 1024 * 1024);
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
		return pages.length;
	}
}
