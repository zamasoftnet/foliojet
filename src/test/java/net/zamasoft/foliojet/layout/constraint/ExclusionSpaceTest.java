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
}
