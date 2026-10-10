package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
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

	protected final FloatPos pos;
	/**
	 * Whether this is a continuation fragment from a page-break split (2026-08-29).
	 * {@code shape-outside} resolution ({@code FloatShapeResolver}) uses this to fall back
	 * to the margin-box rectangle for continuation fragments, which lack the original dimensions
	 * and position. Continuation fragments are created only by the protected constructor
	 * through {@link #fragmentRecipe}.
	 */
	private final boolean continuation;

	public FloatBlockBox(final BlockParams params, final FloatPos pos) {
		super(params);
		this.pos = pos;
		this.continuation = false;
	}

	protected FloatBlockBox(final BlockParams params, final FloatPos pos, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
		this.continuation = true;
		this.continuePageAxis();
	}

	public final boolean isContinuationFragment() {
		return this.continuation;
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
