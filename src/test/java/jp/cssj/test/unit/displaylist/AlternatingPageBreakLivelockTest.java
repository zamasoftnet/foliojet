package jp.cssj.test.unit.displaylist;

import java.io.File;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/** 自動改ページが2状態を交互に反復して再帰し続けないことの回帰(seed 44749)。 */
public class AlternatingPageBreakLivelockTest extends TestCase {
	public AlternatingPageBreakLivelockTest(final String name) {
		super(name);
	}

	public void testStrictSeed44749Terminates() throws Exception {
		final long alarms = ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get();
		final RandomDocumentFuzzTest.Generated generated = RandomDocumentFuzzTest.generate(44749, true);
		final File outDir = new File("build/fuzz-regressions/alternating-page-break-livelock-dl");
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/alternating-page-break-livelock.html"), outDir, true,
				"alternating-page-break-livelock-regression");
		assertTrue("the period-2 page-break cycle was not detected",
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get() > alarms);
	}
}
