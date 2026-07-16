package net.zamasoft.foliojet.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IFramedBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.InnerTableParams;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.TableRowPos;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
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

	public final IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// System.err.println("A:" + flags + "/" + pageLimit + "/" + mode
		// + "/" + this.getHeight()+"/"+this.params.augmentation);

		final boolean vertical = this.tableParams.flow.isVertical();
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) == 0) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
				// ページ頭ではない場合
				if (StyleUtils.compare(pageLimit, 0) < 0) {
					// 切断線より下にある場合
					return this;
				}
				if (StyleUtils.compare(pageLimit, this.getPageSize()) >= 0) {
					// 切断線より上にある場合
					// 連結されたセルによる高さを考慮するため、全てのセルの高さをチェック
					boolean leave = true;
					if (vertical) {
						for (int i = 0; i < this.cells.size(); ++i) {
							Cell cell = (Cell) this.cells.get(i);
							if (StyleUtils.compare(pageLimit, cell.getCellBox().getWidth()) < 0) {
								leave = false;
								break;
							}
						}
					} else {
						for (int i = 0; i < this.cells.size(); ++i) {
							Cell cell = (Cell) this.cells.get(i);
							if (StyleUtils.compare(pageLimit, cell.getCellBox().getHeight()) < 0) {
								leave = false;
								break;
							}
						}
					}
					if (leave) {
						// 移動なし
						return null;
					}
				}
				boolean breakAvoid = false;
				// ページ頭に接したセルがある場合は改ページ禁止を無視する
				if (this.params.pageBreakInside == Types.PAGE_BREAK_AVOID) {
					// 行の改ページ禁止
					breakAvoid = true;
				} else {
					for (int i = 0; i < this.cells.size(); ++i) {
						Cell cell = (Cell) this.cells.get(i);
						BlockParams cellParams = cell.getCellBox().getBlockParams();
						// 書字方向が違う場合は改ページ禁止
						if (cellParams.flow.isVertical() != this.tableParams.flow.isVertical()) {
							return this;
						}
						if (cellParams.pageBreakInside == Types.PAGE_BREAK_AVOID) {
							// セルの改ページ禁止
							breakAvoid = true;
						}
					}
				}
				if (breakAvoid && (flags & IPageBreakableBox.FLAGS_FIRST_ROW) == 0) {
					return this;
				}
			} else {
				// ページ頭の場合
				if (StyleUtils.compare(pageLimit, this.getPageSize()) >= 0) {
					// rowspanによる連結のはみ出しがあったとしても、あとの処理で切る
					return null;
				}
				// 書字方向が違う場合は移動しない
				for (int i = 0; i < this.cells.size(); ++i) {
					Cell cell = (Cell) this.cells.get(i);
					BlockParams cellParams = cell.getCellBox().getBlockParams();
					if (cellParams.flow.isVertical() != this.tableParams.flow.isVertical()) {
						return null;
					}
				}
				// 上部境界がなく、高さがゼロのセルが存在すれば分割を諦める
				if (vertical) {
					for (int i = 0; i < this.cells.size(); ++i) {
						Cell cell = (Cell) this.cells.get(i);
						TableCellBox cellBox = cell.getCellBox();
						if (cellBox.getFrame().getFrameRight() <= 0
								&& StyleUtils.compare(cellBox.getInnerWidth(), 0) <= 0) {
							return null;
						}
					}
				} else {
					for (int i = 0; i < this.cells.size(); ++i) {
						Cell cell = (Cell) this.cells.get(i);
						TableCellBox cellBox = cell.getCellBox();
						if (cellBox.getFrame().getFrameTop() <= 0
								&& StyleUtils.compare(cellBox.getInnerHeight(), 0) <= 0) {
							return null;
						}
					}
				}
			}
		}
		// System.err.println("TR B/flags=" + flags + "/" + pageLimit);
		byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
		final double pageWindow = this.pageSize - pageLimit;
		TableRowBox nextRowBox = null;
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
			// 必ず切断する
			nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
			nextRowBox.setTableParams(this.tableParams);
			this.pageSize = pageLimit;
			nextRowBox.pageSize = pageWindow;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			TableCellBox prevCellBox = cell.getCellBox();
			TableCellBox nextCellBox;
			double cutPageAxis = pageLimit;
			if (!cell.isSource()) {
				Cell sCell = cell.getSource();
				cutPageAxis += sCell.getTableRow().getPageSize();
				for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell
						.getNextExtendedCell()) {
					if (xcell == cell) {
						break;
					}
					cutPageAxis += xcell.getTableRow().getPageSize();
				}
			}
			// System.err.println(prevCellBox.getInnerHeight());
			nextCellBox = (TableCellBox) prevCellBox.splitPageAxis(cutPageAxis, mode, xflags);
			// System.err.println("TR C: " + i + "/" + xflags + "/pass="
			// + (nextCellBox == prevCellBox) + "/leave="
			// + (nextCellBox == null) + "/" + mode + "/" + cutPageAxis);
			if (nextCellBox == null || nextCellBox == prevCellBox) {
				if (nextRowBox == null) {
					continue;
				}
				// 他に分割されたセルがある場合、強制切断する
				// System.err.println("B another cell splitted =" + xflags);
				byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
				nextCellBox = (TableCellBox) prevCellBox.splitPageAxis(cutPageAxis, mode, xxflags);
				assert nextCellBox != null;
				assert nextCellBox != prevCellBox;
			}
			// System.err.println("TR C: " + i + "/prevHeight="
			// + prevCellBox.getInnerHeight() + "/" + cutPageAxis);
			if (nextRowBox == null) {
				nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
				nextRowBox.setTableParams(this.tableParams);
				this.pageSize = pageLimit;
				nextRowBox.pageSize = pageWindow;
				// System.err.println("ROW Split:"+pageLimit+"/"+pageWindow);
				for (int j = 0; j < i; ++j) {
					Cell cell2 = (Cell) this.cells.get(j);
					TableCellBox prevCell2 = cell2.getCellBox();
					double cutPageAxis2 = pageLimit;
					if (!cell2.isSource()) {
						Cell sCell = cell2.getSource();
						cutPageAxis2 += sCell.getTableRow().getPageSize();
						for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell
								.getNextExtendedCell()) {
							if (xcell == cell2) {
								break;
							}
							cutPageAxis2 += xcell.getTableRow().getPageSize();
						}
					}
					byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
					TableCellBox nextCell2 = (TableCellBox) prevCell2.splitPageAxis(cutPageAxis2, mode, xxflags);
					assert nextCell2 != null;
					assert nextCell2 != prevCell2;
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
					if (vertical) {
						nextRowBox.pageSize = Math.max(nextRowBox.pageSize, nextCell2.getWidth() / span);
					} else {
						nextRowBox.pageSize = Math.max(nextRowBox.pageSize, nextCell2.getHeight() / span);
					}
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
			if (vertical) {
				nextRowBox.pageSize = Math.max(nextRowBox.pageSize, nextCellBox.getWidth() / span);
			} else {
				nextRowBox.pageSize = Math.max(nextRowBox.pageSize, nextCellBox.getHeight() / span);
			}
		}

		// System.err.println("TR nextRowBox: pass=" + (nextRowBox ==
		// null)+"/"+flags);
		if (nextRowBox == null) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return null;
			}
			// 現在の行を持ち越す
			return this;
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
		return nextRowBox;
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
			TableCellBox nextCell = (TableCellBox) prevCell.splitPageAxis(cutPageAxis, BreakMode.DEFAULT_BREAK_MODE,
					IPageBreakableBox.FLAGS_SPLIT);
			assert nextCell != null;
			assert nextCell != prevCell;
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
			if (vertical) {
				this.pageSize = Math.max(this.pageSize, nextCell.getWidth() / span);
			} else {
				this.pageSize = Math.max(this.pageSize, nextCell.getHeight() / span);
			}
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
