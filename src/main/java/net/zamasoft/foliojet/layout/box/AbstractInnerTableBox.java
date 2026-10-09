package net.zamasoft.foliojet.layout.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

public abstract class AbstractInnerTableBox extends AbstractBox implements INonReplacedBox {
	protected final InnerTableParams params;
	protected TableParams tableParams;
	protected double lineSize, pageSize;

	public AbstractInnerTableBox(final InnerTableParams params) {
		this.params = params;
	}

	public final Params getParams() {
		return this.params;
	}

	public final InnerTableParams getInnerTableParams() {
		return this.params;
	}

	public final void setTableParams(TableParams tableParams) {
		this.tableParams = tableParams;
	}

	public final double getLineSize() {
		return this.lineSize;
	}

	public final double getPageSize() {
		return this.pageSize;
	}

	public final double getWidth() {
		return this.tableParams.flow.isVertical() ? this.pageSize : this.lineSize;
	}

	public final double getHeight() {
		return this.tableParams.flow.isVertical() ? this.lineSize : this.pageSize;
	}

	public final double getInnerWidth() {
		return this.getWidth();
	}

	public final double getInnerHeight() {
		return this.getHeight();
	}
	
	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double d,
			Deque<TextShapeStep> worklist) {
		// TODO
	}

	/**
	 * Draws frames (made iterative on 2026-07-20, for the same reason and under the same contract
	 * as {@link AbstractContainerBox#frames}). Table internals (rows, row groups, columns, column groups)
	 * form a separate hierarchy that does not extend {@link AbstractContainerBox},
	 * so this common parent class provides the entry point.
	 */
	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		final Deque<FramesStep> worklist = new ArrayDeque<>();
		worklist.push(AbstractInnerTableBox.framesStep(this, pageBox, drawer, clip, transform, x, y));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/** Creates one {@link FramesStep} that executes {@code box}'s {@link #pushFramesSteps}. */
	public static FramesStep framesStep(final AbstractInnerTableBox box, final PageBox pageBox, final Drawer drawer,
			final Shape clip, final AffineTransform transform, final double x, final double y) {
		return worklist -> box.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	/**
	 * Pushes frame-drawing steps for this box and its descendants onto {@code worklist}.
	 * Follow the same convention as {@link IBox#pushDrawSteps}: push in **reverse order**
	 * to preserve the original traversal order.
	 */
	public abstract void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform,
			double x, double y, Deque<FramesStep> worklist);
}
