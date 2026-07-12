package net.zamasoft.foliojet.style.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.content.Container;
import net.zamasoft.foliojet.style.box.params.Background;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.Insets;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.RectBorder;
import net.zamasoft.foliojet.style.box.params.RectFrame;
import net.zamasoft.foliojet.style.box.params.TableCellPos;
import net.zamasoft.foliojet.style.box.params.TableParams;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.style.draw.DebugDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.part.AbsoluteInsets;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;

/**
 * テーブルセルの実装です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableCellBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class TableCellBox extends AbstractContainerBox {
	private static final boolean DEBUG = false;

	protected final BlockParams params;

	protected final TableCellPos pos;

	protected double verticalAlign = 0, pageSize = 0;

	protected boolean collapse;

	protected boolean forceDraw;

	public TableCellBox(final BlockParams params, final TableCellPos pos, final Container container) {
		this(params, pos, params.size, params.minSize, new AbsoluteRectFrame(params.frame), container);
	}

	public TableCellBox(final BlockParams params, final TableCellPos pos, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, Container container) {
		super(size, minSize, container);
		this.params = params;
		this.pos = pos;
		this.frame = frame;
	}

	public final byte getType() {
		return IBox.TYPE_TABLE_CELL;
	}

	public final Params getParams() {
		return this.params;
	}

	public final BlockParams getBlockParams() {
		return this.params;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableCellPos getTableCellPos() {
		return this.pos;
	}

	public final boolean isSpecifiedPageSize() {
		return false;
	}

	public void setPageAxis(double newSize) {
		if (newSize > this.pageSize) {
			this.pageSize = newSize;
		}
		super.setPageAxis(newSize);
	}

	public final void setWidth(double width) {
		assert !StyleUtils.isNone(width);
		this.width = width - this.frame.getFrameWidth();
	}

	public final void setHeight(double height) {
		//System.out.println("setInnerHeight:"+this.height+"/"+height);
		assert !StyleUtils.isNone(height);
		this.height = height - this.frame.getFrameHeight();
	}

	public final void verticalAlign() {
		switch (this.pos.verticalAlign) {
		case Types.VERTICAL_ALIGN_START:
		case Types.VERTICAL_ALIGN_BASELINE:
			// 上寄せ・ベースライン
			return;
		}
		double pageSize;
		if (StyleUtils.isVertical(this.params.flow)) {
			pageSize = this.width;
		} else {
			pageSize = this.height;
		}
		double diff = Math.max(0, pageSize - this.pageSize);
		switch (this.pos.verticalAlign) {
		case Types.VERTICAL_ALIGN_END:
			// 下寄せ
			this.verticalAlign = diff;
			break;
		case Types.VERTICAL_ALIGN_MIDDLE:
			// 中央寄せ
			this.verticalAlign = diff / 2.0;
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public final void prepareLayout(double lineSize, TableBox tableBox, AbsoluteInsets spacing) {
		StyleUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		this.frame.margin = spacing;

		TableParams tableParams = tableBox.getTableParams();
		this.collapse = tableParams.borderCollapse == TableParams.BORDER_COLLAPSE;
		RectFrame frame = this.frame.frame;
		if (this.collapse) {
			this.frame.frame = RectFrame.create(frame.margin, RectBorder.NONE_RECT_BORDER, frame.background,
					frame.padding);
		}

		if (StyleUtils.isVertical(this.params.flow)) {
			switch (this.minSize.getWidthType()) {
			case Dimension.TYPE_ABSOLUTE:
				this.minPageAxis = this.minSize.getWidth();
				break;
			case Dimension.TYPE_RELATIVE:
			case Dimension.TYPE_AUTO:
				this.minPageAxis = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (params.maxSize.getWidthType()) {
			case Dimension.TYPE_ABSOLUTE:
				this.maxPageAxis = this.params.maxSize.getWidth();
				break;
			case Dimension.TYPE_RELATIVE:
			case Dimension.TYPE_AUTO:
				this.maxPageAxis = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			this.width = this.minPageAxis;
		} else {
			switch (this.minSize.getHeightType()) {
			case Dimension.TYPE_ABSOLUTE:
				this.minPageAxis = this.minSize.getHeight();
				break;
			case Dimension.TYPE_RELATIVE:
			case Dimension.TYPE_AUTO:
				this.minPageAxis = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (params.maxSize.getHeightType()) {
			case Dimension.TYPE_ABSOLUTE:
				this.maxPageAxis = this.params.maxSize.getHeight();
				break;
			case Dimension.TYPE_RELATIVE:
			case Dimension.TYPE_AUTO:
				this.maxPageAxis = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			this.height = this.minPageAxis;
		}
	}

	public final void baseline(double rowAscent) {
		// System.err.println("baseline: " + rowAscent);
		if (this.pos.verticalAlign != Types.VERTICAL_ALIGN_BASELINE) {
			return;
		}
		double firstAscent = this.getFirstAscent();
		if (StyleUtils.isNone(firstAscent)) {
			return;
		}
		double xascent = rowAscent - firstAscent;
		if (xascent > 0) {
			this.verticalAlign += xascent;
			if (StyleUtils.isVertical(this.params.flow)) {
				this.width += xascent;
			} else {
				this.height += xascent;
			}
		}
	}

	public final boolean isContextBox() {
		return this.getTableCellPos().offset != null;
	}

	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		if (this.params.opacity == 0) {
			return;
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		if (this.draw()) {
			Drawable drawable = new TableCellBoxDrawable(clip, pageBox, this.params.opacity, transform,
					this.frame.frame.background, this.frame.frame.border, this.frame.frame.padding, this.collapse, this.frame.margin,
					this.getWidth(), this.getHeight());
			drawer.visitDrawable(drawable, x, y);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		if (StyleUtils.isVertical(this.params.flow)) {
			x -= this.verticalAlign;
		} else {
			y += this.verticalAlign;
		}
		this.container.drawFlowFrames(pageBox, drawer, clip, transform, x, y);
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0 || this.isContextBox() || !this.container.hasFloatings()) {
			return;
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		if (this.params.overflow == Types.OVERFLOW_HIDDEN) {
			// クリッピング
			clip = this.clip(clip, x, y);
		}
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		if (StyleUtils.isVertical(this.params.flow)) {
			x -= this.verticalAlign;
		} else {
			y += this.verticalAlign;
		}
		this.container.drawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
	}

	public final void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (DEBUG) {
			Drawable drawable = new DebugDrawable(this.getWidth(), this.getHeight(), RGBColor.create(0, 1, 1));
			drawer.visitDrawable(drawable, x, y);
		}
		if (this.isContextBox()) {
			this.frames(pageBox, drawer, clip, transform, x, y);
		}
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			Drawer newDrawer = new Drawer(this.params.zIndexValue);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}
		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		if (StyleUtils.isVertical(this.params.flow)) {
			x -= this.verticalAlign;
		} else {
			y += this.verticalAlign;
		}

		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		final boolean contextBox = this.isContextBox();
		if (contextBox) {
			contextX = x;
			contextY = y;
			this.container.drawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
		}
		this.container.drawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
		if (!contextBox) {
			clip = null;
		}
		this.container.drawAbsolutes(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);

		pageBox.endStruct(drawer, this.params.element, structCount, x, y);
	}

	private final boolean draw() {
		if (DEBUG) {
			return true;
		}
		if (!this.frame.isVisible()) {
			return false;
		}
		if (this.pos.emptyCells == Types.EMPTY_CELLS_SHOW) {
			return true;
		}
		if (this.collapse) {
			return true;
		}
		if (this.container.hasFlows()) {
			return true;
		}
		if (this.container.hasFloatings()) {
			return true;
		}
		if (this.forceDraw) {
			return true;
		}
		return false;
	}

	protected static class TableCellBoxDrawable extends BackgroundBorderDrawable {
		protected final boolean collapse;
		protected final AbsoluteInsets spacing;

		public TableCellBoxDrawable(Shape clip, PageBox pageBox, float opacity, AffineTransform transform,
				Background background, RectBorder border, Insets padding, boolean collapse, AbsoluteInsets spacing, double width,
				double height) {
			super(pageBox, clip, opacity, transform, background, border, padding, width, height);
			this.collapse = collapse;
			this.spacing = spacing;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			if (this.collapse) {
				this.background.draw(gc, x, y, this.width, this.height, this.border, this.padding, null);// TODO text clip
			} else {
				x += this.spacing.left;
				y += this.spacing.top;
				double width = this.width - this.spacing.getFrameWidth();
				double height = this.height - this.spacing.getFrameHeight();
				if (width >= 0 || height >= 0) {
					this.background.draw(gc, x, y, width, height, this.border, this.padding, null);// TODO text clip
					this.border.draw(gc, x, y, width, height);
				}
			}
		}
	}

	protected final AbstractContainerBox splitPage(Container container, double pageLimit, byte flags) {
		final boolean vertical = StyleUtils.isVertical(this.params.flow);
		final Dimension nextSize, nextMinSize;
		final AbsoluteRectFrame nextFrame;
		if (vertical) {
			if (this.size.getWidthType() != Dimension.TYPE_AUTO) {
				// 高さ指定
				double width = Math.max(0, this.width - pageLimit);
				nextSize = Dimension.create(this.size.getHeight(), width, this.size.getHeightType(),
						Dimension.TYPE_ABSOLUTE);
			} else {
				nextSize = this.size;
			}
			if (this.minSize.getWidthType() != Dimension.TYPE_AUTO) {
				// 高さ指定
				double width = Math.max(0, this.width - pageLimit);
				nextMinSize = Dimension.create(this.minSize.getHeight(), width, this.minSize.getHeightType(),
						Dimension.TYPE_ABSOLUTE);
			} else {
				nextMinSize = this.minSize;
			}
			nextFrame = this.frame.cut(true, false, true, true);
		} else {
			if (this.size.getHeightType() != Dimension.TYPE_AUTO) {
				// 高さ指定
				double height = Math.max(0, this.height - pageLimit);
				nextSize = Dimension.create(this.size.getWidth(), height, this.size.getWidthType(),
						Dimension.TYPE_ABSOLUTE);
			} else {
				nextSize = this.size;
			}
			if (this.minSize.getHeightType() != Dimension.TYPE_AUTO) {
				// 高さ指定
				double height = Math.max(0, this.height - pageLimit);
				nextMinSize = Dimension.create(this.minSize.getWidth(), height, this.minSize.getWidthType(),
						Dimension.TYPE_ABSOLUTE);
			} else {
				nextMinSize = this.minSize;
			}
			nextFrame = this.frame.cut(false, true, true, true);
		}

		final TableCellBox cell = new TableCellBox(this.params, this.pos, nextSize, nextMinSize, nextFrame, container);
		cell.collapse = this.collapse;
		cell.forceDraw = this.draw();
		if (vertical) {
			cell.height = this.height;
			this.frame = this.frame.cut(true, true, true, false);
			this.width = pageLimit;
		} else {
			cell.width = this.width;
			this.frame = this.frame.cut(true, true, false, true);
			this.height = pageLimit;
		}
		this.forceDraw = this.draw();
		// System.err.println("CELL B: "+this.height+"/"+cell.getInnerHeight());
		return cell;
	}

	public final IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		boolean vertical = StyleUtils.isVertical(this.params.flow);
		pageLimit -= this.verticalAlign;
		TableCellBox nextBox = (TableCellBox) super.splitPageAxis(pageLimit, mode, flags);
		// System.err.println("CELL A: pageLimit=" + pageLimit + "/mode=" + mode
		// + "/flags=" + flags + "/" + (nextBox == null) + "/"
		// + (nextBox == this));
		if (nextBox == null || nextBox == this) {
			assert (flags & IPageBreakableBox.FLAGS_SPLIT) == 0 : (nextBox != null);
			return nextBox;
		}
		if (this.container.hasFloatings()) {
			if (vertical) {
				pageLimit -= this.frame.getFrameRight();
			} else {
				pageLimit -= this.frame.getFrameTop();
			}
			this.container.splitFloatings(nextBox.container, pageLimit, flags);
		}
		return nextBox;
	}
}
