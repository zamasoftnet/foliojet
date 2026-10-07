package jp.cssj.test.unit._0370_PAGE_CONTENT;

import junit.framework.TestCase;

/**
 * Verifies that retained size after finalization does not scale with document length after many short
 * running updates.
 */
public final class RunningRetentionTest extends TestCase {
	public void testTwentyThousandAssignments() throws Exception {
		final StringBuilder body = new StringBuilder();
		for (int page = 0; page < 20; ++page) {
			body.append("<section class='page'>");
			for (int i = 0; i < 1_000; ++i) {
				body.append("<span class='running'>H</span>");
			}
			body.append("<p>BODY ").append(page).append("</p></section>");
		}
		final String common = ".page+.page{break-before:page}.running{font-size:1pt;line-height:1pt}";
		// Control: normal layout with the same length and DOM. Warm up both paths with small inputs first.
		RunningCaptureTest.convert(".running{position:running(h)}", "<span class='running'>H</span><p>BODY</p>",
				false, false, false);
		RunningCaptureTest.convert(".running{font-size:1pt}", "<span class='running'>H</span><p>BODY</p>",
				false, false, false);
		final var without = RunningCaptureTest.convert(common, body.toString(),
				false, false, false);
		final var with = RunningCaptureTest.convert(common + ".running{position:running(h)}", body.toString(),
				false, false, false);
		final var registry = with.context().getRunningRegistry();
		final double ratio = (double) with.nanos() / without.nanos();
		System.err.println("[running R1b] 20000 assignments: running=" + with.nanos() / 1_000_000
				+ "ms, without=" + without.nanos() / 1_000_000 + "ms, ratio=" + ratio
				+ ", candidates=" + registry.retainedCandidateCount("h") + ", pending=" + registry.pendingCount());
		assertEquals(20_000L, registry.assignedCount());
		assertTrue(registry.retainedCandidateCount("h") > 0);
		assertTrue(registry.retainedCandidateCount("h") <= 3);
		assertEquals(0, registry.pendingCount());
		assertTrue("変換時間の緩い上限(目標2倍、assert3倍): " + ratio, ratio <= 3);
	}
}
