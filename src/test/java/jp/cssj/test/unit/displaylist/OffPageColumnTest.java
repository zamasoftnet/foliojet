package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Verify that <b>min-content sizes inflated by the column count do not place content off the paper</b>
 * (introduced 2026-07-28).
 *
 * <p>
 * This was the last remaining defect category under {@code RandomDocumentFuzzTest}'s
 * <b>invariant 6</b> (unexplained off-paper placement): eight cases in 50,000 seeds,
 * all containing <b>vertical writing</b> and <b>multi-column layout</b>.
 * Measurements identified <b>two independent mechanisms</b>: six cases used mechanism 1
 * and two used mechanism 2. This test covers <b>only mechanism 1</b>.
 * </p>
 *
 * <h2>Mechanism 1 (fixed; covered by this test)</h2>
 *
 * <p>
 * {@code fit-content} is {@code max(min-content, min(available, max-content))}, so
 * <b>if min-content exceeds available space, it is used directly</b>.
 * Multi-column min-content equals "column count × inner min-content + gaps", <b>multiplying
 * by the column count</b>; nested columns can thus reach many times the paper size.
 * Furthermore, <b>the line axis cannot split</b> (pagination affects only the page axis),
 * so overflowing content is drawn off the paper instead of moving to the next page.
 * </p>
 *
 * <p>
 * This document (from seed 35842) <b>contains no explicit sizes</b>, yet produced a 264 pt box
 * for a 130 pt line axis and drew content at y=−260 (150 pt paper).
 * The fix in {@code AbstractStaticBlockBox.shrinkToFit} caps the line axis at available
 * space <b>only when column-count inflation applies</b> ({@code IntrinsicSizes.columnInflated}).
 * </p>
 *
 * <h2>Mechanism 2 (fixed; covered by this test)</h2>
 *
 * <p>
 * An <b>indivisible box too large for a column</b> (image or inline-block) keeps
 * {@code BreakableBuilder.addBound()}'s "call {@code autoBreak()} while overflowing" loop running.
 * {@code findColumnBreak()} honors the column-count limit via {@code AbstractContainerBox.canColumnBreak()},
 * but rejected {@code autoBreak()} calls fall back to {@code pageBreak()}. Within columns, this
 * <b>unconditionally creates a column break</b> through {@code ColumnBuilder.pageBreak()}.
 * Measured seed 46577 grew {@code column-count:4} to <b>14 columns</b>, drawing content at
 * y=2,835 (842 pt paper). The remaining two cases (seeds 45399, 46577) used this mechanism.
 * </p>
 *
 * <p>
 * <b>Simply returning {@code false} when columns run out does not work</b>
 * (measured and withdrawn once on 2026-07-28). Various builder sites assume
 * <b>requested page breaks always happen</b>; naively returning {@code false} breaks them in succession:
 * </p>
 * <ol>
 * <li>The float-cutting loop in {@code endFlowBlock()} ({@code breakFloats} empties only in
 * {@code beginBreak()}): <b>infinite loop</b>.</li>
 * <li>Interline page breaks in {@code flush()} (uses {@code textBuilder} immediately afterward
 * without checking): AssertionError/NPE.</li>
 * <li>Even after fixing 1 and 2 separately, another assertion fires in {@code TextBuilder.finish()}
 * during replay because it closes an empty text block.</li>
 * </ol>
 *
 * <p>
 * <b>The accepted approach</b> (2026-07-28): stop patching things up after inspecting return values;
 * add the <b>ask before jumping</b> contract {@code BreakableBuilder.canFragmentFurther()}.
 * It defaults to {@code true}; only {@code ColumnBuilder} returns {@code false} when columns run out.
 * The two dangerous sites, interline page breaks in {@code flush()} and float cutting in
 * {@code endFlowBlock()}, check it <b>before attempting a break</b>, proceeding without closing
 * the text block or discarding the reservation and exiting. Also, {@code ColumnBuilder.pageBreak()},
 * on <b>automatic</b> breaks after exhausting columns, calls {@code beginBreak()}
 * before returning {@code false} (the same contract as {@code RootBuilder.pageBreak()} returning
 * {@code false} for no break point; emptying {@code breakFloats} terminates that loop).
 * <b>Forced</b> breaks still create columns because the author requested them, for the same reason
 * {@code ContinuationStats.guardBreakProgress} monitors only automatic page breaks.
 * </p>
 *
 * <p>
 * <b>This decision must tolerate failure without breaking</b>. The first implementation defined
 * {@code canFragmentFurther()} as {@code findColumnBreak() != null || canColumnBreak()},
 * causing <b>three strict and five wild</b> conversion failures in a 50,000-seed sweep
 * (seeds 2928/40824/41678, 10322/10538/15952/19100/37455).
 * With nested columns in {@code flowStack}, <b>even then</b> {@code columnBreak()} can fail with
 * {@code Keep}/{@code Move}, so a positive preflight answer does not guarantee a successful break.
 * <b>Note how this failure appears</b>: its category is {@code Invariant: textBuilder remains open},
 * but the assertion that actually fires is {@code assert this.textBuilder != null}
 * in {@code BreakableBuilder.flush()}, meaning it is <b>null, not left open</b>
 * ({@code RandomDocumentFuzzTest.classify} simply groups all {@code "Unexpected error."} cases
 * under that name).
 * </p>
 *
 * <h2>Criteria</h2>
 *
 * <p>
 * Use <b>the same criteria as fuzz invariant 6</b>: count only overflow beyond one entire paper
 * dimension and beyond twice the document's largest explicit size.
 * Since {@code overflow} defaults to {@code visible}, drawing content beyond its box and off
 * the paper is itself correct; naively requiring everything on paper would fail many valid documents.
 * </p>
 *
 * <p>
 * <b>Build documents here</b>: external files risk changing the verdict through relative image paths
 * (lessons learned §6.9h). That is why these reduced cases use no images.
 * </p>
 *
 * <p>
 * <b>Dropping content also eliminates off-paper placement</b>, so check token survival too.
 * </p>
 */
public class OffPageColumnTest extends TestCase {
	/** Timeout. Measured execution is under one second per case. */
	private static final long WATCHDOG_MS = 60_000L;

	/** Display-list drawing positions. Same format as {@code RandomDocumentFuzzTest}. */
	private static final Pattern POS_IN_DUMP = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+)");

	public OffPageColumnTest(String name) {
		super(name);
	}

	/**
	 * Mechanism 1: nested columns inside orthogonal writing. <b>No explicit sizes</b>,
	 * so even slight overflow triggers invariant 6.
	 */
	private static final String ORTHOGONAL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="300pt"?>
			<?jp.cssj.property name="output.page-height" value="150pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 13pt/1.2 serif;writing-mode:horizontal-tb}
			p,div{margin:0;padding:0}
			</style></head><body>
			<div style="writing-mode:vertical-lr">
			<div style="column-count:3;column-gap:5pt">
			<div style="column-count:4;column-gap:9pt">
			<p>T0</p>
			<p>T1</p>
			<p>T2</p>
			</div>
			</div>
			</div>
			</body></html>
			""";

	public void testOrthogonalNestedColumnsStayOnPage() throws Exception {
		assertNoUnexplainedOffPage("orthogonal", ORTHOGONAL, 300, 150, 0, 3);
	}

	/**
	 * Mechanism 2: <b>an indivisible box too large for a column</b> creates unlimited columns
	 * (reduced seed 46577). Only the image was replaced with {@code display:inline-block};
	 * display-list numbers were verified to differ by <b>not even 1 pt</b> from the original.
	 * Before the fix, both had worst overflow of 1,151 pt at {@code y=2835.08}.
	 *
	 * <p>
	 * {@code column-count:4} produced <b>14 columns</b>. Columns align along the line axis
	 * (<b>y</b> in this vertical-writing document) at {@code i×(column width+gap)},
	 * so each extra column sends content straight farther off the paper.
	 * </p>
	 */
	private static final String COLUMN_BUDGET = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 13pt/1.2 serif;writing-mode:vertical-rl}
			p,div,td{margin:0;padding:0}
			table{border-collapse:separate;table-layout:fixed}
			td{border:1pt solid black}
			</style></head><body>
			<div style="column-count:4;column-gap:19pt">
			<div style="float:right;width:44pt">
			<p>T0 T1 T2 T3 T4 T5 </p>
			<p><span style="display:inline-block;width:249pt;height:236pt">T6</span></p>
			</div>
			<table><tbody>
			<tr><td>T7</td><td rowspan="3">T8</td><td colspan="2">T9</td></tr>
			<tr><td>T10</td><td>T11</td><td colspan="3">T12</td></tr>
			<tr><td>T13</td><td colspan="1" rowspan="3">T14</td><td rowspan="3">T15</td></tr>
			</tbody></table>
			</div>
			</body></html>
			""";

	/**
	 * Verify that <b>no columns are created beyond the column-count limit</b> (introduced 2026-07-28).
	 *
	 * <p>
	 * Use two checks. First, the invariant 6 criterion (explicit size 249 pt gives 498 pt allowance),
	 * which fails at <b>1,151 pt</b> before the fix. This measures severity, not <b>column count</b>,
	 * so also require <b>drawing positions along the line axis (y here) to fit the paper height</b>.
	 * Columns sit at {@code i×(column width+gap)=i×212.75pt}; with four columns, the last spans
	 * {@code y=596..789.75}, with measured maximum {@code y=707.58}.
	 * A fifth column reaches {@code y≧808.75}, failing this check
	 * (measured before the fix: {@code y=2835.08}, or 14 columns).
	 * </p>
	 *
	 * <p>
	 * <b>Use paper height (842 pt) as the threshold because it is the most straightforward boundary
	 * for saying the columns fit the paper.</b> Since {@code overflow} defaults to {@code visible},
	 * overflow along the <b>page axis</b> (x) is valid and is not checked here. Even after the fix,
	 * a {@code T6} fragment is drawn at {@code x=620.8} (595 pt paper width).
	 * This is the design of the fix: <b>overflow within the last column instead of extending
	 * along the unsplittable line axis</b>.
	 * </p>
	 */
	public void testColumnCountIsNotExceeded() throws Exception {
		final File dir = assertNoUnexplainedOffPage("column-budget", COLUMN_BUDGET, 595, 842, 249, 16);

		double maxY = 0;
		String at = null;
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		java.util.Arrays.sort(pages);
		for (final File page : pages) {
			final String dump = java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8);
			final Matcher m = POS_IN_DUMP.matcher(dump);
			while (m.find()) {
				final double y = Double.parseDouble(m.group(2));
				if (y > maxY) {
					maxY = y;
					at = m.group(0) + " " + page.getName();
				}
			}
		}
		assertTrue("段を作りすぎている: 行方向の最大 y=" + Math.round(maxY) + "pt (紙面の高さ842pt, " + at
				+ ")。段は i×212.75pt に並ぶので、これは段" + (int) Math.floor(maxY / 212.75 + 1) + "に相当する"
				+ "(column-count は 4)", maxY <= 842);
	}

	/**
	 * Convert and check (1) no unexplained off-paper placement, and
	 * (2) all tokens T0..T(n-1) appear on some page.
	 *
	 * @param name            working directory name
	 * @param html            document
	 * @param pageWidth       paper width (pt)
	 * @param pageHeight      paper height (pt)
	 * @param maxExplicitSize largest {@code width}/{@code height} specified by the document
	 * @param tokenCount      number of T tokens in the document
	 * @return working directory containing display-list dumps (for additional checks)
	 */
	private static File assertNoUnexplainedOffPage(final String name, final String html, final double pageWidth,
			final double pageHeight, final double maxExplicitSize, final int tokenCount) throws Exception {
		final File dir = new File("local/off-page-column/" + name);
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
		}, "off-page-column-" + name, 64L * 1024 * 1024);
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
		java.util.Arrays.sort(pages);

		// Count only overflow of at least one entire paper dimension, and allow up to twice
		// the explicit size as a consequence of author declarations (same criterion as invariant 6).
		final double slack = 2 * maxExplicitSize;
		double worst = 0;
		String worstAt = null;
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			final String dump = java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8);
			all.append(dump);
			final Matcher m = POS_IN_DUMP.matcher(dump);
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				final double over = Math.max(Math.max(-x - pageWidth, x - 2 * pageWidth),
						Math.max(-y - pageHeight, y - 2 * pageHeight));
				if (over > worst) {
					worst = over;
					worstAt = "x=" + x + " y=" + y + " " + page.getName();
				}
			}
		}
		assertTrue(name + ": 紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(pageWidth) + "x"
				+ Math.round(pageHeight) + "pt, 最大明示サイズ" + Math.round(maxExplicitSize) + "pt, " + worstAt + ", 全"
				+ pages.length + "ページ)", worst <= slack);

		// Dropping content also eliminates off-paper placement. Make that visible as a regression.
		final List<String> lost = new ArrayList<>();
		for (int i = 0; i < tokenCount; ++i) {
			if (all.indexOf("T" + i) < 0) {
				lost.add("T" + i);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
		return dir;
	}
}
