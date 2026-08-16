package net.zamasoft.foliojet.layout.part;

import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * つぶし境界の実装です。
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
	 * 優先度が高い方の境界を返します。
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
	 * カラム幅が全てゼロで、全ての境界が非表示のインスタンスを生成します。
	 * 
	 * @param columnCount
	 * @param headerCount
	 * @param bodyCount
	 * @param footerCount
	 */
	public TableCollapsedBorders(int columnCount, int headerCount, int bodyCount, int footerCount) {
		// System.out.println(columnCount + "/" + headerCount + "/" + bodyCount
		// + "/" + footerCount);
		this.columnSizes = new double[columnCount];
		this.headerRowSizes = new double[headerCount];
		this.headerVborders = new Border[headerCount][columnCount + 1];
		this.headerHborders = new Border[columnCount][headerCount + 1];
		this.bodyRowSizes = new double[bodyCount];
		this.bodyVborders = new Border[bodyCount][columnCount + 1];
		this.bodyHborders = new Border[columnCount][bodyCount + 1];
		this.footerRowSizes = new double[footerCount];
		this.footerVborders = new Border[footerCount][columnCount + 1];
		this.footerHborders = new Border[columnCount][footerCount + 1];
	}

	/**
	 * カラム幅と各境界を配列で指定したインスタンスを生成します。
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
	 * カラムの数を返します。
	 * 
	 * @return
	 */
	public int getColumnCount() {
		return this.columnSizes.length;
	}

	/**
	 * カラムの幅を設定します。
	 * 
	 * @param col
	 * @param size
	 */
	public void setColumnSize(int col, double size) {
		assert !LayoutUtils.isNone(size);
		this.columnSizes[col] = size;
	}

	/**
	 * カラムの幅を返します。
	 * 
	 * @param col
	 * @return
	 */
	public double getColumnSize(int col) {
		return this.columnSizes[col];
	}

	/**
	 * 行数を返します。
	 * 
	 * @return
	 */
	public int getRowCount() {
		return this.headerRowSizes.length + this.bodyRowSizes.length + this.footerRowSizes.length;
	}

	/** つぶし境界モデルに、実際に描かれる境界が一つでもあるか。 */
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
	 * 行の高さを返します。
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
	 * 行の高さを返します。
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
	 * 水平境界を追加します。
	 * 
	 * @param col
	 * @param index
	 * @param bottom
	 * @param border
	 */
	public void collapseHBorder(int col, int index, boolean bottom, Border border) {
		if (index < this.headerRowSizes.length) {
			this.headerHborders[col][index] = collapseBorder(this.headerHborders[col][index], border);
			return;
		}
		if (index == this.headerRowSizes.length) {
			if (bottom) {
				this.headerHborders[col][index] = collapseBorder(this.headerHborders[col][index], border);
			} else {
				this.bodyHborders[col][0] = collapseBorder(this.bodyHborders[col][0], border);
			}
			return;
		}
		index -= this.headerRowSizes.length;
		if (index < this.bodyRowSizes.length) {
			this.bodyHborders[col][index] = collapseBorder(this.bodyHborders[col][index], border);
			return;
		}
		if (index == this.bodyRowSizes.length) {
			if (bottom) {
				this.bodyHborders[col][index] = collapseBorder(this.bodyHborders[col][index], border);
			} else {
				this.footerHborders[col][0] = collapseBorder(this.footerHborders[col][0], border);
			}
			return;
		}
		index -= this.bodyRowSizes.length;
		this.footerHborders[col][index] = collapseBorder(this.footerHborders[col][index], border);
	}

	/**
	 * 水平境界を返します。
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
	 * 垂直境界を設定します。
	 * 
	 * @param row
	 * @param index
	 * @param border
	 */
	public void collapseVBorder(int row, int index, Border border) {
		// System.out.println(row + "/" + index);
		if (row < this.headerRowSizes.length) {
			this.headerVborders[row][index] = collapseBorder(this.headerVborders[row][index], border);
			return;
		}
		row -= this.headerRowSizes.length;
		if (row < this.bodyRowSizes.length) {
			this.bodyVborders[row][index] = collapseBorder(this.bodyVborders[row][index], border);
			return;
		}
		row -= this.bodyRowSizes.length;
		this.footerVborders[row][index] = collapseBorder(this.footerVborders[row][index], border);
	}

	/**
	 * 垂直境界を返します。
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
	 * ページ方向に分割します。
	 * 
	 * @param prevTable
	 * @param nextTable
	 * @return
	 */
	/**
	 * 元の並びの<b>末尾</b>を、行き先の長さぶんだけ写します。
	 *
	 * <p>
	 * 行き先が元より長いことがある——分割後の表の本体行数が、分割前より
	 * <b>多く</b>なる場合である(段組の中の表を無名の行が包み直すなど、
	 * 表が組み直される経路で起きる。2026-08-03に実測:
	 * {@code local/shrink/w56-min.html})。従来は
	 * {@code System.arraycopy(src, src.length - n, ...)} と直に書いていた
	 * ため、その場合に添字が負になり
	 * {@code ArrayIndexOutOfBoundsException} で変換ごと落ちていた。
	 * </p>
	 *
	 * <p>
	 * 行数の食い違い自体は下の {@code origBodyRowCount != prev + next} の
	 * 分岐が想定しており(境界の罫線を落とす)、ここで写せない先頭側は
	 * 罫線なし(null)のままにする。
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

	/** 元の先頭を、行き先へ収まる分だけ写す。増えた末尾側は未指定のままにする。 */
	private static void copyHead(final Object src, final Object dst) {
		final int count = Math.min(java.lang.reflect.Array.getLength(src), java.lang.reflect.Array.getLength(dst));
		if (count > 0) {
			System.arraycopy(src, 0, dst, 0, count);
		}
	}

	public TableCollapsedBorders splitPageAxis(final TableBox prevTable, final TableBox nextTable,
			final int origBodyRowCount) {
		// 前のテーブルのtable-body-groupの数と行の数を計算
		int prevBodyGroupCount = prevTable.getTableBodyCount();
		int prevBodyRowCount = 0;
		for (int i = 0; i < prevBodyGroupCount; ++i) {
			prevBodyRowCount += prevTable.getTableBody(i).getTableRowCount();
		}

		// 次のテーブルのtable-body-groupの数と行の数を計算
		int nextBodyGroupCount = nextTable.getTableBodyCount();
		int nextBodyRowCount = 0;
		for (int i = 0; i < nextBodyGroupCount; ++i) {
			nextBodyRowCount += nextTable.getTableBody(i).getTableRowCount();
		}

		// 水平境界
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

		// 垂直境界
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
		// 写せなかった先頭側(元より行が増えた場合)は空の並びで埋める。
		// nullのままだと getVBorder が NullPointerException になる
		for (int i = 0; i < nextVBorders.length; ++i) {
			if (nextVBorders[i] == null) {
				nextVBorders[i] = new Border[this.columnSizes.length + 1];
			}
		}

		// 行の高さ
		final double[] nextRowSizes = new double[nextBodyRowCount];
		copyTail(this.bodyRowSizes, nextRowSizes);

		TableCollapsedBorders nextBorders = new TableCollapsedBorders(this.columnSizes, this.headerRowSizes,
				this.headerVborders, this.headerHborders, nextRowSizes, nextVBorders, nextHBorders, this.footerRowSizes,
				this.footerVborders, this.footerHborders);

		// 行
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
