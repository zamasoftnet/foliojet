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
	// 極小断片(実質白紙ページ)を作らない — 2026-07-25、増分4で追加
	// ------------------------------------------------------------------

	/**
	 * 実用上の下限は絶対値20pt(={@code BreakableBuilder.MIN_PAGE_LIMIT}、
	 * エンジン自身が「縮退」とみなす唯一既存の閾値)と、フラグメンテナ
	 * 容量の1/4の大きい方。
	 */
	public void testMinUsefulSliceCombinesAbsoluteAndProportionalFloors() {
		assertEquals(20.0, VisualRescuePlanner.MIN_RESCUE_SLICE, 0);
		assertEquals(0.25, VisualRescuePlanner.MIN_RESCUE_FRACTION, 0);
		// 小さいページでは絶対値が効く
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(40), 0);
		// 大きいページでは割合が効く
		assertEquals(200.0, VisualRescuePlanner.minUsefulSlice(800), 0);
		// 容量が不明・非正なら絶対値だけ
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(0), 0);
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(Double.NaN), 0);
		assertEquals(20.0, VisualRescuePlanner.minUsefulSlice(LayoutUtils.NONE), 0);
	}

	/**
	 * 利用可能量が極端に小さいなら救済を<b>始めない</b>(数ptの断片ページが
	 * 連続する=実質白紙ページの量産を防ぐ)。前進保証だけなら通る値でも
	 * 拒否されることを固定する。
	 */
	public void testSliverCapacityDoesNotStartARescue() {
		// 前進保証(1pt)は満たすが、実用下限(20pt)に届かない
		assertTrue(VisualRescuePlanner.plan(true, 10, 5000, 0) instanceof RescueDecision.Slice);
		assertEquals(RescueDecision.Reason.SLIVER_CAPACITY,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 800, 10, 5000, 0)));
		// 絶対下限は満たすが、容量800ptの1/4(200pt)に届かない
		assertEquals(RescueDecision.Reason.SLIVER_CAPACITY,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 800, 100, 5000, 0)));
		// 割合を満たせば救済する
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 800, 200, 5000, 0));
		assertEquals(200.0, slice.sliceExtent(), 0);
	}

	/**
	 * すでに切り始めている断片は、利用可能量が小さくても切り進める。
	 * ここで「小さすぎるからやめる」を選ぶと残りの内容が失われる
	 * (=従来どおりはみ出して切り捨てられる)ため。
	 */
	public void testSliverGuardDoesNotAbandonAnStartedRescue() {
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 800, 10, 5000, 200));
		assertEquals(200.0, slice.offset(), 0);
		assertEquals(10.0, slice.sliceExtent(), 0);
		assertTrue(slice.isContinuation());
	}

	/**
	 * 開始後でも、送り先の空きが{@link VisualRescuePlanner#MIN_RESCUE_ADVANCE}
	 * 未満なら救済の連鎖はそこで終わる(2026-07-25、独立レビュー指摘)。
	 *
	 * <p>
	 * これは「開始後は必ず切り進める」の<b>唯一の例外</b>であり、意図した
	 * 終端である。ここで「外側のフラグメンテナへ委譲」を選ぶと、容量は
	 * ページごとに変わらないため送っても同じ判定になり、無限ループになる。
	 * 到達するのは容量1pt未満という縮退したフラグメンテナだけ——通常の
	 * 救済は先頭側の下限(20pt以上)を満たさないと始まらないため。
	 * </p>
	 */
	public void testStartedRescueEndsWhenTheNextFragmentainerIsDegenerate() {
		assertEquals(RescueDecision.Reason.INSUFFICIENT_CAPACITY,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 0.4, 0.4, 5000, 200)));
		// 前進保証ちょうど(2×THRESHOLD)なら切り進める——境界を固定する
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.planInFragmentainer(null, true,
				VisualRescuePlanner.MIN_RESCUE_ADVANCE, VisualRescuePlanner.MIN_RESCUE_ADVANCE, 5000, 200));
		assertTrue(slice.isContinuation());
		assertEquals(VisualRescuePlanner.MIN_RESCUE_ADVANCE, slice.sliceExtent(), 0);
	}

	/**
	 * はみ出し量が実用上小さいなら救済を<b>始めない</b>(2026-07-25、増分6)。
	 * 数ptのはみ出しを救うために丸ごと1ページ増やすと、そのページは実質
	 * 白紙になる——「意図しない白紙ページを作らない」の末尾側の守り。
	 */
	public void testSliverRemainderDoesNotStartARescue() {
		// はみ出し10pt(20pt未満) → 救済しない
		assertEquals(RescueDecision.Reason.SLIVER_REMAINDER,
				reasonOf(VisualRescuePlanner.planInFragmentainer(null, true, 200, 200, 210, 0)));
		// ちょうど20ptのはみ出しは救済する(境界を含む)
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 200, 200, 220, 0));
		assertEquals(200.0, slice.sliceExtent(), 0);
		assertFalse(slice.lastFragment());
	}

	/**
	 * 末尾側の下限に<b>割合</b>は課さない。A4(842pt)に貼られた少しだけ
	 * 背の高い画像——本来の用途そのもの——を拒否してしまうため。
	 */
	public void testSliverRemainderUsesTheAbsoluteFloorOnly() {
		// 容量800pt、はみ出し100pt。割合(200pt)を課すと拒否されてしまう
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 800, 800, 900, 0));
		assertEquals(800.0, slice.sliceExtent(), 0);
	}

	/**
	 * 末尾側の下限も<b>開始時だけ</b>。切り始めた断片は、残余が小さくても
	 * 最後まで切り進める(やめると内容が失われる)。
	 */
	public void testSliverRemainderGuardDoesNotAbandonAnStartedRescue() {
		final RescueDecision.Slice slice = sliceOf(
				VisualRescuePlanner.planInFragmentainer(null, true, 200, 200, 405, 200));
		assertEquals(200.0, slice.offset(), 0);
		assertEquals(200.0, slice.sliceExtent(), 0);
		assertFalse("残り5ptでも続ける", slice.lastFragment());
	}

	/** 絶対配置の除外は容量判定より前に効く。 */
	public void testFragmentainerPlanStillRejectsAbsolute() {
		assertEquals(RescueDecision.Reason.ABSOLUTE,
				reasonOf(VisualRescuePlanner.planInFragmentainer(PosType.ABSOLUTE, true, 800, 800, 5000, 0)));
	}

	/**
	 * 残余が{@code LayoutUtils.THRESHOLD}以下なら消費済みとみなす。
	 * 素の{@code remaining > 0}だと、丸めで0.1pt等の残余が出たときに
	 * 「実質白紙の断片ページ」を1枚作ってしまう。
	 */
	public void testNegligibleRemainderIsExhaustedNotADegenerateFragment() {
		assertEquals(RescueDecision.Reason.EXHAUSTED,
				reasonOf(VisualRescuePlanner.plan(true, 100, 250, 249.9)));
		// THRESHOLD以上の残余は最終断片になる(LayoutUtils.compareは
		// 「差がTHRESHOLD未満」を同一とみなす——境界そのものは有意)
		assertEquals(LayoutUtils.THRESHOLD,
				sliceOf(VisualRescuePlanner.plan(true, 100, 250, 250 - LayoutUtils.THRESHOLD)).sliceExtent(), 1e-9);
		assertEquals(0.6, sliceOf(VisualRescuePlanner.plan(true, 100, 250, 249.4)).sliceExtent(), 1e-9);
	}

	/**
	 * 容量いっぱいを切ったあとに、退化した極小tailが残らない
	 * (残余が容量をTHRESHOLD以下だけ超える場合は最終断片として一度で
	 * 収める)。
	 */
	public void testNoDegenerateTailAfterAFullSlice() {
		// 残余200.3、容量200 → 収まる扱い(最終断片)
		final RescueDecision.Slice slice = sliceOf(VisualRescuePlanner.plan(true, 200, 400.3, 200));
		assertTrue(slice.lastFragment());
		assertFalse(slice.hasTail());
	}

	/**
	 * フラグメンテナ容量つきの判定を繰り返しても、断片数はページ数の
	 * 常識的な範囲で収束する(容量いっぱいを消費するため)。
	 */
	public void testFragmentainerPlanningConsumesFullCapacity() {
		final double capacity = 200, sourcePageExtent = 1000;
		double offset = 0;
		int steps = 0;
		while (VisualRescuePlanner.planInFragmentainer(PosType.FLOW, true, capacity, capacity, sourcePageExtent,
				offset) instanceof RescueDecision.Slice slice) {
			assertTrue("前進しなければ停止しない", slice.nextOffset() > offset);
			offset = slice.nextOffset();
			++steps;
			assertTrue("断片が多すぎる: " + steps, steps <= 10);
			if (slice.lastFragment()) {
				break;
			}
		}
		assertEquals(5, steps);
		assertEquals(sourcePageExtent, offset, 0);
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
