package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * A flex item box (Flex F1d, 2026-08-02 --
 * consult-codex-2026-08-02-flexbox.txt Q2).
 *
 * <p>
 * Unlike {@code GridItemBox} (a neutral synthetic wrapper), for a plain direct block child,
 * **create it by inheriting the authored child's BlockParams/FlowPos**, without constructing
 * the original outer box. This lets the authored background/border follow the item size
 * under future stretch (F3c), the consultation's most important prototype condition.
 * Only anonymous text, replaced elements, and non-plain children (tables, nested containers, etc.)
 * use wrappers with neutral params. Neither the synthetic nor the authored path exposes
 * the box to the source protocol (it is deterministically synthesized again from child events
 * during recording/replay).
 * </p>
 */
public class FlexItemBox extends FlowBlockBox {

	/**
	 * Whether a neutral wrapper has taken over the authored child's specified line-axis size
	 * (2026-08-08, {@code FlexBuilder.startNeutralElementItem}).
	 * If true, the direct child resolves its line-axis specification as auto (fill the wrapper)
	 * ({@code FlowBlockBox.calculateSize}), because resolving percentages in both wrapper and child
	 * would apply them twice. Do not neutralize the child's params: replay reifies the item itself
	 * from the child's recording, so live mutations do not survive
	 * (verified with the high-school baseball strip on asahi.com).
	 */
	private boolean neutralLineFill;

	public FlexItemBox(final BlockParams params, final FlowPos pos) {
		super(params, pos);
		this.markSpecifiedPageAxisFromSize();
	}

	/**
	 * FlexBuilder injects flex item dimensions, so items may bypass the relevant branch of
	 * {@code AbstractStaticBlockBox.calculateSize}, the only place that sets
	 * {@code specifiedPageAxis} (2026-08-09). Then remainder calculation for cross-page splitting
	 * ({@code FragmentState.of}) mistakenly sees "no specified size" and does not split the specified
	 * page-axis size into a remainder; the continuation fragment resolves the <b>full</b> specified
	 * height again. A flex line with fixed-height items spanning pages thus inflated the continuation
	 * line by almost the entire specified height and pushed later content down (an actual bug recorded
	 * as deformation in the whole-flex-move replay path). Set this only for absolute lengths
	 * (conservatively keep the existing percentage behavior, which requires the flex basis).
	 */
	private void markSpecifiedPageAxisFromSize() {
		this.specifiedPageAxis = this.size
				.getPageType(this.getBlockParams().flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
	}

	/** Records takeover of the line-axis size (neutral wrappers only). */
	public void markNeutralLineFill() {
		this.neutralLineFill = true;
	}

	/** See {@link #markNeutralLineFill}. */
	public boolean isNeutralLineFill() {
		return this.neutralLineFill;
	}

	/**
	 * FlexBuilder has already resolved auto margins for flex items
	 * (see {@link FlowBlockBox#coordinatorOwnsAutoMargins}).
	 */
	@Override
	public boolean coordinatorOwnsAutoMargins() {
		return true;
	}

	/**
	 * Sets the item's line-axis start position (origin at the flex container's inner edge,
	 * relative to the natural position; F6: physical Y in vertical writing).
	 *
	 * <p>
	 * Also save the same value in {@code baseOffsetX}/{@code baseOffsetY} (2026-08-06).
	 * This is the basis on which {@code AbstractContainerBox.resolveRelativeOffset} adds
	 * the {@code position:relative} offset. Without it, that method overwrites {@code offsetX}
	 * by assignment and loses flex placement (causing reversed search-button positions and
	 * icons to cluster at the origin).
	 * </p>
	 */
	public void setFlexLineOffset(final double lineOffset, final boolean vertical) {
		if (vertical) {
			this.baseOffsetY = lineOffset;
			this.offsetY = lineOffset;
		} else {
			this.baseOffsetX = lineOffset;
			this.offsetX = lineOffset;
		}
	}

	/**
	 * Reads the line-axis position set by {@link #setFlexLineOffset}
	 * (2026-08-07, for flex row splitting).
	 *
	 * <p>
	 * {@code fragmentRecipe} creates a new remainder {@link FlexItemBox} for a forced split
	 * across a row, so it does not inherit the line-axis position. After splitting, the caller
	 * must read the original value here and apply {@link #setFlexLineOffset} again to the remainder
	 * (the cross-axis position belongs to the container's Flow, not the item, so needs no change here).
	 * </p>
	 */
	public double getFlexLineOffset(final boolean vertical) {
		return vertical ? this.baseOffsetY : this.baseOffsetX;
	}

	/** Sets the finalized inner line-axis size (content-box; called just before bind; height in vertical writing). */
	public void setFlexMainSize(final double mainSize, final boolean vertical) {
		if (vertical) {
			this.height = mainSize;
		} else {
			this.width = mainSize;
		}
	}

	/**
	 * A fresh, empty copy of this item for measuring its content in a column flex (2026-10-08, like
	 * {@code TableCellBox.newMeasureReplica}): same params, specified sizes and frame, a new container. The body is
	 * replayed into it without being consumed and the copy is dropped after its page-axis size is read.
	 *
	 * @return the replica, or null for an item whose container cannot be copied (multi-column)
	 */
	public FlexItemBox newMeasureReplica() {
		if (!(this.container instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer)) {
			return null;
		}
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frameCopy = new net.zamasoft.foliojet.layout.part.AbsoluteRectFrame(
				this.frame.frame);
		frameCopy.margin = new net.zamasoft.foliojet.layout.part.AbsoluteInsets(this.frame.margin.top,
				this.frame.margin.right, this.frame.margin.bottom, this.frame.margin.left);
		frameCopy.padding.set(this.frame.padding);
		final FlexItemBox replica = new FlexItemBox(this.getBlockParams(), this.pos, this.size, this.minSize, frameCopy,
				new net.zamasoft.foliojet.layout.box.content.FlowContainer());
		replica.neutralLineFill = this.neutralLineFill;
		return replica;
	}

	protected FlexItemBox(final BlockParams params, final FlowPos pos,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame,
			final net.zamasoft.foliojet.layout.box.content.Container container) {
		super(params, pos, size, minSize, frame, container);
		this.markSpecifiedPageAxisFromSize();
	}

	/**
	 * <b>Creates continuation fragments with the same type</b> (2026-08-05).
	 *
	 * <p>
	 * {@link FlowBlockBox#fragmentRecipe()} directly uses {@code new FlowBlockBox(...)},
	 * so <b>without an override, continuation fragments become plain blocks</b>.
	 * {@code ContinuationValidator} detects the type mismatch and <b>stops the entire conversion</b>.
	 * This caused {@code ecma262} in real-world corpus wave 23 (the ECMAScript specification,
	 * a 7.5 MB single page) to fail after producing 2.9 MB of output.
	 * Only {@code MulticolumnBlockBox} had an override.
	 * </p>
	 */
	@Override
	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		// Carry the **used size after flex resolution**, not the specified size, along the line axis
		// (main axis) to continuation fragments (2026-08-08). FragmentState's nextSize retains
		// the specified line-axis size. If flex-shrink has shrunk a width:100% item, resolving
		// the percentage again in the continuation restores its pre-shrink width and pushes the adjacent item
		// (a fixed-width sidebar with flex-shrink:0) off the paper.
		// This actual bug made the breaking-news section on asahi.com's home page disappear except for the times.
		// Capture by value because recipes must not retain this.
		final boolean vertical = params.flow.isVertical();
		// Dimension's absolute values use the box-sizing scale (BORDER_BOX deducts the frame
		// during resolution -- AbstractStaticBlockBox). Add the frame back to the inner
		// this.width/height before carrying the values.
		final double usedMain = (vertical ? this.height : this.width)
				+ (params.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? this.frame.getBorderLineExtent(params.flow)
						: 0);
		final boolean fill = this.neutralLineFill;
		return (state, container) -> {
			final net.zamasoft.foliojet.layout.box.params.Dimension ns = state.nextSize();
			final net.zamasoft.foliojet.layout.box.params.Dimension sized = vertical
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(ns.getWidth(), ns.getWidthRatio(),
							usedMain, 0, ns.getWidthType(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: net.zamasoft.foliojet.layout.box.params.Dimension.create(usedMain, 0, ns.getHeight(),
							ns.getHeightRatio(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE,
							ns.getHeightType());
			final FlexItemBox next = new FlexItemBox(params, pos, sized, state.nextMinSize(), state.nextFrame(),
					container);
			if (fill) {
				next.markNeutralLineFill();
			}
			return next;
		};
	}
}
