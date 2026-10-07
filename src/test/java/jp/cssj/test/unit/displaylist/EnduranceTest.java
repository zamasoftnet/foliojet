package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.builder.impl.TableBuildStats;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.fragment.LayoutSourceTestHooks;
import net.zamasoft.foliojet.layout.fragment.TextSpillException;
import net.zamasoft.foliojet.layout.rescue.RescueStats;
import net.zamasoft.foliojet.layout.segment.TextSpill;
import net.zamasoft.foliojet.layout.segment.TextSpillTestHooks;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Endurance tests for E-6 (spillable tape infrastructure) (2026-07-24;
 * "Endurance tests (acceptance criteria for proposal A)" in the development record;
 * scope adjusted by the adaptation decision).
 *
 * <p>
 * Consists of scaled-down tests always run in CI (most tests in this class) and
 * full-scale tests gated by {@code -Dfoliojet.perf} (completion in a separate JVM with {@code -Xmx128m};
 * {@code ./gradlew test --tests "*.EnduranceTest" -Dfoliojet.perf= }).
 * Assert guarantees; for known remaining limitations that cannot be guaranteed
 * (retention of every row's box tree in a completed TableBox; row-by-row parent commits
 * belong to a future Incremental integration step), report measurements only.
 * Do not create failing tests with unjustified assertions.
 * </p>
 *
 * <ul>
 * <li>Text spill bound: with identical CSS and 8× the body text,
 * the {@code LIVE_TEXT_PAYLOAD_BYTES} high-water mark stays within "budget + at most one record"
 * (independent of body size). Spill volume scales with body size.</li>
 * <li>Typed spill failures: write/read failures are {@link TextSpillException},
 * no temporary files remain, and subsequent conversions work.</li>
 * <li>Header/footer progress: even extreme fixtures with repeated headers taller than a page
 * do not loop forever (existing protection: {@code TableCutter.keepOrMoveAll};
 * if header + footer do not fit, KEEP at the page start commits overflow,
 * otherwise MOVE; the next page starts with KEEP, so termination is guaranteed).</li>
 * <li>Separate JVM with -Xmx128m (perf gate): measure the scale at which a huge single-cell auto table
	 * and a 100,000-row short-cell auto table complete.</li>
	 * <li>{@code -Dfoliojet.rowRetentionDiag=true}: in a separate JVM, separately observe row/cell counts
	 * for pending plans, bound but unsubmitted rows, the parent's current page, and repeated groups.
	 * Observe B-2c emission-eligible tables even without this option and also collect histograms.</li>
 * <li>Visual rescue splitting (2026-07-25, increment 8): a fixture combines a 20,000 pt float,
 * a 20,000 pt block with a mismatched writing direction, and a 3,000 pt line.
 * Verify no crashes, infinite loops, or stalls; finite, reasonable page counts; and no
 * unintended blank pages (always in CI).</li>
 * </ul>
 */
public class EnduranceTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final File WORK_DIR = new File("local/unittest/endurance");

	private static final double STREAMING_PAGE_HEIGHT = 794; // 842 pt - 24 pt at the top and bottom.
	private static final double STREAMING_ROW_HEIGHT = 9.1;

	/**
	 * The image's <b>absolute URI</b>. With a relative path, moving the fixture silently
	 * removes the image, producing a different document (2026-07-27).
	 */
	private static final String RED_PNG_URI = new File("files/unittest/red.png").getAbsoluteFile().toURI()
			.toString();

	// ------------------------------------------------------------------
	// 1. Text spill bound (always in CI).
	// ------------------------------------------------------------------

	/**
	 * Transcode two documents with identical CSS and an 8× difference in body size under a tiny budget (4 KB).
	 * Verify (1) both inline retention high-water marks stay within "budget + at most one record"
	 * (independent of body size, the central E-6 guarantee), and
	 * (2) spilled bytes increase roughly in proportion to body size (4–12× for the 8× document).
	 */
	public void testTextPayloadHighWaterBoundedByBudgetPlusOneRecord() throws Exception {
		final long budget = 4096;
		final File doc1 = generateProse("spill-bound-1x", 160);
		final File doc8 = generateProse("spill-bound-8x", 160 * 8);
		try {
			final long[] r1 = this.measureSpill(doc1, "spill-bound-1x", budget);
			final long[] r8 = this.measureSpill(doc8, "spill-bound-8x", budget);
			final long live1 = r1[0], spilled1 = r1[1], maxRecord1 = r1[2];
			final long live8 = r8[0], spilled8 = r8[1], maxRecord8 = r8[2];
			System.err.println("[E-6 endurance spill-bound] budget=" + budget + " live1x=" + live1 + " spilled1x="
					+ spilled1 + " maxRecord1x=" + maxRecord1 + " live8x=" + live8 + " spilled8x=" + spilled8
					+ " maxRecord8x=" + maxRecord8);

			assertTrue("1x文書でspillが発火していません(fixtureが小さすぎます): " + spilled1, spilled1 > 0);
			assertTrue("8x文書でspillが発火していません: " + spilled8, spilled8 > 0);
			// Core guarantee: inline retention high-water stays within "budget + at most one record", regardless of body size.
			assertTrue("1x: inline保持高水位が予算+1recordを超えています: " + live1 + " > " + budget + "+" + maxRecord1,
					live1 <= budget + maxRecord1);
			assertTrue("8x: inline保持高水位が予算+1recordを超えています(本文量比例の疑い): " + live8 + " > " + budget
					+ "+" + maxRecord8, live8 <= budget + maxRecord8);
			// Spill volume scales with body size (4–12× for an 8× document; under deterministic budget decisions,
			// the inline share can cause deviation from exactly 8×).
			assertTrue("spill量が本文量に比例していません: 1x=" + spilled1 + ", 8x=" + spilled8,
					spilled8 >= spilled1 * 4 && spilled8 <= spilled1 * 12);
		} finally {
			doc1.delete();
			doc8.delete();
		}
	}

	/** Transcode under a tiny budget and return {live high-water, spilled bytes, largest Chars record bytes}. */
	private long[] measureSpill(final File doc, final String name, final long budget) throws Exception {
		ContinuationStats.reset();
		final AtomicLong maxRecordBytes = new AtomicLong();
		LayoutSourceTestHooks.setAppendObserver(event -> {
			if (event instanceof LayoutSource.Chars chars) {
				maxRecordBytes.accumulateAndGet(chars.payload().utf16Length() * 2L, Math::max);
			}
		});
		try {
			this.transcode(doc, name, String.valueOf(budget));
		} finally {
			LayoutSourceTestHooks.setAppendObserver(null);
		}
		return new long[] { ContinuationStats.LIVE_TEXT_PAYLOAD_BYTES.get(), ContinuationStats.SPILLED_TEXT_BYTES.get(),
				maxRecordBytes.get() };
	}

	// ------------------------------------------------------------------
	// 3. Typed spill failures (always in CI).
	// ------------------------------------------------------------------

	/**
	 * Verify directly on LayoutSource that spill write/read failures throw {@link TextSpillException}
	 * (no silent suppression, bare IOException, or nondeterministic fallback).
	 * Also verify that no temporary files remain after close.
	 */
	public void testSpillIoFailureIsTypedTextSpillException() throws Exception {
		// (a) Write failure.
		try (LayoutSource source = new LayoutSource(0)) {
			TextSpillTestHooks.setFaultInjector(() -> {
				throw new IOException("injected write failure");
			}, null);
			try {
				source.appendChars(0, "endurance".toCharArray(), 0, 9, false);
				fail("spill書き込み失敗はTextSpillExceptionになるはずです");
			} catch (final TextSpillException e) {
				assertTrue("原因IOExceptionが失われています", e.getCause() instanceof IOException);
			} finally {
				TextSpillTestHooks.clearFaultInjector();
			}
			final TextSpill spill = source.textSpillForTest();
			assertNotNull("spillストアが開かれているはずです(openは注入対象外)", spill);
			source.close();
			assertTrue("close後にspill一時ファイルが残っています", spill.tempFilesDeletedForTest());
		}

		// (b) Read failure (writing already succeeded).
		try (LayoutSource source = new LayoutSource(0)) {
			final long id = source.appendChars(0, "endurance".toCharArray(), 0, 9, false);
			final LayoutSource.Chars chars = (LayoutSource.Chars) source.get(id);
			assertTrue("予算0では必ずSpilledになるはずです",
					chars.payload() instanceof LayoutSource.TextPayload.Spilled);
			TextSpillTestHooks.setFaultInjector(null, () -> {
				throw new IOException("injected read failure");
			});
			try {
				chars.payload().freshChars();
				fail("spill読み出し失敗はTextSpillExceptionになるはずです");
			} catch (final TextSpillException e) {
				assertTrue("原因IOExceptionが失われています", e.getCause() instanceof IOException);
			} finally {
				TextSpillTestHooks.clearFaultInjector();
			}
			// Reads succeed after removing the fault (the fault does not corrupt the store).
			assertEquals("endurance", new String(chars.payload().freshChars()));
		}
	}

	/**
	 * Verify that a spill write failure during transcode (1) propagates as a conversion failure
	 * (not silently suppressed), with {@link TextSpillException} as its root cause,
	 * (2) leaves no spill temporary files, and (3) allows subsequent conversions to work.
	 * The same applies to read failures (decoding during page-break replay).
	 */
	public void testSpillFailureDuringTranscodeFailsCleanlyAndRecovers() throws Exception {
		final File writeDoc = new File("files/unittest/0460-segment-restyle/mid-paragraph.html");
		// Inject read failures using a document that actually decodes spilled payload during page-break replay
		// (measured: moved-blocks reads 8 records with budget 0. Mid-paragraph tail replay may
		// stop at the already-delivered terminal gate before decoding).
		final File readDoc = new File("files/unittest/0460-segment-restyle/moved-blocks.html");

		// (a) Write failure (injected on the fourth spill after three successful spills).
		final AtomicInteger appends = new AtomicInteger();
		this.checkTranscodeSpillFailure(writeDoc, "spill-write-fail", () -> {
			if (appends.incrementAndGet() > 3) {
				throw new IOException("injected write failure");
			}
		}, null);

		// (b) Read failure (injected during decoding of spilled payload in page-break replay).
		this.checkTranscodeSpillFailure(readDoc, "spill-read-fail", null, () -> {
			throw new IOException("injected read failure");
		});

		// (c) Subsequent conversion works (no injection, same document, tiny budget; verify
		// both spill writes and decoded replay succeed).
		ContinuationStats.reset();
		this.transcode(readDoc, "spill-recovered", "0");
		assertTrue("後続変換でspillが正常動作していません", ContinuationStats.SPILLED_TEXT_RECORDS.get() > 0);
	}

	private void checkTranscodeSpillFailure(final File doc, final String name,
			final TextSpillTestHooks.IOAction beforeAppend, final TextSpillTestHooks.IOAction beforeRead)
			throws Exception {
		ContinuationStats.reset();
		final List<TextSpill> spills = Collections.synchronizedList(new ArrayList<>());
		LayoutSourceTestHooks.setAppendObserver(event -> {
			if (event instanceof LayoutSource.Chars chars
					&& chars.payload() instanceof LayoutSource.TextPayload.Spilled spilled
					&& !spills.contains(spilled.spill())) {
				spills.add(spilled.spill());
			}
		});
		// DirectSession does not retain the cause chain when wrapping unexpected failures in
		// TranscoderException(FATAL_UNEXPECTED), so verify the type through the SEVERE log's thrown field.
		final Throwable[] layoutFailure = new Throwable[1];
		final Logger sessionLog = Logger.getLogger(DirectSession.class.getName());
		final Handler capture = new Handler() {
			@Override
			public void publish(final LogRecord record) {
				if (record.getThrown() != null && layoutFailure[0] == null) {
					layoutFailure[0] = record.getThrown();
				}
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		sessionLog.addHandler(capture);
		TextSpillTestHooks.setFaultInjector(beforeAppend, beforeRead);
		try {
			this.transcode(doc, name, "0");
			fail(name + ": spill障害注入下の変換は失敗するはずです");
		} catch (final TranscoderException expected) {
			// DirectSession.transcode's catch Throwable converts this to FATAL_UNEXPECTED.
		} finally {
			TextSpillTestHooks.clearFaultInjector();
			LayoutSourceTestHooks.setAppendObserver(null);
			sessionLog.removeHandler(capture);
		}
		assertNotNull(name + ": レイアウト失敗がSEVEREログに現れていません", layoutFailure[0]);
		assertTrue(name + ": 失敗の型がTextSpillExceptionではありません: " + layoutFailure[0].getClass(),
				layoutFailure[0] instanceof TextSpillException);
		// No spill temporary files remain even on failure (formatter's finally →
		// LayoutSource.close() cleanup).
		assertFalse(name + ": spillストアが観測されていません", spills.isEmpty());
		for (final TextSpill spill : spills) {
			assertTrue(name + ": 失敗後にspill一時ファイルが残っています", spill.tempFilesDeletedForTest());
		}
	}

	// ------------------------------------------------------------------
	// 4. Header/footer progress conditions (always in CI).
	// ------------------------------------------------------------------

	/**
	 * Verify termination for an extreme fixture whose repeated header (+ footer) cannot fit on one page.
	 *
	 * <p>
	 * Findings on the existing protection (2026-07-24): {@code TableBox.splitPageAxis}
	 * attempts row splitting only if the cut line remains positive after subtracting header,
	 * footer, and frame. If they do not fit, it falls back to {@code TableCutter.keepOrMoveAll}:
	 * KEEP at the page start (FLAGS_FIRST), placing everything on this page and allowing overflow
	 * to force progress; otherwise MOVE (the next page is necessarily at its start, so KEEP commits).
	 * Thus there is at most one retry, and termination follows structurally without a dedicated
	 * progress counter. This test verifies that behavior (completion and finite page count)
	 * with a watchdog.
	 * </p>
	 */
	public void testRepeatedHeaderTallerThanPageDoesNotLoop() throws Exception {
		// (a) Extreme: 500 pt header > 400 pt page (footer is also oversized).
		final File extreme = generateTallHeaderTable("tall-header-extreme", 500, 450, 40);
		final int extremePages = this.transcodeWithWatchdogCountingPages(extreme, "tall-header-extreme", 120_000);
		assertTrue("極端fixtureでページが出力されていません", extremePages > 0);
		// KEEP at the page start commits overflow, so page count should remain small and finite,
		// independent of row count (1–2 pages because the entire table is unsplittable).
		assertTrue("極端fixtureのページ数が異常です(進捗せずヘッダだけ反復した疑い): " + extremePages,
				extremePages <= 5);

		// (b) Control: a 300 pt header + 10 pt rows on 400 pt pages repeats the header on each page
		// while body rows progress (verify that the normal repeated-header path still works).
		final File tall = generateTallHeaderTable("tall-header-progress", 300, -1, 40);
		final int tallPages = this.transcodeWithWatchdogCountingPages(tall, "tall-header-progress", 120_000);
		assertTrue("反復ヘッダの正常系が複数ページに進捗していません: " + tallPages, tallPages > 1);
		assertTrue("反復ヘッダ行数high-waterが観測されていません",
				TableBuildStats.RETAINED_REPEATED_HEADER_ROW_HIGH_WATER.get() >= 1);
		System.err.println("[E-6 endurance header] extremePages=" + extremePages + " progressPages=" + tallPages);
	}

	// ------------------------------------------------------------------
	// 5. Visual rescue splitting endurance (always in CI; 2026-07-25, increment 8).
	// ------------------------------------------------------------------

	/**
	 * Verify that repeated visual rescue splits at extreme scale produce
	 * (1) no crashes, infinite loops, or stalls, (2) finite, reasonable page counts,
	 * and (3) no unintended blank pages
	 * ({@link net.zamasoft.foliojet.layout.rescue.VisualRescuePlanner}).
	 *
	 * <p>
	 * The fixture <b>combines</b> all three rescue paths on 200×200 pt pages:
	 * a 20,000 pt float (replaced element), a 20,000 pt block with a mismatched
	 * writing direction, and a 3,000 pt oversized line. Combining them instead of testing
	 * them separately lets the float's exclusion area reduce the available body-text space,
	 * exercising the lower-bound checks for tiny fragment pages (slivers)
	 * ({@code MIN_RESCUE_SLICE}/{@code MIN_RESCUE_FRACTION}).
	 * </p>
	 *
	 * <p>
	 * Assert a range for page count, not an exact value. The exact count depends on interactions
	 * between multi-column layout and exclusion areas, making it more brittle than useful as a golden.
	 * Check only termination properties: the count does not grow in proportion to the number of lines,
	 * and processing does not fail to advance even one page (stall).
	 * </p>
	 */
	public void testRescueSplitEnduranceIsFiniteAndLeavesNoBlankPage() throws Exception {
		final int floatPt = 20_000, orthogonalPt = 20_000, linePt = 3_000;
		final File doc = generateRescueSplitStress("rescue-stress", floatPt, orthogonalPt, linePt);
		RescueStats.reset();
		final List<String> pages = this.transcodeWithWatchdogDumpingPages(doc, "rescue-stress", 300_000);
		final long slices = RescueStats.ENABLED_SLICES.get();
		System.err.println("[rescue endurance] pages=" + pages.size() + " candidates=" + RescueStats.CANDIDATES.get()
				+ " slices=" + RescueStats.SLICES.get() + " enabledSlices=" + slices);

		// (1) Reaching the return value confirms completion (the watchdog did not fail).
		// Rescue must actually activate: if the fixture silently falls back to the normal path,
		// this endurance test tests nothing.
		assertTrue("救済分割が一度も発火していません(fixtureが通常経路へ落ちた疑い)", slices > 0);

		// (2) Page count is finite and reasonable.
		// Lower bound: even the tallest unsplittable element (20,000 pt) needs 100 fragments
		// of 200 pt each. Fewer means content was lost
		// (processing stalled and was cut short).
		// Upper bound: even with no page shared by the three elements, total 43,000 pt/200 pt=215.
		// Cutting 1 pt at a time and endlessly generating pages would produce tens of thousands.
		// Measured (2026-07-25): 160 pages, 156 rescue fragments.
		final int tallestPages = Math.max(floatPt, orthogonalPt) / 200;
		final int disjointPages = (floatPt + orthogonalPt + linePt) / 200;
		assertTrue("ページ数が少なすぎます(停滞して内容を捨てた疑い): " + pages.size(), pages.size() >= tallestPages);
		assertTrue("ページ数が過大です(極小断片で切り刻んだ疑い): " + pages.size(), pages.size() <= disjointPages * 2);

		// (3) No unintended blank pages (every page has drawing commands).
		for (int i = 0; i < pages.size(); ++i) {
			assertTrue("ページ" + (i + 1) + "の表示リストが空です(意図しない白紙):\n" + pages.get(i),
					pages.get(i).contains("  x="));
		}
	}

	/**
	 * Endurance fixture combining the three rescue paths (200×200 pt pages).
	 *
	 * @param floatPt      height of the unsplittable float (replaced element)
	 * @param orthogonalPt height of the block whose writing direction differs from the trunk
	 * @param linePt       height of one line with an oversized font
	 */
	private static File generateRescueSplitStress(final String name, final int floatPt, final int orthogonalPt,
			final int linePt) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{margin:0;font:normal 10pt/1 serif}p{margin:0}"
					+ "img#f{float:left;width:60pt;height:" + floatPt + "pt}"
					+ "div#o{writing-mode:vertical-rl;width:100pt;height:" + orthogonalPt + "pt;background:#dddddd}"
					+ "p#huge{font:normal " + linePt + "pt/1 serif}</style>\n");
			w.write("</head><body>\n");
			// **Use an absolute URI** (2026-07-27). Previously, the relative path
			// from WORK_DIR (local/unittest/endurance) was hardcoded as `../../../`,
			// but **moving the fixture elsewhere silently removes the image,
			// producing a different document**. The same fragility in RandomDocumentFuzzTest
			// cost an hour: copying a reproducer to reduce it made it stop reproducing,
			// leading to the false conclusion that behavior depended on the input path.
			w.write("<img src=\"" + RED_PNG_URI + "\" id=\"f\" />\n");
			w.write("<div id=\"o\">O</div>\n");
			w.write("<p id=\"huge\">A</p>\n");
			w.write("<p id=\"after\">after</p>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/** Transcode with a watchdog (separate daemon thread + join timeout). Return the display-list page count. */
	private int transcodeWithWatchdogCountingPages(final File doc, final String name, final long timeoutMs)
			throws Exception {
		return this.transcodeWithWatchdogDumpingPages(doc, name, timeoutMs).size();
	}

	/** Transcode with a watchdog. Return display-list dumps for each page. */
	private List<String> transcodeWithWatchdogDumpingPages(final File doc, final String name, final long timeoutMs)
			throws Exception {
		final File dumpDir = new File(WORK_DIR, name + "-dump");
		deleteRecursively(dumpDir);
		dumpDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, dumpDir.getPath());
		final Throwable[] failure = new Throwable[1];
		try {
			final Thread worker = new Thread(() -> {
				try {
					this.transcode(doc, name, null);
				} catch (final Throwable t) {
					failure[0] = t;
				}
			}, "endurance-header-watchdog");
			worker.setDaemon(true);
			worker.start();
			worker.join(timeoutMs);
			if (worker.isAlive()) {
				fail(name + ": " + timeoutMs + "msで完了しません(無限ループの疑い——header/footer進捗保護の重大発見)");
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が失敗しました", failure[0]);
		}
		final File[] files = dumpDir.listFiles((d, n) -> n.endsWith(".txt"));
		if (files == null) {
			return Collections.emptyList();
		}
		java.util.Arrays.sort(files);
		final List<String> pages = new ArrayList<>(files.length);
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}

	// ------------------------------------------------------------------
	// 2. Completion in a separate JVM limited to -Xmx128m (perf gate).
	// ------------------------------------------------------------------

	/**
	 * Transcode (a) a huge single-cell auto table and (b) a short-cell auto table with many rows
	 * in a separate JVM with {@code -Xmx128m}, empirically characterizing the E-6 guarantees
	 * ({@code ./gradlew test --tests "*.EnduranceTest" -Dfoliojet.perf= }).
	 *
	 * <p>
	 * Assert only guaranteed lower bounds: (a) completion with 4 MB of cell body text
	 * (UTF-16 payload) via E-6, and (b) completion of 8,000 rows. Report larger reachable
	 * scales and retention during range replay as measurements (stderr).
	 * The upper bound for (b) is dominated by retention of the completed TableBox's entire row box tree
	 * (a known limitation; row-by-row parent commits belong to a future Incremental integration step),
	 * so do not assert it.
	 * </p>
	 */
	public void testConstrainedHeapEndurance() throws Exception {
		if (System.getProperty("foliojet.perf") == null) {
			System.out.println("EnduranceTest.testConstrainedHeapEndurance: skipped (-Dfoliojet.perf not set)");
			return;
		}
		WORK_DIR.mkdirs();
		final StringBuilder report = new StringBuilder("[E-6 endurance -Xmx128m]\n");
		try {
			// Run the two required cases before the diagnostic ladder, independently of heap overrides.
			final ChildResult requiredRows;
			final File rowsDoc = generateManyRowsTable("many-rows-required-8000", 8000);
			try {
				requiredRows = this.runChild(rowsDoc, "rows-required-8000-128m", null, 300_000, "128m");
			} finally {
				rowsDoc.delete();
			}
			report.append("  required rows=8000 heap=128m -> ").append(requiredRows).append('\n');
			final ChildResult requiredCell;
			final File cellDoc = generateBigCellTable("big-cell-required-4m", (4L << 20) / 2);
			try {
				requiredCell = this.runChild(cellDoc, "bigcell-required-4m-128m", null, 420_000, "128m");
			} finally {
				cellDoc.delete();
			}
			report.append("  required bigcell payload=4MB heap=128m -> ").append(requiredCell).append('\n');
			final boolean sameHeap = "128m".equals(System.getProperty("foliojet.enduranceHeap", "128m"));
			// ---- (a) Huge single-cell auto table (cell body payload bytes = chars×2) ----
			// Require completion with a 4 MB body, and measure the reachable scale with range replay.
			final long[] cellPayloadLadder = { 4L << 20, 5L << 20, 6L << 20, 8L << 20 };
			long maxCellPayload = -1;
			for (final long payload : cellPayloadLadder) {
				final ChildResult result;
				if (payload == (4L << 20) && sameHeap) {
					result = requiredCell;
				} else {
					final File doc = generateBigCellTable("big-cell-" + (payload >> 20) + "m", payload / 2);
					try {
						result = this.runChild(doc, "bigcell-new-" + (payload >> 20) + "m", null, 420_000);
					} finally {
						doc.delete();
					}
				}
				report.append("  bigcell new payload=").append(payload >> 20).append("MB -> ").append(result)
						.append('\n');
				if (!result.ok) {
					break;
				}
				maxCellPayload = payload;
			}
			// ---- (b) Short-cell auto table with many rows (measure every ladder step and record the largest completed row count) ----
			// Retaining the completed TableBox's entire row box tree also constrains the upper bound.
			final int[] rowsLadder = { 100_000, 50_000, 25_000, 12_000, 9_000, 8_000, 7_000, 6_000 };
			final int[] rowsLadderOverride = System.getProperty("foliojet.enduranceRows") == null ? rowsLadder
					: java.util.Arrays.stream(System.getProperty("foliojet.enduranceRows").split(","))
							.mapToInt(Integer::parseInt).toArray();
			int maxRowsNew = -1;
			for (final int rows : rowsLadderOverride) {
				final ChildResult result;
				if (rows == 8000 && sameHeap) {
					result = requiredRows;
				} else {
					final File doc = generateManyRowsTable("many-rows-" + rows, rows);
					try {
						result = this.runChild(doc, "rows-new-" + rows, null, 300_000);
					} finally {
						doc.delete();
					}
				}
				report.append("  rows new rows=").append(rows).append(" -> ").append(result).append('\n');
				if (result.ok) {
					maxRowsNew = Math.max(maxRowsNew, rows);
				}
			}
			report.append("  SUMMARY maxCellPayloadMB(new)=").append(maxCellPayload > 0 ? (maxCellPayload >> 20) : -1)
					.append(" maxRows(range)=").append(maxRowsNew)
					.append('\n');
			assertTrue("必須ケースが未完走: rows=" + requiredRows + " bigcell=" + requiredCell,
					requiredRows.ok && requiredCell.ok);
		} finally {
			System.err.print(report);
			if (System.getProperty("foliojet.enduranceKeepLogs") == null) deleteRecursively(new File(WORK_DIR, "pdf"));
		}
	}

	/**
	 * B-2c: Measure an actually emitting table with short rows and no frame, separately from existing comparison
	 * tables.
	 */
	public void testConstrainedHeapRowStreaming() throws Exception {
		if (System.getProperty("foliojet.perf") == null) return;
		final int[] rowsToMeasure = java.util.Arrays.stream(System.getProperty(
				"foliojet.enduranceRows", "8000,10000").split(",")).mapToInt(Integer::parseInt).toArray();
		final List<String> failures = new ArrayList<>();
		for (final int rows : rowsToMeasure) {
			final File doc = generateStreamingRowsTable("row-streaming-" + rows, rows);
			final ChildResult result;
			try {
				result = this.runChild(doc, "row-streaming-" + rows + "-128m", null, 300_000, "128m", true);
			} finally {
				doc.delete();
			}
			System.err.println("[B-2c endurance] rows=" + rows + " heap=128m -> " + result);
			// Completing 10,000 rows at 128m is the target. Treat OOM/timeout as measurements; fail on contract violations or other errors.
			if (!result.ok && (rows == 8000 || (!result.timedOut && !result.stats.equals("OutOfMemoryError")))) {
				failures.add("rows=" + rows + ": " + result);
			}
		}
		assertTrue("行送出の発火・保持上限または変換が失敗: " + failures, failures.isEmpty());
	}

	/** Result from a separate JVM run (ok means normal exit, i.e. completion). */
	private static final class ChildResult {
		final boolean ok;
		final boolean timedOut;
		final int exitCode;
		final long millis;
		final String stats;

		ChildResult(final boolean ok, final boolean timedOut, final int exitCode, final long millis,
				final String stats) {
			this.ok = ok;
			this.timedOut = timedOut;
			this.exitCode = exitCode;
			this.millis = millis;
			this.stats = stats;
		}

		@Override
		public String toString() {
			return (this.ok ? "OK" : this.timedOut ? "TIMEOUT" : "FAIL(exit=" + this.exitCode + ")") + " "
					+ this.millis + "ms" + (this.stats.isEmpty() ? "" : " " + this.stats);
		}
	}

	/**
	 * In a separate JVM ({@code -Xmx128m}) inheriting the current test JVM's classpath
	 * and settings, run {@link Child}.
	 */
	private ChildResult runChild(final File doc, final String name, final String budget,
			final long timeoutMs) throws Exception {
		return this.runChild(doc, name, budget, timeoutMs, System.getProperty("foliojet.enduranceHeap", "128m"));
	}

	private ChildResult runChild(final File doc, final String name, final String budget,
			final long timeoutMs, final String heap) throws Exception {
		return this.runChild(doc, name, budget, timeoutMs, heap, false);
	}

	private ChildResult runChild(final File doc, final String name, final String budget,
			final long timeoutMs, final String heap, final boolean streaming) throws Exception {
		final File pdfDir = new File(WORK_DIR, "pdf");
		pdfDir.mkdirs();
		final File pdf = new File(pdfDir, name + ".pdf");
		final File log = new File(pdfDir, name + ".log");
		final String javaExe = new File(new File(System.getProperty("java.home"), "bin"),
				System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").getPath();
		final List<String> command = new ArrayList<>();
		command.add(javaExe);
		command.add("-Xmx" + heap);
		// ExitOnOutOfMemoryError bypasses Child's catch/finally and erases the stack.
		// Preserve the cause in logs through normal OOM propagation (the parent's watchdog handles nontermination).
		command.add("-Djava.awt.headless=true");
		if (Boolean.getBoolean("foliojet.rowRetentionDiag")) command.add("-Dfoliojet.rowRetentionDiag=true");
		command.add("-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"));
		command.add("-Djp.cssj.driver.default=" + System.getProperty("jp.cssj.driver.default"));
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(Child.class.getName());
		command.add(doc.getPath());
		command.add(pdf.getPath());
		command.add(budget == null ? "-" : budget);
		command.add(Boolean.toString(streaming));
		final ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(new File(System.getProperty("user.dir")));
		pb.redirectErrorStream(true);
		pb.redirectOutput(log);
		final long t0 = System.nanoTime();
		final Process process = pb.start();
		final boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroyForcibly();
			process.waitFor(30, TimeUnit.SECONDS);
		}
		final long millis = (System.nanoTime() - t0) / 1_000_000;
		String stats = "";
		if (log.exists()) {
			for (final String line : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)) {
				if (line.startsWith("ENDURANCE-OK ")) {
					stats = line.substring("ENDURANCE-OK ".length());
				} else if (streaming && (line.startsWith("[B-2") || line.startsWith("[T5a"))) {
					// Also save the child JVM's histogram from the same point in the parent test log.
					System.err.println(line);
				} else if (line.contains("OutOfMemoryError")) {
					stats = "OutOfMemoryError";
				}
			}
		}
		final int exit = finished ? process.exitValue() : -1;
		pdf.delete();
		return new ChildResult(finished && exit == 0, !finished, exit, millis, stats);
	}

	/**
	 * Separate JVM entry point (arguments: document path, PDF output path, spill budget
	 * ({@code -} means the default 8 MB)). On completion, print {@code ENDURANCE-OK} plus major counters
	 * to stdout and exit 0; otherwise exit nonzero.
	 */
	public static final class Child {
		private static final RowRetentionReport ROW_RETENTION = new RowRetentionReport();

		public static void main(final String[] args) {
			final boolean streaming = args.length > 3 && Boolean.parseBoolean(args[3]);
			final boolean[] beforeFirstEmission = { false };
			final long stalledAlarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
			try (final AutoCloseable rows = streaming || Boolean.getBoolean("foliojet.rowRetentionDiag")
					? ROW_RETENTION.observe() : null;
					final AutoCloseable histogram = !streaming ? null : RangeOnlyInvariantTest.observe(
							net.zamasoft.foliojet.layout.builder.impl.RetainedTableBuilder.class, "retentionObserver",
							(java.util.function.BiConsumer<String, LayoutSource>) (stage, source) -> {
								if (stage.equals("before-row-emission") && !beforeFirstEmission[0]) {
									beforeFirstEmission[0] = true;
									RetentionHighWaterReportTest.reportStage("before-first-row-emission", source);
								} else if (stage.equals("before-pass-b") || stage.equals("after-pass-b")
										|| stage.equals("during-pass-c") || stage.matches("after-row-[0-9]+")
										|| stage.equals("after-table-end")) {
									final var live = RetentionHighWaterReportTest.reportStage(stage, source);
									if (stage.equals("after-table-end")) ROW_RETENTION.recordTableEndHistogram(live);
								}
							})) {
				final File doc = new File(args[0]);
				final File pdf = new File(args[1]);
				final String budget = args[2];
				try (OutputStream out = new FileOutputStream(pdf)) {
					final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
					try {
						session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
						session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
						session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
						session.property("input.include", "**");
						session.property("input.property-pi", "true");
						session.property("processing.fail-on-fatal-error", "true");
						if (streaming) session.property("processing.table-row-emission", "true");
						if (!"-".equals(budget)) {
							session.property("processing.text-spill-budget", budget);
						}
						CTISessionHelper.transcodeFile(session, doc, "text/html", null);
					} finally {
						session.close();
					}
				}
				if (streaming) {
					final int pageRows = (int) Math.floor((STREAMING_PAGE_HEIGHT + 0.5) / STREAMING_ROW_HEIGHT);
					ROW_RETENTION.assertStreamingBound(pageRows, 1, 1, 3);
					ROW_RETENTION.assertLiveTableBound(pageRows + 1 + 1, 3);
					assertEquals("行消費中に停滞を誤検出", stalledAlarms, ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
				}
				if (streaming) System.err.println("[B-2c row retention summary] " + ROW_RETENTION);
				System.out.println("ENDURANCE-OK " + retentionStats());
				System.exit(0);
			} catch (final Throwable t) {
				t.printStackTrace();
				if (streaming) System.err.println("[B-2c row retention summary] " + ROW_RETENTION);
				System.err.println("ENDURANCE-FAIL " + retentionStats());
				System.exit(3);
			}
		}

		private static String retentionStats() {
			return "maxMemMB=" + (Runtime.getRuntime().maxMemory() >> 20)
						+ " spilledBytes=" + ContinuationStats.SPILLED_TEXT_BYTES.get() + " spilledRecords="
						+ ContinuationStats.SPILLED_TEXT_RECORDS.get() + " livePayloadHW="
						+ ContinuationStats.LIVE_TEXT_PAYLOAD_BYTES.get() + " sourceEventHW="
						+ ContinuationStats.SOURCE_EVENT_HIGH_WATER.get() + " retainedRowHW="
						+ TableBuildStats.RETAINED_ROW_HIGH_WATER.get() + " sourceLeaseHW="
						+ TableBuildStats.SOURCE_LEASE_HIGH_WATER.get() + " retainedEventHW="
						+ TableBuildStats.SOURCE_RETAINED_EVENT_HIGH_WATER.get() + " cellRangeSeals="
						+ ContinuationStats.CELL_RANGE_SEALS.get() + " passCTables="
						+ ContinuationStats.TABLE_PASS_C_TABLES.get() + " legacyBindRows="
						+ ContinuationStats.TABLE_LEGACY_BINDROWS.get() + " oldestWatermark="
						+ TableBuildStats.SOURCE_OLDEST_WATERMARK_AT_HIGH_WATER.get() + " watermarkLagHW="
						+ TableBuildStats.SOURCE_OLDEST_WATERMARK_LAG_HIGH_WATER.get()
						+ (Boolean.getBoolean("foliojet.rowRetentionDiag") ? " " + ROW_RETENTION : "");
		}
	}

	// ------------------------------------------------------------------
	// Fixture generation and shared transcode helpers.
	// ------------------------------------------------------------------

	private static final String[] SENTENCES = { //
			"The quick brown fox jumps over the lazy dog while the sun sets slowly behind distant mountains. ", //
			"Typography is the art and technique of arranging type to make written language legible and readable. ", //
			"Paragraph composition with total-fit line breaking minimizes the sum of squared badness over lines. ", //
			"Endurance testing characterizes the achieved guarantees of the spillable tape infrastructure. ", //
	};

	/** Prose document with identical CSS (A4-equivalent pages; only paragraph count varies). */
	private static File generateProse(final String name, final int paragraphs) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"595pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"842pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:24pt}body{font:normal 8pt/1.3 serif}p{margin:0 0 4pt 0}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < paragraphs; ++i) {
				w.write("<p>");
				for (int j = 0; j < 4; ++j) {
					w.write(SENTENCES[(i + j) % SENTENCES.length]);
				}
				w.write("</p>\n");
			}
			w.write("</body></html>\n");
		}
		return file;
	}

	/**
	 * Single-cell auto table (cell body is a sequence of paragraphs totaling about {@code totalChars} characters).
	 */
	private static File generateBigCellTable(final String name, final long totalChars) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"595pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"842pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:24pt}body{font:normal 8pt/1.3 serif}td{border:1pt solid black}"
					+ "p{margin:0 0 4pt 0}</style>\n");
			w.write("</head><body><table><tbody><tr><td>\n");
			long written = 0;
			int i = 0;
			while (written < totalChars) {
				w.write("<p>");
				for (int j = 0; j < 4; ++j) {
					final String s = SENTENCES[(i + j) % SENTENCES.length];
					w.write(s);
					written += s.length();
				}
				w.write("</p>\n");
				++i;
			}
			w.write("</td></tr></tbody></table></body></html>\n");
		}
		return file;
	}

	/** Auto table with short cells ({@code r{i}c{j}}) × 3 columns (with one thead row). */
	static File generateManyRowsTable(final String name, final int rows) throws IOException {
		return generateManyRowsTable(name, rows, false);
	}

	static File generateStreamingRowsTable(final String name, final int rows) throws IOException {
		return generateManyRowsTable(name, rows, true);
	}

	private static File generateManyRowsTable(final String name, final int rows, final boolean streaming) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"595pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"842pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			if (streaming) {
				w.write("<style>@page{margin:24pt}body{margin:0;font:6pt/1 serif}"
						+ "table{width:400pt;border-collapse:separate;border-spacing:0;margin:0;border:0}"
						+ "td,th{padding:0;border:0}td{height:" + STREAMING_ROW_HEIGHT + "pt}</style>\n");
			} else {
				w.write("<style>@page{margin:24pt}body{font:normal 8pt/1 serif}td,th{border:1pt solid black}</style>\n");
			}
			w.write("</head><body><table>\n");
			w.write("<thead><tr><th>h0</th><th>h1</th><th>h2</th></tr></thead>\n");
			w.write("<tbody>\n");
			for (int i = 0; i < rows; ++i) {
				w.write("<tr><td>r" + i + "c0</td><td>r" + i + "c1</td><td>r" + i + "c2</td></tr>\n");
			}
			w.write("</tbody></table></body></html>\n");
		}
		return file;
	}

	/**
	 * Auto table whose repeated header (and optional footer) rows have height {@code headerPt}
	 * (400×400 pt pages). A negative {@code footerPt} means no footer.
	 */
	private static File generateTallHeaderTable(final String name, final int headerPt, final int footerPt,
			final int bodyRows) throws IOException {
		WORK_DIR.mkdirs();
		final File file = new File(WORK_DIR, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"400pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}td,th{border:1pt solid black}</style>\n");
			w.write("</head><body><table>\n");
			w.write("<thead><tr><th><div style=\"height:" + headerPt + "pt\">H</div></th></tr></thead>\n");
			if (footerPt >= 0) {
				w.write("<tfoot><tr><td><div style=\"height:" + footerPt + "pt\">F</div></td></tr></tfoot>\n");
			}
			w.write("<tbody>\n");
			for (int i = 0; i < bodyRows; ++i) {
				w.write("<tr><td>row" + i + "</td></tr>\n");
			}
			w.write("</tbody></table></body></html>\n");
		}
		return file;
	}

	private void transcode(final File source, final String name, final String budget) throws Exception {
		final File pdf = new File(WORK_DIR, name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (budget != null) {
					session.property("processing.text-spill-budget", budget);
				}
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static void deleteRecursively(final File file) {
		final File[] children = file.listFiles();
		if (children != null) {
			for (final File child : children) {
				deleteRecursively(child);
			}
		}
		file.delete();
	}
}
