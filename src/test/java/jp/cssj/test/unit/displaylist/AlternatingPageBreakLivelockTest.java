package jp.cssj.test.unit.displaylist;

import java.io.File;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/**
 * Regression test ensuring automatic page breaks do not recurse forever by alternating between two states
 * (seed 44749).
 */
public class AlternatingPageBreakLivelockTest extends TestCase {
	public AlternatingPageBreakLivelockTest(final String name) {
		super(name);
	}

	public void testStrictSeed44749Terminates() throws Exception {
		final long alarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		final RandomDocumentFuzzTest.Generated generated = RandomDocumentFuzzTest.generate(44749, true, true); // Pin v1 (v1 expectations).
		final File outDir = new File("build/fuzz-regressions/alternating-page-break-livelock-dl");
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/alternating-page-break-livelock.html"), outDir, true,
				"alternating-page-break-livelock-regression");
		// 2026-09-19: avoid rollback no longer occurs if it would leave no body content on the page
		// (FlowContainer.hasInFlowContentBefore), eliminating this seed's period-2 cycle itself
		// (20 pages, 8 blank → 24 pages, 4 blank; no missing or duplicated words). As in TallBlockPaginationTest on
		// 2026-08-23, verify that the guard does not fire.
		assertEquals("the period-2 page-break guard fired (the cycle was resolved on 2026-09-19)", alarms,
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
	}
}
