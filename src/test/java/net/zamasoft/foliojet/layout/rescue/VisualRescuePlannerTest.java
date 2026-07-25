package net.zamasoft.foliojet.layout.rescue;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 救済分割(visual rescue split)の判定と<b>前進保証</b>の単体テストです
 * (2026-07-25新設、増分1)。
 *
 * <p>
 * この機能の絶対要件は「クラッシュ・無限ループ・リークの不在」であり、
 * そのうち無限ループの不在は再試行カウンタではなく
 * {@link VisualRescuePlanner}の純判定の性質だけで保証されます。ここでは
 * その性質を直接固定します:
 * </p>
 *
 * <ul>
 * <li>tailを作る断片は必ず{@link VisualRescuePlanner#MIN_RESCUE_ADVANCE}
 * 以上を消費する。</li>
 * <li>NaN・Infinity・容量0・容量1pt未満・負値・極大doubleの丸めで
 * {@code offset}が厳密増加しない場合は、必ず「救済しない」を返す。</li>
 * <li>判定を繰り返すと{@code offset}は厳密増加し、有限回で終わる。</li>
 * </ul>
 */
public class VisualRescuePlannerTest extends TestCase {

	private static RescueDecision.Reason reasonOf(final RescueDecision decision) {
		assertTrue("救済しない判定のはず: " + decision, decision instanceof RescueDecision.None);
		return ((RescueDecision.None) decision).reason();
	}

	private static RescueDecision.Slice sliceOf(final RescueDecision decision) {
		assertTrue("断片を作る判定のはず: " + decision, decision instanceof RescueDecision.Slice);
		return (RescueDecision.Slice) decision;
	}

	// ------------------------------------------------------------------
	// 定数
	// ------------------------------------------------------------------

	/** 前進の下限は1pt相当(= 2 * LayoutUtils.THRESHOLD)。 */
	public void testMinRescueAdvanceIsTwiceThreshold() {
		assertEquals(2 * LayoutUtils.THRESHOLD, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 0);
		assertEquals(1.0, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 0);
	}

	// ------------------------------------------------------------------
	// 基本の分割
	// ------------------------------------------------------------------

	/** 先頭で収まらないなら容量いっぱいを切り、tailが続く。 */
	public void testHeadSliceTakesAvailableAndLeavesTail() {
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.plan(true, 100, 250, 0));
		assertEquals(0.0, slice.offset(), 0);
		assertEquals(100.0, slice.sliceExtent(), 0);
		assertEquals(100.0, slice.nextOffset(), 0);
		assertTrue(slice.firstFragment());
		assertFalse(slice.lastFragment());
		assertTrue(slice.hasTail());
		assertFalse(slice.isContinuation());
	}

	/** 残余が容量に収まったら最終断片になり、tailを作らない。 */
	public void testFinalSliceTakesTheRemainder() {
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.plan(true, 100, 250, 200));
		assertEquals(200.0, slice.offset(), 0);
		assertEquals(50.0, slice.sliceExtent(), 0);
		assertEquals(250.0, slice.nextOffset(), 0);
		assertFalse(slice.firstFragment());
		assertTrue(slice.lastFragment());
		assertFalse(slice.hasTail());
		assertTrue(slice.isContinuation());
	}

	/** 先頭でそもそも収まるなら救済しない(通常経路)。 */
	public void testFittingContentIsNotRescued() {
		assertEquals(RescueDecision.Reason.FITS, reasonOf(VisualRescuePlanner.plan(true, 300, 250, 0)));
		// 閾値ぴったりも「収まる」側
		assertEquals(RescueDecision.Reason.FITS, reasonOf(VisualRescuePlanner.plan(true, 250, 250, 0)));
	}

	/** フラグメント先頭でなければ救済しない(まだ次へ送る手段が残っている)。 */
	public void testNotAtFragmentStartIsNotRescued() {
		assertEquals(RescueDecision.Reason.NOT_FIRST, reasonOf(VisualRescuePlanner.plan(false, 100, 250, 0)));
		assertEquals(RescueDecision.Reason.NOT_FIRST, reasonOf(VisualRescuePlanner.plan(false, 100, 250, 100)));
	}

	// ------------------------------------------------------------------
	// 絶対配置の除外
	// ------------------------------------------------------------------

	/** 絶対配置は明示的にrejectする(合意仕様。意図的なはみ出しを壊さない)。 */
	public void testAbsolutePositioningIsRejected() {
		assertFalse(VisualRescuePlanner.isRescuablePos(PosType.ABSOLUTE));
		assertEquals(RescueDecision.Reason.ABSOLUTE,
				reasonOf(VisualRescuePlanner.plan(PosType.ABSOLUTE, true, 100, 250, 0)));
	}

	/** 絶対配置以外はすべて対象になり得る。 */
	public void testOtherPositioningIsRescuable() {
		for (final PosType posType : PosType.values()) {
			if (posType == PosType.ABSOLUTE) {
				continue;
			}
			assertTrue(posType.name(), VisualRescuePlanner.isRescuablePos(posType));
			assertTrue(posType.name(),
					VisualRescuePlanner.plan(posType, true, 100, 250, 0) instanceof RescueDecision.Slice);
		}
	}

	/** {@code null}(配置不明)は除外しない。 */
	public void testNullPosTypeIsRescuable() {
		assertTrue(VisualRescuePlanner.isRescuablePos(null));
	}

	// ------------------------------------------------------------------
	// 前進保証: 病的な入力ではすべて「救済しない」
	// ------------------------------------------------------------------

	/** NaNが混じったら救済しない。 */
	public void testNaNIsNotRescued() {
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, Double.NaN, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, Double.NaN, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, 250, Double.NaN)));
	}

	/** Infinityが混じったら救済しない。 */
	public void testInfinityIsNotRescued() {
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, Double.POSITIVE_INFINITY, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, Double.NEGATIVE_INFINITY, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, Double.POSITIVE_INFINITY, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, 250, Double.POSITIVE_INFINITY)));
	}

	/** 未確定({@code LayoutUtils.NONE})は数値としては有限でも扱わない。 */
	public void testUndefinedMagicValueIsNotRescued() {
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, LayoutUtils.NONE, 250, 0)));
		assertEquals(RescueDecision.Reason.UNDEFINED_GEOMETRY,
				reasonOf(VisualRescuePlanner.plan(true, 100, LayoutUtils.NONE, 0)));
	}

	/** 容量0では救済しない(1pt進めないので前進保証が満たせない)。 */
	public void testZeroCapacityIsNotRescued() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, 0, 250, 0)));
	}

	/** 容量が1pt未満では救済しない(外側のfragmentainerへ委譲する)。 */
	public void testCapacityBelowMinimumAdvanceIsNotRescued() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, 0.9, 250, 0)));
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, VisualRescuePlanner.MIN_RESCUE_ADVANCE - 1e-9, 250, 0)));
		// ちょうど下限なら救済する
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.plan(true, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 250, 0));
		assertEquals(VisualRescuePlanner.MIN_RESCUE_ADVANCE, slice.sliceExtent(), 0);
	}

	/** 負の容量では救済しない。 */
	public void testNegativeCapacityIsNotRescued() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.plan(true, -100, 250, 0)));
	}

	/** 負のoffset・非正の元寸法では救済しない。 */
	public void testInvalidGeometryIsNotRescued() {
		assertEquals(RescueDecision.Reason.INVALID_GEOMETRY, reasonOf(VisualRescuePlanner.plan(true, 100, 250, -1)));
		assertEquals(RescueDecision.Reason.INVALID_GEOMETRY, reasonOf(VisualRescuePlanner.plan(true, 100, 0, 0)));
		assertEquals(RescueDecision.Reason.INVALID_GEOMETRY, reasonOf(VisualRescuePlanner.plan(true, 100, -250, 0)));
	}

	/** 消費し切っていたら救済しない。 */
	public void testExhaustedSourceIsNotRescued() {
		assertEquals(RescueDecision.Reason.EXHAUSTED, reasonOf(VisualRescuePlanner.plan(true, 100, 250, 250)));
		assertEquals(RescueDecision.Reason.EXHAUSTED, reasonOf(VisualRescuePlanner.plan(true, 100, 250, 300)));
	}

	/**
	 * 極大doubleでは{@code offset + chunk == offset}になり得る。丸めで
	 * 前進しない場合は必ず救済しない。
	 */
	public void testHugeDoubleThatCannotAdvanceIsNotRescued() {
		// 1e300 に 100 を足しても値は変わらない(倍精度の刻み幅が桁違い)
		final double offset = 1e300;
		assertEquals(offset, offset + 100.0, 0);
		final double sourcePageExtent = 1e301;
		assertEquals(RescueDecision.Reason.NO_PROGRESS,
				reasonOf(VisualRescuePlanner.plan(true, 100, sourcePageExtent, offset)));
	}

	/**
	 * 極大doubleの丸めで停滞する組み合わせは、どれも救済しない
	 * (どの理由で止まるかは問わず、断片を作らないことが要件)。
	 */
	public void testHugeDoubleCombinationsNeverProduceAStalledSlice() {
		final double[] offsets = { 1e290, 1e300, Double.MAX_VALUE / 4, Double.MAX_VALUE / 2 };
		final double[] availables = { 1, 100, 1e6, 1e200 };
		for (final double offset : offsets) {
			for (final double available : availables) {
				for (final double extent : new double[] { offset * 2, offset * 10, Double.MAX_VALUE / 2 }) {
					final RescueDecision decision = VisualRescuePlanner.plan(true, available, extent, offset);
					if (decision instanceof RescueDecision.Slice slice) {
						assertTrue("offset=" + offset + " available=" + available + " extent=" + extent,
								slice.nextOffset() > slice.offset());
					}
				}
			}
		}
	}

	/**
	 * 「残余をそのまま消費する最終断片」でも、丸めで前進しないなら
	 * 断片を作らない(値型側の不変条件と同じ守り)。
	 */
	public void testFinalSliceGuardRejectsNonAdvancingInterval() {
		// offset に remaining を足しても値が変わらない区間は Slice にできない
		final double offset = 1e300;
		try {
			new RescueDecision.Slice(offset, 100, offset + 100, false, true);
			fail("前進しない最終断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
	}

	/** 断片を作ったなら、必ず{@code offset}が厳密増加している。 */
	public void testEverySliceStrictlyAdvancesTheOffset() {
		final double[] availables = { 1, 1.5, 7, 100, 1e6, 1e300 };
		final double[] extents = { 1.0001, 2, 250, 1e7, 1e301 };
		final double[] offsets = { 0, 0.5, 1, 123, 1e6, 1e299 };
		for (final double available : availables) {
			for (final double extent : extents) {
				for (final double offset : offsets) {
					final RescueDecision decision = VisualRescuePlanner.plan(true, available, extent, offset);
					if (decision instanceof RescueDecision.Slice slice) {
						final String at = "available=" + available + " extent=" + extent + " offset=" + offset;
						assertTrue(at, slice.nextOffset() > slice.offset());
						assertEquals(at, offset, slice.offset(), 0);
						assertTrue(at, slice.sliceExtent() > 0);
						if (slice.hasTail()) {
							// tailを作る断片は必ず1pt以上を消費する
							assertTrue(at, slice.sliceExtent() >= VisualRescuePlanner.MIN_RESCUE_ADVANCE);
						}
						// 元ボックスをはみ出さない
						assertTrue(at, LayoutUtils.compare(slice.nextOffset(), extent) <= 0);
					}
				}
			}
		}
	}

	/**
	 * 判定を繰り返すループが有限回で必ず終わること(構造的な無限ループ
	 * 不在の直接確認)。
	 */
	public void testRepeatedPlanningTerminates() {
		final double sourcePageExtent = 10000;
		final double available = 1;
		double offset = 0;
		int steps = 0;
		while (true) {
			final RescueDecision decision = VisualRescuePlanner.plan(true, available, sourcePageExtent, offset);
			if (!(decision instanceof RescueDecision.Slice slice)) {
				break;
			}
			assertTrue("前進しなければ停止しない", slice.nextOffset() > offset);
			offset = slice.nextOffset();
			++steps;
			assertTrue("ページ数の上限を大きく超えた: " + steps, steps <= 20000);
			if (slice.lastFragment()) {
				break;
			}
		}
		assertEquals(sourcePageExtent, offset, 0);
		assertEquals((int) (sourcePageExtent / available), steps);
	}

	/** 容量が足りないページに当たっても、判定は必ず停止する(救済を始めない)。 */
	public void testTinyCapacityTerminatesImmediately() {
		double offset = 0;
		int steps = 0;
		while (VisualRescuePlanner.plan(true, 0.4, 250, offset) instanceof RescueDecision.Slice slice) {
			offset = slice.nextOffset();
			++steps;
			assertTrue(steps < 10);
		}
		assertEquals(0, steps);
	}

	// ------------------------------------------------------------------
	// 値型の不変条件
	// ------------------------------------------------------------------

	/** 前進しないSliceは構築できない。 */
	public void testSliceRejectsNonAdvancingInterval() {
		try {
			new RescueDecision.Slice(10, 5, 10, false, false);
			fail("前進しないSliceは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
	}

	/** 非正の断片は構築できない。 */
	public void testSliceRejectsNonPositiveExtent() {
		try {
			new RescueDecision.Slice(0, 0, 1, true, false);
			fail("寸法0のSliceは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
	}

	/** {@code firstFragment}は{@code offset == 0}と一致していなければならない。 */
	public void testSliceRejectsInconsistentFirstFlag() {
		try {
			new RescueDecision.Slice(10, 5, 15, true, false);
			fail("offset>0でfirstFragmentは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
	}
}
