package jp.cssj.test.unit.fragment;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.FlowCutter;

/**
 * ブロック間の改ページ禁止(avoid)押し戻しのテストです。
 *
 * <p>
 * 特に「切断線を跨ぐ浮動体と keep の相互作用」を固定します:
 * 切断可能な crossing float は keep 後退を解除し(自身は独立に分割される)、
 * 切断不能(置換要素または page-break-inside:avoid)な crossing float は
 * keep 後退を妨げない。この分岐は統合フィクスチャ
 * (float-split-in-chain / float-uncut-before-prefix)の対と対応します。
 * </p>
 */
public class FlowCutterTest extends TestCase {
	// フロー2つ: [0]=0..60(filler)、[1]=60..80(avoid-before で接続)。
	// 切断線 100 に対し押し戻しが成立すれば [1] の前(60 付近)へ戻る
	private static final double[] FLOW_STARTS = { 0, 60 };
	private static final double[] FLOW_EXTENTS = { 60, 20 };
	private static final boolean[] AVOID_BEFORE = { false, true };
	private static final boolean[] AVOID_AFTER = { false, false };
	private static final double[] FLOW_END_FRAMES = { 0, 0 };

	private static FlowCutter.AvoidPushback pushback(final double[] floatStarts, final double[] floatExtents,
			final boolean[] floatUncut) {
		return FlowCutter.avoidPushback(1, 100, FLOW_STARTS, FLOW_EXTENTS, AVOID_BEFORE, AVOID_AFTER, FLOW_END_FRAMES,
				floatStarts, floatExtents, floatUncut);
	}

	public void testAvoidPushbackWithoutFloats() {
		// フロートがなければ avoid 連鎖どおり押し戻される
		final FlowCutter.AvoidPushback p = pushback(null, null, null);
		assertNotNull(p);
		assertEquals(60, p.newPageLimit(), 1);
	}

	public void testCuttableCrossingFloatCancelsAvoidPushback() {
		// 切断線(100)を跨ぐ切断可能な float(60..107)は keep 後退を解除する
		// (float 自身は splitFloatings が独立に分割する)
		assertNull(pushback(new double[] { 60 }, new double[] { 47 }, new boolean[] { false }));
	}

	public void testUncutCrossingFloatPreservesAvoidPushback() {
		// 切断不能(置換要素 / page-break-inside:avoid)な crossing float は
		// keep 後退を妨げない
		final FlowCutter.AvoidPushback p = pushback(new double[] { 60 }, new double[] { 47 },
				new boolean[] { true });
		assertNotNull(p);
		assertEquals(60, p.newPageLimit(), 1);
	}

	public void testFloatBeyondCutLineCancelsAvoidPushback() {
		// 始端が切断線以後の float も keep を解除する(現行規則の忠実な固定)
		assertNull(pushback(new double[] { 100 }, new double[] { 10 }, new boolean[] { false }));
	}

	public void testFloatBeforeCutLineIsIrrelevant() {
		// 終端が切断線以前の float は無関係
		final FlowCutter.AvoidPushback p = pushback(new double[] { 0 }, new double[] { 40 },
				new boolean[] { false });
		assertNotNull(p);
	}

	// ---- stepFlags(自動改ページ主ループのフラグ計算、二相分離・増分1) ----

	private static final byte FIRST = net.zamasoft.foliojet.layout.box.IPageBreakableBox.FLAGS_FIRST;
	private static final byte LAST = net.zamasoft.foliojet.layout.box.IPageBreakableBox.FLAGS_LAST;
	private static final byte SPLIT = net.zamasoft.foliojet.layout.box.IPageBreakableBox.FLAGS_SPLIT;

	public void testStepFlagsHeadFlowKeepsFirst() {
		// 始端に接するフロー(pageAxis=0): FIRSTは落とさない
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 0, 0, 3, false, (byte) (FIRST | LAST | SPLIT));
		assertEquals(100.0, s.splitLine(), 0);
		assertTrue((s.positionMask() & FIRST) != 0);
		// 非末尾なのでLASTは落ちる
		assertTrue((s.positionMask() & LAST) == 0);
		assertEquals((byte) (FIRST | SPLIT), s.splitFlags());
	}

	public void testStepFlagsDetachedFlowDropsFirst() {
		// 始端から離れたフロー(pageAxis>0): FIRSTを落とし、splitLineはローカル化
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 40, 1, 3, false, (byte) (FIRST | SPLIT));
		assertEquals(60.0, s.splitLine(), 0);
		assertTrue((s.positionMask() & FIRST) == 0);
		assertEquals(SPLIT, s.splitFlags());
	}

	public void testStepFlagsTailFlowOfForeignBreakKeepsLast() {
		// 末尾フローかつ自動改ページの対象が自分でない: LASTを保持
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 40, 2, 3, false, (byte) (FIRST | LAST));
		assertTrue((s.positionMask() & LAST) != 0);
		assertEquals(LAST, s.splitFlags());
	}

	public void testStepFlagsOwnerTargetDropsLastEvenAtTail() {
		// 自動改ページの対象がこのコンテナ自身なら末尾でもLASTを落とす
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 0, 2, 3, true, (byte) (FIRST | LAST));
		assertTrue((s.positionMask() & LAST) == 0);
		assertEquals(FIRST, s.splitFlags());
	}

	public void testStepFlagsPassesThroughOtherBits() {
		// positionMaskは0xFF起点のため、FIRST/LAST以外のビット(SPLIT等)は
		// 常に外側flagsのまま透過する
		final byte others = (byte) (0xFF & ~(FIRST | LAST));
		final FlowCutter.StepFlags s = FlowCutter.stepFlags(100, 40, 1, 3, false, others);
		assertEquals(others, s.splitFlags());
	}
}
