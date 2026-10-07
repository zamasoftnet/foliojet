package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IInlineBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Implementation of a block box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: InlineBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class InlineBlockBox extends AbstractStaticBlockBox implements IInlineBox {
	protected final InlinePos pos;

	public InlineBlockBox(BlockParams params, InlinePos pos) {
		super(params);
		this.pos = pos;
	}

	protected InlineBlockBox(BlockParams params, InlinePos pos, Dimension nextSize, Dimension nextMinSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, nextSize, nextMinSize, frame, container);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbstractStaticPos getStaticPos() {
		return this.pos;
	}

	public final InlinePos getInlinePos() {
		return this.pos;
	}

	/**
	 * Whether this box has definite dimensions at construction and does not need content
	 * measurement (two-pass shrink-to-fit) (2026-07-25).
	 *
	 * <p>
	 * Normal inline blocks determine their width from content, so a measurement builder
	 * ({@code TwoPassBlockBuilder.newBuilder}) is created as a pair. Synthetic boxes such as
	 * {@code RubyUnitBox}, which hold only shaped glyph sequences, have no such pair,
	 * so the measurement path must check this flag and pass them through.
	 * </p>
	 *
	 * @return true if measurement is unnecessary
	 */
	public boolean isPreMeasured() {
		return false;
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
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
		final InlinePos pos = this.pos;
		return (state, container) -> new InlineBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
