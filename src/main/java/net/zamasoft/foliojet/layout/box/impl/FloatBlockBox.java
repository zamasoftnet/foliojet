package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Implementation of a block box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: FloatBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FloatBlockBox extends AbstractStaticBlockBox implements IFloatBox {
	/**
	 * Marks a float known not to make progress when split (2026-09-17).
	 * The placement-time progress check ({@code RootBuilder.fragmentStartFloatSplitProgresses})
	 * sets this for floats whose remainder, split at the page start, is laid out at the same size
	 * on the next page (contents such as orthogonal-flow cells or explicit dimensions prevent
	 * page-axis splitting). Page-break classification ({@code FloatSplitPlan.classify}) treats
	 * it as unsplittable and falls back to rescue splitting or "place while overflowing".
	 * Both always make progress.
	 */
	private boolean splitMakesNoProgress;

	public final void markSplitMakesNoProgress() {
		this.splitMakesNoProgress = true;
	}

	public final boolean splitMakesNoProgress() {
		return this.splitMakesNoProgress;
	}

	/**
	 * For the remainder of a float split at a fragment start by page-break classification, the occupied page extent
	 * the float had before that split; NaN otherwise (2026-10-18). The placement-time check above does not run for
	 * floats in multi-column layout (they are placed unconditionally in the column band), so a remainder that a
	 * column break rebuilt at the same size or larger was split again at every page start without end (wild seed
	 * 24521670: a table float in a three-column multicol, 102.1pt and then 108.25pt on a 100pt page, 51,000 pages in
	 * 300 s since e18e4fb6). A remainder that has not shrunk below this counts as unsplittable when it is first again
	 * ({@code FloatMeasurement#notShrunk}); behind body text it is split by content as before (triage §83).
	 */
	private double fragmentStartSplitExtent = Double.NaN;

	public final void noteFragmentStartSplitExtent(final double extent) {
		this.fragmentStartSplitExtent = extent;
	}

	public final double fragmentStartSplitExtent() {
		return this.fragmentStartSplitExtent;
	}

	protected final FloatPos pos;

	public FloatBlockBox(final BlockParams params, final FloatPos pos) {
		super(params);
		this.pos = pos;
	}

	protected FloatBlockBox(final BlockParams params, final FloatPos pos, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
		this.continuePageAxis();
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbstractStaticPos getStaticPos() {
		return this.pos;
	}

	public final FloatPos getFloatPos() {
		return this.pos;
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		this.frames(pageBox, drawer, clip, transform, x, y);
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			// Draw children with negative z-index after this box's background/border and before other content (Appendix E ③).
			drawer.markOwnDecorationEnd();
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FloatPos pos = this.getFloatPos();
		return (state, container) -> new FloatBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
