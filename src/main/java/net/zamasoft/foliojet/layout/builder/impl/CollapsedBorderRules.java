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
 * Rules for applying collapsed borders (P2-5 (b): table builder unification in §5.2b).
 *
 * <p>
 * Rules for putting one row's borders into the H (before/after) and V arrays. The layer order
 * (table → column group → row group → row → cell) is fixed to match collapse's first-wins tie rule.
 * Extracted as pure rules from OnePass streaming accumulation; the next step is to replace
 * TwoPass whole-table processing (createBorders) with a loop over these rules.
 * </p>
 *
 * <p>
 * Apply the row group's before/after H borders only to the group's boundary rows, and use
 * the actual next row's params for next-row peeking (old OnePass had its own conventions:
 * applying borders to every row in the unit and referencing the pending row —
 * corrected in 0330-table-border/collapse-group-inner-lines.html).
 * </p>
 */
final class CollapsedBorderRules {
	private CollapsedBorderRules() {
		// rules
	}

	/**
	 * One row group's borders, transposed from row-wise accumulation (rows = list,
	 * columns = array) to the column-major arrays of TableCollapsedBorders.
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
	 * Cell spacing for separate borders (half the border spacing).
	 */
	static AbsoluteInsets separateSpacing(final TableParams tableParams) {
		final double v = tableParams.borderSpacingV / 2.0;
		final double h = tableParams.borderSpacingH / 2.0;
		return new AbsoluteInsets(v, h, v, h);
	}

	/**
	 * Cell spacing for collapsed borders (grid access, after all table borders resolve).
	 * Use the maximum border half-width over the span for each edge.
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
	 * Returns half the border width. {@code null} is a valid value meaning "no border here",
	 * so return 0 (found by random document generation, 2026-07-25).
	 *
	 * <p>
	 * {@link TableCollapsedBorders#getHBorder}/{@link
	 * TableCollapsedBorders#getVBorder} return {@code null} at header/footer/body boundaries
	 * when the arrays on both sides are empty. All other readers —
	 * {@link #streamSpacing}, {@code BorderRenderer}, and {@code TableBox} diagnostic output —
	 * check for {@code null}; <b>only {@link #gridSpacing} dereferenced it unconditionally</b>.
	 * This causes a {@code NullPointerException}, for example when a page break in a table
	 * shrinks the continuation table's border grid.
	 * </p>
	 */
	private static double halfWidth(final Border border) {
		return border == null ? 0 : border.width / 2.0;
	}

	/**
	 * Cell spacing for collapsed borders (stream access, a window of row-wise accumulation).
	 * Read the same rules as grid access from the accumulation lists.
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
			// A colspan exceeding the column count can overflow the index. gridSpacing has
			// a `rightIndex <= columnCount` guard, but this path alone
			// lacked it (independent review finding, 2026-07-26).
			// **Not reproduced**: upstream (TABLE_CELL addition in IncrementalTableBuilder)
			// clamps colspan to the remaining column count, so
			// an overflowing value currently cannot reach this path. However, the same omission
			// was actually reachable in collapseRow and caused ArrayIndexOutOfBounds
			// (detected in a 5000-seed sweep), so add the same defensive guard.
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
	 * Puts one row's collapsed borders into the arrays.
	 *
	 * @param firstBorder   H border before the row (column count)
	 * @param lastBorder    H border after the row (column count)
	 * @param lineBorder    row's V borders (column count + 1)
	 * @param ax            edge selection
	 * @param tableParams   table parameters
	 * @param colgroup      column group (null if absent)
	 * @param rowGroupParams row group parameters
	 * @param rowBox        current row
	 * @param cells         current row's cells
	 * @param nextRowBox    next row (non-null when hasNextRow is true)
	 * @param nextCells     next row's cells
	 * @param tableFirst    first row of the table
	 * @param tableLast     last row of the table
	 * @param groupFirst    first boundary row of the row group
	 * @param groupLast     last boundary row of the row group
	 * @param rowFirst      first row of the unit
	 * @param hasNextRow    peek at the next row (the group continues, or the unit has a next row)
	 * @param columnCount   column count
	 */
	static void collapseRow(final Border[] firstBorder, final Border[] lastBorder, final Border[] lineBorder,
			final BorderAxes ax, final TableParams tableParams, final TableColumnGroupBox colgroup,
			final InnerTableParams rowGroupParams, final TableRowBox rowBox, final List<CellContent> cells,
			final TableRowBox nextRowBox, final List<CellContent> nextCells, final boolean tableFirst,
			final boolean tableLast, final boolean groupFirst, final boolean groupLast, final boolean rowFirst,
			final boolean hasNextRow, final int columnCount) {
		// Table borders
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

		// Column group borders
		// Column borders
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

		// Row group borders
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

		// Row borders
		final InnerTableParams rowParams = rowBox.getInnerTableParams();
		lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0], ax.vStart().apply(rowParams.border));
		lineBorder[lineBorder.length - 1] = TableCollapsedBorders.collapseBorder(lineBorder[lineBorder.length - 1],
				ax.vEnd().apply(rowParams.border));
		// The border arrays have length **column count**. A row can have more cells
		// (carried-over spans push cells beyond the column count), so always cap the index
		// (2026-07-25: random cell span checks detected ArrayIndexOutOfBounds).
		for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
			final CellContent cell = cells.get(j);
			if (cell.rowspan == 1) {
				lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
						ax.hEnd().apply(rowParams.border));
			}
		}
		// Above the next row
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
			// Above the first row
			for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
				firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
						ax.hStart().apply(rowParams.border));
			}
		}

		// Cell borders
		for (int j = 0, n = Math.min(cells.size(), columnCount); j < n; ++j) {
			final CellContent cell = cells.get(j);
			final BlockParams cellParams = cell.getCellBox().getBlockParams();
			lineBorder[j] = TableCollapsedBorders.collapseBorder(lineBorder[j],
					ax.vStart().apply(cellParams.frame.border));
			// V lines inside a span are hidden but guaranteed non-null (for reads when
			// a rowspan crosses the inside of a span in a malformed table).
			// lineBorder has column count + 1 entries (between columns), so cap its index too.
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
			// Above the first row
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
		// Above the next row
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
