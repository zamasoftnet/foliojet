package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * テーブル行の実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowBox.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class TableRowBox extends AbstractInnerTableBox implements IPageBreakableBox {
	private static final boolean DEBUG = false;

	protected final TableRowPos pos;

	public static interface Cell {
		public boolean isSource();

		public TableCellBox getCellBox();

		public Cell getSource();

		public void setNextExtendedCell(ExtendedCell extended);

		public ExtendedCell getNextExtendedCell();

		public TableRowBox getTableRow();
	}

	public static interface ExtendedCell extends Cell {
		public void setSourceCell(Cell source);
	}

	protected static abstract class AbstractCell implements Cell {
		protected ExtendedCell extended;
		protected final TableRowBox row;

		protected AbstractCell(TableRowBox row) {
			this.row = row;
		}

		public ExtendedCell getNextExtendedCell() {
			return this.extended;
		}

		public void setNextExtendedCell(ExtendedCell extended) {
			this.extended = extended;
		}

		public TableRowBox getTableRow() {
			return this.row;
		}
	}

	protected static class SourceCellImpl extends AbstractCell {
		protected final TableCellBox cell;

		public SourceCellImpl(TableCellBox cell, TableRowBox row) {
			super(row);
			this.cell = cell;
		}

		public boolean isSource() {
			return true;
		}

		public Cell getSource() {
			return this;
		}

		public TableCellBox getCellBox() {
			return this.cell;
		}
	}

	protected static class ExtendedCellImpl extends AbstractCell implements ExtendedCell {
		protected Cell source;

		public ExtendedCellImpl(TableRowBox row) {
			super(row);
		}

		public boolean isSource() {
			return false;
		}

		public Cell getSource() {
			return this.source;
		}

		public TableCellBox getCellBox() {
			return this.source.getCellBox();
		}

		public void setSourceCell(Cell source) {
			this.source = source;
		}
	}

	protected final List<Cell> cells = new ArrayList<Cell>();

	public TableRowBox(final InnerTableParams params, final TableRowPos pos) {
		super(params);
		this.pos = pos;
	}

	public final BoxType getType() {
		return BoxType.TABLE_ROW;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableRowPos getTableRowPos() {
		return this.pos;
	}

	public final void setLineSize(double lineSize) {
		this.lineSize = lineSize;
	}

	public final void setPageSize(double pageSize) {
		this.pageSize = pageSize;
	}

	public final Cell addTableSourceCell(TableCellBox cellBox) {
		Cell source = new SourceCellImpl(cellBox, this);
		this.cells.add(source);
		return source;
	}

	public final ExtendedCell addTableExtendedCell(Cell cell) {
		ExtendedCell extended = new ExtendedCellImpl(this);
		extended.setSourceCell(cell.getSource());
		cell.setNextExtendedCell(extended);
		this.cells.add(extended);
		return extended;
	}

	public final Cell getCell(int i) {
		return (Cell) this.cells.get(i);
	}

	public final int getCellCount() {
		return this.cells.size();
	}

	public final void finishLayout(IFramedBox containerBox) {
		if (this.cells == null) {
			return;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			if (cell.isSource()) {
				cell.getCellBox().finishLayout(containerBox);
			}
		}
	}

	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.params.background.isVisible()) {
			Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.background, this.params.border, null, this.getWidth(), this.getHeight());
			drawer.visitDrawable(drawable, x, y);
		}
		if (this.cells == null) {
			return;
		}
		if (this.tableParams.flow.isVertical()) {
			// 縦書き
			for (int i = 0; i < this.cells.size(); ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					cellBox.frames(pageBox, drawer, clip, transform, x - cellBox.getWidth() + this.pageSize, y);
				}
				y += cellBox.getHeight();
			}
		} else {
			// 横書き
			for (int i = 0; i < this.cells.size(); ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					cellBox.frames(pageBox, drawer, clip, transform, x, y);

				}
				x += cellBox.getWidth();
			}
		}
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.cells == null) {
			return;
		}
		if (this.tableParams.flow.isVertical()) {
			for (int i = 0; i < this.cells.size(); ++i) {
				// 縦書き
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					cellBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY,
							x - cellBox.getWidth() + this.pageSize, y);

				}
				y += cellBox.getHeight();
			}
		} else {
			// 横書き
			for (int i = 0; i < this.cells.size(); ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					cellBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);

				}
				x += cellBox.getWidth();
			}
		}
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}
		if (DEBUG) {
			drawer.visitDrawable(new Drawable() {
				public void draw(GC gc, double x, double y) throws GraphicsException {
					try (final var gcState = gc.begin()) {
						gc.setStrokePaint(RGBColor.create(127, 127, 255));
						double width = 0;
						for (int i = 0; i < TableRowBox.this.cells.size(); ++i) {
							Cell cell = (Cell) TableRowBox.this.cells.get(i);
							width += cell.getCellBox().getWidth();
						}
						gc.draw(new Rectangle2D.Double(x, y, width, getHeight()));
					}
				}
			}, x, y);
		}
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}
		if (this.cells == null) {
			return;
		}
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
		if (this.tableParams.flow.isVertical()) {
			// 縦書き
			for (int i = 0; i < this.cells.size(); ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource()) {
					cellBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY,
							x - cellBox.getWidth() + this.pageSize, y);

				}
				y += cellBox.getHeight();
			}
		} else {
			// 横書き
			for (int i = 0; i < this.cells.size(); ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource()) {
					cellBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);

				}
				x += cellBox.getWidth();
			}
		}
		pageBox.endStruct(drawer, this.params.element, structCount, x, y);
	}

	public final void getText(StringBuilder textBuff) {
		if (this.cells == null) {
			return;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			cell.getCellBox().getText(textBuff);
		}
	}

	/**
	 * セルの切断位置です(C4-T3)。連結セル(rowspan)は連結元の行から
	 * 当行までのページ寸を加算する — 切断線はセル上端基準になる。
	 */
	private static double cellCutPageAxis(final Cell cell, final double pageLimit) {
		double cutPageAxis = pageLimit;
		if (!cell.isSource()) {
			final Cell sCell = cell.getSource();
			cutPageAxis += sCell.getTableRow().getPageSize();
			for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell
					.getNextExtendedCell()) {
				if (xcell == cell) {
					break;
				}
				cutPageAxis += xcell.getTableRow().getPageSize();
			}
		}
		return cutPageAxis;
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// System.err.println("A:" + flags + "/" + pageLimit + "/" + mode
		// + "/" + this.getHeight()+"/"+this.params.augmentation);

		final boolean vertical = this.tableParams.flow.isVertical();
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) == 0) {
			// 前置判定は TableCutter に純化(C4-T3)
			final double[] cellPageExtents = new double[this.cells.size()];
			final boolean[] cellFlowMatch = new boolean[this.cells.size()];
			final boolean[] cellInsideAvoid = new boolean[this.cells.size()];
			final boolean[] cellCollapsedAtStart = new boolean[this.cells.size()];
			for (int i = 0; i < this.cells.size(); ++i) {
				final TableCellBox cellBox = ((Cell) this.cells.get(i)).getCellBox();
				final BlockParams cellParams = cellBox.getBlockParams();
				cellPageExtents[i] = cellBox.getPageExtent(this.tableParams.flow);
				cellFlowMatch[i] = cellParams.flow.isVertical() == vertical;
				cellInsideAvoid[i] = cellParams.pageBreakInside == PageBreakMode.AVOID;
				cellCollapsedAtStart[i] = cellBox.getFrame().getFramePageStart(this.tableParams.flow) <= 0
						&& LayoutUtils.compare(cellBox.getInnerPageExtent(this.tableParams.flow), 0) <= 0;
			}
			final SplitResult pre = net.zamasoft.foliojet.layout.fragment.TableCutter.rowPreDecide(
					(flags & IPageBreakableBox.FLAGS_FIRST) != 0,
					(flags & IPageBreakableBox.FLAGS_FIRST_ROW) != 0, pageLimit, this.getPageSize(),
					this.params.pageBreakInside == PageBreakMode.AVOID, cellPageExtents, cellFlowMatch,
					cellInsideAvoid, cellCollapsedAtStart);
			if (pre != null) {
				return pre;
			}
		}
		// System.err.println("TR B/flags=" + flags + "/" + pageLimit);
		byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
		final double pageWindow = this.pageSize - pageLimit;
		TableRowBox nextRowBox = null;
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
			// 必ず切断する
			// 分割断片は継続物: 共有 params のソースアンカーを無効化(M6b)
			this.params.sourceEventId = -1;
			nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
			nextRowBox.setTableParams(this.tableParams);
			this.pageSize = pageLimit;
			nextRowBox.pageSize = pageWindow;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			TableCellBox prevCellBox = cell.getCellBox();
			TableCellBox nextCellBox;
			final double cutPageAxis = cellCutPageAxis(cell, pageLimit);
			// System.err.println(prevCellBox.getInnerHeight());
			final SplitResult cellResult = prevCellBox.split(cutPageAxis, mode, xflags);
			// System.err.println("TR C: " + i + "/" + xflags + "/pass="
			// + (nextCellBox == prevCellBox) + "/leave="
			// + (nextCellBox == null) + "/" + mode + "/" + cutPageAxis);
			if (cellResult instanceof SplitResult.Split(final IPageBreakableBox cellRemainder)) {
				nextCellBox = (TableCellBox) cellRemainder;
			} else {
				if (nextRowBox == null) {
					continue;
				}
				// 他に分割されたセルがある場合、強制切断する
				byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
				nextCellBox = (TableCellBox) ((SplitResult.Split) prevCellBox.split(cutPageAxis, mode, xxflags))
						.remainder();
			}
			// System.err.println("TR C: " + i + "/prevHeight="
			// + prevCellBox.getInnerHeight() + "/" + cutPageAxis);
			if (nextRowBox == null) {
				this.params.sourceEventId = -1;
				nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
				nextRowBox.setTableParams(this.tableParams);
				this.pageSize = pageLimit;
				nextRowBox.pageSize = pageWindow;
				// System.err.println("ROW Split:"+pageLimit+"/"+pageWindow);
				for (int j = 0; j < i; ++j) {
					Cell cell2 = (Cell) this.cells.get(j);
					TableCellBox prevCell2 = cell2.getCellBox();
					final double cutPageAxis2 = cellCutPageAxis(cell2, pageLimit);
					byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
					TableCellBox nextCell2 = (TableCellBox) ((SplitResult.Split) prevCell2.split(cutPageAxis2, mode,
							xxflags)).remainder();
					// System.err.println("TR D: " + j + "/cell splitted ="
					// + prevCell2.getInnerHeight() + "/"
					// + nextCell2.getInnerHeight());
					if (vertical) {
						prevCell2.setWidth(cutPageAxis2);
					} else {
						prevCell2.setHeight(cutPageAxis2);
					}
					this.restyleCell(nextCell2);
					Cell source = nextRowBox.addTableSourceCell(nextCell2);
					ExtendedCell xcell = cell2.getNextExtendedCell();
					double span = 1;
					if (xcell != null) {
						source.setNextExtendedCell(xcell);
						do {
							++span;
							xcell.setSourceCell(source);
							xcell = xcell.getNextExtendedCell();
						} while (xcell != null);
					}
					nextRowBox.pageSize = Math.max(nextRowBox.pageSize,
							nextCell2.getPageExtent(this.tableParams.flow) / span);
				}
			}
			if (vertical) {
				prevCellBox.setWidth(cutPageAxis);
			} else {
				prevCellBox.setHeight(cutPageAxis);
			}
			this.restyleCell(nextCellBox);
			Cell source = nextRowBox.addTableSourceCell(nextCellBox);
			ExtendedCell xcell = cell.getNextExtendedCell();
			double span = 1;
			if (xcell != null) {
				source.setNextExtendedCell(xcell);
				do {
					++span;
					xcell.setSourceCell(source);
					xcell = xcell.getNextExtendedCell();
				} while (xcell != null);
			}
			nextRowBox.pageSize = Math.max(nextRowBox.pageSize, nextCellBox.getPageExtent(this.tableParams.flow) / span);
		}

		// System.err.println("TR nextRowBox: pass=" + (nextRowBox ==
		// null)+"/"+flags);
		if (nextRowBox == null) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return SplitResult.KEEP;
			}
			// 現在の行を持ち越す
			return SplitResult.MOVE;
		}

		// 分割の後処理
		// System.err.println("ROW break:" + "/" + mode + "/" +
		// nextRowBox.height);
		for (int i = 0; i < nextRowBox.cells.size(); ++i) {
			Cell cell = (Cell) nextRowBox.cells.get(i);
			double rowSize = nextRowBox.pageSize;
			for (ExtendedCell xcell = cell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				rowSize += xcell.getTableRow().getPageSize();
			}
			TableCellBox nextCell = cell.getCellBox();
			// System.err.println("TR RowHeight:" + i + "/" + rowHeight);
			if (vertical) {
				nextCell.setWidth(rowSize);
			} else {
				nextCell.setHeight(rowSize);
			}
		}
		return new SplitResult.Split(nextRowBox);
	}

	public final void cutRowspanCells() {
		// 連結されたセルを強制切断する
		final boolean vertical = this.tableParams.flow.isVertical();
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			if (cell.isSource()) {
				continue;
			}
			TableCellBox prevCell = cell.getCellBox();
			// 切断面は行の上辺、即ちセルの高さから現在行の高さを引いたもの
			Cell sCell = cell.getSource();
			double cutPageAxis = sCell.getTableRow().getPageSize();
			for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				if (xcell == cell) {
					break;
				}
				cutPageAxis += xcell.getTableRow().getPageSize();
			}
			// System.err.println(cutPageAxis);
			TableCellBox nextCell = (TableCellBox) ((SplitResult.Split) prevCell.split(cutPageAxis,
					BreakMode.DEFAULT_BREAK_MODE, IPageBreakableBox.FLAGS_SPLIT)).remainder();
			if (vertical) {
				prevCell.setWidth(cutPageAxis);
			} else {
				prevCell.setHeight(cutPageAxis);
			}
			this.restyleCell(nextCell);
			// addTableSourceCellの代わりに直接更新する
			Cell source = new SourceCellImpl(nextCell, this);
			this.cells.set(i, source);
			ExtendedCell xcell = cell.getNextExtendedCell();
			int span = 1;
			if (xcell != null) {
				source.setNextExtendedCell(xcell);
				do {
					++span;
					xcell.setSourceCell(source);
					xcell = xcell.getNextExtendedCell();
				} while (xcell != null);
			}
			// 行の高さをセルの高さを行数で割ったもので更新
			this.pageSize = Math.max(this.pageSize, nextCell.getPageExtent(this.tableParams.flow) / span);
			// System.err.println("TR cutRowSpanCells: " + i + "/"
			// + prevCell.getHeight());
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			double rowSize = this.pageSize;
			for (ExtendedCell xcell = cell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				rowSize += xcell.getTableRow().getPageSize();
			}
			TableCellBox nextCell = cell.getCellBox();
			if (vertical) {
				nextCell.setWidth(rowSize);
			} else {
				nextCell.setHeight(rowSize);
			}
		}
	}

	private final void restyleCell(TableCellBox nextCell) {
		// 再レイアウトにFIXEDボックスは関与しないのでpageContextBuilderはnullでよい
		final BlockBuilder cellBindBuilder = new BlockBuilder(null, nextCell);
		nextCell.restyle(cellBindBuilder, 0);
		cellBindBuilder.close();
	}
}
