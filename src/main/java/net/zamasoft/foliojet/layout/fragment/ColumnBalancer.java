package net.zamasoft.foliojet.layout.fragment;

import java.util.function.DoubleUnaryOperator;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Capacity calculation for column balancing (column-fill: balance) (M5-B).
 *
 * <p>
 * Balancing selects the minimum fragment capacity that distributes all content as evenly as possible over N
 * fragments (columns). Actual cuts send content extending beyond the proposed position to the next fragment (=
 * round down to the preceding boundary). The old single snap at total/N accumulated this rounding across columns,
 * making only the last column longer. Here, a cut-below oracle (Container.getCutPointBelow, estimating cut
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
	 * Finds the minimum capacity that distributes all content evenly among columnCount columns.
	 *
	 * @param cutPointBelow function returning the feasible cut position immediately before the proposed position
	 *                      (Container.getCutPointBelow)
	 * @param totalSize     total content size in the page direction
	 * @param columnCount   number of columns
	 * @return column capacity in the page direction
	 */
	public static double balance(final DoubleUnaryOperator cutPointBelow, final double totalSize,
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
	private static boolean fits(final DoubleUnaryOperator cutPointBelow, final double capacity, final double totalSize,
			final int columnCount) {
		double pos = 0;
		for (int k = 1; k < columnCount; ++k) {
			final double proposed = pos + capacity;
			if (LayoutUtils.compare(proposed, totalSize) >= 0) {
				return true;
			}
			double end = cutPointBelow.applyAsDouble(proposed);
			if (LayoutUtils.compare(end, pos) <= 0) {
				// Region with no internal boundary (uncuttable): the actual cut cannot advance either,
				// so use the proposed position unchanged and proceed.
				end = proposed;
			}
			pos = end;
		}
		return LayoutUtils.compare(totalSize - pos, capacity) <= 0;
	}
}
