package net.zamasoft.foliojet.style.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.IAbsoluteBox;
import net.zamasoft.foliojet.style.box.IFloatBox;
import net.zamasoft.foliojet.style.box.IFlowBox;
import net.zamasoft.foliojet.style.box.IFramedBox;
import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.style.draw.AbstractDrawable;
import net.zamasoft.foliojet.style.draw.Drawable;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.util.BorderRenderer;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.foliojet.style.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class ColumnsContainer implements Container {
	protected class ColumnRuleDrawable extends AbstractDrawable {
		protected final double x, y;

		public ColumnRuleDrawable(PageBox pageBox, Shape clip, float opacity, AffineTransform transform, double x,
				double y) {
			super(pageBox, clip, opacity, transform);
			this.x = x;
			this.y = y;
		}

		public void innerDraw(GC gc, double x, double y) throws GraphicsException {
			final BlockParams params = ColumnsContainer.this.box.getBlockParams();
			final double columnSize = ColumnsContainer.this.box.getLineSize() + params.columns.gap;
			if (StyleUtils.isVertical(params.flow)) {
				for (int i = 1; i < ColumnsContainer.this.getColumnCount(); ++i) {
					y += columnSize;
					BorderRenderer.INSTANCE.drawHorizontalBorder(gc, params.columns.rule, x,
							y - params.columns.gap / 2, ColumnsContainer.this.box.getInnerWidth());
				}
			} else {
				for (int i = 1; i < ColumnsContainer.this.getColumnCount(); ++i) {
					x += columnSize;
					BorderRenderer.INSTANCE.drawVerticalBorder(gc, params.columns.rule,
							x - params.columns.gap / 2, y, ColumnsContainer.this.box.getInnerHeight());
				}
			}
		}
	}

	protected final List<Container> columns = new ArrayList<Container>();

	protected AbstractContainerBox box;

	public ColumnsContainer(FlowContainer container) {
		this.columns.add(container);
	}

	public final byte getType() {
		return TYPE_COLUMNS;
	}

	public int getColumnCount() {
		return this.columns.size();
	}

	public void setBox(AbstractContainerBox box) {
		this.box = box;
	}

	public final FlowContainer getLastColumn() {
		return (FlowContainer) this.columns.get(this.columns.size() - 1);
	}

	public void addFlow(IFlowBox box, double pageAxis) {
		this.getLastColumn().addFlow(box, pageAxis);
	}

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.getLastColumn().addFloating(box, lineAxis, pageAxis);
	}

	public void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		this.getLastColumn().addAbsolute(box, staticX, staticY);
	}

	public boolean avoidBreakAfter() {
		return false;
	}

	public boolean avoidBreakBefore() {
		return false;
	}

	public double getFirstAscent() {
		return 0;
	}

	public double getLastDescent() {
		return 0;
	}

	public double getContentSize() {
		return this.getLastColumn().getContentSize();
	}

	public double getCutPoint(double pageAxis) {
		FlowContainer first = (FlowContainer) this.columns.get(0);
		return first.getCutPoint(pageAxis);
	}

	public void drawFlowFrames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		if (params.columns.rule.isVisible()) {
			Drawable drawable = new ColumnRuleDrawable(pageBox, clip, params.opacity, transform, x, y);
			drawer.visitDrawable(drawable, x, y);
		}
		if (StyleUtils.isVertical(this.box.getBlockParams().flow)) {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					y += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawFlowFrames(pageBox, drawer, clip, transform, x, y);
			}
		} else {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					x += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawFlowFrames(pageBox, drawer, clip, transform, x, y);
			}
		}
	}

	public void drawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		if (StyleUtils.isVertical(this.box.getBlockParams().flow)) {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					y += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		} else {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					x += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		}
	}

	public void drawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		if (StyleUtils.isVertical(this.box.getBlockParams().flow)) {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					y += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		} else {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					x += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		}
	}

	public void drawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		if (StyleUtils.isVertical(this.box.getBlockParams().flow)) {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					y += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawAbsolutes(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		} else {
			for (int i = 0; i < this.columns.size(); ++i) {
				if (i >= 1) {
					x += columnSize;
				}
				FlowContainer container = (FlowContainer) this.columns.get(i);
				container.drawAbsolutes(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y);
			}
		}
	}

	public void finishLayout(IFramedBox containerBox) {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			container.finishLayout(containerBox);
		}
	}

	public void getText(StringBuilder textBuff) {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			container.getText(textBuff);
		}
	}
	
	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y) {
		// TODO
	}

	public boolean hasFloatings() {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			if (container.hasFloatings()) {
				return true;
			}
		}
		return false;
	}

	public boolean hasFlows() {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			if (container.hasFlows()) {
				return true;
			}
		}
		return false;
	}

	public Container splitPageAxis(final double pageLimit, final BreakMode mode, final byte flags) {
		return this.getLastColumn().splitPageAxis(pageLimit, mode, flags);
	}

	public Container splitFloatings(Container nextBox, double pageLimit, byte flags) {
		return nextBox;
	}

	public Floatings splitFloatings(double pageLimit, byte flags) {
		return null;
	}

	public void newColumn() {
		Container container = new FlowContainer();
		container.setBox(this.box);
		this.columns.add(container);
	}

	public void restyle(BlockBuilder builder, int depth, boolean restyleAbsolutes) {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			container.restyle(builder, depth, restyleAbsolutes);
		}
	}
}
