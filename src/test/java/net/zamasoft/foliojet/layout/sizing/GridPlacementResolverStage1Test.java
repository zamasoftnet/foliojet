package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.GridArea;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.Result;

/** Pure calculation tests for bounded placement added in row subgrid Stage 1. */
public class GridPlacementResolverStage1Test extends TestCase {

	private static final GridLineValue A = GridLineValue.AUTO_VALUE;

	private static GridItemSpec auto() {
		return GridItemSpec.AUTO;
	}

	private static GridItemSpec row(final GridLineValue start, final GridLineValue end) {
		return GridItemSpec.of(A, A, start, end);
	}

	private static GridArea area(final Result result, final int index) {
		return ((Result.Resolved) result).plan().areas().get(index);
	}

	/** The default overload returns the same Plan as the previous overload with explicit arguments. */
	public void testDefaultModeIsUnchanged() {
		final List<GridItemSpec> items = List.of(auto(), auto(), auto(), auto(), auto());
		final Result shortResult = GridPlacementResolver.resolve(items, 2);
		final Result fullResult = GridPlacementResolver.resolve(items, 2, 0, false, false);
		assertEquals(shortResult, fullResult);
	}

	/** Bounded mode partially clips an area that extends beyond the end. */
	public void testBoundedRowsClipsPartialOverflow() {
		final Result result = GridPlacementResolver.resolve(
				List.of(row(GridLineValue.line(2), GridLineValue.line(5))), 1, 3, false, false, 3);
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 1, 1, 2), area(result, 0));
		assertEquals(3, ((Result.Resolved) result).plan().rowCount());
	}

	/** An area entirely outside the bounded range collapses to span 1 in the final track. */
	public void testBoundedRowsClampsCompleteOverflow() {
		final Result result = GridPlacementResolver.resolve(
				List.of(row(GridLineValue.line(5), GridLineValue.line(7))), 1, 3, false, false, 3);
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 2, 1, 1), area(result, 0));
	}

	/** Virtual implicit lines on the negative side are also clipped to the range after normal placement. */
	public void testBoundedRowsClipsNegativeLines() {
		final Result result = GridPlacementResolver.resolve(
				List.of(row(GridLineValue.line(-5), GridLineValue.line(-2))), 1, 3, false, false, 3);
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 0, 1, 2), area(result, 0));
	}

	/** Even with the explicit range full, auto placement advances to virtual rows before clamping to the end. */
	public void testBoundedRowsAutoPlacementAfterFullRange() {
		final Result result = GridPlacementResolver.resolve(List.of(
				row(GridLineValue.line(1), A), row(GridLineValue.line(2), A), auto()), 1, 2, false, false, 2);
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 0, 1, 1), area(result, 0));
		assertEquals(new GridArea(0, 1, 1, 1), area(result, 1));
		assertEquals(new GridArea(0, 1, 1, 1), area(result, 2));
		assertEquals(2, ((Result.Resolved) result).plan().rowCount());
	}
}
