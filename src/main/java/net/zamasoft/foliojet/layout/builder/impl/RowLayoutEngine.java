package net.zamasoft.foliojet.layout.builder.impl;

import java.util.List;

/**
 * Shared kernel for row and cell layout (P2-2: §5.2b table builder unification).
 *
 * <p>
 * The claim that this "unified the row-height algorithms of both builders" was overstated
 * (correction on 2026-07-19, C4-D and external design review). What is actually shared is pure
 * array arithmetic that separates each local/global row-height policy from the box tree.
 * When, over which range, and in which order it applies differ between Incremental
 * (IncrementalTableBuilder) and Retained (RetainedTableBuilder).
 * Callers build arrays from row boxes and write the results back (only the data source differs
 * by builder: the same division of responsibilities as CellContent.complementRowspan).
 * </p>
 * <p>
 * Conceptually, there are two categories (the class itself was not split as of 2026-07-19):
 * <ul>
 * <li><b>Local (self-contained within a window, shared by Incremental/Retained)</b>: {@link #rowSpec},
 * {@link #distributeSpannedRowSizes} (only the range of open rowspans),
 * {@link #distributeGroupSize} (within one row-group; even Incremental retains an entire row-group
 * with an absolute height; see P0-2).</li>
 * <li><b>Whole table (Retained only. With specifiedPageSize=0, i.e., table height auto,
 * these are identically no-ops, so Incremental effectively does not call them)</b>:
 * {@link #distributePercentRowSizes}, {@link #distributeTableSize}.</li>
 * </ul>
 * </p>
 */
public final class RowLayoutEngine {
	private RowLayoutEngine() {
		// engine
	}

	/**
	 * Result of deriving a specified row height.
	 *
	 * @param size  row height resolved from the specification and min/max (0 for auto or %)
	 * @param ratio ratio for a % specification (0 if absent)
	 * @param auto  automatic height (0% is also treated as automatic)
	 */
	public record RowSpec(double size, double ratio, boolean auto) {
	}

	/**
	 * Derives the specified row height (consolidates identical switches in both builders).
	 * ABSOLUTE uses the specified value; % becomes a ratio; 0% and AUTO are automatic rows.
	 * Clamps to ABSOLUTE min/max specifications.
	 */
	public static RowSpec rowSpec(final net.zamasoft.foliojet.layout.box.params.InnerTableParams rowParams) {
		double rowSize;
		double ratio = 0;
		boolean auto = false;
		switch (rowParams.size.getType()) {
		case ABSOLUTE:
			rowSize = rowParams.size.getLength();
			break;
		case RELATIVE:
			ratio = rowParams.size.getLength();
			if (ratio > 0) {
				rowSize = 0;
				break;
			}
		case AUTO:
			auto = true;
			rowSize = 0;
			break;
		case MIXED:
			// A row height mixing an absolute length and percentage in calc() (e.g., calc(50% + 10pt))
			// does not fit RowSpec's assumption of either an absolute value or a ratio
			// (uses of ratio in the row-height distribution algorithm assume a ratio alone).
			// Conservatively treat it like RELATIVE (ignore the absolute component and pass only
			// the ratio component to ratio; see the development plan).
			ratio = rowParams.size.getRatio();
			if (ratio > 0) {
				rowSize = 0;
				break;
			}
			auto = true;
			rowSize = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		switch (rowParams.minSize.getType()) {
		case ABSOLUTE:
			rowSize = Math.max(rowParams.minSize.getLength(), rowSize);
			break;
		case RELATIVE:
		case MIXED:
		case AUTO:
			break;
		default:
			throw new IllegalStateException();
		}
		switch (rowParams.maxSize.getType()) {
		case ABSOLUTE:
			rowSize = Math.min(rowParams.maxSize.getLength(), rowSize);
			break;
		case RELATIVE:
		case MIXED:
		case AUTO:
			break;
		default:
			throw new IllegalStateException();
		}
		return new RowSpec(rowSize, ratio, auto);
	}

	/**
	 * Distributes a row group's specified height to its rows (consolidates the identical algorithm
	 * in both builders). If the sum of row heights is less than specified, scales proportionally;
	 * if the sum is 0, distributes equally (the denominator is the group's own row count.
	 * Old TwoPass divided by the entire table's row count, so the sum did not reach the specified
	 * height. This branch is hard to reach in normal documents and was not confirmed to fire
	 * in fixtures. Normalized and consolidated).
	 *
	 * @param rowSizes  height of each row (input/output)
	 * @param groupSize the row group's specified height
	 * @return the increase in total row height
	 */
	public static double distributeGroupSize(final double[] rowSizes, final double groupSize) {
		double sum = 0;
		for (final double s : rowSizes) {
			sum += s;
		}
		if (groupSize <= sum) {
			return 0;
		}
		double added = 0;
		for (int i = 0; i < rowSizes.length; ++i) {
			final double size = sum == 0 ? groupSize / rowSizes.length : rowSizes[i] * groupSize / sum;
			added += size - rowSizes[i];
			rowSizes[i] = size;
		}
		return added;
	}

	/**
	 * Expands heights of rows with % specifications toward the table's specified height
	 * (consumes the remainder in document order).
	 *
	 * @param rowSizes          height of each row (input/output)
	 * @param rowRatios         ratios of rows with % specifications (0 if absent)
	 * @param specifiedPageSize the table's specified height
	 * @param remainder         remainder available for distribution
	 * @return the increase in total row height
	 */
	public static double distributePercentRowSizes(final double[] rowSizes, final double[] rowRatios,
			final double specifiedPageSize, double remainder) {
		double added = 0;
		for (int i = 0; i < rowSizes.length && remainder > 0; ++i) {
			if (rowRatios[i] > 0) {
				final double diff = Math.min(remainder, specifiedPageSize * rowRatios[i] - rowSizes[i]);
				if (diff > 0) {
					remainder -= diff;
					rowSizes[i] += diff;
					added += diff;
				}
			}
		}
		return added;
	}

	/**
	 * Distributes the shortfall from the table's specified height to rows. With both automatic and
	 * fixed rows, distributes to automatic rows in proportion to their current heights (equally if
	 * their sum is 0). Otherwise, scales all rows proportionally to the specified height (equally if
	 * the sum is 0).
	 *
	 * @param rowSizes          height of each row (input/output)
	 * @param autoRows          rows whose specified height is auto (excludes %0;
	 *                          differs from the autoRows criterion for rowspan distribution)
	 * @param specifiedPageSize the table's specified height
	 */
	public static void distributeTableSize(final double[] rowSizes, final boolean[] autoRows,
			final double specifiedPageSize) {
		double rowSizeSum = 0;
		int autoRowCount = 0;
		for (int i = 0; i < rowSizes.length; ++i) {
			rowSizeSum += rowSizes[i];
			if (autoRows[i]) {
				++autoRowCount;
			}
		}
		if (rowSizeSum >= specifiedPageSize) {
			return;
		}
		if (autoRowCount > 0 && autoRowCount < rowSizes.length) {
			// If there are rows with fixed heights
			final double remainder = specifiedPageSize - rowSizeSum;
			double autoSum = 0;
			for (int i = 0; i < rowSizes.length; ++i) {
				if (autoRows[i]) {
					autoSum += rowSizes[i];
				}
			}
			for (int i = 0; i < rowSizes.length; ++i) {
				if (autoRows[i]) {
					rowSizes[i] += autoSum <= 0 ? remainder / autoRowCount : remainder * rowSizes[i] / autoSum;
				}
			}
		} else {
			for (int i = 0; i < rowSizes.length; ++i) {
				rowSizes[i] = rowSizeSum <= 0 ? specifiedPageSize / rowSizes.length
						: specifiedPageSize * rowSizes[i] / rowSizeSum;
			}
		}
	}

	/**
	 * Extracts the measured page-axis size from resolved outer cell sizes in the Incremental rowspan
	 * window. The page axis is physical width in vertical writing and physical height in horizontal writing.
	 */
	public static double measuredRowspanPageSize(final net.zamasoft.foliojet.layout.box.impl.TableCellBox cellBox,
			final boolean vertical) {
		return vertical ? cellBox.getWidth() : cellBox.getHeight();
	}

	/**
	 * Required page-axis size of a cell (A-4, 2026-07-30; consolidates equivalent calculations
	 * in both builders): the larger of the measured value and the ABSOLUTE specification
	 * (plus the frame for content-box). Preserves the old order of operations.
	 */
	public static double demandPageSize(final double measured,
			final net.zamasoft.foliojet.layout.box.params.BlockParams cellParams,
			final net.zamasoft.foliojet.layout.box.impl.TableCellBox cellBox, final boolean vertical) {
		double cellSize = measured;
		if (vertical) {
			if (cellParams.size.getWidthType() == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				double width = cellParams.size.getWidth();
				if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
					width += cellBox.getFrame().getFrameWidth();
				}
				cellSize = Math.max(cellSize, width);
			}
		} else {
			if (cellParams.size.getHeightType() == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				double height = cellParams.size.getHeight();
				if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
					height += cellBox.getFrame().getFrameHeight();
				}
				cellSize = Math.max(cellSize, height);
			}
		}
		return cellSize;
	}

	/**
	 * Registers a rowspan distribution request (A-4; combines identical (row,span) pairs into one,
	 * taking the maximum requested value; consolidates equivalent registration in both builders).
	 */
	public static void addSpannedDemand(final java.util.Map<Rowspan, Rowspan> rowspans,
			final java.util.List<Rowspan> rowspanList, final int row, final int span, final double size) {
		final Rowspan key = new Rowspan(row, span);
		Rowspan rowspan = rowspans.get(key);
		if (rowspan == null) {
			rowspan = key;
			rowspans.put(key, rowspan);
			rowspanList.add(rowspan);
		}
		rowspan.min = Math.max(rowspan.min, size);
	}

	/**
	 * Distributes heights of rows connected by rowspan (consolidates the identical algorithm
	 * in both builders). For each span, if the total row height over the span falls short of the
	 * spanning cell's requirement (min), distributes the shortfall in priority order:
	 * (1) apply ratios to rows with % specifications → (2) automatic rows expanded only by spanning
	 * → (3) automatic rows → (4) all rows.
	 *
	 * @param rowSizes    height of each row (input/output)
	 * @param rowspanList spans (row=starting row, span=span count, min=required height).
	 *                    Must be sorted by Rowspan.SPAN_COMPARATOR
	 * @param noAdjRows   rows containing non-spanning cells
	 * @param autoRows    rows with automatic heights
	 * @param rowRatios   ratios of rows with % specifications (0 if absent)
	 */
	public static void distributeSpannedRowSizes(final double[] rowSizes, final List<Rowspan> rowspanList,
			final boolean[] noAdjRows, final boolean[] autoRows, final double[] rowRatios) {
		for (int j = 0; j < rowspanList.size(); ++j) {
			final Rowspan rowspan = (Rowspan) rowspanList.get(j);
			double minSum = rowSizes[rowspan.row];
			for (int k = 1; k < rowspan.span; ++k) {
				final int kk = rowspan.row + k;
				if (kk < rowSizes.length) {
					minSum += rowSizes[kk];
				}
			}
			double minRem = rowspan.min - minSum;
			if (minRem > 0) {
				// Distribute min
				double adjCount = 0, autoCount = 0;
				for (int k = 0; k < rowspan.span; ++k) {
					final int kk = rowspan.row + k;
					if (kk >= rowSizes.length) {
						break;
					}
					if (!noAdjRows[kk] && autoRows[kk]) {
						++adjCount;
					}
					if (autoRows[kk]) {
						++autoCount;
					}
					// Apply %
					if (rowRatios[kk] > 0) {
						final double diff = minRem * rowRatios[kk];
						minRem -= diff;
						rowSizes[kk] += diff;
					}
				}
				if (adjCount > 0 && adjCount < rowspan.span) {
					// Distribute to rows containing only cells expanded by spanning
					minRem /= adjCount;
					for (int k = 0; k < rowspan.span; ++k) {
						final int kk = rowspan.row + k;
						if (kk >= rowSizes.length) {
							break;
						}
						if (!noAdjRows[kk] && autoRows[kk]) {
							rowSizes[kk] += minRem;
						}
					}
				} else if (autoCount > 0 && autoCount < rowspan.span) {
					// Distribute to rows with automatic heights
					minRem /= autoCount;
					for (int k = 0; k < rowspan.span; ++k) {
						final int kk = rowspan.row + k;
						if (kk >= rowSizes.length) {
							break;
						}
						if (autoRows[kk]) {
							rowSizes[kk] += minRem;
						}
					}
				} else {
					// Distribute height
					minRem /= rowspan.span;
					for (int k = 0; k < rowspan.span; ++k) {
						final int kk = rowspan.row + k;
						if (kk >= rowSizes.length) {
							break;
						}
						rowSizes[kk] += minRem;
					}
				}
			}
		}
	}
}
