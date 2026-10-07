package net.zamasoft.foliojet.layout.sizing;

/**
 * Calculates placement in a fixed-column Grid (Grid G1a, 2026-07-31:
 * consult-codex-2026-07-31-grid-g1.txt §2.2/§5). A pure calculation independent of boxes:
 * source-order row auto-placement (row=index/column count, col=index%column count),
 * row height=maximum actual item height in the row, with gaps applied.
 *
 * @author MIYABE Tatsuhiko
 */
public final class FixedGridLayout {

	private final double[] columnWidths;

	private final double columnGap, rowGap;

	public FixedGridLayout(final double[] columnWidths, final double columnGap, final double rowGap) {
		if (columnWidths.length == 0) {
			throw new IllegalArgumentException("no columns");
		}
		this.columnWidths = columnWidths.clone();
		this.columnGap = columnGap;
		this.rowGap = rowGap;
	}

	public int columnCount() {
		return this.columnWidths.length;
	}

	public double columnWidth(final int index) {
		return this.columnWidths[index % this.columnWidths.length];
	}

	/** Returns the column index for an item's source index. */
	public int columnOf(final int sourceIndex) {
		return sourceIndex % this.columnWidths.length;
	}

	/** Returns the row index for an item's source index. */
	public int rowOf(final int sourceIndex) {
		return sourceIndex / this.columnWidths.length;
	}

	/** A column's line-axis start position (sum of preceding column widths and gaps). */
	public double columnStart(final int columnIndex) {
		double start = 0;
		for (int i = 0; i < columnIndex; ++i) {
			start += this.columnWidths[i] + this.columnGap;
		}
		return start;
	}

	/**
	 * Placement result.
	 *
	 * @param rowStarts   Page-axis start position of each row
	 * @param rowHeights  Height of each row (maximum actual item height in the row)
	 * @param totalExtent Total page-axis height including gaps
	 */
	public record Placement(double[] rowStarts, double[] rowHeights, double totalExtent) {
	}

	/**
	 * Resolves row heights, row starts, and total height from actual heights of all items.
	 *
	 * @param itemExtents Actual page-axis height of each item in source order
	 * @return Placement result (empty with total height 0 if there are no items)
	 */
	public Placement place(final double[] itemExtents) {
		final int rows = (itemExtents.length + this.columnWidths.length - 1) / this.columnWidths.length;
		final double[] rowHeights = new double[rows];
		for (int i = 0; i < itemExtents.length; ++i) {
			final int row = this.rowOf(i);
			rowHeights[row] = Math.max(rowHeights[row], itemExtents[i]);
		}
		final double[] rowStarts = new double[rows];
		double cursor = 0;
		for (int r = 0; r < rows; ++r) {
			rowStarts[r] = cursor;
			cursor += rowHeights[r];
			if (r < rows - 1) {
				cursor += this.rowGap;
			}
		}
		return new Placement(rowStarts, rowHeights, cursor);
	}
}
