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
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Regression tests for <b>defects found in the WPT corpus</b> (introduced on 2026-07-28).
 *
 * <p>
 * These were found by checking 2,409 WPT documents (`css/css-break`, `css-multicol`, `css-page`)
 * against invariants 1–3 (no termination by exception, termination, bounded page count)
 * ({@link WptCorpusTest}). <b>None appeared in the random sweep of 200,000 documents</b>:
 * they exercised forms the generator does not produce (a spanner inside an inline,
 * {@code column-width:0}, and a border with a 32-bit width).
 * </p>
 *
 * <p>
 * <b>Reproduction conditions differ by defect.</b> Spanners and borders fail even on default A4,
 * but {@code column-width:0} <b>requires a small page to reproduce</b>. This is why the sweep
 * uses 120x120 pt; these tests create the same conditions. Cases where page counts explode
 * only on small pages (grid, etc.) are handled separately as degenerate geometry issues
 * (`開発メモ`).
 * </p>
 *
 * <h2>Mechanism: {@code column-span:all} inside an inline</h2>
 *
 * <p>
 * Ten of the 2,409 documents failed with
 * {@code IndexOutOfBoundsException: Index -1 out of bounds for length 0}
 * from the same cause.
 * </p>
 *
 * <p>
 * For a spanner ({@code column-span:all}), the FLOW branch of {@code DocumentBuilder.startBox}
 * called <b>{@code startColumnSpan} first</b>, then {@code closeInlines}.
 * {@code startColumnSpan} unwound through {@code endFlowBlock} to exit multi-column layout,
 * replacing {@code containerBuilder} in the process. The {@code endInline} emitted by
 * {@code closeInlines} then reached <b>a new {@code StyledTextUnitizer} that had never seen
 * the corresponding {@code startInline}</b>. Its {@code InlineParamsStack} contained only the root,
 * so pop removed that root and {@code current()} failed on the empty list.
 * </p>
 *
 * <p>
 * Open inlines were <b>opened in the context before the spanner</b> and must be closed in that context.
 * The nesting was corrected by moving {@code closeInlines} first and restoring inlines
 * ({@code restoreInlines}) after {@code endColumnSpan}.
 * </p>
 *
 * <p>
 * <b>Some cases remain unfixed</b>: if the spanner is <b>inside a block that is itself inside an
 * inline</b>,
 * {@code startColumnSpan} itself reopens inlines with {@code restoreInlines} before calling
 * {@code endFlowBlock}, leaving the same kind of imbalance
 * (two cases, including {@code multicol-span-all-children-height-010}).
 * A guard in {@code InlineParamsStack.pop} alone was confirmed to <b>merely shift the failure
 * to another null</b>, not fix it (`開発メモ`).
 * </p>
 */
public class WptRegressionTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** Time limit per document. Normally completes in under one second. */
	private static final long WATCHDOG_MS = 60_000L;

	public WptRegressionTest(String name) {
		super(name);
	}

	/**
	 * Minimal form: a spanning block directly inside a {@code <span>}.
	 * WPT cases such as {@code css-multicol/spanner-in-child-after-parallel-flow-003}
	 * have this form.
	 */
	private static final String SPANNER_IN_INLINE = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			</head><body>
			<div style="columns:2; width:100px">
			<span>
			<div style="column-span:all; height:10px; background:green"></div>
			</span>
			</div>
			</body></html>
			""";

	/**
	 * A form with inline content before the spanner
	 * (the skeleton of {@code css-multicol/multicol-span-all-019}).
	 * If the inline actually contains characters, {@code endInline} passes through the glyph pipeline,
	 * changing the path.
	 */
	private static final String SPANNER_IN_INLINE_WITH_TEXT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>body{font:normal 10pt/1.2 serif}</style>
			</head><body>
			<div style="columns:2; width:100px; orphans:1; widows:1">
			<div style="height:15px">
			<span>ABC DEF
			<div style="column-span:all; height:20px; background:green"></div>
			</span>
			</div>
			<div style="height:40px"></div>
			</div>
			</body></html>
			""";

	/** A replaced element as the spanner (the same ordering on the {@code addReplacedBox} side). */
	private static final String REPLACED_SPANNER_IN_INLINE = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			</head><body>
			<div style="columns:2; width:100px">
			<span>XY
			<img src="@IMG@" style="column-span:all; display:block; width:20pt" />
			</span>
			</div>
			</body></html>
			""";

	public void testSpannerDirectlyInsideInline() throws Exception {
		convertWithin("spanner-in-inline", SPANNER_IN_INLINE);
	}

	public void testSpannerInsideInlineWithText() throws Exception {
		convertWithin("spanner-in-inline-text", SPANNER_IN_INLINE_WITH_TEXT);
	}

	/**
	 * A spanner <b>inside a block that is itself inside an inline</b>
	 * (2026-07-28, WPT {@code multicol-span-all-children-height-010} and
	 * {@code inline-with-spanner-in-overflowed-container-before-multicol-float}).
	 *
	 * <p>
	 * <b>The failure occurs at a different point</b> from the direct-child cases (the two above).
	 * {@code startColumnSpan} closes ancestor flow blocks in turn to exit multi-column layout,
	 * but it called {@code restoreInlines} at the end of each iteration, so
	 * <b>reopened inlines crossed the next iteration's {@code endContainer()}</b>.
	 * {@code endContainer} removes the top of {@code textParamsStack} and discards
	 * {@code textShaper} (and thus its {@code InlineParamsStack}), causing <b>three stacks to
	 * become misaligned simultaneously</b> when closing.
	 * </p>
	 *
	 * <p>
	 * This {@code restoreInlines} <b>prematurely performed</b> registration that should pair with
	 * {@code closeInlines} in {@code startBox}; the matching operation belongs on the {@code endBox} side.
	 * The premature registration was removed, along with the corresponding {@code closeInlines}
	 * in {@code endColumnSpan}.
	 * </p>
	 */
	private static final String SPANNER_IN_BLOCK_IN_INLINE = """
			<!DOCTYPE html>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			</head><body>
			<div style="columns:2; width:100px">
			<span>
			<div style="height:20px">
			<div>
			<div style="column-span:all; height:10px; background:green"></div>
			</div>
			</div>
			</span>
			</div>
			</body></html>
			""";

	public void testSpannerInsideBlockInsideInline() throws Exception {
		convertWithin("spanner-in-block-in-inline", SPANNER_IN_BLOCK_IN_INLINE);
	}

	/**
	 * {@code column-width:0} completes in a practical time (2026-07-28, WPT
	 * {@code css-multicol/zero-column-width-layout.html}).
	 *
	 * <p>
	 * css-multicol-1 §3.1 states that {@code column-width:0} is valid as a specified and computed value,
	 * but <b>the used value is never less than 1px</b>. Without clamping, the division in
	 * {@code LayoutUtils.getColumnCount} divides by zero and attempts to create
	 * {@code (int)Infinity} = 2,147,483,647 columns.
	 * </p>
	 *
	 * <p>
	 * <b>Strictly speaking, this is extremely slow, not an infinite loop</b>: measurements showed that
	 * even before the fix, it completed in <b>about 50 seconds</b>. The sweep classified it as nontermination
	 * because its timeout is 30 seconds. This test therefore uses <b>a short budget</b>:
	 * with the default 60 seconds, reverting the fix would still pass and miss the regression
	 * (actually encountered on 2026-07-28). After the fix, it takes less than one second.
	 * </p>
	 */
	public void testZeroColumnWidthIsFast() throws Exception {
		// **Use the original WPT document directly instead of constructing a document.**
		// (files/unittest/0490-robustness/wpt-zero-column-width.html)。
		// Several minimal forms copying only the skeleton were tried, but none reproduced the failure.
		// A **small page** is also essential, and it must be supplied through session properties,
		// not a PI, to reproduce it (use the same path as the WPT sweep).
		convertWithinFile("wpt-zero-column-width.html", "120x120", 15_000L);
	}

	/**
	 * Conversion does not fail even with a huge {@code border-width} (2026-07-28, WPT
	 * {@code css-break/grid/grid-large-end-border-crash.html}).
	 *
	 * <p>
	 * {@code 4294967295px} becomes 3.22e9 pt and caused <b>conversion to fail</b> at the
	 * abnormal-drawing-height assertion in {@code BackgroundBorderDrawable}.
	 * Clamp it to {@code Border.MAX_WIDTH}, taking the same approach as clamping
	 * {@code colspan}/{@code rowspan} to the HTML Standard limits.
	 * </p>
	 */
	public void testHugeBorderWidthDoesNotFail() throws Exception {
		convertWithin("huge-border", """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				</head><body>
				<div style="column-count:2; column-fill:auto; border-bottom:4294967295px solid">
				<div style="display:grid; padding-top:1px; border-bottom:4294967295px solid">
				<div></div>
				</div>
				</div>
				</body></html>
				""");
	}

	public void testReplacedSpannerInsideInline() throws Exception {
		final File png = new File("files/unittest/red.png");
		assertTrue("テスト画像が見つからない: " + png.getAbsolutePath(), png.isFile());
		convertWithin("replaced-spanner-in-inline",
				REPLACED_SPANNER_IN_INLINE.replace("@IMG@", png.toURI().toString()));
	}

	/**
	 * Convert the document on a separate thread and check that it completes without exceptions
	 * within {@link #WATCHDOG_MS} (the same form as {@code SpanRobustnessTest}).
	 */
	private static void convertWithin(final String name, final String html) throws Exception {
		convertWithin(name, html, null);
	}

	/**
	 * Convert documents in {@code files/unittest/0490-robustness/} as they are.
	 *
	 * <p>
	 * Other cases construct documents here (教訓集 §6.9h), but cases that <b>cannot be reproduced by
	 * copying the skeleton</b> use imported WPT originals. Fixing expectations against a minimal form
	 * that does not reproduce the failure is not a regression test: it does not fail when the fix is reverted.
	 * </p>
	 */
	private static void convertWithinFile(final String fileName, final String pageSize, final long budgetMs)
			throws Exception {
		final File input = new File("files/unittest/0490-robustness/" + fileName);
		assertTrue("テスト文書が見つからない: " + input.getAbsolutePath(), input.isFile());
		final File dir = new File("local/unittest/wpt-regression");
		dir.mkdirs();
		runWithin(fileName, input, new File(dir, fileName + ".pdf"), pageSize, budgetMs);
	}

	/**
	 * @param pageSize page dimensions such as {@code "120x120"} (pt); {@code null} uses the default.
	 *                  <b>Supply this through session properties, not a PI</b>, because that is what
	 *                  the WPT sweep ({@link WptCorpusTest}) uses. A PI does not create the same
	 *                  conditions, and the test still passes when the fix is reverted
	 *                  (actually encountered on 2026-07-28).
	 */
	private static void convertWithin(final String name, final String html, final String pageSize) throws Exception {
		final File dir = new File("local/unittest/wpt-regression");
		dir.mkdirs();
		final File input = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}

		runWithin(name, input, new File(dir, name + ".pdf"), pageSize, WATCHDOG_MS);
	}

	/** Convert on a separate thread and check that it completes without exceptions within {@code budgetMs}. */
	private static void runWithin(final String name, final File input, final File pdf, final String pageSize,
			final long budgetMs) throws Exception {
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(input, pdf, pageSize);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "wpt-regression-" + name);
		worker.setDaemon(true);
		worker.start();
		worker.join(budgetMs);
		if (worker.isAlive()) {
			fail(name + ": " + budgetMs + "ms以内に変換が終わりませんでした");
		}
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わりました", failure[0]);
		}
	}

	private static void convert(final File input, final File pdf, final String pageSize) throws Exception {
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (pageSize != null) {
					final int x = pageSize.indexOf('x');
					session.property("output.page-width", pageSize.substring(0, x) + "pt");
					session.property("output.page-height", pageSize.substring(x + 1) + "pt");
				}
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
