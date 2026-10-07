package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.GridArea;

/**
 * Pure calculation tests for {@link GridRowSizing} (Grid G4d).
 */
public class GridRowSizingTest extends TestCase {

	/** Only rowSpan1: maximum within each row. Empty rows have size 0. */
	public void testSingleSpanRows() {
		final double[] h = GridRowSizing.resolve(List.of(new GridArea(0, 0, 1, 1), new GridArea(1, 0, 1, 1),
				new GridArea(0, 2, 1, 1)), new double[] { 20, 30, 10 }, 3, 5);
		assertEquals(30.0, h[0], 0.001);
		assertEquals(0.0, h[1], 0.001);
		assertEquals(10.0, h[2], 0.001);
	}

	/** Add the rowSpan deficit equally to each row (deficit/rowSpan). */
	public void testRowSpanDeficit() {
		// Row 0=20(q), row 1=15(r). span2 p=50: deficit=50-0-35=15 → +7.5 per row.
		final double[] h = GridRowSizing.resolve(List.of(new GridArea(0, 0, 1, 2), new GridArea(1, 0, 1, 1),
				new GridArea(1, 1, 1, 1)), new double[] { 50, 20, 15 }, 2, 0);
		assertEquals(27.5, h[0], 0.001);
		assertEquals(22.5, h[1], 0.001);
	}

	/** Deduct internal rowGap from the deficit. If there is enough space, row heights stay unchanged. */
	public void testRowSpanGapAndNoDeficit() {
		// gap10, row 0=20, row 1=30. span2=55 → deficit=55-10-50=-5 → no change.
		final double[] h = GridRowSizing.resolve(List.of(new GridArea(0, 0, 1, 2), new GridArea(1, 0, 1, 1),
				new GridArea(1, 1, 1, 1)), new double[] { 55, 20, 30 }, 2, 10);
		assertEquals(20.0, h[0], 0.001);
		assertEquals(30.0, h[1], 0.001);
	}

	/** Apply equal-span-length items together using planned increase (maximum required increase). */
	public void testSameSpanBatch() {
		// Two span2 items (60 and 40) span the same pair of rows; only the larger affects the result (+30 each).
		final double[] h = GridRowSizing.resolve(
				List.of(new GridArea(0, 0, 1, 2), new GridArea(1, 0, 1, 2)), new double[] { 60, 40 }, 2, 0);
		assertEquals(30.0, h[0], 0.001);
		assertEquals(30.0, h[1], 0.001);
	}
}
