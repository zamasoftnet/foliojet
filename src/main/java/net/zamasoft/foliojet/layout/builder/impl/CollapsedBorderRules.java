package net.zamasoft.foliojet.layout.builder.impl;

import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.part.TableCollapsedBorders;

/**
 * つぶし境界の適用規則です(P2-5 (b): §5.2b 表ビルダー統一)。
 *
 * <p>
 * 「1行分の境界を H(前/後)・V 配列へ載せる」規則。層順(表→列グループ→
 * 行グループ→行→セル)は collapse の同点先勝ちに合わせて固定。OnePass の
 * ストリーミング蓄積から純化したもので、TwoPass の全表一括
 * (createBorders)をこの規則のループへ置換するのが次段。
 * </p>
 *
 * <p>
 * 行グループの前後 H はグループ境界行のみに適用し、次行 peek は
 * 実際の次行の params を使う(旧 OnePass は単位全行への適用と保留行
 * 参照の固有規約を持っていた —
 * 0330-table-border/collapse-group-inner-lines.html で是正)。
 * </p>
 */
final class CollapsedBorderRules {
	private CollapsedBorderRules() {
		// rules
	}

	/**
	 * 1行分のつぶし境界を配列へ載せます。
	 *
	 * @param firstBorder   行の前側 H 境界(列数)
	 * @param lastBorder    行の後側 H 境界(列数)
	 * @param lineBorder    行の V 境界(列数+1)
	 * @param ax            辺選択
	 * @param tableParams   表のパラメータ
	 * @param colgroup      列グループ(なければ null)
	 * @param rowGroupParams 行グループのパラメータ
	 * @param rowBox        当行
	 * @param cells         当行のセル列
	 * @param nextRowBox    次行(hasNextRow のとき非 null)
	 * @param nextCells     次行のセル列
	 * @param tableFirst    表の最初の行
	 * @param tableLast     表の最後の行
	 * @param groupFirst    行グループの先頭境界行
	 * @param groupLast     行グループの末尾境界行
	 * @param rowFirst      単位の最初の行
	 * @param hasNextRow    次行の peek を行う(グループが続くか単位内に次行)
	 * @param columnCount   列数
	 */
	static void collapseRow(final Border[] firstBorder, final Border[] lastBorder, final Border[] lineBorder,
			final BorderAxes ax, final TableParams tableParams, final TableColumnGroupBox colgroup,
			final InnerTableParams rowGroupParams, final TableRowBox rowBox, final List<CellContent> cells,
			final TableRowBox nextRowBox, final List<?> nextCells, final boolean tableFirst,
			final boolean tableLast, final boolean groupFirst, final boolean groupLast, final boolean rowFirst,
			final boolean hasNextRow, final int columnCount) {
		// テーブル境界
		lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0],
				ax.vStart().apply(tableParams.frame.border));
		lineBorder[lineBorder.length - 1] = TableCollapsedBorders.collapseBorder(lineBorder[lineBorder.length - 1],
				ax.vEnd().apply(tableParams.frame.border));
		if (tableFirst) {
			for (int i = 0; i < firstBorder.length; ++i) {
				firstBorder[i] = TableCollapsedBorders.collapseBorder(firstBorder[i],
						ax.hStart().apply(tableParams.frame.border));
			}
		}
		if (tableLast) {
			for (int i = 0; i < lastBorder.length; ++i) {
				lastBorder[i] = TableCollapsedBorders.collapseBorder(lastBorder[i],
						ax.hEnd().apply(tableParams.frame.border));
			}
		}

		// カラムグループ境界
		// カラム境界
		if (colgroup != null) {
			colgroup.eachColumn((column, col, colspan) -> {
				final InnerTableParams colParams = column.getInnerTableParams();
				if (tableFirst) {
					for (int j = 0; j < colspan; ++j) {
						final int jj = col + j;
						firstBorder[jj] = TableCollapsedBorders.collapseBorder(firstBorder[jj],
								ax.hStart().apply(colParams.border));
					}
				}
				if (tableLast) {
					for (int j = 0; j < colspan; ++j) {
						final int jj = col + j;
						lastBorder[jj] = TableCollapsedBorders.collapseBorder(lastBorder[jj],
								ax.hEnd().apply(colParams.border));
					}
				}
				lineBorder[col] = TableCollapsedBorders.collapseBorder(lineBorder[col],
						ax.vStart().apply(colParams.border));
				lineBorder[col + colspan] = TableCollapsedBorders.collapseBorder(lineBorder[col + colspan],
						ax.vEnd().apply(colParams.border));
			});
		}

		// 行グループ境界
		lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0], ax.vStart().apply(rowGroupParams.border));
		lineBorder[lineBorder.length - 1] = TableCollapsedBorders.collapseBorder(lineBorder[lineBorder.length - 1],
				ax.vEnd().apply(rowGroupParams.border));
		if (groupFirst) {
			for (int j = 0; j < columnCount; ++j) {
				firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
						ax.hStart().apply(rowGroupParams.border));
			}
		}
		if (groupLast) {
			for (int j = 0; j < columnCount; ++j) {
				lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
						ax.hEnd().apply(rowGroupParams.border));
			}
		}

		// 行境界
		final InnerTableParams rowParams = rowBox.getInnerTableParams();
		lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0], ax.vStart().apply(rowParams.border));
		lineBorder[lineBorder.length - 1] = TableCollapsedBorders.collapseBorder(lineBorder[lineBorder.length - 1],
				ax.vEnd().apply(rowParams.border));
		for (int j = 0; j < cells.size(); ++j) {
			final CellContent cell = cells.get(j);
			if (cell.rowspan == 1) {
				lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
						ax.hEnd().apply(rowParams.border));
			}
		}
		// 次の行の上
		if (hasNextRow) {
			final InnerTableParams nextRowParams = nextRowBox.getInnerTableParams();
			for (int j = 0; j < nextCells.size(); ++j) {
				final CellContent nextCell = (CellContent) nextCells.get(j);
				final TableCellPos cellPos = nextCell.getCellBox().getTableCellPos();
				if (nextCell.rowspan == cellPos.rowspan) {
					lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
							ax.hStart().apply(nextRowParams.border));
				}
			}
		}
		if (groupFirst && rowFirst) {
			// 最初の行の上
			for (int j = 0; j < cells.size(); ++j) {
				firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
						ax.hStart().apply(rowParams.border));
			}
		}

		// セル境界
		for (int j = 0; j < cells.size(); ++j) {
			final CellContent cell = cells.get(j);
			final BlockParams cellParams = cell.getCellBox().getBlockParams();
			lineBorder[j] = TableCollapsedBorders.collapseBorder(lineBorder[j],
					ax.vStart().apply(cellParams.frame.border));
			j += cell.colspan - 1;
			lineBorder[j + 1] = TableCollapsedBorders.collapseBorder(lineBorder[j + 1],
					ax.vEnd().apply(cellParams.frame.border));
		}
		if (groupFirst && rowFirst) {
			// 最初の行の上
			for (int j = 0; j < cells.size(); ++j) {
				final CellContent cell = cells.get(j);
				final BlockParams cellParams = cell.getCellBox().getBlockParams();
				firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
						ax.hStart().apply(cellParams.frame.border));
			}
		}
		for (int j = 0; j < cells.size(); ++j) {
			final CellContent cell = cells.get(j);
			final BlockParams cellParams = cell.getCellBox().getBlockParams();
			if (cell.rowspan == 1) {
				lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
						ax.hEnd().apply(cellParams.frame.border));
			} else {
				lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j], Border.NONE_BORDER);
			}
		}
		// 次の行の上
		if (hasNextRow) {
			for (int j = 0; j < nextCells.size(); ++j) {
				final CellContent cell = (CellContent) nextCells.get(j);
				final BlockParams cellParams = cell.getCellBox().getBlockParams();
				if (cell.rowspan == cell.getCellBox().getTableCellPos().rowspan) {
					lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
							ax.hStart().apply(cellParams.frame.border));
				} else {
					lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j], Border.NONE_BORDER);
				}
			}
		}
	}
}
