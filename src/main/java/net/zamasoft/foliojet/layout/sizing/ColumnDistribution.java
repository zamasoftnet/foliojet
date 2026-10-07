package net.zamasoft.foliojet.layout.sizing;

/**
 * Distributes table column widths. SPEC css-tables-3 [Distributing width to columns]
 *
 * <p>
 * Ensures each column has at least min[i], then uses surplus space to expand to target[i]
 * in PERCENT→CONSTRAINED→AUTO priority order (if surplus is insufficient, distributes it in
 * proportion to each column's deficit). If space remains after all columns reach their targets,
 * distributes it to the first existing column type in AUTO→CONSTRAINED→PERCENT order,
 * proportionally to current widths (equally if all are zero).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ColumnDistribution {
	/**
	 * Column types, in ascending priority order.
	 */
	public enum ColumnType {
		/** A column with no specified width (target=max-content width). */
		AUTO,
		/** A column whose width is specified as a length (target=specified width). */
		CONSTRAINED,
		/** A column whose width is specified as a percentage (target=resolved percentage width). */
		PERCENT;
	}

	private static final ColumnType[] GROW_ORDER = { ColumnType.PERCENT, ColumnType.CONSTRAINED, ColumnType.AUTO };

	private static final ColumnType[] EXCESS_ORDER = { ColumnType.AUTO, ColumnType.CONSTRAINED, ColumnType.PERCENT };

	private ColumnDistribution() {
		// utility
	}

	/**
	 * Distributes column widths.
	 *
	 * @param min       Initial width of each column (normally min-content width)
	 * @param target    Target width of each column (AUTO=max-content width, CONSTRAINED=specified width,
	 *                  PERCENT=resolved percentage width)
	 * @param types     Type of each column
	 * @param available Width available for distribution
	 * @return Array of column widths (sum is max(Σmin, available))
	 */
	public static double[] distribute(double[] min, double[] target, ColumnType[] types, double available) {
		final int n = min.length;
		final double[] sizes = new double[n];
		double sum = 0;
		for (int i = 0; i < n; ++i) {
			sizes[i] = min[i];
			sum += min[i];
		}

		// Expand to target widths in priority order.
		for (final ColumnType type : GROW_ORDER) {
			if (available <= sum) {
				return sizes;
			}
			double deficit = 0;
			for (int i = 0; i < n; ++i) {
				if (types[i] == type) {
					deficit += Math.max(0, target[i] - sizes[i]);
				}
			}
			if (deficit <= 0) {
				continue;
			}
			final double rem = available - sum;
			final double ratio = deficit <= rem ? 1 : rem / deficit;
			for (int i = 0; i < n; ++i) {
				if (types[i] != type) {
					continue;
				}
				final double diff = Math.max(0, target[i] - sizes[i]) * ratio;
				sizes[i] += diff;
				sum += diff;
			}
		}

		// Distribute surplus space.
		if (available > sum) {
			final double rem = available - sum;
			for (final ColumnType type : EXCESS_ORDER) {
				int count = 0;
				double typeSum = 0;
				for (int i = 0; i < n; ++i) {
					if (types[i] == type) {
						++count;
						typeSum += sizes[i];
					}
				}
				if (count == 0) {
					continue;
				}
				for (int i = 0; i < n; ++i) {
					if (types[i] != type) {
						continue;
					}
					sizes[i] += typeSum > 0 ? rem * sizes[i] / typeSum : rem / count;
				}
				break;
			}
		}
		return sizes;
	}
}
