package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public class OutsideMarkerBox extends InlineBlockBox {
	private double lineAxis;

	/**
	 * Whether the line uses vertical writing. <b>The marker's own {@code params.flow} cannot
	 * determine this</b> (2026-09-01). Applying {@code text-combine-upright: all} to {@code ::marker}
	 * makes {@code TextCombineShorthand} expand {@code block-flow} to horizontal writing, so only
	 * the marker's flow becomes {@code TB}, even on a vertical page. Using it to determine the axis
	 * swaps both the axis whose advance is zeroed and the axis shifted during drawing.
	 * This caused numbers to appear outside their lines in numbered lists in vertical writing.
	 * Determine this from the writing direction of <b>the box containing the marker</b> and store it here.
	 */
	private boolean verticalLine;

	/**
	 * Whether the marker is emitted ahead of a table, outside it, because the table is the list-item's
	 * first child. Overlay this marker-only line at the table start without advancing normal flow.
	 */
	private boolean overlaysFollowingBlock;

	public OutsideMarkerBox(BlockParams params, InlinePos pos) {
		super(params, pos);
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		params.textIndent = Length.ZERO_LENGTH;
	}

	public final void setOverlaysFollowingBlock(final boolean overlaysFollowingBlock) {
		this.overlaysFollowingBlock = overlaysFollowingBlock;
	}

	public final boolean overlaysFollowingBlock() {
		return this.overlaysFollowingBlock;
	}

	public void firstPassLayout(AbstractContainerBox containerBox) {
		super.firstPassLayout(containerBox);
		this.verticalLine = containerBox.getBlockParams().flow.isVertical();
		// **The line (containing box) determines the shift axis; the marker's writing direction determines
		// which component to zero**. Tate-chu-yoko markers are laid out horizontally in vertical lines,
		// so the marker's `width` supplies its line-axis advance.
		if (this.params.flow.isVertical()) {
			this.height = 0;
		} else {
			this.width = 0;
		}
	}

	public void shrinkToFit(LayoutStack builder, IntrinsicSizes sizes, boolean table) {
		super.shrinkToFit(builder, sizes, table);
		this.lineAxis = sizes.maxContent();
		if (this.params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL) {
			// Tate-chu-yoko (all) fits into a 1em cell (css-writing-modes-3 §9.1).
			// The advance is 1em after `startInline` compression; shifting by the natural width before
			// compression made only three-digit markers float 0.5 em along the line axis.
			this.lineAxis = Math.min(this.lineAxis, this.params.fontStyle.getSize());
		}
		final AbstractContainerBox containerBox = builder.getFlowBox();
		this.verticalLine = containerBox.getBlockParams().flow.isVertical();
		if (this.verticalLine) {
			this.lineAxis += containerBox.getFrame().getFrameTop();
		} else {
			this.lineAxis += containerBox.getFrame().getFrameLeft();
		}
		if (this.params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL) {
			// **Fit into the 1em cell before zeroing the advance**. Compression in `startInline` checks
			// the natural width (`width`), so zeroing it first caused an early return and skipped
			// compression, which was why three-digit markers failed to fit into 1em.
			this.compressTextCombine(this.params.fontStyle.getSize(), null);
		}
		if (this.params.flow.isVertical()) {
			this.height = 0;
		} else {
			this.width = 0;
		}
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {
		if (this.verticalLine) {
			y -= this.lineAxis;
		} else {
			x -= this.lineAxis;
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}
}
