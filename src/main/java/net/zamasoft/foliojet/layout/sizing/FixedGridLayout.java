package net.zamasoft.foliojet.layout.sizing;

/**
 * 固定列Gridの配置計算です(Grid G1a、2026-07-31——
 * consult-codex-2026-07-31-grid-g1.txt §2.2/§5)。boxに依存しない
 * 純粋計算: source-order row auto-placement(row=index/列数、
 * col=index%列数)、行高=行内itemの実高の最大、gap適用。
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

	/** itemのsource indexから列indexを返します。 */
	public int columnOf(final int sourceIndex) {
		return sourceIndex % this.columnWidths.length;
	}

	/** itemのsource indexから行indexを返します。 */
	public int rowOf(final int sourceIndex) {
		return sourceIndex / this.columnWidths.length;
	}

	/** 列の行方向開始位置(先行列幅+gapの合計)です。 */
	public double columnStart(final int columnIndex) {
		double start = 0;
		for (int i = 0; i < columnIndex; ++i) {
			start += this.columnWidths[i] + this.columnGap;
		}
		return start;
	}

	/**
	 * 配置結果です。
	 *
	 * @param rowStarts   各行のページ方向開始位置
	 * @param rowHeights  各行の高さ(行内itemの実高の最大)
	 * @param totalExtent gap込みのページ方向総高
	 */
	public record Placement(double[] rowStarts, double[] rowHeights, double totalExtent) {
	}

	/**
	 * 全itemの実高から行高・行開始・総高を解決します。
	 *
	 * @param itemExtents source-order各itemのページ方向実高
	 * @return 配置結果(item無しなら総高0の空)
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
