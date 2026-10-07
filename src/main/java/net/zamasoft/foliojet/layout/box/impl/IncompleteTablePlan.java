package net.zamasoft.foliojet.layout.box.impl;

/**
 * Operation history for a single body group split at row boundaries. Keeps all Pass B row heights
 * and separates the actual box range [start, visibleEnd) from the virtual full remainder [start, end).
 * Do not set unbound row heights on actual boxes. Connection to Retained is B-2b-2.
 * Shares the row-height array with continuation plans, but retains no row, cell, or preceding-fragment boxes.
 */
public final class IncompleteTablePlan {

	public enum SplitKind {
		AUTO, FORCED
	}

	/** Finalized emission range [start, cut), and virtual dimensions immediately before and after splitting. */
	public record Cut(int start, int cut, int visibleEnd, int end, SplitKind kind,
			double groupBefore, double tableBefore, double groupKeep, double tableKeep,
			double groupNext, double tableNext) {
	}

	private final double[] rowSizes;
	private final int start;
	private final double headerSize, groupSize, tableSize;
	private int visibleEnd;
	private double visibleGroupSize;
	private Cut cut;

	/** Header height comes from the completed shared header. Add initial table height in header → body order. */
	public IncompleteTablePlan(final double[] rowSizes, final double headerSize) {
		this.rowSizes = rowSizes.clone();
		if (rowSizes.length == 0 || !Double.isFinite(headerSize) || headerSize < 0) {
			throw new IllegalArgumentException("Expected nonempty rows and a finite header size");
		}
		double size = 0;
		for (final double rowSize : this.rowSizes) {
			if (!Double.isFinite(rowSize) || rowSize < 0) {
				throw new IllegalArgumentException("Expected finite nonnegative row sizes");
			}
			size += rowSize;
		}
		this.start = 0;
		this.headerSize = headerSize;
		this.groupSize = size;
		this.tableSize = (0 + headerSize) + size;
	}

	private IncompleteTablePlan(final IncompleteTablePlan previous, final Cut cut) {
		this.rowSizes = previous.rowSizes;
		this.start = cut.cut();
		this.visibleEnd = this.start;
		this.headerSize = previous.headerSize;
		this.groupSize = cut.groupNext();
		this.tableSize = cut.tableNext();
	}

	public int start() {
		return this.start;
	}

	public int visibleEnd() {
		return this.visibleEnd;
	}

	public int end() {
		return this.rowSizes.length;
	}

	public double groupSize() {
		return this.groupSize;
	}

	public double tableSize() {
		return this.tableSize;
	}

	public double headerSize() {
		return this.headerSize;
	}

	public Cut cut() {
		return this.cut;
	}

	public double visibleGroupSize() {
		return this.cut != null ? this.cut.groupKeep()
				: this.visibleEnd == this.end() ? this.groupSize : this.visibleGroupSize;
	}

	public double visibleTableSize() {
		return this.cut != null ? this.cut.tableKeep()
				: this.visibleEnd == this.end() ? this.tableSize : (0 + this.headerSize) + this.visibleGroupSize;
	}

	/** Checks appends and updates the visible range. Scans the unprocessed tail only when finalizing the cut. */
	void rowsAppended(final TableRowGroupBox body) {
		final int end = this.start + body.getTableRowCount();
		if (this.cut != null || end < this.visibleEnd || end > this.end()) {
			throw new IllegalStateException("Rows outside the active incomplete table plan");
		}
		for (int i = this.visibleEnd; i < end; ++i) {
			this.requireRowSize(body, i);
			this.visibleGroupSize += this.rowSizes[i];
		}
		this.visibleEnd = end;
	}

	private void requireRowSize(final TableRowGroupBox body, final int row) {
		if (Double.doubleToLongBits(body.getTableRow(row - this.start).getPageSize())
				!= Double.doubleToLongBits(this.rowSizes[row])) {
			throw new IllegalStateException("Row splitting or changed row sizes are outside the numeric plan");
		}
	}

	/** Before TableRowGroupBox.split returns, finalizes subtraction from the same start as the full remainder. */
	IncompleteTablePlan split(final TableRowGroupBox kept, final TableRowGroupBox next, final SplitKind kind) {
		final int k = this.start + kept.getTableRowCount();
		if (this.cut != null || k <= this.start || k >= this.visibleEnd
				|| k + next.getTableRowCount() != this.visibleEnd) {
			throw new IllegalStateException("Expected a split between visible rows");
		}
		for (int i = this.start; i < k; ++i) {
			this.requireRowSize(kept, i);
		}
		double keep = this.groupSize;
		double moved = 0;
		for (int i = k; i < this.end(); ++i) {
			keep -= this.rowSizes[i];
			moved += this.rowSizes[i];
		}
		final double tableKeep = kind == SplitKind.FORCED ? this.tableSize - moved
				: this.tableSize - (this.groupSize - keep);
		this.cut = new Cut(this.start, k, this.visibleEnd, this.end(), kind, this.groupSize, this.tableSize,
				keep, tableKeep, moved, (0 + this.headerSize) + moved);
		final IncompleteTablePlan remainder = new IncompleteTablePlan(this, this.cut);
		remainder.rowsAppended(next);
		this.visibleEnd = k;
		return remainder;
	}
}
