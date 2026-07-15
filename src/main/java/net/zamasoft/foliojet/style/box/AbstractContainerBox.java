package net.zamasoft.foliojet.style.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.content.ColumnsContainer;
import net.zamasoft.foliojet.style.box.content.Container;
import net.zamasoft.foliojet.style.box.content.FlowContainer;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.LengthType;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.style.box.params.Types;
import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.builder.impl.ColumnBuilder;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.style.util.StyleUtils;

/**
 * 通常のフローを含むことができるボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractContainerBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public abstract class AbstractContainerBox extends AbstractBox
		implements IPageBreakableBox, INonReplacedBox, IFramedBox {

	protected Dimension size, minSize;

	protected AbsoluteRectFrame frame;

	protected double width = 0;
	protected double height = 0;
	protected double minPageAxis = 0, maxPageAxis = Double.MAX_VALUE;
	protected double offsetX = 0, offsetY = 0;

	protected Container container;

	protected AbstractContainerBox(Dimension size, Dimension minSize, Container container) {
		assert size != null;
		this.size = size;
		this.minSize = minSize;
		this.container = container;
		container.setBox(this);
	}

	public final Container getContainer() {
		return this.container;
	}

	/**
	 * コンテナボックスのパラメータを返します。
	 * 
	 * @return
	 */
	public abstract BlockParams getBlockParams();

	/**
	 * ページ方向に拡張します。
	 * 
	 * @param newSize
	 */

	public void setPageAxis(final double newSize) {
		final BlockParams params = this.getBlockParams();
		if (StyleUtils.isVertical(params.flow)) {
			// 縦書き
			if (newSize <= this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		} else {
			// 横書き
			if (newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
	}

	/**
	 * ページ方向サイズが明示されていればtrueを返します。
	 * 
	 * @return
	 */
	public abstract boolean isSpecifiedPageSize();

	/**
	 * 絶対配置の基準となるボックスではtrueを返します。
	 * 
	 * @return
	 */
	public abstract boolean isContextBox();

	/**
	 * 枠を描画します。
	 * 
	 * @param pageBox
	 *            TODO
	 * @param drawer
	 * @param clip
	 * @param transform
	 *            TODO
	 * @param x
	 * @param y
	 */
	public abstract void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y);

	/**
	 * 行幅を返します。
	 * 
	 * @return
	 */
	public final double getLineSize() {
		final BlockParams params = this.getBlockParams();
		double lineSize = StyleUtils.getMaxAdvance(this);
		final int columnCount = this.getColumnCount();
		if (columnCount >= 2) {
			// マルチカラム
			lineSize = (lineSize + params.columns.gap) / columnCount - params.columns.gap;
		}
		return lineSize;
	}

	public int getColumnCount() {
		return 1;
	}

	public final boolean canColumnBreak() {
		final int columnCount = this.getColumnCount();
		if (columnCount < 2) {
			return false;
		}
		if (this.isSpecifiedPageSize()) {
			return true;
		}
		return this.getActualColumnCount() < columnCount;
	}

	protected int getActualColumnCount() {
		if (this.container instanceof ColumnsContainer columns) {
			return columns.getColumnCount();
		}
		return 1;
	}

	public final boolean isFixedMulcolumn() {
		if (this.getColumnCount() <= 1) {
			return false;
		}
		BlockParams params = this.getBlockParams();
		if (StyleUtils.isVertical(params.flow)) {
			if (this.size.getWidthType() == LengthType.AUTO) {
				return false;
			}
		} else {
			if (this.size.getHeightType() == LengthType.AUTO) {
				return false;
			}
		}
		return true;
	}

	public final void balance(final BlockBuilder builder) {
		final Container oldCont = this.container;

		final int acc = this.getActualColumnCount();
		double pageSize = oldCont.getContentSize();
		if (StyleUtils.isVertical(this.getBlockParams().flow)) {
			// 縦書き
			if (acc >= 2) {
				pageSize += this.width * (acc - 1);
			}
			pageSize /= (double) this.getColumnCount();
			pageSize = oldCont.getCutPoint(pageSize);
			this.maxPageAxis = this.width = pageSize;
		} else {
			// 横書き
			if (acc >= 2) {
				pageSize += this.height * (acc - 1);
			}
			pageSize /= (double) this.getColumnCount();
			pageSize = oldCont.getCutPoint(pageSize);
			this.maxPageAxis = this.height = pageSize;
		}

		this.container = new FlowContainer();
		this.container.setBox(this);

		final ColumnBuilder columnBuilder = new ColumnBuilder(builder, this);
		oldCont.restyle(columnBuilder, 0, true);
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getFirstAscent() {
		return this.container.getFirstAscent();
	}

	public final double getLastDescent() {
		return this.container.getLastDescent();
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public final double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final double getTextIndent() {
		final double textIndent;
		final BlockParams params = this.getBlockParams();
		switch (params.textIndent.getType()) {
		case ABSOLUTE:
			textIndent = params.textIndent.getLength();
			break;
		case RELATIVE:
			textIndent = params.textIndent.getLength() * this.getLineSize();
			break;
		default:
			throw new IllegalStateException();
		}
		return textIndent;
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	public final void addFlow(IFlowBox box, double pageAxis) {
		this.container.addFlow(box, pageAxis);
	}

	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.container.addAbsolute(box, staticX, staticY);
	}

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.container.addFloating(box, lineAxis, pageAxis);
	}

	public void finishLayout(IFramedBox containerBox) {
		this.container.finishLayout(containerBox);
	}

	protected final Shape clip(Shape clip, double x, double y) {
		if (this.getBlockParams().overflow != Types.OVERFLOW_HIDDEN) {
			return clip;
		}
		Rectangle2D.Double newClip = new Rectangle2D.Double(
				x + this.frame.frame.border.getLeft().width + this.frame.margin.left,
				y + this.frame.frame.border.getTop().width + this.frame.margin.top,
				this.width + this.frame.padding.getFrameWidth(), this.height + this.frame.padding.getFrameHeight());
		if (clip == null) {
			return newClip;
		}
		return newClip.createIntersection((Rectangle2D) clip);
	}

	protected abstract AbstractContainerBox splitPage(Container container, double pageLimit, byte flags);

	public Container newColumn(double pageLimit, final BreakMode mode, final byte flags) {
		// このpageLimitは内辺から始まる
		final Container newContainer = this.container.splitPageAxis(pageLimit, mode, flags);

		if (newContainer == null) {
			return null;
		}
		final ColumnsContainer columns;
		if (this.container instanceof ColumnsContainer cc) {
			columns = cc;
			if (newContainer == columns.getLastColumn()) {
				return null;
			}
		} else {
			if (newContainer == this.container) {
				return null;
			}
			this.container = columns = new ColumnsContainer((FlowContainer) this.container);
			columns.setBox(this);
		}
		if (StyleUtils.isVertical(this.getBlockParams().flow)) {
			this.width = pageLimit;
		} else {
			this.height = pageLimit;
		}
		columns.newColumn();
		return newContainer;
	}

	public IPageBreakableBox splitPageAxis(double pageLimit, final BreakMode mode, final byte flags) {
		if (StyleUtils.isVertical(this.getBlockParams().flow)) {
			pageLimit -= this.frame.getFrameRight();
		} else {
			pageLimit -= this.frame.getFrameTop();
		}
		byte xflags = flags;
		if ((flags & IPageBreakableBox.FLAGS_COLUMN) != 0 && this.getColumnCount() > 1) {
			xflags ^= IPageBreakableBox.FLAGS_COLUMN;
		}
		final Container nextContainer = this.container.splitPageAxis(pageLimit, mode, xflags);
		if (nextContainer == null) {
			return null;
		}
		if (nextContainer == this.container) {
			return this;
		}
		return this.splitPage(nextContainer, pageLimit, flags);
	}

	public final void getText(StringBuilder textBuff) {
		this.container.getText(textBuff);
	}
	
	public final void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y) {
		x += this.offsetX;
		y += this.offsetY;
		transform = this.transform(transform, x, y);
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		this.container.textShape(pageBox, path, transform, x, y);
	}

	public void restyle(BlockBuilder builder, int depth) {
		this.container.restyle(builder, depth, false);
	}
}
