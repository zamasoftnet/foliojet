package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
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

	public double getCutPointBelow(double pageAxis) {
		FlowContainer first = (FlowContainer) this.columns.get(0);
		return first.getCutPointBelow(pageAxis);
	}

	public void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		if (params.columns.rule.isVisible()) {
			Drawable drawable = new ColumnRuleDrawable(pageBox, clip, params.opacity, transform, x, y);
			drawer.visitDrawable(drawable, x, y);
		}
		// カラムは書字方向によらず行軸に沿って並ぶ(ページ方向の反転は不要)。
		// カラム数は小さく固定なので直接呼び出してよい(走査順を保つため
		// 逆順で処理する)
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			container.pushFramesSteps(pageBox, drawer, clip, transform, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, i * columnSize), worklist);
		}
	}

	public void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// カラムは書字方向によらず行軸に沿って並ぶ(ページ方向の反転は不要)。
		// カラム数は小さく固定なので、直接呼び出しても(逆順にせずとも)
		// スタック深さは問題にならないが、走査順を保つため逆順で処理する
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			container.pushDrawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, i * columnSize), worklist);
		}
	}

	public void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// カラムは書字方向によらず行軸に沿って並ぶ(ページ方向の反転は不要)
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, i * columnSize), worklist);
		}
	}

	public void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist) {
		final BlockParams params = this.box.getBlockParams();
		final double columnSize = this.box.getLineSize() + params.columns.gap;
		// カラムは書字方向によらず行軸に沿って並ぶ(ページ方向の反転は不要)
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final FlowContainer container = (FlowContainer) this.columns.get(i);
			container.pushDrawAbsolutes(pageBox, drawer, visitor, clip, transform, contextX, contextY, LayoutUtils.drawX(this.box.getBlockParams().flow, x, 0, 0, i * columnSize),
					LayoutUtils.drawY(this.box.getBlockParams().flow, y, 0, i * columnSize), worklist);
		}
	}

	public void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// 元の走査順(先頭カラムから)を保つため、スタックへは逆順(末尾カラムから)でpushする
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			final Container container = this.columns.get(i);
			worklist.push(w -> container.pushFinishLayoutChildren(containerBox, w));
		}
	}

	public void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		// カラム数は小さく固定なので直接呼び出してよい(走査順を保つため
		// 逆順で処理する)
		for (int i = this.columns.size() - 1; i >= 0; --i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			container.pushGetTextSteps(textBuff, worklist);
		}
	}
	
	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist) {
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
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordColumnsSplitAttempt();
		final FlowContainer lastColumn = this.getLastColumn();
		final Container result = lastColumn.splitPageAxis(pageLimit, mode, flags);
		if (this.columns.size() > 1 && result == lastColumn) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordLastColumnMoveCandidate();
		}
		return result;
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
		// 段組コンテナはこの経路でfloatを移動しない(旧APIのnextBox
		// そのまま返し=移動なしと同じ)
		return FloatTransferResult.KEEP_OWNER;
	}

	public java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags) {
		// 段組コンテナはこの経路でfloatを移動しない(3引数版と同じ理由)
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

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes) {
		for (int i = 0; i < this.columns.size(); ++i) {
			FlowContainer container = (FlowContainer) this.columns.get(i);
			container.restyle(builder, shape, restyleAbsolutes);
		}
	}
}
