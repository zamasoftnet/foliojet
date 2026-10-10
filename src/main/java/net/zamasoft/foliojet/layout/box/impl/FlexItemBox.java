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

	/**
	 * Whether this neutral wrapper holds a replaced element and the flex main axis is the line axis (2026-10-10,
	 * {@code FlexBuilder.NeutralTransfer}): the wrapper is the element's border box, sized by the flex algorithm, and
	 * the element fills it along the line axis ({@code LayoutUtils.calculateReplacedSize}).
	 */
	private boolean replacedMainFill;

	/**
	 * The percentage basis of the item's padding and margins, the flex container's inner line size, once the item is
	 * bound (NONE before; 2026-10-10). A replaced element in a neutral wrapper resolves its own padding against it: the
	 * wrapper is not its containing block in Chrome.
	 */
	private double insetBase = net.zamasoft.foliojet.layout.util.LayoutUtils.NONE;

	/**
	 * The percentage basis along the page axis of a replaced element in this neutral wrapper: the flex container's
	 * definite inner page-axis size, NONE without one (2026-10-10). The element's containing block is the flex
	 * container in Chrome; the wrapper's own height is not known while the element is measured, so an svg logo with
	 * height: 80% and only a ratio measured 0 wide (KaTeX's documents).
	 */
	private double pageBase = net.zamasoft.foliojet.layout.util.LayoutUtils.NONE;

	/**
	 * Whether the item is laid out in the normal flow of its container's host, breaking across pages as a block does
	 * (2026-10-09, {@code FlexBuilder} for a single item that fills its line). It still seals its margins and floats.
	 */
	private boolean streamedInFlow;

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

	/** See {@link #streamedInFlow}. */
	public void markStreamedInFlow() {
		this.streamedInFlow = true;
	}

	/** See {@link #streamedInFlow}. */
	public boolean isStreamedInFlow() {
		return this.streamedInFlow;
	}

	/** Records takeover of the line-axis size (neutral wrappers only). */
	public void markNeutralLineFill() {
		this.neutralLineFill = true;
	}

	/** See {@link #markNeutralLineFill}. */
	public boolean isNeutralLineFill() {
		return this.neutralLineFill;
	}

	/** See {@link #replacedMainFill}. */
	public void markReplacedMainFill() {
		this.replacedMainFill = true;
	}

	/**
	 * Whether this neutral wrapper holds a replaced element in a column, where the line axis is the cross axis
	 * (2026-10-10): an element of auto width fills the wrapper's width, which stretch makes the column's (Chrome
	 * stretches an image of width auto; without stretch the wrapper is the element's own width).
	 */
	private boolean replacedCrossFill;

	/** See {@link #replacedCrossFill}. */
	public void markReplacedCrossFill() {
		this.replacedCrossFill = true;
	}

	/** See {@link #replacedCrossFill}. */
	public boolean isReplacedCrossFill() {
		return this.replacedCrossFill;
	}

	/** See {@link #replacedMainFill}. */
	public boolean isReplacedMainFill() {
		return this.replacedMainFill;
	}

	/**
	 * The replaced element laid out in this neutral wrapper, set when it is sized ({@code LayoutUtils
	 * .calculateReplacedSize}; null otherwise): the flex algorithm stretches the wrapper along the cross axis of a row
	 * and gives it the main size of a column after the element is laid out, and passes the size on (2026-10-10,
	 * {@code FlexBuilder}).
	 */
	private net.zamasoft.foliojet.layout.box.AbstractReplacedBox replacedChild;

	/** See {@link #replacedChild}. */
	public void setReplacedChild(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox replacedChild) {
		this.replacedChild = replacedChild;
	}

	/** See {@link #replacedChild}. */
	public net.zamasoft.foliojet.layout.box.AbstractReplacedBox getReplacedChild() {
		return this.replacedChild;
	}

	/**
	 * Passes a page-axis size the flex algorithm gave this wrapper on to its replaced element, along the page axis
	 * (2026-10-10): the whole size for the main size of a column, the stretched size for the cross axis of a row when
	 * the element's height is auto. Chrome sizes the element itself so; the element stayed at its own height inside the
	 * larger wrapper (an image with flex: 1 in a column, an image of height auto in a stretched row).
	 *
	 * @param whatever false to pass it on only when the element's page-axis size is auto
	 */
	public void passPageSizeToReplaced(final boolean whatever) {
		final net.zamasoft.foliojet.layout.box.AbstractReplacedBox image = this.replacedChild;
		if (image == null) {
			return;
		}
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.getBlockParams().flow;
		final net.zamasoft.foliojet.layout.box.params.Dimension size = image.getReplacedParams().size;
		if (!whatever && (flow.isVertical() ? size.getWidthType() : size.getHeightType())
				!= net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			return;
		}
		if (!whatever && net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(this.pageBase)
				&& (percentage(flow.isVertical() ? image.getReplacedParams().minSize.getWidthType()
						: image.getReplacedParams().minSize.getHeightType())
						|| percentage(flow.isVertical() ? image.getReplacedParams().maxSize.getWidthType()
								: image.getReplacedParams().maxSize.getHeightType()))) {
			// A percentage limit without a basis (the container's height is not an absolute length): the element keeps
			// its own size, which resolved it against the laid-out container (codex, 2026-10-10: height: 100% of a
			// 100pt block with max-height: 50% stretched to 100, Chrome 50)
			return;
		}
		image.fillPage(flow.isVertical(),
				Math.max(0, this.getInnerPageExtent(flow) - image.getFrame().getBorderPageExtent(flow)), !whatever,
				this.pageBase);
	}

	private static boolean percentage(final net.zamasoft.foliojet.layout.box.params.LengthType type) {
		return type == net.zamasoft.foliojet.layout.box.params.LengthType.RELATIVE
				|| type == net.zamasoft.foliojet.layout.box.params.LengthType.MIXED;
	}

	/** See {@link #insetBase}. */
	public void setInsetBase(final double insetBase) {
		this.insetBase = insetBase;
	}

	/** See {@link #insetBase}. */
	public double getInsetBase() {
		return this.insetBase;
	}

	/** See {@link #pageBase}. */
	public void setPageBase(final double pageBase) {
		this.pageBase = pageBase;
	}

	/** See {@link #pageBase}. */
	public double getPageBase() {
		return this.pageBase;
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

	/**
	 * Sets the used main size of an item of a column flex (its inner page-axis size, css-flexbox-1 §9.7) after its bind
	 * (2026-10-08). It replaces a specified height, which a content basis or a shrink can make smaller: setPageAxis kept
	 * the specified height, so a {@code flex-basis: content; height: 100pt} item measured 20pt tall was drawn 100pt tall
	 * under the next item; in vertical writing it also kept every content-sized width (codex review).
	 */
	public void setColumnMainSize(final double mainSize) {
		this.contentSize = Math.max(this.contentSize, mainSize);
		if (this.getBlockParams().flow.isVertical()) {
			this.width = mainSize;
		} else {
			this.height = mainSize;
		}
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
		replica.replacedMainFill = this.replacedMainFill;
		replica.replacedCrossFill = this.replacedCrossFill;
		replica.insetBase = this.insetBase;
		replica.pageBase = this.pageBase;
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
		final boolean replacedFill = this.replacedMainFill;
		final boolean replacedCrossFill = this.replacedCrossFill;
		final double base = this.insetBase;
		final double pageBase = this.pageBase;
		final boolean streamed = this.streamedInFlow;
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
			if (replacedFill) {
				next.markReplacedMainFill();
			}
			if (replacedCrossFill) {
				next.markReplacedCrossFill();
			}
			next.insetBase = base;
			next.pageBase = pageBase;
			if (streamed) {
				next.markStreamedInFlow();
			}
			return next;
		};
	}
}
