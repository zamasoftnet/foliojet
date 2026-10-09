package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.Align;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractStaticBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Implementation of a block box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: FlowBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class FlowBlockBox extends AbstractStaticBlockBox implements IFlowBox {

	protected final FlowPos pos;

	protected double contentSize;

	/** One-shot used line-axis size applied before flowing children during RowSplitContainer reconstruction. */
	private double restyleLineWidth = LayoutUtils.NONE;
	private double restyleLineHeight = LayoutUtils.NONE;

	/**
	 * Resolved alignment (table auto-margin alignment). Initially pos.align;
	 * shrinkToFit(table) saves the resolved result. Do not write it back to shared pos
	 * (the pos referenced by the log is immutable after recording -- §5.7 assumption (ii)).
	 * splitPage carries it to split fragments.
	 */
	protected Align resolvedAlign;

	/**
	 * Whether this box continues a fragment broken on an earlier page (made by {@link #fragmentRecipe}). The streamed
	 * flex basis applies to the head only: a continuation's remaining minimum can be the same interned zero dimension as
	 * the params', which the identity check took for a head (codex review 2026-10-08).
	 */
	private boolean continuation;

	public FlowBlockBox(BlockParams params, FlowPos pos) {
		super(params);
		this.pos = pos;
		this.resolvedAlign = pos.align;
	}

	protected FlowBlockBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
		this.resolvedAlign = pos.align;
	}

	/**
	 * Returns the resolved alignment (the result of table alignment resolution once performed).
	 */
	public final Align getResolvedAlign() {
		return this.resolvedAlign;
	}

	/**
	 * Aligns this item of a column flex laid out in normal flow across the column (2026-10-09): its margins take the
	 * room its width leaves, as {@code margin: 0 auto} does (auto margins still come first).
	 */
	public final void alignInStreamedColumn(final Align align) {
		this.resolvedAlign = align;
	}

	/**
	 * Restores page-axis content size inflated by restyle reconstruction
	 * (2026-08-08; exclusively for {@code RowSplitContainer.restoreAnchoredPageAxis};
	 * generalized from flex to shared flex/grid use on 2026-08-10).
	 * Generic reconstruction registers items again as a vertical stack, so each item's
	 * endFlowBlock writes the sum of item heights to this parent box. Because contentSize
	 * in {@code setPageAxis} only increases through Math.max, passing the correct value later
	 * leaves the vertical-stack value intact. Restore by assignment only here.
	 */
	public final void restoreContentExtent(final double content) {
		this.contentSize = content;
		if (this.getBlockParams().flow.isVertical()) {
			this.width = content;
		} else {
			this.height = content;
		}
	}

	/**
	 * Updates only the end of an incomplete table. Reapplies setPageAxis once from the
	 * accounting state before table placement, so the provisional incomplete height does not
	 * remain as the contentSize maximum. Also finalizes shrinking due to a negative trailing
	 * margin while preserving specified-height and min/max rules.
	 */
	public final void updateIncompleteTableExtent(final double pageSize, final double beforeContentSize,
			final double beforePageSize) {
		if (this.params.flow.isVertical()) {
			throw new IllegalStateException("Incomplete table intake requires horizontal flow");
		}
		this.contentSize = beforeContentSize;
		this.height = beforePageSize;
		this.setPageAxis(pageSize);
	}

	/**
	 * Restores finalized dimensions overwritten by restyle reconstruction
	 * (2026-08-08; exclusively for {@code RowSplitContainer.restyle}; generalized from flex
	 * to shared flex/grid use on 2026-08-10). The item coordinator (FlexBuilder/GridBuilder)
	 * owns item dimensions, but generic reconstruction after moving across pages resolves them
	 * again: {@code startFlowBlock.calculateSize} sets width:auto to the containing width,
	 * and {@code endFlowBlock} sets height:auto to the content height (0 with only absolutely
	 * positioned children). This actual bug turned ranking badges on yahoo.co.jp into colored
	 * strips spanning the entire line.
	 */
	public final void restoreExtents(final double width, final double height) {
		// The caller passes outer dimensions including the frame, obtained from IBox.getWidth/getHeight.
		// Assigning these directly to inner-size fields would add margin/border/padding again
		// on every restyle, inflating framed items each generation.
		this.width = width - this.frame.getFrameWidth();
		this.height = height - this.frame.getFrameHeight();
	}

	/**
	 * On the next {@link #calculateSize}, first applies the used line-axis size finalized
	 * by flex/grid. Restoring only the box width afterward leaves inner text wrapped at
	 * the containing width, making it draw over an image in a fixed-width sibling.
	 */
	public final void prepareRestyleLineExtent(final double width, final double height, final boolean vertical) {
		if (vertical) {
			this.restyleLineHeight = height;
		} else {
			this.restyleLineWidth = width;
		}
	}

	/** Discards the one-shot specification if replay did not go through box reconstruction. */
	public final void clearRestyleLineExtent() {
		this.restyleLineWidth = LayoutUtils.NONE;
		this.restyleLineHeight = LayoutUtils.NONE;
	}

	/**
	 * Whether the placement coordinator owns per-item auto margins (flex §8.1).
	 * FlexBuilder has already resolved flex item auto margins from free space in the line
	 * and incorporated them into the line-axis position ({@code flexLineOffset}) and cross offset.
	 * The absolute margin values (amargin) are carried as 0. If restyle reconstruction of a
	 * cross-page continuation fragment resolves them again using the block rules (§10.3.3)
	 * in {@code calculateSize}, (1) it adds margins to positions that already include them,
	 * and (2) the frame deduction in {@code restoreExtents} grows, shrinking the inner size
	 * by 2×margin each generation. A fixed-width flex item (body text column) with
	 * {@code margin:0 auto} drifted right and shrank to one character wide over successive pages
	 * (an asahi.com article page, 2026-08-27). If true, {@code calculateSize} uses the carried
	 * absolute values for AUTO margin sides without resolving them again.
	 */
	public boolean coordinatorOwnsAutoMargins() {
		return false;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbstractStaticPos getStaticPos() {
		return this.pos;
	}

	public final FlowPos getFlowPos() {
		return this.pos;
	}

	public final void shrinkToFit(LayoutStack layoutStack, IntrinsicSizes sizes, boolean table) {
		super.shrinkToFit(layoutStack, sizes, table);
		final AbstractContainerBox containerBox;
		if (!table) {
			return;
		}
		// Table
		BlockBuilder builder = (BlockBuilder) layoutStack;
		containerBox = builder.getFlow(builder.getFlowCount() - 2).box;

		Align align = this.resolvedAlign;
		final boolean containerVertical = containerBox.getBlockParams().flow.isVertical();
		// For an orthogonal table (e.g. horizontal writing inside vertical writing), if the parent's line-axis size
		// (height for a vertical-writing parent) is unknown (e.g. inside a float before line layout), do not align
		// with auto margins (2026-10-05). Centering at size 0 made the margin half the parent's line length, so the table
		// overflowed downward from the parent's middle (jigensha report). BlockBuilder.addFlowBound aligns once size is known.
		final boolean unsizedOrthogonal = containerVertical != this.getBlockParams().flow.isVertical()
				&& LayoutUtils.compare(containerVertical ? this.height : this.width, 0) <= 0;
		if (containerVertical) {
			// Vertical writing
			if (align == Align.START) {
				Insets margin = this.getBlockParams().frame.margin;
				if (margin.getTopType() == LengthType.AUTO) {
					if (margin.getBottomType() == LengthType.AUTO) {
						align = Align.CENTER;
					} else {
						align = Align.END;
					}
				}
			}
			final double remainder = containerBox.getLineSize() - this.height;
			switch (unsizedOrthogonal ? null : align) {
			case null:
				break;
			case Align.START:
				this.frame.margin.bottom = remainder;
				break;
			case Align.END:
				this.frame.margin.top = remainder;
				break;
			case Align.CENTER:
				this.frame.margin.top = this.frame.margin.bottom = remainder / 2;
				break;
			}
			// Fix the width
			this.size = Dimension.create(0, this.height, LengthType.AUTO, LengthType.ABSOLUTE);
		} else {
			// Horizontal writing
			if (align == Align.START) {
				Insets margin = this.getBlockParams().frame.margin;
				if (margin.getLeftType() == LengthType.AUTO) {
					if (margin.getRightType() == LengthType.AUTO) {
						align = Align.CENTER;
					} else {
						align = Align.END;
					}
				}
			}
			final double remainder = containerBox.getLineSize() - this.width;
			// **Align boxes wider than their containing block to the start** (2026-08-03).
			//
			// CSS 2.1 §10.3.3: if the width is specified and the total exceeds the containing block,
			// direction: ltr ignores the specified margin-right: the box aligns to the start
			// and overflows at the end. Previously, the remainder was mechanically divided by 2,
			// so a negative remainder produced a **negative start margin**, putting the left half
			// of the content off the paper and clipping it. Centering a fixed-width type area
			// with margin: 0 auto is extremely common (found in wave 3 with Statistics Bureau pages; PLAN §3).
			// A type area wider than the paper overflows anyway, but **protecting the start keeps it readable**.
			if (remainder < 0) {
				align = Align.START;
			}
			switch (unsizedOrthogonal ? null : align) {
			case null:
				break;
			case Align.START:
				this.frame.margin.right = remainder;
				break;
			case Align.END:
				this.frame.margin.left = remainder;
				break;
			case Align.CENTER:
				this.frame.margin.left = this.frame.margin.right = remainder / 2;
				break;
			}
			// Fix the width
			this.size = Dimension.create(this.width, 0, LengthType.ABSOLUTE, LengthType.AUTO);
		}
		this.resolvedAlign = align;
	}

	/**
	 * Uses {@code aspect-ratio} to determine the inner page-axis size from the finalized inner
	 * line-axis size (2026-08-29; called just after a flex/grid item receives its finalized line-axis
	 * size, before binding body content; moved up from FlexItemBox in G7).
	 * Does nothing if the page-axis size is an absolute length or no ratio is specified.
	 * If content is taller than the ratio-derived height, {@code overflow:visible} allows expansion
	 * ({@code minPageAxis} = ratio-derived height; {@code maxPageAxis} is unlimited when visible).
	 */
	public void applyAspectRatio(final double lineExtent) {
		final BlockParams params = this.getBlockParams();
		if (!(params.aspectRatio > 0) || this.size.getPageType(params.flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			return;
		}
		final double page = this.aspectRatioPageExtent(lineExtent);
		if (net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(page)) {
			return;
		}
		if (params.flow.isVertical()) {
			this.width = page;
		} else {
			this.height = page;
		}
		this.minPageAxis = page;
		if (params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
			this.maxPageAxis = page;
		}
		this.specifiedPageAxis = true;
	}

	/**
	 * Gives an item of a column flex container laid out in normal flow its flex main size (F0+, 2026-10-08; called by
	 * {@code BlockBuilder.startFlowBlock} before sizing). The container's main size is indefinite, so the free space is
	 * zero and the item's main size is its hypothetical main size (css-flexbox-1 §9.2): the flex base size clamped by
	 * the min/max main sizes. The main axis is this box's page axis (the caller skips orthogonal items).
	 *
	 * <ul>
	 * <li>A length basis L (or {@code calc()} without a percentage): with an automatic minimum (min-height/width
	 * {@code auto}, {@code overflow: visible}), the item starts at L (at most max) and its content may grow it, as
	 * {@code min-height: L}; otherwise it is fixed at L clamped by min/max, as {@code height: L}. The basis follows
	 * {@code box-sizing} like the size properties. Approximation: when the item also has a definite size H larger than L,
	 * the automatic minimum min(content, H) would stop at H; here content can grow the item past H up to max.</li>
	 * <li>{@code content}, or a percentage of the indefinite main size (css-flexbox-1 §7.2.3: it behaves as
	 * {@code content}, whatever the size property says): the content size; the size property no longer applies.</li>
	 * <li>{@code auto}: the size property, which normal flow already applies.</li>
	 * </ul>
	 *
	 * <p>
	 * Only the head fragment is adjusted. Continuation fragments carry the adjusted sizes through {@code FragmentState}
	 * (the remaining specified or minimum size).
	 * </p>
	 */
	public final void applyStreamedFlexBasis(final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec) {
		if (this.continuation || this.size != this.params.size || this.minSize != this.params.minSize
				|| spec == null) {
			return;
		}
		final WritingMode flow = this.params.flow;
		final boolean vertical = flow.isVertical();
		final net.zamasoft.foliojet.css.value.FlexBasisValue basis = spec.basis();
		if (basis.isAuto()) {
			return;
		}
		final double length;
		if (basis.isContent()) {
			length = Double.NaN;
		} else if (basis.getSize() instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue absolute) {
			length = absolute.getLength();
		} else if (basis.getSize() instanceof net.zamasoft.foliojet.css.value.CalcLengthValue calc
				&& calc.getRatio() == 0) {
			length = calc.getAbsolute();
		} else {
			length = Double.NaN;
		}
		if (Double.isNaN(length)) {
			// The content size (a 100pt height under flex: 1 kept the item 100pt tall; Chrome makes it its content)
			if (this.size.getPageType(flow) != LengthType.AUTO) {
				this.size = withPage(this.size, vertical, 0, LengthType.AUTO);
			}
			return;
		}
		final boolean minAuto = vertical ? spec.minWidthAuto() : spec.minHeightAuto();
		if (minAuto && this.params.overflow == net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
			// min wins over max, so clamp the basis by an absolute max first.
			double start = Math.max(0, length);
			if (this.params.maxSize.getPageType(flow) == LengthType.ABSOLUTE) {
				start = Math.min(start, this.params.maxSize.getPageLength(flow));
			}
			this.size = withPage(this.size, vertical, 0, LengthType.AUTO);
			this.minSize = withPage(this.minSize, vertical, start, LengthType.ABSOLUTE);
		} else {
			// calculateSize clamps a definite size by min/max and fixes it.
			this.size = withPage(this.size, vertical, Math.max(0, length), LengthType.ABSOLUTE);
		}
	}

	/** {@code d} with its page-axis component replaced (the line-axis component, including its ratio, is kept). */
	private static Dimension withPage(final Dimension d, final boolean vertical, final double length,
			final LengthType type) {
		return vertical
				? Dimension.create(length, 0, d.getHeight(), d.getHeightRatio(), type, d.getHeightType())
				: Dimension.create(d.getWidth(), d.getWidthRatio(), length, 0, d.getWidthType(), type);
	}

	/**
	 * <b>Applies the specified inner page-axis size to this box</b> (G7, 2026-08-29).
	 *
	 * <p>
	 * An item box that takes over the child ({@code GridItemBox}) does not pass through
	 * {@code calculateSize}, so {@code specifiedPageAxis} is not set. Setting only the height
	 * without the flag makes fragmentation ({@link net.zamasoft.foliojet.layout.fragment.FragmentState})
	 * treat it as "not a specified size" and <b>pass the original specified height unchanged to
	 * the continuation fragment</b>. A row-split fixed-height item then continues at its full height
	 * instead of the remainder (observed on page 2 of files/unittest/0500-grid/row-split-carry.html).
	 * </p>
	 */
	public final void applySpecifiedPageAxis(final double pageExtent) {
		this.setPageAxis(pageExtent);
		this.specifiedPageAxis = true;
	}

	public final void setPageAxis(final double newSize) {
		this.contentSize = Math.max(this.contentSize, newSize);

		if (this.params.flow.isVertical()) {
			// Vertical writing
			if (newSize <= this.width) {
				return;
			}
			if ((this.isSpecifiedPageSize() || this.getColumnCount() > 1) && newSize < this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		} else {
			// Horizontal writing
			if (newSize == this.height) {
				return;
			}
			if ((this.isSpecifiedPageSize() || this.getColumnCount() > 1) && newSize < this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
	}

	/**
	 * Converts specified line-axis min/max values (box-sizing scale) to the content-size scale
	 * (2026-08-29). For border-box, subtract line-axis borders and padding.
	 * The caller must already have resolved padding (after computePaddings).
	 */
	private double lineMinMaxToContent(final double specified, final boolean lineIsHeight) {
		if (this.params.boxSizing != BoxSizingMode.BORDER_BOX) {
			return specified;
		}
		return Math.max(0, specified - (lineIsHeight ? this.frame.getBorderHeight() : this.frame.getBorderWidth()));
	}

	public void calculateSize(LayoutStack layoutStack, double xmargin, double lineSize) {
		final AbstractContainerBox containerBox = layoutStack.getFlowBox();
		final BlockParams cParams = containerBox.getBlockParams();
		// If a neutral Flex wrapper has taken over the line-axis size specification,
		// the direct child resolves its line-axis size as fill (auto) (2026-08-08: resolving percentages
		// in both wrapper and child would apply them twice; see FlexItemBox).
		final boolean neutralLineFill = containerBox instanceof FlexItemBox item && item.isNeutralLineFill();
		if (this.params.flow.isVertical()) {
			// Vertical-writing flow
			this.specifiedPageAxis = this.size.getWidthType() == LengthType.ABSOLUTE
					|| (this.size.getWidthType().needsReference() && containerBox.isSpecifiedPageSize());
		} else {
			// Horizontal-writing flow
			this.specifiedPageAxis = this.size.getHeightType() == LengthType.ABSOLUTE
					|| (this.size.getHeightType().needsReference() && containerBox.isSpecifiedPageSize());
		}

		//
		// ■ Calculate padding
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ Calculate margins
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		Insets margin = this.frame.frame.margin;
		AbsoluteInsets amargin = this.frame.margin;
		double marginLeft, marginRight, marginTop, marginBottom;

		//
		// ■ Calculate the line-axis size for static or relative positioning
		//

		// Calculate the line-axis size
		double minWidth = LayoutUtils.NONE, maxWidth = LayoutUtils.NONE, minHeight = LayoutUtils.NONE,
				maxHeight = LayoutUtils.NONE;
		if (cParams.flow.isVertical()) {
			// Vertical-writing flow
			marginLeft = amargin.left;
			marginRight = amargin.right;
			this.height = neutralLineFill ? LayoutUtils.NONE
					: LayoutUtils.computeDimensionHeight(this.size, lineSize);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(this.height)) {
				this.height -= this.frame.getBorderHeight();
			}
			if (LayoutUtils.isNone(this.height) && !neutralLineFill && this.params.aspectRatio > 0) {
				// aspect-ratio: if the line-axis size (height) is auto and the page-axis size (width) is definite,
				// derive the height from the ratio (2026-08-29).
				final double definite = this.definitePageExtentForRatio(
						this.isSpecifiedPageSize() ? containerBox.getInnerWidth() : LayoutUtils.NONE);
				if (!LayoutUtils.isNone(definite)) {
					this.height = this.aspectRatioLineExtent(definite);
				}
			}
			marginTop = marginBottom = 0;
			for (int state = 0; state < 2; ++state) {
				if (!LayoutUtils.isNone(this.height)) {
					// For a fixed width (do not resolve auto margins again;
					// see the coordinatorOwnsAutoMargins explanation).
					final boolean ownedMargins = this.coordinatorOwnsAutoMargins();
					marginTop = margin.getTopType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.top;
					marginBottom = margin.getBottomType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.bottom;
					if (LayoutUtils.isNone(marginTop) && LayoutUtils.isNone(marginBottom)) {
						// Make the top and bottom margins equal
						marginTop = marginBottom = (lineSize - this.height - this.frame.getFrameHeight()) / 2.0;
					} else if (LayoutUtils.isNone(marginTop)) {
						// Top margin is undetermined
						marginTop = lineSize - this.height - this.frame.getFrameHeight();
					} else if (LayoutUtils.isNone(marginBottom)) {
						// Bottom margin is undetermined
						marginBottom = lineSize - marginBottom - this.frame.getFrameHeight();
					} else {
						// Over-constrained
												switch (this.resolvedAlign) {
						case Align.START:
							// Align to top
							marginBottom = 0;
							break;
						case Align.END:
							// Align to bottom
							marginTop += lineSize - this.height - this.frame.getFrameHeight();
							break;
						case Align.CENTER:
							// Center
							double remainder = lineSize - this.height - this.frame.getFrameHeight();
							remainder /= 2.0;
							marginTop += remainder;
							marginBottom += remainder;
							break;
						default:
							throw new IllegalStateException();
						}
					}
				} else {
					// For automatic width
					marginTop = amargin.top;
					marginBottom = amargin.bottom;
					this.height = lineSize - this.frame.getFrameHeight();
				}
				switch (state) {
				case 0:
					maxHeight = LayoutUtils.computeDimensionHeight(this.params.maxSize, lineSize);
					if (LayoutUtils.isNone(maxHeight)) {
						maxHeight = Double.MAX_VALUE;
					} else {
						// min/max-height use the box-sizing scale. this.height is the inner size,
						// so for border-box subtract the frame before comparing
						// (2026-08-29; see the corresponding horizontal-writing code).
						maxHeight = this.lineMinMaxToContent(maxHeight, true);
						if (this.height > maxHeight) {
							this.height = maxHeight;
							continue;
						}
					}
					state = 1;
				case 1:
					minHeight = this.lineMinMaxToContent(LayoutUtils.computeDimensionHeight(this.minSize, lineSize), true);
					if (this.height < minHeight) {
						this.height = minHeight;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(minHeight);
			assert !LayoutUtils.isNone(maxHeight);
			switch (this.minSize.getWidthType()) {
			// The percentage basis for min/max is the containing block's inner page-axis size:
			// check definiteness on the container side (containerBox). Previously this checked this
			// (size definiteness), so max-width:90% on a width:66pt (definite) box collapsed to 0
			// against an indefinite parent width (0) (2026-08-23, v2 sweep seed 2006804).
			case RELATIVE:
				if (containerBox.isSpecifiedPageSize()) {
					minWidth = this.minSize.getWidth() * containerBox.getInnerWidth();
					break;
				}
				// Fall through to AUTO if isSpecifiedPageSize() is false (existing intentional behavior).
			case AUTO:
				minWidth = 0;
				break;
			case ABSOLUTE:
				minWidth = this.minSize.getWidth();
				break;
			case MIXED:
				if (containerBox.isSpecifiedPageSize()) {
					minWidth = this.minSize.getWidth() + this.minSize.getWidthRatio() * containerBox.getInnerWidth();
					break;
				}
				minWidth = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.params.maxSize.getWidthType()) {
			case RELATIVE:
				if (containerBox.isSpecifiedPageSize()) {
					maxWidth = this.params.maxSize.getWidth() * containerBox.getInnerWidth();
					break;
				}
				// Fall through to AUTO if isSpecifiedPageSize() is false (existing intentional behavior).
			case AUTO:
				maxWidth = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxWidth = this.params.maxSize.getWidth();
				break;
			case MIXED:
				if (containerBox.isSpecifiedPageSize()) {
					maxWidth = this.params.maxSize.getWidth()
							+ this.params.maxSize.getWidthRatio() * containerBox.getInnerWidth();
					break;
				}
				maxWidth = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			// Specified page-axis min/max values use the box-sizing scale. Before comparing with this.width
			// (inner size), subtract the frame for border-box to use the inner-size scale
			// (2026-08-29). Previously, minPageAxis/maxPageAxis still included the frame,
			// so setPageAxis increased content height to the frame-inclusive lower bound (a border-box with
			// min-height:40px + padding-block:8px became 56 px).
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				minWidth = Math.max(0, minWidth - this.getFrame().getBorderWidth());
				if (maxWidth != Double.MAX_VALUE) {
					maxWidth = Math.max(0, maxWidth - this.getFrame().getBorderWidth());
				}
			}
			switch (this.size.getWidthType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					this.width = this.size.getWidth() * containerBox.getInnerWidth();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.width = Math.max(0, this.width - this.getFrame().getBorderWidth());
					}
					this.width = Math.max(this.width, minWidth);
					this.width = Math.min(this.width, maxWidth);
					minWidth = maxWidth = this.width;
					break;
				}
				// Fall through to AUTO if isSpecifiedPageSize() is false (existing intentional behavior).
			case AUTO:
				if (!this.params.flow.isVertical()) {
					// Horizontal-writing box
					this.width = layoutStack.getFixedWidth() - this.frame.getFrameWidth();
				} else {
					this.width = 0;
				}
				this.width = Math.max(this.width, minWidth);
				break;
			case ABSOLUTE:
				this.width = this.size.getWidth();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					this.width = Math.max(0, this.width - this.getFrame().getBorderWidth());
				}
				this.width = Math.max(this.width, minWidth);
				this.width = Math.min(this.width, maxWidth);
				minWidth = maxWidth = this.width;
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					this.width = this.size.getWidth() + this.size.getWidthRatio() * containerBox.getInnerWidth();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.width = Math.max(0, this.width - this.getFrame().getBorderWidth());
					}
					this.width = Math.max(this.width, minWidth);
					this.width = Math.min(this.width, maxWidth);
					minWidth = maxWidth = this.width;
					break;
				}
				if (!this.params.flow.isVertical()) {
					this.width = layoutStack.getFixedWidth() - this.frame.getFrameWidth();
				} else {
					this.width = 0;
				}
				this.width = Math.max(this.width, minWidth);
				break;
			default:
				throw new IllegalStateException();
			}
			if (!this.specifiedPageAxis && this.params.aspectRatio > 0) {
				// aspect-ratio: if the page-axis size (width) is auto, derive it from height and the ratio
				// (2026-08-29; paired with the corresponding horizontal-writing code).
				double page = this.aspectRatioPageExtent(this.height);
				page = Math.max(page, minWidth);
				page = Math.min(page, maxWidth);
				this.width = page;
				minWidth = page;
				if (this.params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
					maxWidth = page;
				}
				this.specifiedPageAxis = true;
			}
			marginTop += xmargin;
		} else {
			// Horizontal-writing flow
			marginTop = amargin.top;
			marginBottom = amargin.bottom;
			this.width = neutralLineFill ? LayoutUtils.NONE
					: LayoutUtils.computeDimensionWidth(this.size, lineSize);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(this.width)) {
				this.width -= this.frame.getBorderWidth();
			}
			if (LayoutUtils.isNone(this.width) && !neutralLineFill && this.params.aspectRatio > 0) {
				// aspect-ratio: with auto width and definite height, width = height × ratio (2026-08-29,
				// css-sizing-4 §5.1). A block with height:40px;aspect-ratio:2 gets
				// width 80 px, with the remainder going to margins.
				final double definite = this.definitePageExtentForRatio(
						this.isSpecifiedPageSize() ? containerBox.getInnerHeight() : LayoutUtils.NONE);
				if (!LayoutUtils.isNone(definite)) {
					this.width = this.aspectRatioLineExtent(definite);
				}
			}
			marginLeft = marginRight = 0;
			for (int state = 0; state < 2; ++state) {
				if (!LayoutUtils.isNone(this.width)) {
					// For a fixed width (do not resolve auto margins again;
					// see the coordinatorOwnsAutoMargins explanation).
					final boolean ownedMargins = this.coordinatorOwnsAutoMargins();
					marginLeft = margin.getLeftType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.left;
					marginRight = margin.getRightType() == LengthType.AUTO && !ownedMargins ? LayoutUtils.NONE
							: amargin.right;
					final double autoRemainder = lineSize - this.width - this.frame.getFrameWidth();
					if (autoRemainder < 0 && (LayoutUtils.isNone(marginLeft) || LayoutUtils.isNone(marginRight))) {
						// **Align boxes wider than their containing block to the start** (2026-08-03).
						// CSS 2.1 §10.3.3: if the width is specified and the total exceeds the containing block,
						// direction: ltr ignores the specified margin-right: the box aligns to the start
						// and overflows at the end. Previously, the remainder was mechanically divided by 2,
						// so a negative remainder produced a **negative left margin**, putting the left half
						// of the content off the paper. Centering a fixed-width type area with margin: 0 auto
						// is extremely common (found with Statistics Bureau pages; PLAN §3).
						marginLeft = LayoutUtils.isNone(marginLeft) ? 0 : marginLeft;
						marginRight = LayoutUtils.isNone(marginRight) ? 0 : marginRight;
					} else if (LayoutUtils.isNone(marginLeft) && LayoutUtils.isNone(marginRight)) {
						// Make the left and right margins equal
						marginLeft = marginRight = autoRemainder / 2.0;
					} else {
						if (LayoutUtils.isNone(marginLeft) && !LayoutUtils.isNone(marginRight)) {
							// Left margin is undetermined
							marginLeft = lineSize - this.width - this.frame.getFrameWidth();
						} else if (LayoutUtils.isNone(marginRight)) {
							// Right margin is undetermined
							marginRight = lineSize - this.width - this.frame.getFrameWidth();
						} else {
							// Over-constrained
														switch (this.resolvedAlign) {
							case Align.START:
								// Align to left
								marginRight = 0;
								break;
							case Align.END:
								// Align to right
								marginLeft += lineSize - this.width - this.frame.getFrameWidth();
								break;
							case Align.CENTER:
								// Center
								double remainder = lineSize - this.width - this.frame.getFrameWidth();
								remainder /= 2.0;
								marginLeft += remainder;
								marginRight += remainder;
								break;
							default:
								throw new IllegalStateException();
							}
						}
					}
				} else {
					// For automatic width
					marginLeft = amargin.left;
					marginRight = amargin.right;
					this.width = lineSize - this.frame.getFrameWidth();
				}
				switch (state) {
				case 0:
					maxWidth = LayoutUtils.computeDimensionWidth(this.params.maxSize, lineSize);
					if (LayoutUtils.isNone(maxWidth)) {
						maxWidth = Double.MAX_VALUE;
					} else {
						// min/max-width use the box-sizing scale. this.width is the inner size,
						// so for border-box subtract borders and padding before comparing
						// (2026-08-29). Previously no subtraction occurred, so a pill with `min-width:100px;
						// padding-inline:8px; box-sizing:border-box` (a grid inside a flex item)
						// expanded to inner size 100 + frame 16 = 116 px. Normal-flow block widths
						// are determined here (BlockBuilder.calculateSize), not in AbstractStaticBlockBox,
						// so converting only there
						// had no effect (0510-flex/min-width-nested-container).
						maxWidth = this.lineMinMaxToContent(maxWidth, false);
						if (this.width > maxWidth) {
							this.width = maxWidth;
							continue;
						}
					}
					state = 1;
				case 1:
					minWidth = this.lineMinMaxToContent(LayoutUtils.computeDimensionWidth(this.minSize, lineSize), false);
					if (this.width < minWidth) {
						this.width = minWidth;
						continue;
					}
					state = 2;
					break;
				}
			}
			assert !LayoutUtils.isNone(minWidth);
			assert !LayoutUtils.isNone(maxWidth);
			switch (this.minSize.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					minHeight = this.minSize.getHeight() * containerBox.getInnerHeight();
					break;
				}
				// Fall through to AUTO if isSpecifiedPageSize() is false (existing intentional behavior).
			case AUTO:
				minHeight = 0;
				break;
			case ABSOLUTE:
				minHeight = this.minSize.getHeight();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					minHeight = this.minSize.getHeight() + this.minSize.getHeightRatio() * containerBox.getInnerHeight();
					break;
				}
				minHeight = 0;
				break;
			default:
				throw new IllegalStateException();
			}
			switch (this.params.maxSize.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					maxHeight = this.params.maxSize.getHeight() * containerBox.getInnerHeight();
					break;
				}
				// Fall through to AUTO if isSpecifiedPageSize() is false (existing intentional behavior).
			case AUTO:
				maxHeight = Double.MAX_VALUE;
				break;
			case ABSOLUTE:
				maxHeight = this.params.maxSize.getHeight();
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					maxHeight = this.params.maxSize.getHeight()
							+ this.params.maxSize.getHeightRatio() * containerBox.getInnerHeight();
					break;
				}
				maxHeight = Double.MAX_VALUE;
				break;
			default:
				throw new IllegalStateException();
			}
			// Convert page-axis min/max to the inner-size scale for border-box (2026-08-29;
			// see the corresponding vertical-writing code).
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				minHeight = Math.max(0, minHeight - this.getFrame().getBorderHeight());
				if (maxHeight != Double.MAX_VALUE) {
					maxHeight = Math.max(0, maxHeight - this.getFrame().getBorderHeight());
				}
			}
			switch (this.size.getHeightType()) {
			case RELATIVE:
				if (this.isSpecifiedPageSize()) {
					this.height = this.size.getHeight() * containerBox.getInnerHeight();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.height = Math.max(0, this.height - this.getFrame().getBorderHeight());
					}
					this.height = Math.max(this.height, minHeight);
					this.height = Math.min(this.height, maxHeight);
					minHeight = this.height;
					maxHeight = this.height;
					break;
				}
				// Fall through to AUTO if isSpecifiedPageSize() is false (existing intentional behavior).
			case AUTO:
				if (this.params.flow.isVertical()) {
					// Vertical-writing box
					this.height = layoutStack.getFixedHeight() - this.frame.getFrameHeight();
				} else {
					this.height = 0;
				}
				this.height = Math.max(this.height, minHeight);
				break;
			case ABSOLUTE:
				this.height = this.size.getHeight();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					this.height = Math.max(0, this.height - this.getFrame().getBorderHeight());
				}
				this.height = Math.max(this.height, minHeight);
				this.height = Math.min(this.height, maxHeight);
				minHeight = this.height;
				// Fix to the specified width
				maxHeight = this.height;
				break;
			case MIXED:
				if (this.isSpecifiedPageSize()) {
					this.height = this.size.getHeight() + this.size.getHeightRatio() * containerBox.getInnerHeight();
					if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
						this.height = Math.max(0, this.height - this.getFrame().getBorderHeight());
					}
					this.height = Math.max(this.height, minHeight);
					this.height = Math.min(this.height, maxHeight);
					minHeight = this.height;
					maxHeight = this.height;
					break;
				}
				if (this.params.flow.isVertical()) {
					this.height = layoutStack.getFixedHeight() - this.frame.getFrameHeight();
				} else {
					this.height = 0;
				}
				this.height = Math.max(this.height, minHeight);
				break;
			default:
				throw new IllegalStateException();
			}
			if (!this.specifiedPageAxis && this.params.aspectRatio > 0) {
				// aspect-ratio: if height is auto (or a percentage with an indefinite basis), derive it
				// from width and the ratio (2026-08-29). A normal-flow block always has a definite width,
				// so this is the main path that determines a thumbnail's 16:9 ratio. If the content
				// exceeds the ratio-derived height, overflow:visible lets the box grow to fit it
				// (approximates min-height:auto = content size; setPageAxis clamps to at least minPageAxis
				// and at most maxPageAxis).
				double page = this.aspectRatioPageExtent(this.width);
				page = Math.max(page, minHeight);
				page = Math.min(page, maxHeight);
				this.height = page;
				minHeight = page;
				if (this.params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
					maxHeight = page;
				}
				this.specifiedPageAxis = true;
			}
			marginLeft += xmargin;
		}
		if (this.params.flow.isVertical()) {
			this.minPageAxis = minWidth;
			this.maxPageAxis = maxWidth;
		} else {
			this.minPageAxis = minHeight;
			this.maxPageAxis = maxHeight;
		}
		assert !LayoutUtils.isNone(marginTop);
		assert !LayoutUtils.isNone(marginRight);
		assert !LayoutUtils.isNone(marginBottom);
		assert !LayoutUtils.isNone(marginLeft);
		this.frame.margin.top = marginTop;
		this.frame.margin.right = marginRight;
		this.frame.margin.bottom = marginBottom;
		this.frame.margin.left = marginLeft;
		if (!LayoutUtils.isNone(this.restyleLineWidth)) {
			this.width = this.restyleLineWidth - this.frame.getFrameWidth();
		}
		if (!LayoutUtils.isNone(this.restyleLineHeight)) {
			this.height = this.restyleLineHeight - this.frame.getFrameHeight();
		}
		this.clearRestyleLineExtent();
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final double getContentSize() {
		return this.contentSize;
	}

	/**
	 * The definite page-axis content-box size used to calculate aspect-ratio in reverse
	 * (definite page axis → line axis) (2026-08-29). Returns a value only for an absolute length
	 * or a percentage/mixed value with a definite basis; otherwise NONE.
	 *
	 * @param percentBase percentage basis (NONE if indefinite)
	 */
	private double definitePageExtentForRatio(final double percentBase) {
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.params.flow;
		final LengthType type = this.size.getPageType(flow);
		double page;
		switch (type) {
		case ABSOLUTE:
			page = this.size.getPageLength(flow);
			break;
		case RELATIVE:
			if (LayoutUtils.isNone(percentBase)) {
				return LayoutUtils.NONE;
			}
			page = this.size.getPageLength(flow) * percentBase;
			break;
		case MIXED:
			if (LayoutUtils.isNone(percentBase)) {
				return LayoutUtils.NONE;
			}
			page = this.size.getPageLength(flow) + this.size.getPageRatio(flow) * percentBase;
			break;
		default:
			return LayoutUtils.NONE;
		}
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			page -= this.frame.getBorderPageExtent(flow);
		}
		return Math.max(0, page);
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, java.util.Deque<DrawStep> worklist) {

		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
		}

		if (this.getFlowPos().offset != null || this.params.isStackingContext()) {
			this.frames(pageBox, drawer, clip, transform, x, y);
		}
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			// Draw children with negative z-index after this box's background/border and before other content (Appendix E ③).
			drawer.markOwnDecorationEnd();
		}
		super.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		// Resolved alignment is shared between fragments (equivalent to the old write-back to pos).
		// The recipe captures the value and does not retain this.
		final Align resolvedAlign = this.resolvedAlign;
		// Pass line-axis sizes determined by intrinsic sizing keywords to continuations as definite values
		// (2026-08-29). The fragment's size remains AUTO along the line axis, so restyle →
		// startFlowBlock → calculateSize would resolve it again to the containing block width.
		// The resolved value is measured from all content and shared across fragments, so fix it as ABSOLUTE.
		final Dimension resolvedLine = params.hasIntrinsicLine() ? this.resolvedLineSize() : null;
		return (state, container) -> {
			final Dimension nextSize = resolvedLine == null ? state.nextSize()
					: withLine(state.nextSize(), resolvedLine, params.flow);
			final FlowBlockBox next = new FlowBlockBox(params, pos, nextSize, state.nextMinSize(),
					state.nextFrame(), container);
			next.resolvedAlign = resolvedAlign;
			next.continuation = true;
			return next;
		};
	}

	/**
	 * Returns a Dimension expressing the used line-axis size as a specified size (ABSOLUTE)
	 * (2026-08-29, for {@link #fragmentRecipe}). For border-box, add the amount that
	 * calculateSize deducts.
	 */
	private Dimension resolvedLineSize() {
		final WritingMode flow = this.params.flow;
		final double line = (flow.isVertical() ? this.height : this.width)
				+ (this.params.boxSizing == BoxSizingMode.BORDER_BOX ? this.frame.getBorderLineExtent(flow) : 0);
		return Dimension.create(line, line, LengthType.ABSOLUTE, LengthType.ABSOLUTE);
	}

	/** Combines the page axis of {@code page} with the line axis of {@code line}. */
	private static Dimension withLine(final Dimension page, final Dimension line, final WritingMode flow) {
		return flow.isVertical()
				? Dimension.create(page.getWidth(), page.getWidthRatio(), line.getHeight(), 0, page.getWidthType(),
						LengthType.ABSOLUTE)
				: Dimension.create(line.getWidth(), 0, page.getHeight(), page.getHeightRatio(), LengthType.ABSOLUTE,
						page.getHeightType());
	}

	public final void restyle(final BlockBuilder builder, final net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		if (shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.Closed && this.params.intrinsicLine != null
				&& LayoutUtils.isNone(this.params.flow.isVertical() ? this.restyleLineHeight : this.restyleLineWidth)) {
			// A box moved whole keeps the line size its content gave it (width: fit-content and the like, 2026-10-09):
			// startFlowBlock resolves only the block rules, which fill the line (a tab list moved to the next page came
			// out the width of the page).
			this.prepareRestyleLineExtent(this.width + this.frame.getFrameWidth(), this.height + this.frame.getFrameHeight(),
					this.params.flow.isVertical());
		}
		builder.startFlowBlock(this);
		super.restyle(builder, shape);
		if (!(shape instanceof net.zamasoft.foliojet.layout.fragment.OpenShape.Closed)) {
			// Open continuation: live processing continues at the tail inside this box.
			return;
		}
		if (!builder.hasOpenFlow()) {
			// If a column break owned by contextFlow occurs while replaying a closed subtree,
			// pruneFlowStackTo() may consume all inner flows without rebuilding open flows
			// on the continuation side. Only in that case, avoid closing twice from the old call frame.
			// If the continuation rebuilt the same depth with a different identity,
			// close that new top level once as usual (seed 4540).
			return;
		}
		builder.endFlowBlock();
	}

	public boolean avoidBreakBefore() {
		if (this.getFlowPos().pageBreakBefore == PageBreakMode.AVOID) {
			return true;
		}
		return this.container.avoidBreakBefore();
	}

	public boolean avoidBreakAfter() {
		if (this.getFlowPos().pageBreakAfter == PageBreakMode.AVOID) {
			return true;
		}
		return this.container.avoidBreakAfter();
	}
}
