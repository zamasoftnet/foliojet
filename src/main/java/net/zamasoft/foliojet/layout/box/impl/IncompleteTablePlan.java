package net.zamasoft.foliojet.layout.box.impl;

/**
 * 本文1グループ・行境界分割の演算履歴です。Pass B の全行高を保持し、
 * 実箱の範囲 [start, visibleEnd) と仮想の全残余 [start, end) を分けます。
 * 未 bind 行の高さを実箱へ設定してはいけません。Retained への接続は B-2b-2。
 * 行高配列は継続計画と共有しますが、行・セル・前断片の箱は保持しません。
 */
public final class IncompleteTablePlan {

	public enum SplitKind {
		AUTO, FORCED
	}

	/** 確定した送出範囲 [start, cut) と、分割直前・直後の仮想寸法です。 */
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

	/** ヘッダ高は完成した共有ヘッダの値。初期の表高は header → body の順で加算します。 */
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

	/** 追記検査と可視範囲の更新。未処理末尾の走査は切断確定時だけ行います。 */
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

	/** TableRowGroupBox.split の返却前に、全残余と同じ先頭からの減算を確定します。 */
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
