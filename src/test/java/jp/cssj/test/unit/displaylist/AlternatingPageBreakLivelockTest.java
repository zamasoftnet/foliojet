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
		final RandomDocumentFuzzTest.Generated generated = RandomDocumentFuzzTest.generate(44749, true, true); // v1固定(期待挙動はv1文書のもの)
		final File outDir = new File("build/fuzz-regressions/alternating-page-break-livelock-dl");
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/alternating-page-break-livelock.html"), outDir, true,
				"alternating-page-break-livelock-regression");
		// 2026-09-19: avoid の押し戻しが「ページに本文を残さない」ときは押し戻さなくなり
		// (FlowContainer.hasInFlowContentBefore)、この seed の周期 2 の循環そのものが起きなくなった
		// (20 ページ・白紙 8 → 24 ページ・白紙 4、語の欠落・重複なし)。TallBlockPaginationTest の
		// 2026-08-23 と同じく、ガードが発火しないことを固定する
		assertEquals("the period-2 page-break guard fired (the cycle was resolved on 2026-09-19)", alarms,
				ContinuationStats.STALLED_AUTO_BREAK_ALARMS.get());
	}
}
