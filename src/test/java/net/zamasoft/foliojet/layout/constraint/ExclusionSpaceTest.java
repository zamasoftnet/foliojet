package net.zamasoft.foliojet.layout.constraint;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * Unit tests that lock down the ordering contract of {@link ExclusionSpace} (added 2026-07-23,
 * P0 of making exclusion areas ConstraintSpace inputs). Not wired in yet (existing consumers such as
 * {@code BlockBuilder} do not reference this type). At this stage, only verify that this value type
 * alone can reproduce the ordering contract of {@code BlockBuilder.FLOAT_COMP}
 * (stable sort by ascending pageEnd, with ties in insertion order).
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
		// Always sort by ascending pageEnd, regardless of insertion order.
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
		// BlockBuilder.FLOAT_COMP uses a stable sort:
		// floats with equal pageEnd retain their insertion order.
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
		// Many BlockBuilder consumers scan backward from floatings.size()-1 to 0,
		// so for equal pageEnd values, the last-added float is seen first.
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
		// The same rules as multicol avoidance in BlockBuilder.startFlowBlock:
		// START pushes lineStart out to floating.lineEnd,
		// and END pushes lineEnd back to floating.lineStart.
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 50));
		space = space.plus(sideExclusion(1, FloatSide.END, 100, 400, 500));

		final AxisSpan band = space.narrowLineBandForMulticol(0, new AxisSpan(0, 500));
		assertEquals(50.0, band.start(), 0);
		assertEquals(400.0, band.end(), 0);
	}

	public void testNarrowLineBandForMulticolIgnoresFloatAtOrBeforePageAxis() {
		// The existing loop's break condition: floats with floating.pageEnd at or before pageAxis
		// are excluded from the avoidance band (they already end before the current page position).
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 50, 0, 80));

		final AxisSpan band = space.narrowLineBandForMulticol(100, new AxisSpan(0, 500));
		assertEquals(0.0, band.start(), 0);
		assertEquals(500.0, band.end(), 0);
	}

	public void testNarrowLineBandForMulticolAppliesFloatPastPageAxisOnly() {
		// Only floats with pageEnd>pageAxis apply; those with pageEnd<=pageAxis
		// are ignored (even if encountered first in descending order).
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 200, 0, 80));
		space = space.plus(sideExclusion(1, FloatSide.START, 50, 0, 999));

		final AxisSpan band = space.narrowLineBandForMulticol(100, new AxisSpan(0, 500));
		assertEquals(80.0, band.start(), 0);
		assertEquals(500.0, band.end(), 0);
	}

	public void testNarrowLineBandForMulticolCanInvertOnHeavyOverlap() {
		// The existing loop allows a negative line size as-is (downstream clamping is outside
		// this method's responsibility); this value type reproduces that behavior.
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

		// pageEnd(50) - marginStart(0) <= pageStart(100), so stop immediately and return null.
		final FloatExclusion found = space.findClearBoundary(100, 0,
				ClearMode.START);
		assertNull(found);
	}

	public void testFindClearBoundaryUsesMarginAdjustedComparison() {
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 150, 0, 100));

		// pageEnd(150) - marginStart(60) = 90 > pageStart(80), so this applies.
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

		// pageStart(100) >= pageEnd(50), so stop immediately with no changes.
		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(100, 100, 500, 0, ClearMode.NONE);
		assertNull(avoidance.clearingExclusion());
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceClearBoundaryShortCircuitsNarrowing() {
		// With clear specified, the scan ends as soon as the boundary float is found;
		// no narrowing from floats beyond it (earlier in descending order)
		// applies.
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 100, 0, 999));
		space = space.plus(sideExclusion(1, FloatSide.START, 200, 0, 100));

		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(0, 100, 500, 0, ClearMode.START);
		assertNotNull(avoidance.clearingExclusion());
		assertEquals(1, avoidance.clearingExclusion().order());
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceStartFloatResetsAndCountsAsClearing() {
		// Encountering a START float sets xMarginStart to 0 and ends the scan.
		// Even this branch in the existing code (like a matching clear condition) falls through
		// to the post-loop clearance application (rewriting pageAxis), so clearingExclusion
		// is non-null (actual behavior discovered 2026-07-23; see the history document).
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 50, 0, 300));
		space = space.plus(sideExclusion(1, FloatSide.START, 100, 0, 999));

		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(0, 100, 500, 0, ClearMode.NONE);
		assertNotNull(avoidance.clearingExclusion());
		assertEquals(1, avoidance.clearingExclusion().order());
		assertEquals(0.0, avoidance.xMarginStart(), 0);
		// The scan stops at order=1 (START), so narrowing by order=0 (END)
		// does not apply.
		assertEquals(500.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceEndFloatNarrowsWhenRoomRemains() {
		// END-side narrowing in addBound reads lineSpan.start()
		// (= floating.lineStart in the existing code); lineSpan.end() is irrelevant.
		// With sufficient width, processing continues with narrowing alone, without clearance.
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 100, 300, 999));

		// lineSpan.start(300) - xMarginStart(0) = 300 >= lineSize(100), so
		// narrowing applies (including the 0.5 tolerance in LayoutUtils.compare).
		final ExclusionSpace.BoundAvoidance avoidance = space.findBoundAvoidance(0, 100, 500, 0, ClearMode.NONE);
		assertNull(avoidance.clearingExclusion());
		assertEquals(300.0, avoidance.lineEnd(), 0);
	}

	public void testFindBoundAvoidanceEndFloatNoRoomCountsAsClearing() {
		// With insufficient width, lineEnd reverts to lineStop, and clearance applies
		// as in the START case (actual behavior discovered 2026-07-23).
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.END, 100, 50, 999));

		// lineSpan.start(50) - xMarginStart(0) = 50 < lineSize(100), so
		// lineEnd reverts to lineStop and the scan ends.
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
		// TextBuilder.locateLine scans in ascending order and uses continue for floats
		// that have already ended (proceeding to the next rather than stopping), unlike
		// the descending-order break behavior of the other three consumers.
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
		// If floating.pageStart is beyond the current line's height range,
		// set maxPageSize and stop the scan (ignoring subsequent floats).
		// Since sideExclusion() always sets pageSpan.start() to 0,
		// construct FloatExclusion directly only for this case.
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(
				new FloatExclusion(0, FloatSide.START, new AxisSpan(30, 200), new AxisSpan(0, 999)));
		space = space.plus(sideExclusion(1, FloatSide.END, 300, 0, 999));

		final ExclusionSpace.LineScan scan = space.scanLineBand(0, 20, 0, 500);
		assertTrue(scan.maxPageSizeSet());
		assertEquals(30.0, scan.maxPageSize(), 0);
		// The scan stops at order=0, so lineEnd narrowing by order=1
		// does not apply.
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
		// With clear=START, retain the END-side narrowing result (order0), processed first
		// in descending order, then update only pageStart and stop immediately upon
		// encountering the START float (order1, matching the clear condition)
		// (2026-07-23; checked beforehand and designed correctly based on the addBound lesson).
		// With clear=START, encountering a START float always matches the clear branch first,
		// so narrowing from the START side itself never accumulates;
		// only narrowing from the previously processed, nonmatching side is retained.
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
		// Lock down that copyOfSorted (O(N) bulk construction) and plus (incremental insertion)
		// produce the same order (2026-07-23, characterization test when removing the old loops).
		final List<FloatExclusion> sorted = List.of(exclusion(0, 100), exclusion(1, 100), exclusion(2, 200));
		ExclusionSpace byPlus = ExclusionSpace.EMPTY;
		for (final FloatExclusion e : sorted) {
			byPlus = byPlus.plus(e);
		}
		final ExclusionSpace byCopy = ExclusionSpace.copyOfSorted(sorted);
		assertEquals(byPlus.ascendingByPageEnd(), byCopy.ascendingByPageEnd());
	}

	public void testScanLineBandSameLineEndDistinctPageEndPinsSelection() {
		// With two START floats sharing the same line edge but different pageEnd values,
		// lock down which becomes the selected boundary (determining the pageEnd to which
		// the caller descends). In an ascending scan, updates on ties (>=) select the one
		// processed later (with the larger pageEnd), matching the old TextBuilder loop
		// (2026-07-23, characterization test when removing the old loops).
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
		// Unlike scanLineBand (ascending), the descending scan for float placement updates
		// on ties (>=) to select the float processed later (with the smaller pageEnd),
		// matching the old addStartFloat/addEndFloat loops (2026-07-23,
		// characterization test when removing the old loops).
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(sideExclusion(0, FloatSide.START, 100, 0, 80));
		space = space.plus(sideExclusion(1, FloatSide.START, 200, 0, 80));

		final ExclusionSpace.FloatPlacementScan scan = space.scanFloatPlacementBand(0, 0, 500, ClearMode.NONE);
		assertNotNull(scan.startExclusion());
		assertEquals(0, scan.startExclusion().order());
		assertEquals(100.0, scan.startExclusion().pageSpan().end(), 0);
		assertEquals(80.0, scan.lineStart(), 0);
	}
	/** A circle with radius 50 and center (50,50) (100×100 margin box, pageSpan 0..100). */
	private static ExclusionShape circleShape() {
		return ExclusionShape.ofShape(new java.awt.geom.Ellipse2D.Double(0, 0, 100, 100), new AxisSpan(0, 100),
				new AxisSpan(0, 100));
	}

	public void testScanLineBandShapedStartFloatUsesChordOfBand() {
		// shape-outside (2026-08-29): narrow line band [0,12] to the circle's chord (≈82.5 at v=12).
		// A rectangle would narrow it to 100.
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(new FloatExclusion(0, FloatSide.START, new AxisSpan(0, 100), new AxisSpan(0, 100),
				circleShape()));
		final ExclusionSpace.LineScan scan = space.scanLineBand(0, 12, 0, 500);
		assertNotNull(scan.startExclusion());
		assertEquals(50 + Math.sqrt(2500 - 38 * 38), scan.lineStart(), 0.5);
		assertEquals(500.0, scan.lineEnd(), 0);
		// A band at the circle's center gives 100, the same as a rectangle.
		assertEquals(100.0, space.scanLineBand(44, 12, 0, 500).lineStart(), 0.5);
	}

	public void testScanLineBandShapedEndFloatUsesChordOfBand() {
		// Narrow the END side to the shape's start edge (the circle's left chord endpoint).
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
		// If the shape does not intersect the band, the float does not narrow the line (lines
		// can enter the empty areas above/below the circle). A rectangle narrows every band within pageSpan.
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
		// maxPageSize (the next float's top edge) is handled identically with or without a shape.
		assertFalse(middle.maxPageSizeSet());
	}

	public void testFloatPlacementBandIgnoresShape() {
		// css-shapes-1 §4.1: shapes do not affect float-to-float placement.
		ExclusionSpace space = ExclusionSpace.EMPTY;
		space = space.plus(new FloatExclusion(0, FloatSide.START, new AxisSpan(0, 100), new AxisSpan(0, 100),
				circleShape()));
		final ExclusionSpace.FloatPlacementScan scan = space.scanFloatPlacementBand(0, 0, 500, ClearMode.NONE);
		assertEquals(100.0, scan.lineStart(), 0);
		final AxisSpan band = space.narrowLineBandForMulticol(0, new AxisSpan(0, 500));
		assertEquals(100.0, band.start(), 0);
	}
}
