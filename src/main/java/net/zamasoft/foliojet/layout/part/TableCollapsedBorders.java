package net.zamasoft.foliojet.layout.part;

import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Implements collapsed borders.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCollapsedBorders.java 1554 2018-04-26 03:34:02Z miyabe $
 */
public class TableCollapsedBorders {
	private static final Border[][] EMPTY_BORDERS = new Border[0][];
	private static final double[] EMPTY_ROW_SIZES = new double[0];

	private double[] columnSizes;
	private double[] headerRowSizes;
	private Border[][] headerHborders;
	private Border[][] headerVborders;
	private double[] bodyRowSizes;
	private Border[][] bodyHborders;
	private Border[][] bodyVborders;
	private double[] footerRowSizes;
	private Border[][] footerHborders;
	private Border[][] footerVborders;

	/**
	 * Returns the border with higher priority.
	 *
	 * @param prev
	 * @param next
	 * @return
	 */
	public static Border collapseBorder(Border prev, Border next) {
		if (prev == null) {
			return next;
		}
		if (prev.compareTo(next) > 0) {
			return next;
		}
		return prev;
	}

	/**
	 * Creates an instance with column widths and borders specified as arrays.
	 *
	 * @param columnWidths
	 * @param headerRowHeights
	 * @param headerVborders
	 * @param headerHborders
	 * @param bodyRowHeights
	 * @param bodyVborders
	 * @param bodyHborders
	 * @param footerRowHeights
	 * @param footerVborders
	 * @param footerHborders
	 */
	public TableCollapsedBorders(double[] columnWidths, double[] headerRowHeights, Border[][] headerVborders,
			Border[][] headerHborders, double[] bodyRowHeights, Border[][] bodyVborders, Border[][] bodyHborders,
			double[] footerRowHeights, Border[][] footerVborders, Border[][] footerHborders) {
		this.columnSizes = columnWidths == null ? EMPTY_ROW_SIZES : columnWidths;
		this.headerRowSizes = headerRowHeights == null ? EMPTY_ROW_SIZES : headerRowHeights;
		this.headerVborders = headerVborders == null ? EMPTY_BORDERS : headerVborders;
		this.headerHborders = headerHborders == null ? EMPTY_BORDERS : headerHborders;
		this.bodyRowSizes = bodyRowHeights == null ? EMPTY_ROW_SIZES : bodyRowHeights;
		this.bodyVborders = bodyVborders == null ? EMPTY_BORDERS : bodyVborders;
		this.bodyHborders = bodyHborders == null ? EMPTY_BORDERS : bodyHborders;
		this.footerRowSizes = footerRowHeights == null ? EMPTY_ROW_SIZES : footerRowHeights;
		this.footerVborders = footerVborders == null ? EMPTY_BORDERS : footerVborders;
		this.footerHborders = footerHborders == null ? EMPTY_BORDERS : footerHborders;
	}

	/**
	 * Returns the number of columns.
	 *
	 * @return
	 */
	public int getColumnCount() {
		return this.columnSizes.length;
	}

	/**
	 * Sets the column width.
	 *
	 * @param col
	 * @param size
	 */
	public void setColumnSize(int col, double size) {
		assert !LayoutUtils.isNone(size);
		this.columnSizes[col] = size;
	}

	/**
	 * Returns the column width.
	 *
	 * @param col
	 * @return
	 */
	public double getColumnSize(int col) {
		return this.columnSizes[col];
	}

	/**
	 * Returns the number of rows.
	 *
	 * @return
	 */
	public int getRowCount() {
		return this.headerRowSizes.length + this.bodyRowSizes.length + this.footerRowSizes.length;
	}

	/** Whether the collapsed-border model contains any border that is actually drawn. */
	public boolean paintsAnything() {
		final int rows = this.getRowCount();
		final int columns = this.getColumnCount();
		for (int column = 0; column < columns; ++column) {
			for (int index = 0; index <= rows; ++index) {
				final Border border = this.getHBorder(column, index);
				if (border != null && border.isVisible()) {
					return true;
				}
			}
		}
		for (int row = 0; row < rows; ++row) {
			for (int index = 0; index <= columns; ++index) {
				final Border border = this.getVBorder(row, index);
				if (border != null && border.isVisible()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns the row height.
	 *
	 * @param row
	 * @param rowSize
	 */
	public void setRowSize(int row, double rowSize) {
		if (row < this.headerRowSizes.length) {
			this.headerRowSizes[row] = rowSize;
			return;
		}
		row -= this.headerRowSizes.length;
		if (row < this.bodyRowSizes.length) {
			this.bodyRowSizes[row] = rowSize;
			return;
		}
		row -= this.bodyRowSizes.length;
		this.footerRowSizes[row] = rowSize;
	}

	/**
	 * Returns the row height.
	 *
	 * @param row
	 * @return
	 */
	public double getRowSize(int row) {
		if (row < this.headerRowSizes.length) {
			return this.headerRowSizes[row];
		}
		row -= this.headerRowSizes.length;
		if (row < this.bodyRowSizes.length) {
			return this.bodyRowSizes[row];
		}
		row -= this.bodyRowSizes.length;
		return this.footerRowSizes[row];
	}

	/**
	 * Returns a horizontal border.
	 *
	 * @param col
	 * @param index
	 * @return
	 */
	public Border getHBorder(int col, int index) {
		if (index < this.headerRowSizes.length) {
			return this.headerHborders[col][index];
		}
		if (index == this.headerRowSizes.length) {
			Border border = null;
			if (this.headerHborders.length > 0 && this.headerHborders[col].length > index) {
				border = collapseBorder(border, this.headerHborders[col][index]);
			}
			if (this.bodyHborders.length > 0 && this.bodyHborders[col].length > 0) {
				border = collapseBorder(border, this.bodyHborders[col][0]);
			}
			return border;
		}
		index -= this.headerRowSizes.length;
		if (index < this.bodyRowSizes.length) {
			return this.bodyHborders[col][index];
		}
		if (index == this.bodyRowSizes.length) {
			Border border = null;
			if (this.footerHborders.length > 0 && this.footerHborders[col].length > 0) {
				border = collapseBorder(border, this.footerHborders[col][0]);
			}
			if (this.bodyHborders.length > 0 && this.bodyHborders[col].length > index) {
				border = collapseBorder(border, this.bodyHborders[col][index]);
			}
			return border;
		}
		index -= this.bodyRowSizes.length;
		return this.footerHborders[col][index];
	}

	/**
	 * Returns a vertical border.
	 *
	 * @param row
	 * @param index
	 * @return
	 */
	public Border getVBorder(int row, int index) {
		if (row < this.headerRowSizes.length) {
			return this.headerVborders[row][index];
		}
		row -= this.headerRowSizes.length;
		if (row < this.bodyRowSizes.length) {
			return this.bodyVborders[row][index];
		}
		row -= this.bodyRowSizes.length;
		return this.footerVborders[row][index];
	}

	/**
	 * Copies the <b>tail</b> of the source sequence for the destination's length.
	 *
	 * <p>
	 * The destination can be longer than the source: the table can have <b>more</b> body rows after splitting
	 * than before. This occurs on paths that rebuild tables, such as an anonymous row rewrapping a table within
	 * multi-column layout (measured 2026-08-03: {@code local/shrink/w56-min.html} ).
	 * Previously, the direct call {@code System.arraycopy(src, src.length - n, ...)} produced a negative index
	 * in this case and crashed the entire conversion with {@code ArrayIndexOutOfBoundsException} .
	 * </p>
	 *
	 * <p>
	 * The {@code origBodyRowCount != prev + next} branch below already anticipates mismatched row counts
	 * (dropping the boundary rules). Leave the leading portion that cannot be copied here without borders
	 * (null).
	 * </p>
	 */
	private static void copyTail(final Object src, final Object dst) {
		final int srcLength = java.lang.reflect.Array.getLength(src);
		final int dstLength = java.lang.reflect.Array.getLength(dst);
		final int count = Math.min(srcLength, dstLength);
		if (count > 0) {
			System.arraycopy(src, srcLength - count, dst, dstLength - count, count);
		}
	}

	/** Copies as much of the source head as fits in the destination. Leaves any added tail unspecified. */
	private static void copyHead(final Object src, final Object dst) {
		final int count = Math.min(java.lang.reflect.Array.getLength(src), java.lang.reflect.Array.getLength(dst));
		if (count > 0) {
			System.arraycopy(src, 0, dst, 0, count);
		}
	}

	/**
	 * Splits in the page direction.
	 *
	 * @param prevTable
	 * @param nextTable
	 * @return
	 */
	public TableCollapsedBorders splitPageAxis(final TableBox prevTable, final TableBox nextTable,
			final int origBodyRowCount) {
		// Calculate the previous table's table-body-group count and row count.
		int prevBodyGroupCount = prevTable.getTableBodyCount();
		int prevBodyRowCount = 0;
		for (int i = 0; i < prevBodyGroupCount; ++i) {
			prevBodyRowCount += prevTable.getTableBody(i).getTableRowCount();
		}

		// Calculate the next table's table-body-group count and row count.
		int nextBodyGroupCount = nextTable.getTableBodyCount();
		int nextBodyRowCount = 0;
		for (int i = 0; i < nextBodyGroupCount; ++i) {
			nextBodyRowCount += nextTable.getTableBody(i).getTableRowCount();
		}

		// Horizontal borders
		Border[][] hborders = this.bodyHborders;
		this.bodyHborders = new Border[this.columnSizes.length][prevBodyRowCount + 1];
		for (int i = 0; i < this.columnSizes.length; ++i) {
			copyHead(hborders[i], this.bodyHborders[i]);
		}
		Border[][] nextHBorders = new Border[this.columnSizes.length][nextBodyRowCount + 1];
		for (int i = 0; i < this.columnSizes.length; ++i) {
			copyTail(hborders[i], nextHBorders[i]);
		}
		if (origBodyRowCount != (prevBodyRowCount + nextBodyRowCount)) {
			for (int i = 0; i < this.columnSizes.length; ++i) {
				this.bodyHborders[i][this.bodyHborders[i].length - 1] = null;
				nextHBorders[i][0] = null;
			}
		}

		// Vertical borders
		Border[][] vborders = this.bodyVborders;
		this.bodyVborders = new Border[prevBodyRowCount][];
		copyHead(vborders, this.bodyVborders);
		for (int i = 0; i < this.bodyVborders.length; ++i) {
			if (this.bodyVborders[i] == null) {
				this.bodyVborders[i] = new Border[this.columnSizes.length + 1];
			}
		}
		Border[][] nextVBorders = new Border[nextBodyRowCount][];
		copyTail(vborders, nextVBorders);
		// Fill the uncopied leading portion (when rows outnumber the source) with empty sequences.
		// Leaving null would cause NullPointerException in getVBorder.
		for (int i = 0; i < nextVBorders.length; ++i) {
			if (nextVBorders[i] == null) {
				nextVBorders[i] = new Border[this.columnSizes.length + 1];
			}
		}

		// Row heights
		final double[] nextRowSizes = new double[nextBodyRowCount];
		copyTail(this.bodyRowSizes, nextRowSizes);

		TableCollapsedBorders nextBorders = new TableCollapsedBorders(this.columnSizes, this.headerRowSizes,
				this.headerVborders, this.headerHborders, nextRowSizes, nextVBorders, nextHBorders, this.footerRowSizes,
				this.footerVborders, this.footerHborders);

		// Rows
		final double[] rowSizes = this.bodyRowSizes;
		this.bodyRowSizes = new double[prevBodyRowCount];
		copyHead(rowSizes, this.bodyRowSizes);
		if (prevBodyGroupCount > 0) {
			TableRowGroupBox bodyGroup = prevTable.getTableBody(prevBodyGroupCount - 1);
			int rowCount = bodyGroup.getTableRowCount();
			if (rowCount > 0) {
				this.bodyRowSizes[this.bodyRowSizes.length - 1] = bodyGroup.getTableRow(rowCount - 1).getPageSize();
			}
		}
		nextBorders.bodyRowSizes = new double[nextBodyRowCount];
		copyTail(rowSizes, nextBorders.bodyRowSizes);
		if (nextBodyGroupCount > 0) {
			TableRowGroupBox bodyGroup = nextTable.getTableBody(0);
			int rowCount = bodyGroup.getTableRowCount();
			if (rowCount > 0) {
				nextBorders.bodyRowSizes[0] = bodyGroup.getTableRow(0).getPageSize();
			}
		}

		return nextBorders;
	}
}
