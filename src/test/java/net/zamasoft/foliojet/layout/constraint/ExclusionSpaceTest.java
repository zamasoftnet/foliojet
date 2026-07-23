package net.zamasoft.foliojet.layout.constraint;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * {@link ExclusionSpace}の並び契約を固定する単体テストです(2026-07-23
 * 新設、排除域のConstraintSpace入力化P0)。まだ未配線
 * ({@code BlockBuilder}等の既存消費者はこの型を参照しない)——この
 * 段階では、{@code BlockBuilder.FLOAT_COMP}(pageEnd昇順の安定ソート、
 * 同値は追加順)と同じ並び契約を、この値型だけで再現できることだけを
 * 確認する。
 */
public class ExclusionSpaceTest extends TestCase {
	public ExclusionSpaceTest(String name) {
		super(name);
	}

	private static FloatExclusion exclusion(long order, double pageEnd) {
		return new FloatExclusion(order, FloatSide.START, new AxisSpan(0, pageEnd), new AxisSpan(0, 100));
	}

	public void testEmptyIsEmpty() {
		assertTrue(ExclusionSpace.EMPTY.isEmpty());
		assertEquals(0, ExclusionSpace.EMPTY.size());
		assertTrue(ExclusionSpace.EMPTY.ascendingByPageEnd().isEmpty());
		assertTrue(ExclusionSpace.EMPTY.descendingByPageEnd().isEmpty());
	}

	public void testPlusIsImmutable() {
		final ExclusionSpace before = ExclusionSpace.EMPTY;
		final ExclusionSpace after = before.plus(exclusion(0, 100));
		assertTrue(before.isEmpty());
		assertEquals(1, after.size());
	}

	public void testAscendingOrderForDistinctPageEnds() {
		// 挿入順はpageEnd順とは無関係に、常にpageEnd昇順で並ぶこと
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 300));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 200));

		final List<FloatExclusion> ascending = space.ascendingByPageEnd();
		assertEquals(3, ascending.size());
		assertEquals(100.0, ascending.get(0).pageSpan().end(), 0);
		assertEquals(200.0, ascending.get(1).pageSpan().end(), 0);
		assertEquals(300.0, ascending.get(2).pageSpan().end(), 0);
	}

	public void testTiesPreserveInsertionOrderAscending() {
		// BlockBuilder.FLOAT_COMPは安定ソート:
		// 同じpageEndの浮動体は追加順のまま。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 100));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 100));

		final List<FloatExclusion> ascending = space.ascendingByPageEnd();
		assertEquals(0, ascending.get(0).order());
		assertEquals(1, ascending.get(1).order());
		assertEquals(2, ascending.get(2).order());
	}

	public void testDescendingViewReversesEntireOrder() {
		// BlockBuilderの多くの消費者はfloatings.size()-1から0へ逆順走査する
		// ——同値のpageEndでは最後に追加されたものを先に見る。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 100));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 200));

		final List<FloatExclusion> descending = space.descendingByPageEnd();
		assertEquals(3, descending.size());
		assertEquals(2, descending.get(0).order());
		assertEquals(1, descending.get(1).order());
		assertEquals(0, descending.get(2).order());
	}

	public void testMixedDistinctAndTiedPageEnds() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(exclusion(0, 200));
		space = space.plus(exclusion(1, 100));
		space = space.plus(exclusion(2, 200));
		space = space.plus(exclusion(3, 50));

		final List<FloatExclusion> ascending = space.ascendingByPageEnd();
		assertEquals(List.of(3L, 1L, 0L, 2L), ascending.stream().map(FloatExclusion::order).toList());
	}

	private static FloatExclusion sideExclusion(long order, FloatSide side, double pageEnd, double lineStart,
			double lineEnd) {
		return new FloatExclusion(order, side, new AxisSpan(0, pageEnd), new AxisSpan(lineStart, lineEnd));
	}

	public void testNarrowLineBandForMulticolNoExclusions() {
		final AxisSpan band = ExclusionSpace.EMPTY.narrowLineBandForMulticol(0, new AxisSpan(0, 500));
		assertEquals(0.0, band.start(), 0);
		assertEquals(500.0, band.end(), 0);
	}

	public void testNarrowLineBandForMulticolStartAndEndFloat() {
		// BlockBuilder.startFlowBlockのmulticol回避と同じ規則:
		// STARTはlineStartをfloating.lineEndまで押し出し、
		// ENDはlineEndをfloating.lineStartまで押し戻す。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 50));
		space = space.plus(sideExclusion(1, FloatSide.END, 100, 400, 500));

		final AxisSpan band = space.narrowLineBandForMulticol(0, new AxisSpan(0, 500));
		assertEquals(50.0, band.start(), 0);
		assertEquals(400.0, band.end(), 0);
	}

	public void testNarrowLineBandForMulticolIgnoresFloatAtOrBeforePageAxis() {
		// 既存ループのbreak条件: floating.pageEndがpageAxis以下の浮動体は
		// 回避帯に含めない(現ページより手前で既に終わっている浮動体)。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 50, 0, 80));

		final AxisSpan band = space.narrowLineBandForMulticol(100, new AxisSpan(0, 500));
		assertEquals(0.0, band.start(), 0);
		assertEquals(500.0, band.end(), 0);
	}

	public void testNarrowLineBandForMulticolAppliesFloatPastPageAxisOnly() {
		// pageEnd>pageAxisの浮動体だけが適用され、pageEnd<=pageAxisの
		// ものは(descending順で先に遭遇しても)無視される。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 200, 0, 80));
		space = space.plus(sideExclusion(1, FloatSide.START, 50, 0, 999));

		final AxisSpan band = space.narrowLineBandForMulticol(100, new AxisSpan(0, 500));
		assertEquals(80.0, band.start(), 0);
		assertEquals(500.0, band.end(), 0);
	}

	public void testNarrowLineBandForMulticolCanInvertOnHeavyOverlap() {
		// 既存ループはnegative line sizeをそのまま許容する(下流のクランプは
		// このメソッドの責務外)——この値型もその挙動を再現する。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 300));
		space = space.plus(sideExclusion(1, FloatSide.END, 100, 200, 500));

		final AxisSpan band = space.narrowLineBandForMulticol(0, new AxisSpan(0, 500));
		assertEquals(300.0, band.start(), 0);
		assertEquals(200.0, band.end(), 0);
		assertEquals(-100.0, band.extent(), 0);
	}

	public void testFindClearBoundaryNoFloatsReturnsNull() {
		assertNull(ExclusionSpace.EMPTY.findClearBoundary(0, 0, ClearMode.BOTH));
	}

	public void testFindClearBoundaryMatchesRequestedSide() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 200, 0, 100));
		space = space.plus(sideExclusion(1, FloatSide.START, 300, 0, 100));

		final FloatExclusion found = space.findClearBoundary(0, 0,
				ClearMode.START);
		assertNotNull(found);
		assertEquals(1, found.order());
	}

	public void testFindClearBoundaryIgnoresNonMatchingSide() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 200, 0, 100));

		final FloatExclusion found = space.findClearBoundary(0, 0,
				ClearMode.START);
		assertNull(found);
	}

	public void testFindClearBoundaryBothMatchesFirstEncountered() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 100, 0, 100));
		space = space.plus(sideExclusion(1, FloatSide.START, 200, 0, 100));

		final FloatExclusion found = space.findClearBoundary(0, 0,
				ClearMode.BOTH);
		assertNotNull(found);
		assertEquals(1, found.order());
	}

	public void testFindClearBoundaryStopsAtOrBeforePageStart() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 50, 0, 100));

		// pageEnd(50) - marginStart(0) <= pageStart(100) なので即座に打ち切り、null。
		final FloatExclusion found = space.findClearBoundary(100, 0,
				ClearMode.START);
		assertNull(found);
	}

	public void testFindClearBoundaryUsesMarginAdjustedComparison() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 150, 0, 100));

		// pageEnd(150) - marginStart(60) = 90 > pageStart(80) なので適用される。
		final FloatExclusion found = space.findClearBoundary(80, 60,
				ClearMode.START);
		assertNotNull(found);
	}

	public void testFindBoundAvoidanceNoExclusionsKeepsLineStop() {
		final ExclusionSpace.BoundAvoidance avoidance = ExclusionSpace.EMPTY.findBoundAvoidance(0, 100, 500, 0,
				ClearMode.NONE);
		assertNull(avoidance.clearingExclusion());
		assertEquals(0.0, avoidance.xMarginStart(), 0);
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceStopsWhenPastPageStart() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 50, 0, 200));

		// pageStart(100) >= pageEnd(50) なので即座に打ち切り、無変更のまま。
		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(100, 100, 500, 0, ClearMode.NONE);
		assertNull(avoidance.clearingExclusion());
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceClearBoundaryShortCircuitsNarrowing() {
		// clearが指定されている場合、境界となる浮動体が見つかった時点で
		// 走査終了——それより後(descending順で先)にある浮動体による
		// 狭窄は一切適用されない。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 100, 0, 999));
		space = space.plus(sideExclusion(1, FloatSide.START, 200, 0, 100));

		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(0, 100, 500, 0, ClearMode.START);
		assertNotNull(avoidance.clearingExclusion());
		assertEquals(1, avoidance.clearingExclusion().order());
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceStartFloatResetsAndCountsAsClearing() {
		// STARTの浮動体に遭遇するとxMarginStartが0になり、走査を打ち切る
		// ——既存コードはこの分岐でも(clearの条件一致と同様に)ループ後の
		// clearance適用(pageAxis書き換え)へ落ちるため、clearingExclusion
		// は非nullになる(2026-07-23発見の実挙動、history文書参照)。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 50, 0, 300));
		space = space.plus(sideExclusion(1, FloatSide.START, 100, 0, 999));

		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(0, 100, 500, 0, ClearMode.NONE);
		assertNotNull(avoidance.clearingExclusion());
		assertEquals(1, avoidance.clearingExclusion().order());
		assertEquals(0.0, avoidance.xMarginStart(), 0);
		// order=1(START)で走査が止まるため、order=0(END)による狭窄は
		// 適用されない。
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceEndFloatNarrowsWhenRoomRemains() {
		// addBoundのEND側narrowingが読むのはlineSpan.start()
		// (=既存コードのfloating.lineStart)——lineSpan.end()側は無関係。
		// 十分な幅がある場合はclearanceにはならず、単なる狭窄として続行する。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 100, 300, 999));

		// lineSpan.start(300) - xMarginStart(0) = 300 >= lineSize(100) なので
		// 狭窄が適用される(LayoutUtils.compareの0.5許容込み)。
		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(0, 100, 500, 0, ClearMode.NONE);
		assertNull(avoidance.clearingExclusion());
		assertEquals(300.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceEndFloatNoRoomCountsAsClearing() {
		// 幅不足の場合はlineEndがlineStopへ戻り、STARTの場合と同じく
		// clearance適用扱いになる(2026-07-23発見の実挙動)。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 100, 50, 999));

		// lineSpan.start(50) - xMarginStart(0) = 50 < lineSize(100) なので
		// lineEndはlineStopへ戻り、走査は打ち切られる。
		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(0, 100, 500, 0, ClearMode.NONE);
		assertNotNull(avoidance.clearingExclusion());
		assertEquals(0, avoidance.clearingExclusion().order());
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}
}
