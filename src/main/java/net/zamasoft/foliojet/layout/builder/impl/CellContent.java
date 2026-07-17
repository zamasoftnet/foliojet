package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;

/**
 * 構築中のセル内容です(P2-2: §5.2b 表ビルダー統一の共通 model 第一片。
 * OnePass/TwoPass 両ビルダーの同名内部クラスの統合 — 差は colspan を
 * 引数で受けるか pos から読むかのコンストラクタだけだった)。
 *
 * <p>
 * 実体は「実測ビルダー(未確定内容)」または「連結の続き
 * (rowspan の2行目以降 = extended、確定済みセルボックス)」の
 * どちらか。§5.2b の統一では CellReplayHandle を加えた
 * CellContent interface(measure/bind/replayHandle)へ発展させる。
 * </p>
 */
class CellContent {
	private final Object cell;

	public final int rowspan, colspan;

	/**
	 * 実測ビルダーから(colspan は pos から読む)。
	 */
	public CellContent(TwoPassBlockBuilder cellBuilder) {
		this(cellBuilder, ((TableCellBox) cellBuilder.getRootBox()).getTableCellPos().colspan);
	}

	/**
	 * 実測ビルダーから(colspan を明示。固定レイアウトの列切り詰め用)。
	 */
	public CellContent(TwoPassBlockBuilder cellBuilder, int colspan) {
		this.cell = cellBuilder;
		this.rowspan = ((TableCellBox) cellBuilder.getRootBox()).getTableCellPos().rowspan;
		this.colspan = colspan;
	}

	/**
	 * 連結の続き(確定済みセルボックス)から。
	 */
	public CellContent(TableCellBox cell, int rowspan, int colspan) {
		assert rowspan >= 1;
		assert colspan >= 1;
		this.cell = cell;
		this.rowspan = rowspan;
		this.colspan = colspan;
	}

	/**
	 * rowspan で連結されたセルの補完です(P2-2 共有核。両ビルダーの
	 * 同一アルゴリズムの統合 — 差はリストの出所だけだった)。
	 * 前行のセル列を参照し、rowspan が続くセルの継続 CellContent を
	 * 当行へ追加する。
	 *
	 * @param cells      当行のセル列(追記される)
	 * @param upperCells 前行のセル列
	 */
	static void complementRowspan(final java.util.List<CellContent> cells, final java.util.List<?> upperCells) {
		while (upperCells.size() > cells.size()) {
			final CellContent upperCell = (CellContent) upperCells.get(cells.size());
			if (upperCell.rowspan > 1) {
				for (int colspan = upperCell.colspan; colspan >= 1; --colspan) {
					cells.add(new CellContent(upperCell.getCellBox(), upperCell.rowspan - 1, colspan));
				}
			} else {
				break;
			}
		}
	}

	/**
	 * 行のベースライン(先頭アセントの最大)を求めます(P2-4 共有核。
	 * 3箇所の同一ループの統合 — 連結の続きは持ち主の行で数える)。
	 */
	static double maxFirstAscent(final java.util.List<CellContent> cells) {
		double rowAscent = 0;
		for (int i = 0; i < cells.size(); ++i) {
			final CellContent cell = cells.get(i);
			if (cell.isExtended()) {
				continue;
			}
			final double firstAscent = cell.getCellBox().getFirstAscent();
			if (!net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(firstAscent) && firstAscent > rowAscent) {
				rowAscent = firstAscent;
			}
		}
		return rowAscent;
	}

	/**
	 * 行高をセルへ適用します(P2-5 (c) 共有核。3箇所の同一処理の統合)。
	 * 非連結セルごとに連結範囲の行高合計をページ方向寸法として設定し、
	 * 縦位置合わせを行う。
	 *
	 * @param cells     行のセル列
	 * @param rowSizes  行高(単位または行グループの窓)
	 * @param rowIndex  当行の窓内位置
	 * @param rowAscent 行のベースライン(NaN なら適用済みとして省略)
	 * @param vertical  縦書きであれば true
	 */
	static void applyCellExtents(final java.util.List<CellContent> cells, final double[] rowSizes, final int rowIndex,
			final double rowAscent, final boolean vertical) {
		for (int k = 0; k < cells.size(); ++k) {
			final CellContent cell = cells.get(k);
			if (cell.isExtended()) {
				continue;
			}
			final TableCellBox cellBox = cell.getCellBox();
			double size = 0;
			final int rowspan = Math.min(rowSizes.length - rowIndex, cellBox.getTableCellPos().rowspan);
			for (int l = 0; l < rowspan; ++l) {
				size += rowSizes[rowIndex + l];
			}
			if (!Double.isNaN(rowAscent)) {
				cellBox.baseline(rowAscent);
			}
			if (vertical) {
				cellBox.setWidth(size);
			} else {
				cellBox.setHeight(size);
			}
			cellBox.verticalAlign();
		}
	}

	public boolean isExtended() {
		return this.cell instanceof TableCellBox;
	}

	public TwoPassBlockBuilder getBuilder() {
		return (TwoPassBlockBuilder) this.cell;
	}

	public TableCellBox getCellBox() {
		if (this.isExtended()) {
			return (TableCellBox) this.cell;
		}
		return (TableCellBox) this.getBuilder().getRootBox();
	}
}
