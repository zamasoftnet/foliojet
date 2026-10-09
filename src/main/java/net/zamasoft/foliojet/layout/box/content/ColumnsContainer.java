package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.TextShapeSink;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public class ColumnsContainer implements Container {

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		for (final Container column : this.columns) {
			column.forEachAssignmentChild(action);
		}
	}
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
			if (params.flow.isVertical()) {
				if (params.writingModeVariant != WritingModeVariant.NORMAL
						&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
								params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
					for (int i = 1; i < ColumnsContainer.this.getColumnCount(); ++i) {
						final double logicalRule = i * columnSize - params.columns.gap / 2;
						final double physicalRule = LayoutUtils.inlineToPhysical(params,
								ColumnsContainer.this.box.getInnerHeight(), logicalRule, logicalRule);
						BorderRenderer.INSTANCE.drawHorizontalBorder(gc, params.columns.rule, x,
								y + physicalRule, ColumnsContainer.this.box.getInnerWidth());
					}
				} else {
					for (int i = 1; i < ColumnsContainer.this.getColumnCount(); ++i) {
						y += columnSize;
						BorderRenderer.INSTANCE.drawHorizontalBorder(gc, params.columns.rule, x,
								y - params.columns.gap / 2, ColumnsContainer.this.box.getInnerWidth());
					}
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

	@Override
	public void eachFloatingBox(final java.util.function.Consumer<IFloatBox> consumer) {
		// Preserve column order and enumeration order within each column. Leave absolute-position binding to the caller.
		for (final Container column : this.columns) {
			column.eachFloatingBox(consumer);
		}
	}

	@Override
	public void eachAbsoluteBox(final java.util.function.Consumer<IAbsoluteBox> consumer) {
		for (final Container column : this.columns) {
			column.eachAbsoluteBox(consumer);
		}
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

	public double paintedPageEnd() {
		// Columns share the same page-axis range, so use the one that paints farthest.
		double end = 0;
		for (int i = 0; i < this.columns.size(); ++i) {
			end = Math.max(end, this.columns.get(i).paintedPageEnd());
		}
		return end;
	}

	public boolean paintsAnything() {
		// Draw column rules (column-rule) even without content.
		if (this.box.getBlockParams().columns.rule.isVisible()) {
			return true;
		}
		for (int i = 0; i < this.columns.size(); ++i) {
			if (this.columns.get(i).paintsAnything()) {
				return true;
			}
		}
		return false;
	}

	public double getCutPoint(double pageAxis) {
		FlowContainer first = (FlowContainer) this.columns.get(0);
		return first.getCutPoint(pageAxis);
	}

	public double getCutPointBelow(double pageAxis) {
		FlowContainer first = (FlowContainer) this.columns.get(0);
		return first.getCutPointBelow(pageAxis);
	}

	public void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		if (params.columns.rule.isVisible()) {
			Drawable drawable = new ColumnRuleDrawable(pageBox, clip, params.opacity, transform, x, y).withBlendMode(params.blendMode).withFilter(params.filter);
			drawer.visitDrawable(drawable, x, y);
		}
		// Columns run along the line axis; only bottom-to-top inline progression reverses in physical coordinates.
		// The column count is small and fixed, so direct calls are safe
		// (process in reverse order to preserve traversal order).
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushFramesSteps(pageBox, drawer, clip, transform, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// Columns run along the line axis; only bottom-to-top inline progression reverses in physical coordinates.
		// The column count is small and fixed, so direct calls (even without reversal) pose no
		// stack-depth problem, but process in reverse order to preserve traversal order.
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushDrawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// Reverse bottom-to-top inline progression when converting to physical coordinates.
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// Reverse bottom-to-top inline progression when converting to physical coordinates.
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushDrawAbsolutes(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
	}

	public void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// Push columns onto the stack in reverse order (last first) to preserve the original traversal order (first first).
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final Container container = this.columns.get(i);
			worklist.push(w -> container.pushFinishLayoutChildren(containerBox, w));
		}
	}

	public void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// The column count is small and fixed, so direct calls are safe
		// (process in reverse order to preserve traversal order).
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			container.pushGetTextSteps(textBuff, worklist);
		}
	}
	
	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist) {
		// At the positions of pushDrawFlows (2026-10-09, for background-clip: text)
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			final double lineStart = LayoutUtils.inlineToPhysical(params, this.box.getInnerHeight(), i * columnSize,
					i * columnSize + this.box.getLineSize());
			container.pushTextShapeSteps(pageBox, sink, transform,
					LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, lineStart), worklist);
		}
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

	public net.zamasoft.foliojet.layout.fragment.ContainerCut splitPageAxis(final double pageLimit,
			final BreakMode mode, final byte flags, final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordColumnsSplitAttempt();
		final FlowContainer lastColumn = this.getLastColumn();
		final net.zamasoft.foliojet.layout.fragment.ContainerCut cut = lastColumn.splitPageAxis(pageLimit, mode,
				flags, plan);
		if (this.columns.size() > 1
				&& cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain(final Container container)
				&& container == lastColumn) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordLastColumnMoveCandidate();
		}
		return cut;
	}

	public FloatTransferResult splitFloatings(FloatTransferTarget target, double pageLimit, byte flags) {
		// A multi-column container does not move floats through this path (equivalent to the old API
		// returning nextBox unchanged, meaning no movement).
		return FloatTransferResult.KEEP_OWNER;
	}

	public java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags) {
		// A multi-column container does not move floats through this path (same reason as the three-argument version).
		return java.util.Optional.empty();
	}

	public void eachFlowBox(java.util.function.Consumer<IFlowBox> consumer) {
		for (int i = 0; i < this.columns.size(); ++i) {
			((FlowContainer) this.columns.get(i)).eachFlowBox(consumer);
		}
	}

	public void newColumn() {
		Container container = new FlowContainer();
		container.setBox(this.box);
		this.columns.add(container);
	}

	/**
	 * Reflows content divided into columns.
	 *
	 * <p>
	 * <b>Do not write while reading</b> (fixed 2026-07-27). Previously, the implementation traversed
	 * the live {@link #columns} while reflowing into that same list:
	 * </p>
	 *
	 * <ul>
	 * <li>{@link #addFlow} always writes to the <b>current last column</b>
	 * ({@link #getLastColumn()})</li>
	 * <li>{@link #newColumn()} appends to the list being traversed</li>
	 * <li>The replaying {@code FlowContainer} takes out its own {@code flows} and sets it to {@code null}</li>
	 * </ul>
	 *
	 * <p>
	 * As a result, <b>the container being read and emptied was also the reflow destination</b>,
	 * silently losing content. Take a snapshot first, install one empty column, then read
	 * the snapshot: the same procedure already used by {@code AbstractContainerBox.balance()}.
	 * </p>
	 *
	 * <p>
	 * <b>Only the last column may receive the open tail ({@code shape}).</b>
	 * {@code shape} represents the open tail of this box's entire logical flow. Passing it to
	 * another column makes {@code FlowContainer.restyleItem} choose open-chain descent,
	 * and {@code FlowBlockBox.restyle} <b>intentionally omits {@code endFlowBlock()}</b>,
	 * leaving that box open on {@code flowStack}. The next column is then laid out inside it,
	 * corrupting the structure, for example by placing an {@code <ol>} inside its own {@code <li>}.
	 * </p>
	 *
	 * <p>
	 * Measured on 2026-07-27, discovered in a 100,000-document sweep (1 in 20,000 documents):
	 * all inner content disappeared in three levels of nested multi-column layout.
	 * Addresses the content-loss defect in nested multi-column layout (2026-07-27).
	 * </p>
	 */
	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes) {
		final List<Container> snapshot = this.beginRestyleScope();
		final int last = snapshot.size() - 1;
		for (int i = 0; i <= last; ++i) {
			final FlowContainer container = (FlowContainer) snapshot.get(i);
			container.restyle(builder,
					i == last ? shape : net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED, restyleAbsolutes);
		}
	}

	/**
	 * Begins a reflow scope: snapshot all columns, clear the original list, and install
	 * an empty first column, in that order. Returns the snapshot of old columns to replay.
	 * Extracted from {@link #restyle} on 2026-07-30 in increment 1 of legacy recursion removal;
	 * shared with MULTICOL native descent in the worklist executor to avoid duplicate implementations.
	 */
	List<Container> beginRestyleScope() {
		final List<Container> snapshot = new ArrayList<Container>(this.columns);
		this.columns.clear();
		final FlowContainer fresh = new FlowContainer();
		fresh.setBox(this.box);
		this.columns.add(fresh);
		return snapshot;
	}
}
