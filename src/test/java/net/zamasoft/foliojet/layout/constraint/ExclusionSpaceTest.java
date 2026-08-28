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

	public void testScanLineBandNoExclusions() {
		final ExclusionSpace.LineScan scan = ExclusionSpace.EMPTY.scanLineBand(0, 20, 0, 500);
		assertNull(scan.startExclusion());
		assertNull(scan.endExclusion());
		assertEquals(0.0, scan.lineStart(), 0);
		assertEquals(500.0, scan.lineEnd(), 0);
		assertFalse(scan.maxPageSizeSet());
	}

	public void testScanLineBandSkipsFloatAlreadyPast() {
		// TextBuilder.locateLineは昇順走査で、既に終わった浮動体は
		// continue(打ち切らず次へ進む)——他3消費者のdescending
		// break挙動とは異なる。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 50, 0, 80));
		space = space.plus(sideExclusion(1, FloatSide.START, 200, 0, 999));

		final ExclusionSpace.LineScan scan = space.scanLineBand(100, 20, 0, 500);
		assertNotNull(scan.startExclusion());
		assertEquals(1, scan.startExclusion().order());
		assertEquals(999.0, scan.lineStart(), 0);
	}

	public void testScanLineBandNarrowsStartAndEnd() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 50));
		space = space.plus(sideExclusion(1, FloatSide.END, 100, 400, 500));

		final ExclusionSpace.LineScan scan = space.scanLineBand(0, 20, 0, 500);
		assertEquals(50.0, scan.lineStart(), 0);
		assertEquals(400.0, scan.lineEnd(), 0);
		assertFalse(scan.maxPageSizeSet());
	}

	public void testScanLineBandSetsMaxPageSizeForFutureFloat() {
		// floating.pageStartが現在行の高さの範囲を超えている場合、
		// maxPageSizeを設定して走査を打ち切る(それより後の浮動体は
		// 無視される)。sideExclusion()はpageSpan.start()を常に0にする
		// ため、この場合だけ直接FloatExclusionを構築する。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(
				new FloatExclusion(0, FloatSide.START, new AxisSpan(30, 200), new AxisSpan(0, 999)));
		space = space.plus(sideExclusion(1, FloatSide.END, 300, 0, 999));

		final ExclusionSpace.LineScan scan = space.scanLineBand(0, 20, 0, 500);
		assertTrue(scan.maxPageSizeSet());
		assertEquals(30.0, scan.maxPageSize(), 0);
		// order=0で打ち切られるため、order=1によるlineEnd狭窄は
		// 適用されない。
		assertEquals(500.0, scan.lineEnd(), 0);
	}

	public void testScanFloatPlacementBandNoExclusions() {
		final ExclusionSpace.FloatPlacementScan scan = ExclusionSpace.EMPTY.scanFloatPlacementBand(0, 0, 500,
				ClearMode.NONE);
		assertNull(scan.startExclusion());
		assertNull(scan.endExclusion());
		assertEquals(0.0, scan.lineStart(), 0);
		assertEquals(500.0, scan.lineEnd(), 0);
		assertEquals(0.0, scan.pageStart(), 0);
	}

	public void testScanFloatPlacementBandNarrowsStartAndEnd() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 50));
		space = space.plus(sideExclusion(1, FloatSide.END, 100, 400, 500));

		final ExclusionSpace.FloatPlacementScan scan = space.scanFloatPlacementBand(0, 0, 500, ClearMode.NONE);
		assertEquals(50.0, scan.lineStart(), 0);
		assertEquals(400.0, scan.lineEnd(), 0);
		assertEquals(0.0, scan.pageStart(), 0);
	}

	public void testScanFloatPlacementBandClearBoundaryPreservesPriorNarrowingAndBumpsPageStart() {
		// clear=STARTの場合、descending順で先に処理されるEND側の狭窄結果
		// (order0)は保持したまま、後で遭遇するSTART側の浮動体(order1、
		// clearの条件に一致)でpageStartだけ更新して即座に打ち切る
		// (2026-07-23、addBoundの教訓を踏まえ事前に確認・正しく設計)。
		// clear=STARTの場合、START側の浮動体に遭遇した時点で必ずclear側の
		// 分岐が先に一致するため、START側自身の狭窄が蓄積することはない
		// ——保持されるのはそれ以前に処理された「一致しない側」の狭窄のみ。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 200, 300, 999));
		space = space.plus(sideExclusion(1, FloatSide.START, 100, 0, 999));

		final ExclusionSpace.FloatPlacementScan scan = space.scanFloatPlacementBand(0, 0, 500, ClearMode.START);
		assertNull(scan.startExclusion());
		assertNotNull(scan.endExclusion());
		assertEquals(0, scan.endExclusion().order());
		assertEquals(300.0, scan.lineEnd(), 0);
		assertEquals(100.0, scan.pageStart(), 0);
	}

	public void testCopyOfSortedMatchesPlusOrdering() {
		// copyOfSorted(O(N)一括構築)とplus(逐次挿入)が同じ並びを
		// 生むことを固定する(2026-07-23、旧ループ撤去時の特性テスト)。
		final List<FloatExclusion> sorted = List.of(exclusion(0, 100), exclusion(1, 100), exclusion(2, 200));
		ExclusionSpace byPlus = ExclusionSpace.EMPTY;
		for (final FloatExclusion e : sorted) {
			byPlus = byPlus.plus(e);
		}
		final ExclusionSpace byCopy = ExclusionSpace.copyOfSorted(sorted);
		assertEquals(byPlus.ascendingByPageEnd(), byCopy.ascendingByPageEnd());
	}

	public void testScanLineBandSameLineEndDistinctPageEndPinsSelection() {
		// 同一line端・異なるpageEndの2つのSTART floatがある場合に、どちらが
		// 「選択された境界」(呼び出し元の降下先pageEndを決める)になるかを
		// 固定する。昇順走査では同値(>=)の更新により後に処理された方
		// (pageEndが大きい方)が選ばれる——旧TextBuilderループと同じ挙動
		// (2026-07-23、旧ループ撤去時の特性テスト)。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 80));
		space = space.plus(sideExclusion(1, FloatSide.START, 200, 0, 80));

		final ExclusionSpace.LineScan scan = space.scanLineBand(0, 20, 0, 500);
		assertNotNull(scan.startExclusion());
		assertEquals(1, scan.startExclusion().order());
		assertEquals(200.0, scan.startExclusion().pageSpan().end(), 0);
		assertEquals(80.0, scan.lineStart(), 0);
	}

	public void testScanFloatPlacementBandSameLineEndDistinctPageEndPinsSelection() {
		// scanLineBand(昇順)とは逆に、float配置の降順走査では同値(>=)の
		// 更新により後に処理された方(pageEndが小さい方)が選ばれる——
		// 旧addStartFloat/addEndFloatループと同じ挙動(2026-07-23、
		// 旧ループ撤去時の特性テスト)。
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 80));
		space = space.plus(sideExclusion(1, FloatSide.START, 200, 0, 80));

		final ExclusionSpace.FloatPlacementScan scan = space.scanFloatPlacementBand(0, 0, 500, ClearMode.NONE);
		assertNotNull(scan.startExclusion());
		assertEquals(0, scan.startExclusion().order());
		assertEquals(100.0, scan.startExclusion().pageSpan().end(), 0);
		assertEquals(80.0, scan.lineStart(), 0);
	}
	/** 半径50・中心(50,50)の円形状(マージンボックス100×100、pageSpan 0..100)。 */
	private static ExclusionShape circleShape() {
		return ExclusionShape.ofShape(new java.awt.geom.Ellipse2D.Double(0, 0, 100, 100), new AxisSpan(0, 100),
				new AxisSpan(0, 100));
	}

	public void testScanLineBandShapedStartFloatUsesChordOfBand() {
		// shape-outside(2026-08-29): 行帯[0,12]では円の弦(v=12で≈82.5)まで
		// 狭める。矩形なら100まで狭まる
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(new FloatExclusion(0, FloatSide.START, new AxisSpan(0, 100), new AxisSpan(0, 100),
				circleShape()));
		final ExclusionSpace.LineScan scan = space.scanLineBand(0, 12, 0, 500);
		assertNotNull(scan.startExclusion());
		assertEquals(50 + Math.sqrt(2500 - 38 * 38), scan.lineStart(), 0.5);
		assertEquals(500.0, scan.lineEnd(), 0);
		// 円の中央の帯では矩形と同じ100
		assertEquals(100.0, space.scanLineBand(44, 12, 0, 500).lineStart(), 0.5);
	}

	public void testScanLineBandShapedEndFloatUsesChordOfBand() {
		// END側は形状の開始端(円の左の弦)まで狭める
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(new FloatExclusion(0, FloatSide.END, new AxisSpan(0, 100), new AxisSpan(400, 500),
				ExclusionShape.ofShape(new java.awt.geom.Ellipse2D.Double(400, 0, 100, 100), new AxisSpan(400, 500),
						new AxisSpan(0, 100))));
		final ExclusionSpace.LineScan scan = space.scanLineBand(0, 12, 0, 500);
		assertNotNull(scan.endExclusion());
		assertEquals(450 - Math.sqrt(2500 - 38 * 38), scan.lineEnd(), 0.5);
		assertEquals(0.0, scan.lineStart(), 0);
	}

	public void testScanLineBandShapeEmptyInBandDoesNotNarrow() {
		// 形状が帯と交わらなければ、その浮動体は行を狭めない(円の上下の
		// 空白部分へ行が入り込める)。矩形ならpageSpan内の全帯で狭める
		final double[] min = new double[100], max = new double[100];
		java.util.Arrays.fill(min, Double.NaN);
		java.util.Arrays.fill(max, Double.NaN);
		for (int k = 40; k < 60; ++k) {
			min[k] = 0;
			max[k] = 100;
		}
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(new FloatExclusion(0, FloatSide.START, new AxisSpan(0, 100), new AxisSpan(0, 100),
				ExclusionShape.ofProfile(0, 1, min, max)));
		final ExclusionSpace.LineScan above = space.scanLineBand(0, 12, 0, 500);
		assertNull(above.startExclusion());
		assertEquals(0.0, above.lineStart(), 0);
		final ExclusionSpace.LineScan middle = space.scanLineBand(40, 12, 0, 500);
		assertNotNull(middle.startExclusion());
		assertEquals(100.0, middle.lineStart(), 0);
		// maxPageSize(次の浮動体の上端)の扱いは形状の有無に関わらず同じ
		assertFalse(middle.maxPageSizeSet());
	}

	public void testFloatPlacementBandIgnoresShape() {
		// css-shapes-1 §4.1: 浮動体同士の配置は形状の影響を受けない
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(new FloatExclusion(0, FloatSide.START, new AxisSpan(0, 100), new AxisSpan(0, 100),
				circleShape()));
		final ExclusionSpace.FloatPlacementScan scan = space.scanFloatPlacementBand(0, 0, 500, ClearMode.NONE);
		assertEquals(100.0, scan.lineStart(), 0);
		final AxisSpan band = space.narrowLineBandForMulticol(0, new AxisSpan(0, 500));
		assertEquals(100.0, band.start(), 0);
	}
}
