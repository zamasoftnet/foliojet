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
