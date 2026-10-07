package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.GridArea;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver.Result;

/**
 * Pure calculation tests for {@link GridPlacementResolver} (Grid G4a)
 * (case list in consult-codex-2026-07-31-grid-g4.txt Q4).
 */
public class GridPlacementResolverTest extends TestCase {

	private static final GridLineValue A = GridLineValue.AUTO_VALUE;

	private static GridItemSpec auto() {
		return GridItemSpec.AUTO;
	}

	private static GridItemSpec col(final GridLineValue start, final GridLineValue end) {
		return GridItemSpec.of(start, end, A, A);
	}

	private static GridArea area(final Result result, final int index) {
		return ((Result.Resolved) result).plan().areas().get(index);
	}

	/** All-auto placement exactly matches G3 source-order placement (col=i%n, row=i/n). */
	public void testAllAutoMatchesSourceOrder() {
		final Result result = GridPlacementResolver.resolve(List.of(auto(), auto(), auto(), auto(), auto()), 2);
		assertTrue(result instanceof Result.Resolved);
		for (int i = 0; i < 5; ++i) {
			assertEquals(new GridArea(i % 2, i / 2, 1, 1), area(result, i));
		}
		assertEquals(3, ((Result.Resolved) result).plan().rowCount());
	}

	/** Positive/negative column line numbers and line/line, line/span, span/line. */
	public void testColumnLineForms() {
		// 3 columns. 1/3 = span of 2 columns, -2/-1 = last column, 2/span 2, span 2/4.
		final Result result = GridPlacementResolver.resolve(List.of( //
				col(GridLineValue.line(1), GridLineValue.line(3)), //
				col(GridLineValue.line(-2), GridLineValue.line(-1)), //
				col(GridLineValue.line(2), GridLineValue.span(2)), //
				col(GridLineValue.span(2), GridLineValue.line(4))), 3);
		assertTrue(result.toString(), result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 0, 2, 1), area(result, 0));
		assertEquals(new GridArea(2, 0, 1, 1), area(result, 1));
		assertEquals(new GridArea(1, 1, 2, 1), area(result, 2)); // Columns 0-1 in row 0 are occupied → row 1 with sparse placement.
		assertEquals(new GridArea(1, 2, 2, 1), area(result, 3)); // span2/line4 = columns 1..3, moving to row 2.
	}

	/** Swap reversed lines; identical lines give span 1; span/span ignores the end. */
	public void testConflictNormalization() {
		final Result result = GridPlacementResolver.resolve(List.of( //
				col(GridLineValue.line(3), GridLineValue.line(1)), // Reversed → 1/3.
				col(GridLineValue.line(2), GridLineValue.line(2)), // Same line → span1@col1.
				col(GridLineValue.span(2), GridLineValue.span(3))), 3); // span/span→auto span2
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 0, 2, 1), area(result, 0));
		assertEquals(new GridArea(1, 1, 1, 1), area(result, 1)); // (1,0) occupied → row 1.
		assertEquals(new GridArea(0, 2, 2, 1), area(result, 2)); // The cursor never moves backward.
	}

	/** Sparse: the cursor never moves backward, and later items do not fill holes in earlier rows. */
	public void testSparseCursorNoBackfill() {
		final Result result = GridPlacementResolver.resolve(List.of( //
				col(GridLineValue.line(3), A), // Column 2.
				auto(), // cursor(0,3) → next row (1,0).
				auto()), 3);
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(2, 0, 1, 1), area(result, 0));
		assertEquals(new GridArea(0, 1, 1, 1), area(result, 1)); // Do not fill the holes (0,1) in row 0.
		assertEquals(new GridArea(1, 1, 1, 1), area(result, 2));
	}

	/**
	 * Explicit positive rows, sparse placement within a row, empty rows; overlap is allowed with both axes
	 * definite.
	 */
	public void testExplicitRows() {
		final Result result = GridPlacementResolver.resolve(List.of( //
				GridItemSpec.of(GridLineValue.line(1), A, GridLineValue.line(3), A), // (0,2)
				GridItemSpec.of(GridLineValue.line(1), A, GridLineValue.line(3), A), // Overlap → (0,2).
				GridItemSpec.of(A, A, GridLineValue.line(3), A), // Definite row, auto column → (1,2).
				auto()), 2); // (0,0)
		assertTrue(result instanceof Result.Resolved);
		assertEquals(new GridArea(0, 2, 1, 1), area(result, 0));
		assertEquals(new GridArea(0, 2, 1, 1), area(result, 1)); // Overlap allowed.
		assertEquals(new GridArea(1, 2, 1, 1), area(result, 2));
		assertEquals(new GridArea(0, 0, 1, 1), area(result, 3));
		assertEquals(3, ((Result.Resolved) result).plan().rowCount());
	}

	/** Specifications requiring implicit columns are Unsupported (no clamping). */
	public void testUnsupportedImplicitColumn() {
		assertTrue(GridPlacementResolver.resolve(List.of(col(GridLineValue.line(4), A)), 3) //
				instanceof Result.Unsupported); // Starts at line 4 = outside the explicit range.
		assertTrue(GridPlacementResolver.resolve(List.of(col(GridLineValue.line(-1), GridLineValue.span(1))), 3) //
				instanceof Result.Unsupported); // -1/span1 = beyond the end.
		assertTrue(GridPlacementResolver.resolve(List.of(col(A, GridLineValue.span(4))), 3) //
				instanceof Result.Unsupported); // Span exceeds the column count.
		assertTrue(GridPlacementResolver.resolve(List.of(col(GridLineValue.line(-5), A)), 3) //
				instanceof Result.Unsupported); // Before the explicit start.
	}

	/** Negative row numbers and huge values are Unsupported. */
	public void testUnsupportedRows() {
		assertTrue(GridPlacementResolver.resolve( //
				List.of(GridItemSpec.of(A, A, GridLineValue.line(-1), A)), 2) instanceof Result.Unsupported);
		assertTrue(GridPlacementResolver.resolve( //
				List.of(GridItemSpec.of(A, A, GridLineValue.line(2000000000), A)), 2) //
				instanceof Result.Unsupported);
		// Calculating backward from span/line goes before the first row.
		assertTrue(GridPlacementResolver.resolve( //
				List.of(GridItemSpec.of(A, A, GridLineValue.span(3), GridLineValue.line(2))), 2) //
				instanceof Result.Unsupported);
	}
}
