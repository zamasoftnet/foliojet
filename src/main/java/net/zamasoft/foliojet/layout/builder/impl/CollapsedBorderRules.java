package net.zamasoft.foliojet.layout.builder.impl;

import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
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
	 * 行単位で蓄積した境界(行=リスト、列=配列)を TableCollapsedBorders の
	 * 列優先配列へ転置した行グループ分です。
	 */
	record GroupBorders(double[] rowSizes, Border[][] hborders, Border[][] vborders) {
		static final GroupBorders NONE = new GroupBorders(null, null, null);

		static GroupBorders of(final double[] rowSizes, final List<Border[]> hborders, final List<Border[]> vborders,
				final int columnCount) {
			if (hborders == null || hborders.isEmpty()) {
				return NONE;
			}
			final int groupRowCount = vborders.size();
			final Border[][] h = new Border[columnCount][groupRowCount + 1];
			final Border[][] v = new Border[groupRowCount][];
			for (int i = 0; i < groupRowCount; ++i) {
				final Border[] border = hborders.get(i);
				for (int j = 0; j < columnCount; ++j) {
					h[j][i] = border[j];
				}
				v[i] = vborders.get(i);
			}
			final Border[] border = hborders.get(groupRowCount);
			for (int j = 0; j < columnCount; ++j) {
				h[j][rowSizes.length] = border[j];
			}
			return new GroupBorders(rowSizes, h, v);
		}
	}

	/**
	 * 分離境界のセル間隔(境界間隔の半分)です。
	 */
	static AbsoluteInsets separateSpacing(final TableParams tableParams) {
		final double v = tableParams.borderSpacingV / 2.0;
		final double h = tableParams.borderSpacingH / 2.0;
		return new AbsoluteInsets(v, h, v, h);
	}

	/**
	 * つぶし境界のセル間隔です(グリッド読み — 全表の境界確定後)。
	 * 連結範囲の境界半幅の最大を各辺に採る。
	 */
	static AbsoluteInsets gridSpacing(final TableCollapsedBorders borders, final int row, final int col,
			final int rowspan, final int colspan, final int rowCount, final int columnCount, final boolean vertical) {
		double pageFirst = 0, lineEnd = 0, pageLast = 0, lineStart = 0;
		final int bottomIndex = row + rowspan;
		for (int k = 0; k < colspan; ++k) {
			final int kk = col + k;
			if (kk >= columnCount) {
				break;
			}
			pageFirst = Math.max(pageFirst, halfWidth(borders.getHBorder(kk, row)));
			if (bottomIndex <= rowCount) {
				pageLast = Math.max(pageLast, halfWidth(borders.getHBorder(kk, bottomIndex)));
			}
		}
		final int rightIndex = col + colspan;
		for (int k = 0; k < rowspan; ++k) {
			final int kk = row + k;
			if (kk >= rowCount) {
				break;
			}
			lineStart = Math.max(lineStart, halfWidth(borders.getVBorder(kk, col)));
			if (rightIndex <= columnCount) {
				lineEnd = Math.max(lineEnd, halfWidth(borders.getVBorder(kk, rightIndex)));
			}
		}
		return spacing(pageFirst, lineEnd, pageLast, lineStart, vertical);
	}

	/**
	 * 境界の半幅を返します。{@code null}は「そこに境界がない」を表す正当な
	 * 値なので0を返します(2026-07-25、ランダム文書生成で発見)。
	 *
	 * <p>
	 * {@link TableCollapsedBorders#getHBorder}/{@link
	 * TableCollapsedBorders#getVBorder}は、ヘッダ・フッタ・本体の境目で
	 * 両側の配列が空のとき{@code null}を返します。他の読み手——
	 * {@link #streamSpacing}・{@code BorderRenderer}・{@code TableBox}の
	 * 診断出力——はいずれも{@code null}検査を持っており、
	 * <b>{@link #gridSpacing}だけが素で参照していた</b>。
	 * 表がページ分割されて継続表の境界グリッドが縮む場合などに
	 * {@code NullPointerException}になる。
	 * </p>
	 */
	private static double halfWidth(final Border border) {
		return border == null ? 0 : border.width / 2.0;
	}

	/**
	 * つぶし境界のセル間隔です(ストリーム読み — 行単位蓄積の窓)。
	 * グリッド読みと同じ規則を蓄積リストから読む。
	 */
	static AbsoluteInsets streamSpacing(final List<Border[]> hborders, final List<Border[]> vborders,
			final int borderRow, final int col, final int rowspan, final int colspan, final int columnCount,
			final boolean vertical) {
		double pageFirst = 0, lineEnd = 0, pageLast = 0, lineStart = 0;
		final Border[] prevBorder = hborders.get(borderRow - 1);
		final Border[] nextBorder = hborders.get(Math.min(hborders.size() - 1, borderRow + rowspan - 1));
		for (int k = 0; k < colspan; ++k) {
			final int kk = col + k;
			if (kk >= columnCount) {
				break;
			}
			if (prevBorder[kk] != null) {
				pageFirst = Math.max(pageFirst, prevBorder[kk].width / 2.0);
			}
			if (nextBorder[kk] != null) {
				pageLast = Math.max(pageLast, nextBorder[kk].width / 2.0);
			}
		}
		for (int k = 0; k < rowspan; ++k) {
			final int rr = borderRow - 1 + k;
			if (rr >= vborders.size()) {
				break;
			}
			final Border[] rowLine = vborders.get(rr);
			// 列数を超える colspan では添字が溢れうる。gridSpacing 側には
			// `rightIndex <= columnCount` のガードがあるのに、こちらだけ
			// 無かった(2026-07-26、独立レビュー指摘)。
			// **再現は取れていない**——上流(IncrementalTableBuilder の
			// TABLE_CELL 追加)で colspan が残り列数へ丸められるため、
			// 現状この経路へ溢れた値は届かない。ただし同型の欠落は
			// collapseRow では実際に到達し ArrayIndexOutOfBounds になった
			// (5000シードの掃過で検出)ので、防御として揃えておく
			if (col < rowLine.length && rowLine[col] != null) {
				lineStart = Math.max(lineStart, rowLine[col].width / 2.0);
			}
			final int rightIndex = col + colspan;
			if (rightIndex < rowLine.length && rowLine[rightIndex] != null) {
				lineEnd = Math.max(lineEnd, rowLine[rightIndex].width / 2.0);
			}
		}
		return spacing(pageFirst, lineEnd, pageLast, lineStart, vertical);
	}

	private static AbsoluteInsets spacing(final double pageFirst, final double lineEnd, final double pageLast,
			final double lineStart, final boolean vertical) {
		if (vertical) {
			return new AbsoluteInsets(lineStart, pageFirst, lineEnd, pageLast);
		}
		return new AbsoluteInsets(pageFirst, lineEnd, pageLast, lineStart);
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
			final TableRowBox nextRowBox, final List<CellContent> nextCells, final boolean tableFirst,
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
		// 境界の配列は**列数**の長さ。行のセル数はそれを超えうる
		// (連結の繰り越しが列数を押し出す)ため、必ず頭打ちにする
		// (2026-07-25、セル連結のランダム検査で ArrayIndexOutOfBounds を検出)
		for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
			final CellContent cell = cells.get(j);
			if (cell.rowspan == 1) {
				lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
						ax.hEnd().apply(rowParams.border));
			}
		}
		// 次の行の上
		if (hasNextRow) {
			final InnerTableParams nextRowParams = nextRowBox.getInnerTableParams();
			for (int j = 0, n = Math.min(nextCells.size(), columnCount); j < n; ++j) {
				final CellContent nextCell = nextCells.get(j);
				final TableCellPos cellPos = nextCell.getCellBox().getTableCellPos();
				if (nextCell.rowspan == cellPos.rowspan) {
					lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
							ax.hStart().apply(nextRowParams.border));
				}
			}
		}
		if (groupFirst && rowFirst) {
			// 最初の行の上
			for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
				firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
						ax.hStart().apply(rowParams.border));
			}
		}

		// セル境界
		for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
			final CellContent cell = cells.get(j);
			final BlockParams cellParams = cell.getCellBox().getBlockParams();
			lineBorder[j] = TableCollapsedBorders.collapseBorder(lineBorder[j],
					ax.vStart().apply(cellParams.frame.border));
			// 連結内側の V 線は非表示だが非 null を保証する(不正な表で
			// rowspan が連結内側を跨いだときの読み取りに備える)。
			// lineBorder は列数+1(列と列の間)なので、そちらでも頭打ちにする
			for (int l = 1; l < cell.colspan && j + l < lineBorder.length; ++l) {
				lineBorder[j + l] = TableCollapsedBorders.collapseBorder(lineBorder[j + l], Border.NONE_BORDER);
			}
			j += cell.colspan - 1;
			if (j + 1 < lineBorder.length) {
				lineBorder[j + 1] = TableCollapsedBorders.collapseBorder(lineBorder[j + 1],
						ax.vEnd().apply(cellParams.frame.border));
			}
		}
		if (groupFirst && rowFirst) {
			// 最初の行の上
			for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
				final CellContent cell = cells.get(j);
				final BlockParams cellParams = cell.getCellBox().getBlockParams();
				firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
						ax.hStart().apply(cellParams.frame.border));
			}
		}
		for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
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
			for (int j = 0, n = Math.min(nextCells.size(), columnCount); j < n; ++j) {
				final CellContent cell = nextCells.get(j);
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
