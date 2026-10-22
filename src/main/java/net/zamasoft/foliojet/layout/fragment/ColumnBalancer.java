package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Capacity calculation for column balancing (column-fill: balance) (M5-B).
 *
 * <p>
 * Balancing selects the minimum fragment capacity that distributes all content as evenly as possible over N
 * fragments (columns). Actual cuts send content extending beyond the proposed position to the next fragment (=
 * round down to the preceding boundary). The old single snap at total/N accumulated this rounding across columns,
 * making only the last column longer. Here, a cut-below oracle (Container.getBalanceCutPointBelow, estimating cut
 * positions without mutating boxes) simulates N-1 cuts with a sliding window, and binary search finds the minimum
 * capacity that fits all content.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ColumnBalancer {
	private static final int MAX_ITERATIONS = 32;

	private ColumnBalancer() {
		// pure functions
	}

	/**
	 * The cut oracle ({@code Container.getBalanceCutPointBelow}).
	 */
	@FunctionalInterface
	public interface CutOracle {
		/**
		 * Returns the cut position for a column from {@code from} to the proposed position: the last boundary after
		 * {@code from}, or {@code from} or before it if the column cannot hold the content starting it.
		 *
		 * @param from     the start of the column
		 * @param proposed the proposed position
		 * @return the cut position
		 */
		double cutPointBelow(double from, double proposed);
	}

	/**
	 * Finds the minimum capacity that distributes all content evenly among columnCount columns.
	 *
	 * @param cutPointBelow the cut oracle (Container.getBalanceCutPointBelow)
	 * @param totalSize     total content size in the page direction
	 * @param columnCount   number of columns
	 * @return column capacity in the page direction
	 */
	public static double balance(final CutOracle cutPointBelow, final double totalSize,
			final int columnCount) {
		if (columnCount <= 1 || LayoutUtils.compare(totalSize, 0) <= 0) {
			return Math.max(totalSize, 0);
		}
		final double even = totalSize / columnCount;
		if (fits(cutPointBelow, even, totalSize, columnCount)) {
			return even;
		}
		// The last column overflows at even: binary-search for the minimum capacity that fits everything.
		double lower = even;
		double upper = totalSize;
		for (int iter = 0; iter < MAX_ITERATIONS && upper - lower > 0.01; ++iter) {
			final double mid = (lower + upper) / 2;
			if (fits(cutPointBelow, mid, totalSize, columnCount)) {
				upper = mid;
			} else {
				lower = mid;
			}
		}
		return upper;
	}

	/**
	 * Determines whether all content fits after N-1 cuts at capacity.
	 */
	private static boolean fits(final CutOracle cutPointBelow, final double capacity, final double totalSize,
			final int columnCount) {
		double pos = 0;
		for (int k = 1; k < columnCount; ++k) {
			final double proposed = pos + capacity;
			if (LayoutUtils.compare(proposed, totalSize) >= 0) {
				return true;
			}
			final double end = cutPointBelow.cutPointBelow(pos, proposed);
			if (LayoutUtils.compare(end, pos) <= 0) {
				// The column cannot hold even the line that starts it. Until 2026-10-22 the oracle gave no boundary
				// here and the search went on from the proposed position as if the line were cut there: one line in
				// four columns got a quarter of its height, and the next content overlapped it, or in vertical-rl
				// columns it was drawn off the paper (sweep seed 30896161). Where the oracle does not see the
				// boundaries (inside a table, a float or a flex item), it still gives back the proposed position.
				return false;
			}
			pos = end;
		}
		return LayoutUtils.compare(totalSize - pos, capacity) <= 0;
	}
}
