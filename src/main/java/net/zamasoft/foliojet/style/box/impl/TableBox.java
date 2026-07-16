package net.zamasoft.foliojet.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractBlockBox;
import net.zamasoft.foliojet.style.box.AbstractBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IFlowBox;
import net.zamasoft.foliojet.style.box.IFramedBox;
import net.zamasoft.foliojet.style.box.INonReplacedBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.RectBorder;
import net.zamasoft.foliojet.style.box.params.RectFrame;
import net.zamasoft.foliojet.style.box.params.TableParams;
import net.zamasoft.foliojet.style.box.params.TablePos;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.draw.AbstractDrawable;
import net.zamasoft.foliojet.style.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.style.draw.DebugDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.style.part.TableCollapsedBorders;
import net.zamasoft.foliojet.style.util.BorderRenderer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * テーブルの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TableBox extends AbstractBox implements IPageBreakableBox, IFlowBox, INonReplacedBox {
	private static final boolean DEBUG = false;

	protected final TableParams params;

	protected final AbstractBlockBox block;

	protected AbsoluteRectFrame frame;

	protected TableColumnGroupBox columnGroupBox = null;

	protected TableRowGroupBox headerGroupBox = null;

	protected List<TableRowGroupBox> bodyGroups = null;

	protected TableRowGroupBox footerGroupBox = null;

	protected TableCollapsedBorders borders;

	protected double width = 0;

	protected double height = 0;

	protected double offsetX = 0;

	protected double offsetY = 0;

	public TableBox(final TableParams params, final AbstractBlockBox block) {
		this(params, new AbsoluteRectFrame(params.frame), block);
	}

	protected TableBox(final TableParams params, final AbsoluteRectFrame frame, final AbstractBlockBox block) {
		this.params = params;
		this.block = block;
		this.frame = frame;
	}

	public final BoxType getType() {
		return BoxType.TABLE;
	}

	public final Pos getPos() {
		return TablePos.POS;
	}

	public final Params getParams() {
		return this.params;
	}

	public final TableParams getTableParams() {
		return this.params;
	}

	public final AbstractBlockBox getBlockBox() {
		return this.block;
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final void setSize(double width, double height) {
		this.width = width;
		this.height = height;
	}

	public final void calculateFrame(double lineSize) {
		StyleUtils.computeMarginsAutoToZero(this.frame.margin, this.params.frame.margin, lineSize);
		if (this.params.borderCollapse == TableParams.BORDER_SEPARATE) {
			// 分離境界モデル
			//
			// ■ パディングの計算
			//
			StyleUtils.computePaddings(this.frame.padding, this.params.frame.padding, lineSize);
			this.frame.padding.top = params.borderSpacingV / 2.0;
			this.frame.padding.right = params.borderSpacingH / 2.0;
			this.frame.padding.bottom = params.borderSpacingV / 2.0;
			this.frame.padding.left = params.borderSpacingH / 2.0;
		} else {
			this.frame.frame = RectFrame.create(params.frame.margin, RectBorder.NONE_RECT_BORDER,
					params.frame.background, params.frame.padding);
			return;
		}
	}

	public final void finishLayout(IFramedBox containerBox) {
		if (this.headerGroupBox != null) {
			this.headerGroupBox.finishLayout(containerBox);
		}
		for (int i = 0; i < this.getTableBodyCount(); ++i) {
			TableRowGroupBox rowGroupBox = this.getTableBody(i);
			rowGroupBox.finishLayout(containerBox);
		}
		if (this.footerGroupBox != null) {
			this.footerGroupBox.finishLayout(containerBox);
		}
	}

	public final void setCollapsedBorders(TableCollapsedBorders borders) {
		assert borders != null;
		this.borders = borders;
	}

	public final TableCollapsedBorders getCollapsedBorders() {
		return this.borders;
	}

	public final void setTableColumnGroup(TableColumnGroupBox columnGroup) {
		this.columnGroupBox = columnGroup;
	}

	public final void setTableHeader(TableRowGroupBox headerGroup) {
		this.headerGroupBox = headerGroup;
		if (this.params.flow.isVertical()) {
			this.width += headerGroup.getWidth();
			if (headerGroup.getHeight() > this.height) {
				this.height = headerGroup.getHeight();
			}
		} else {
			this.height += headerGroup.getHeight();
			if (headerGroup.getWidth() > this.width) {
				this.width = headerGroup.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableHeader() {
		return this.headerGroupBox;
	}

	public final void setTableFooter(TableRowGroupBox footerGroup) {
		this.footerGroupBox = footerGroup;
		if (this.params.flow.isVertical()) {
			this.width += footerGroup.getWidth();
			if (footerGroup.getHeight() > this.height) {
				this.height = footerGroup.getHeight();
			}
		} else {
			this.height += footerGroup.getHeight();
			if (footerGroup.getWidth() > this.width) {
				this.width = footerGroup.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableFooter() {
		return this.footerGroupBox;
	}

	public final void addTableBody(TableRowGroupBox rowGroupBox) {
		if (this.bodyGroups == null) {
			this.bodyGroups = new ArrayList<TableRowGroupBox>();
		}
		this.bodyGroups.add(rowGroupBox);
		if (this.params.flow.isVertical()) {
			this.width += rowGroupBox.getWidth();
			if (rowGroupBox.getHeight() > this.height) {
				this.height = rowGroupBox.getHeight();
			}
		} else {
			this.height += rowGroupBox.getHeight();
			if (rowGroupBox.getWidth() > this.width) {
				this.width = rowGroupBox.getWidth();
			}
		}
	}

	public final TableRowGroupBox getTableBody(int i) {
		return (TableRowGroupBox) this.bodyGroups.get(i);
	}

	public final int getTableBodyCount() {
		return this.bodyGroups == null ? 0 : this.bodyGroups.size();
	}

	private void drawBorders(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x, double y,
			double xx, double yy) {
		switch (this.params.borderCollapse) {
		case TableParams.BORDER_SEPARATE: {
			if (!this.frame.frame.border.isVisible()) {
				break;
			}
			// 分離境界
			Drawable drawable = new BorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.frame.frame.border,
					this.width + this.frame.padding.getFrameWidth() + this.frame.frame.border.getFrameWidth(),
					this.height + this.frame.padding.getFrameHeight() + this.frame.frame.border.getFrameHeight());
			drawer.visitDrawable(drawable, x + this.frame.margin.left, y + this.frame.margin.top);
		}
			break;

		case TableParams.BORDER_COLLAPSE: {
			// つぶし境界
			Drawable drawable = new CollapsedBordersDrawable(pageBox, clip, this.params.opacity, transform,
					this.borders, this.params.flow.isVertical());
			drawer.visitDrawable(drawable, xx, yy);
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		assert !StyleUtils.isNone(x) : "Undefined x";
		assert !StyleUtils.isNone(y) : "Undefined y";
		x += this.offsetX;
		y += this.offsetY;

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}
		double xx = x + this.frame.getFrameLeft();
		double yy = y + this.frame.getFrameTop();

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		if (this.params.frame.background.isVisible()) {
			Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.frame.background, this.params.frame.border, this.params.frame.padding,
					this.getWidth() - this.frame.getFrameWidth(), this.getHeight() - this.frame.getFrameHeight());
			drawer.visitDrawable(drawable, xx, yy);
		}

		if (this.params.flow.isVertical()) {
			// 縦書き
			// 内部の境界/背景
			{
				double xxx = xx;
				if (this.columnGroupBox != null) {
					this.columnGroupBox.frames(pageBox, drawer, clip, transform, xxx, yy);
				}
				xxx += this.width;
				if (this.headerGroupBox != null) {
					xxx -= this.headerGroupBox.getWidth();
					this.headerGroupBox.frames(pageBox, drawer, clip, transform, xxx, yy);
				}
				if (this.bodyGroups != null) {
					for (int i = 0; i < this.bodyGroups.size(); ++i) {
						TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
						xxx -= rowGroup.getWidth();
						rowGroup.frames(pageBox, drawer, clip, transform, xxx, yy);
					}
				}
				if (this.footerGroupBox != null) {
					xxx -= this.footerGroupBox.getWidth();
					this.footerGroupBox.frames(pageBox, drawer, clip, transform, xxx, yy);
				}
			}

			this.drawBorders(pageBox, drawer, clip, transform, x, y, xx, yy);

			// 浮動ボックス
			{
				double xxx = xx + this.width;
				if (this.headerGroupBox != null) {
					xxx -= this.headerGroupBox.getWidth();
					this.headerGroupBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yy);
				}
				if (this.bodyGroups != null) {
					for (int i = 0; i < this.bodyGroups.size(); ++i) {
						TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
						xxx -= rowGroup.getWidth();
						rowGroup.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yy);
					}
				}
				if (this.footerGroupBox != null) {
					xxx -= this.footerGroupBox.getWidth();
					this.footerGroupBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yy);
				}
			}

			// 内容
			{
				double xxx = xx;
				if (this.columnGroupBox != null) {
					this.columnGroupBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yy);
				}
				xxx += this.width;
				if (this.headerGroupBox != null) {
					xxx -= this.headerGroupBox.getWidth();
					this.headerGroupBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yy);
				}
				if (this.bodyGroups != null) {
					for (int i = 0; i < this.bodyGroups.size(); ++i) {
						TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
						xxx -= rowGroup.getWidth();
						rowGroup.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yy);
					}
				}
				if (this.footerGroupBox != null) {
					xxx -= this.footerGroupBox.getWidth();
					this.footerGroupBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xxx, yy);
				}
			}
		} else {
			// 横書き
			// 内部の境界/背景
			{
				double yyy = yy;
				if (this.columnGroupBox != null) {
					this.columnGroupBox.frames(pageBox, drawer, clip, transform, xx, yyy);
				}
				if (this.headerGroupBox != null) {
					this.headerGroupBox.frames(pageBox, drawer, clip, transform, xx, yyy);
					yyy += this.headerGroupBox.getHeight();
				}
				if (this.bodyGroups != null) {
					for (int i = 0; i < this.bodyGroups.size(); ++i) {
						TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
						rowGroup.frames(pageBox, drawer, clip, transform, xx, yyy);
						yyy += rowGroup.getHeight();
					}
				}
				if (this.footerGroupBox != null) {
					this.footerGroupBox.frames(pageBox, drawer, clip, transform, xx, yyy);
					yyy += this.footerGroupBox.getHeight();
				}
			}

			this.drawBorders(pageBox, drawer, clip, transform, x, y, xx, yy);

			// 浮動ボックス
			{
				double yyy = yy;
				if (this.headerGroupBox != null) {
					this.headerGroupBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yyy);
					yyy += this.headerGroupBox.getHeight();
				}
				if (this.bodyGroups != null) {
					for (int i = 0; i < this.bodyGroups.size(); ++i) {
						TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
						rowGroup.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yyy);
						yyy += rowGroup.getHeight();
					}
				}
				if (this.footerGroupBox != null) {
					this.footerGroupBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yyy);
					yyy += this.footerGroupBox.getHeight();
				}
			}

			// 内容
			{
				double yyy = yy;
				if (this.columnGroupBox != null) {
					this.columnGroupBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yyy);
				}
				if (this.headerGroupBox != null) {
					this.headerGroupBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yyy);
					yyy += this.headerGroupBox.getHeight();
				}
				if (this.bodyGroups != null) {
					for (int i = 0; i < this.bodyGroups.size(); ++i) {
						TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
						rowGroup.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yyy);
						yyy += rowGroup.getHeight();
					}
				}
				if (this.footerGroupBox != null) {
					this.footerGroupBox.draw(pageBox, drawer, visitor, clip, transform, contextX, contextY, xx, yyy);
					yyy += this.footerGroupBox.getHeight();
				}
			}
		}
		pageBox.endStruct(drawer, this.params.element, structCount, x, y);
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(1, 0, 1));
			drawer.visitDrawable(drawable, x, y);
		}
	}

	public final void getText(final StringBuilder textBuff) {
		if (this.headerGroupBox != null) {
			this.headerGroupBox.getText(textBuff);
		}
		if (this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				TableRowGroupBox rowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
				rowGroup.getText(textBuff);
			}
		}
		if (this.footerGroupBox != null) {
			this.footerGroupBox.getText(textBuff);
		}
	}
	
	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d) {
		// TODO
	}

	protected static class BorderDrawable extends AbstractDrawable {
		protected final RectBorder border;
		protected final double width, height;

		public BorderDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform, RectBorder border,
				double width, double height) {
			super(pageBox, clip, opacity, transform);
			this.border = border;
			this.width = width;
			this.height = height;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			this.border.draw(gc, x, y, this.width, this.height);
		}

		@Override
		public String describe() {
			return String.format(java.util.Locale.ROOT, "TableBorder[w=%.2f h=%.2f]", this.width, this.height);
		}
	}

	protected static class CollapsedBordersDrawable extends AbstractDrawable {
		protected final TableCollapsedBorders borders;
		protected final boolean vertical;

		public CollapsedBordersDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform,
				TableCollapsedBorders borders, boolean vertical) {
			super(pageBox, clip, opacity, transform);
			this.borders = borders;
			this.vertical = vertical;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			BorderRenderer.INSTANCE.drawTableCollapseBorders(gc, this.borders, x, y, this.vertical);

		}
	}

	public final IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, byte flags) {
		// assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		// System.err.println("TABLE A: flags=" + flags + "/pageLimit=" +
		// pageLimit
		// + "/mode=" + mode + "/height=" + this.getHeight() + "/bodies="
		// + (this.bodyGroups == null ? 0 : this.bodyGroups.size()) + "/"
		// + this.getParams().element);

		final boolean vertical = this.params.flow.isVertical();
		int origBodyRowCount = 0;
		if (this.borders != null && this.bodyGroups != null) {
			for (int i = 0; i < this.bodyGroups.size(); ++i) {
				origBodyRowCount += this.getTableBody(i).getTableRowCount();
			}
		}
		if (mode instanceof BreakMode.ForceBreakMode) {
			// 行間強制改ページ
			TableForceBreakMode force = (TableForceBreakMode) mode;
			TableBox nextTable = this.splitTableBox();
			int rowGroup = force.rowGroup;
			int row = force.row;
			if (row != -1) {
				assert force.box.getType() == BoxType.TABLE_ROW || force.box.getType() == BoxType.TABLE_ROW_GROUP;
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) this.bodyGroups.get(rowGroup);
				TableRowGroupBox newRowGroupBox = (TableRowGroupBox) rowGroupBox.splitPageAxis(pageLimit, mode,
						(byte) 0);
				nextTable.addTableBody(newRowGroupBox);
				if (vertical) {
					this.width -= newRowGroupBox.getPageSize();
				} else {
					this.height -= newRowGroupBox.getPageSize();
				}
			}
			for (int j = rowGroup + 1; j < this.bodyGroups.size(); ++j) {
				nextTable.addTableBody((TableRowGroupBox) this.bodyGroups.get(j));
			}
			for (int j = this.bodyGroups.size() - 1; j > rowGroup; --j) {
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) this.bodyGroups.remove(j);
				if (vertical) {
					this.width -= rowGroupBox.getPageSize();
				} else {
					this.height -= rowGroupBox.getPageSize();
				}
			}
			if (this.borders != null) {
				// つぶし境界
				nextTable.borders = this.borders.splitPageAxis(this, nextTable, origBodyRowCount);
			}
			return nextTable;
		}

		if (this.bodyGroups == null || this.bodyGroups.isEmpty()) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return null;
			}
			// 全部移動
			return this;
		}

		if (vertical) {
			// 上下の改ページしない部分の高さを差し引く
			double over = this.getWidth() - pageLimit;
			pageLimit -= this.frame.getFrameRight();
			if (this.headerGroupBox != null) {
				pageLimit -= this.headerGroupBox.getPageSize();
			}
			if (this.footerGroupBox != null) {
				pageLimit -= this.footerGroupBox.getPageSize();
				pageLimit -= this.frame.getFrameLeft();
			} else {
				// 境界が下マージンに差し掛かった場合は切る
				if (over > 0 && StyleUtils.compare(over, this.frame.margin.left) < 0) {
					pageLimit -= this.frame.margin.left;
				}
			}
		} else {
			// 上下の改ページしない部分の高さを差し引く
			double over = this.getHeight() - pageLimit;
			pageLimit -= this.frame.getFrameTop();
			if (this.headerGroupBox != null) {
				pageLimit -= this.headerGroupBox.getPageSize();
			}
			if (this.footerGroupBox != null) {
				pageLimit -= this.footerGroupBox.getPageSize();
				pageLimit -= this.frame.getFrameBottom();
			} else {
				// 境界が下マージンに差し掛かった場合は切る
				if (over > 0 && StyleUtils.compare(over, this.frame.margin.bottom) < 0) {
					pageLimit -= this.frame.margin.bottom;
				}
			}
		}

		// System.err.println("TABLE B:" +
		// this.bodyGroups.size()+"/"+pageLimit);
		// テーブルのヘッダとフッタがおさまらない
		if (StyleUtils.compare(pageLimit, 0) <= 0) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return null;
			}
			// 全部移動
			return this;
		}

		TableBox nextTable = null;
		int i;
		boolean ignoreBreakAvoid = false;
		double savePageLimit = pageLimit;
		// System.err.println("C: " +pageLimit + "/" +
		// this.getHeight());
		for (i = 0; i < this.bodyGroups.size(); ++i) {
			final TableRowGroupBox prevRowGroup = (TableRowGroupBox) this.bodyGroups.get(i);
			double prevRowGroupSize = prevRowGroup.getPageSize();
			if (i < this.bodyGroups.size() - 1 && StyleUtils.compare(pageLimit, prevRowGroupSize) > 0) {
				pageLimit -= prevRowGroupSize;
				continue;
			}
			byte lflags = (byte) (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT);
			if (i > 0) {
				lflags ^= IPageBreakableBox.FLAGS_FIRST;
			}
			final TableRowGroupBox nextRowGroup = (TableRowGroupBox) prevRowGroup.splitPageAxis(pageLimit, mode,
					(byte) (lflags & flags));
			assert nextTable == null || nextRowGroup != null;
			// System.err.println("TABLE D:
			// "+lflags+"/"+flags+"/"+index+"/"+(nextTable
			// != null)+"/"+(nextRowGroup != null));
			if (nextRowGroup == null) {
				if (!ignoreBreakAvoid && i == 0 && (flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
					// ページ先頭の場合は改ページ禁止を無視してやりなおす
					ignoreBreakAvoid = true;
					pageLimit = savePageLimit;
					i = -1;
					continue;
				}
				pageLimit -= prevRowGroupSize;
				continue;
			}
			if (nextRowGroup == prevRowGroup) {
				if (i == 0) {
					// 全部移動
					assert (flags & IPageBreakableBox.FLAGS_FIRST) == 0;
					return this;
				}
				if (!ignoreBreakAvoid) {
					TableRowGroupBox beforeGroup = (TableRowGroupBox) this.bodyGroups.get(i - 1);
					boolean breakAvoid = beforeGroup.getTableRowGroupPos().pageBreakAfter == Types.PAGE_BREAK_AVOID
							|| prevRowGroup.getTableRowGroupPos().pageBreakBefore == Types.PAGE_BREAK_AVOID;
					if (!breakAvoid && beforeGroup.getTableRowCount() > 0) {
						breakAvoid = beforeGroup.getTableRow(beforeGroup.getTableRowCount() - 1)
								.getTableRowPos().pageBreakAfter == Types.PAGE_BREAK_AVOID;
					}
					if (!breakAvoid && prevRowGroup.getTableRowCount() > 0) {
						breakAvoid = prevRowGroup.getTableRow(0)
								.getTableRowPos().pageBreakBefore == Types.PAGE_BREAK_AVOID;
					}
					if (breakAvoid) {
						// 行グループの改ページ禁止
						// 一つ戻って前の行グループを末尾で切る
						pageLimit = beforeGroup.getPageSize() - StyleUtils.THRESHOLD * 2;
						i -= 2;
						continue;
					}
				}
				nextTable = this.splitTableBox();
				break;
			}
			nextTable = this.splitTableBox();
			prevRowGroupSize -= prevRowGroup.getPageSize();
			if (vertical) {
				this.width -= prevRowGroupSize;
			} else {
				this.height -= prevRowGroupSize;
			}
			nextTable.addTableBody(nextRowGroup);
			++i;
			break;
		}

		if (nextTable == null) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return null;
			}
			return this;
		}

		int remove = 0;
		for (int j = i; j < this.bodyGroups.size(); ++j) {
			final TableRowGroupBox prevRowGroup = (TableRowGroupBox) this.bodyGroups.get(j);
			if (vertical) {
				this.width -= prevRowGroup.getPageSize();
			} else {
				this.height -= prevRowGroup.getPageSize();
			}
			nextTable.addTableBody(prevRowGroup);
			++remove;
		}
		for (int j = 0; j < remove; ++j) {
			this.bodyGroups.remove(this.bodyGroups.size() - 1);
		}
		if (this.columnGroupBox != null) {
			// カラム
			if (vertical) {
				nextTable.columnGroupBox = (TableColumnGroupBox) this.columnGroupBox.splitPageAxis(this.width,
						nextTable.width);
			} else {
				nextTable.columnGroupBox = (TableColumnGroupBox) this.columnGroupBox.splitPageAxis(this.height,
						nextTable.height);
			}
		}
		if (this.borders != null) {
			// つぶし境界
			nextTable.borders = this.borders.splitPageAxis(this, nextTable, origBodyRowCount);
		}
		return nextTable;
	}

	public final TableBox splitTableBox() {
		final boolean vertical = this.params.flow.isVertical();
		final AbsoluteRectFrame nextFrame;
		if (this.headerGroupBox != null) {
			nextFrame = this.frame;
		} else {
			if (vertical) {
				nextFrame = this.frame.cut(true, false, true, true);
			} else {
				nextFrame = this.frame.cut(false, true, true, true);
			}
		}
		TableBox nextTable = new TableBox(this.params, nextFrame, this.block);
		if (vertical) {
			nextTable.height = this.height;
		} else {
			nextTable.width = this.width;
		}

		if (this.headerGroupBox != null) {
			nextTable.setTableHeader(this.headerGroupBox);
		}
		if (this.footerGroupBox != null) {
			nextTable.setTableFooter(this.footerGroupBox);
		} else {
			if (vertical) {
				this.frame = this.frame.cut(true, true, true, false);
			} else {
				this.frame = this.frame.cut(true, true, false, true);
			}
		}
		return nextTable;
	}

	public final boolean avoidBreakBefore() {
		return false;
	}

	public final boolean avoidBreakAfter() {
		return false;
	}
}
