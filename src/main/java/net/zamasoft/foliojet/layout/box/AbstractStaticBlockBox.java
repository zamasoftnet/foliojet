package net.zamasoft.foliojet.layout.box;

import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.sizing.Sizing;
import net.zamasoft.foliojet.layout.sizing.SizingContext;
import net.zamasoft.foliojet.layout.sizing.SizingMode;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.IntrinsicSize;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.DebugFlags;

/**
 * Implements a block box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractStaticBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class AbstractStaticBlockBox extends AbstractBlockBox {
	protected boolean specifiedPageAxis = false;

	public AbstractStaticBlockBox(final BlockParams params) {
		super(params);
	}

	protected AbstractStaticBlockBox(final BlockParams params, final Dimension size, final Dimension minSize,
			final AbsoluteRectFrame frame, final Container container) {
		super(params, size, minSize, frame, container);
	}

	public abstract AbstractStaticPos getStaticPos();

	/**
	 * The horizontal compression factor for tate-chu-yoko ({@code text-combine-upright: all}).
	 * 1 means no compression. Set by {@link #compressTextCombine}.
	 */
	private double textCombineScaleX = 1;

	/** The physical X offset that centers the content within the compressed cell. */
	private double textCombineOffsetX = 0;

	/** Whether {@link #compressTextCombine} has already fitted the content into the 1em cell. */
	private boolean textCombineFitted = false;

	protected final double internalScaleX() {
		return this.textCombineScaleX;
	}

	protected final double internalOffsetX() {
		return this.textCombineOffsetX;
	}

	/**
	 * Fits tate-chu-yoko content into a 1em cell (css-writing-modes-4 §9.1.3,
	 * 2026-08-11).
	 *
	 * <p>
	 * Call <b>after</b> layout at the natural width {@code W}. Replace the box width with
	 * {@code cellExtent} (=1em) and apply a horizontal affine transform of
	 * {@code min(1, cellExtent/W)} to the content. This order is required because laying out
	 * at a width of 1em first would wrap the digits. For 2–4 characters, {@code hwid/twid/qwid}
	 * is requested before shaping, so the natural width here is the remeasured result.
	 * Only fonts without those features fall back to affine compression.
	 * If the natural width is less than 1em, keep the scale at 1 and center the content in the cell.
	 * </p>
	 *
	 * <p>
	 * Do nothing for a box already fitted, so repeated calls are safe: two-pass layout and column balancing
	 * may place the same box into a line again. Previously, the check tested whether it was compressed
	 * (scaleX≠1), so an uncompressed single character was centered again using its already centered glyph bounds
	 * on the second call. The offset reverted to 0, placing the character's right edge at the line center
	 * (2026-10-06, jigensha report: tate-chu-yoko "2" and "1" in columns shifted about 0.25 em to the left).
	 * </p>
	 *
	 * @param cellExtent the cell width (normally 1em)
	 * @param inkBounds  the glyph outline in local coordinates before compression, or null if unavailable
	 */
	public final void compressTextCombine(final double cellExtent, final Rectangle2D inkBounds) {
		if (this.textCombineFitted || cellExtent <= 0) {
			return;
		}
		final double natural = this.width;
		if (natural <= 0) {
			return;
		}
		if (natural > cellExtent) {
			this.textCombineScaleX = cellExtent / natural;
		}
		if (inkBounds != null && !inkBounds.isEmpty()) {
			// Center the actual glyph bounds (ink), rather than the advance, in the 1em cell.
			// Digits with the same advance have different left/right side bearings depending on the glyph,
			// so scaling from the left edge made two-digit page numbers shift sideways depending on the digits
			// (2026-08-13: measured 41/43/45 at 1200 dpi in the table of contents of an actual book).
			this.textCombineOffsetX = cellExtent / 2.0
					- this.textCombineScaleX * inkBounds.getCenterX();
		} else if (natural <= cellExtent) {
			this.textCombineOffsetX = (cellExtent - natural) / 2;
		}
		this.width = cellExtent;
		this.textCombineFitted = true;
	}

	public final boolean isSpecifiedPageSize() {
		return this.specifiedPageAxis;
	}

	/**
	 * Takes a continuation fragment's page-axis size from what its split left of a definite size and of a min-size
	 * (2026-10-10). A float's continuation is laid out again without {@link #shrinkToFit}, so it took neither and fitted
	 * its content: a float with {@code height: 60pt} split at 30pt went on 0pt tall on the next page, where Chrome keeps
	 * the other 30pt, and so did one with {@code min-height}. A block's continuation gets them from
	 * {@code calculateSize}. The rest is a floor only: content longer than it still makes the continuation grow, as
	 * before, which the overflow checks of an opposite-progression region need (they measure the box, not what it
	 * paints).
	 */
	protected final void continuePageAxis() {
		final WritingMode flow = this.params.flow;
		final double min = this.minSize.getPageType(flow) == LengthType.ABSOLUTE ? this.minSize.getPageLength(flow) : 0;
		double extent = min;
		if (this.size.getPageType(flow) == LengthType.ABSOLUTE) {
			extent = Math.max(this.size.getPageLength(flow), min);
			this.specifiedPageAxis = true;
		}
		this.minPageAxis = extent;
		this.continuedPageAxis = extent;
		if (flow.isVertical()) {
			this.width = extent;
		} else {
			this.height = extent;
		}
	}

	/**
	 * The page-axis content-box size derived from {@code aspect-ratio}
	 * (2026-08-29, css-sizing-4 §5). The ratio is physical width/height and applies to the
	 * {@code box-sizing} box (including padding+border for border-box).
	 *
	 * @param lineExtent the line-axis content-box size
	 * @return the page-axis content-box size (NONE if no ratio is specified)
	 */
	protected final double aspectRatioPageExtent(final double lineExtent) {
		final double ratio = this.params.aspectRatio;
		if (!(ratio > 0)) {
			return LayoutUtils.NONE;
		}
		final boolean borderBox = this.params.boxSizing == BoxSizingMode.BORDER_BOX;
		final double lineFrame = borderBox ? this.frame.getBorderLineExtent(this.params.flow) : 0;
		final double pageFrame = borderBox ? this.frame.getBorderPageExtent(this.params.flow) : 0;
		final double outerLine = Math.max(0, lineExtent) + lineFrame;
		// Horizontal writing: line axis = width, so height = width / ratio. Vertical: height, so width = height × ratio.
		final double outerPage = this.params.flow.isVertical() ? outerLine * ratio : outerLine / ratio;
		return Math.max(0, outerPage - pageFrame);
	}

	/**
	 * The line-axis content-box size derived from {@code aspect-ratio}
	 * (the inverse of {@link #aspectRatioPageExtent}, used when only the page-axis size
	 * is definite; 2026-08-29).
	 */
	protected final double aspectRatioLineExtent(final double pageExtent) {
		final double ratio = this.params.aspectRatio;
		if (!(ratio > 0)) {
			return LayoutUtils.NONE;
		}
		final boolean borderBox = this.params.boxSizing == BoxSizingMode.BORDER_BOX;
		final double lineFrame = borderBox ? this.frame.getBorderLineExtent(this.params.flow) : 0;
		final double pageFrame = borderBox ? this.frame.getBorderPageExtent(this.params.flow) : 0;
		final double outerPage = Math.max(0, pageExtent) + pageFrame;
		final double outerLine = this.params.flow.isVertical() ? outerPage / ratio : outerPage * ratio;
		return Math.max(0, outerLine - lineFrame);
	}

	public final boolean isContextBox() {
		return this.getStaticPos().offset != null;
	}

	/** Whether a line-axis length is a cyclic percentage: one (or calc() with one) resolved against the scratch page's
	 * line while an intrinsic size is measured (CSS Sizing 3 §5.2;
	 * {@link net.zamasoft.foliojet.layout.sizing.CyclicPercent#cyclic(double)}). */
	private static boolean cyclicLength(final Dimension dimension, final WritingMode flow, final double basis) {
		final LengthType type = dimension.getLineType(flow);
		return (type == LengthType.RELATIVE || type == LengthType.MIXED)
				&& net.zamasoft.foliojet.layout.sizing.CyclicPercent.cyclic(basis);
	}

	public void shrinkToFit(LayoutStack layoutStack, IntrinsicSizes sizes, boolean table) {
		this.shrinkToFit(layoutStack, sizes, table, LayoutUtils.NONE);
	}

	/**
	 * Containing-block line length for column footnotes only.
	 * NONE uses the existing measurement, including page footnotes.
	 */
	public void shrinkToFit(LayoutStack layoutStack, IntrinsicSizes sizes, boolean table, final double hostLineSize) {
		final boolean columnFootnote = !LayoutUtils.isNone(hostLineSize);
		final double minLineAxis = sizes.minContent(), maxLineAxis = sizes.maxContent();
		final AbstractContainerBox containerBox;
		if (this.getPos().getType() == PosType.FLOW) {
			if (table) {
				// Table
				BlockBuilder builder = (BlockBuilder) layoutStack;
				containerBox = builder.getFlow(builder.getFlowCount() - 2).box;
			} else {
				// Mixed writing directions
				containerBox = layoutStack.getFlowBox();
			}
		} else {
			containerBox = layoutStack.getFlowBox();
		}
		if (!columnFootnote && !table && containerBox.getType() == BoxType.TABLE_CELL) {
			table = true;
		}
		final BlockParams cParams = containerBox.getBlockParams();
		final double lineSize = columnFootnote ? hostLineSize : containerBox.getLineSize();
		final WritingMode flow = this.params.flow;
		{
			final LengthType pageType = this.params.size.getPageType(flow);
			// An orthogonal block (whose writing-mode axis differs from its parent's) uses the parent's line axis
			// as the basis for page-axis percentages, and that axis is always definite. Checking the parent's
			// page axis (isSpecifiedPageSize) here treated height:100% on horizontal blocks in vertical documents
			// as indefinite, falling through to AUTO and becoming 0.
			// This overwrote the correct value firstPassLayout had derived from the parent's line axis (2026-08-10:
			// discovered when all reference illustration pages in an actual book failed).
			final boolean orthogonal = cParams.flow.isVertical() != flow.isVertical();
			this.specifiedPageAxis = pageType == LengthType.ABSOLUTE || (pageType.needsReference() && (!table
					&& (this.getPos().getType() == PosType.INLINE || orthogonal
							|| containerBox.isSpecifiedPageSize())));
		}

		//
		// ■ Calculate padding
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ Calculate margins
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		//
		// ■ Calculate the line-axis size
		//
		// Calculate on logical axes (line/page) and write back to physical dimensions at the end.
		final SizingContext context = this.fitContentContext(layoutStack, containerBox, table);
		// **Normal flow on the same axis as the parent reaches here only with intrinsic sizing keywords**
		// (width:max-content, etc., 2026-08-29; dispatched by DocumentBuilder.startBox).
		// The available size and percentage basis are the containing block's line size,
		// not getFixedWidth(), which is for floats.
		final boolean sameAxisFlow = !table && this.getPos().getType() == PosType.FLOW
				&& cParams.flow.isVertical() == flow.isVertical();
		final double cLine = columnFootnote ? hostLineSize : sameAxisFlow ? lineSize : context.availableLine();

		// Line axis: fit-content and min/max clamping
		// While an intrinsic size is being measured, a percentage (or calc() with one) is cyclic and counts as auto
		// (CSS Sizing 3 §5.2, 2026-10-09): resolved against the scratch page's 10^6 it made a button holding a
		// width: 100% input as wide as the page.
		final boolean cyclic = cyclicLength(this.size, flow, cLine);
		double lineExtent = cyclic ? LayoutUtils.NONE : LayoutUtils.computeDimensionLine(this.size, flow, cLine);
		// aspect-ratio: If the line-axis size is auto and the page-axis size is an absolute length,
		// derive the line-axis size from the ratio instead of fit-content (2026-08-29: a float/inline-block
		// with height:40px;aspect-ratio:2 has width 80px).
		boolean ratioLine = false;
		if (LayoutUtils.isNone(lineExtent) && this.params.aspectRatio > 0
				&& this.size.getPageType(flow) == LengthType.ABSOLUTE) {
			double page = this.size.getPageLength(flow);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				page -= this.frame.getBorderPageExtent(flow);
			}
			lineExtent = this.aspectRatioLineExtent(page);
			ratioLine = true;
		}
		if (LayoutUtils.isNone(lineExtent)) {
			lineExtent = maxLineAxis;
		} else if (!ratioLine) {
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				lineExtent -= this.frame.getBorderLineExtent(flow);
			}
		}
		final double limitLine = columnFootnote ? Math.max(0, hostLineSize - this.frame.getFrameLineExtent(flow))
				: this.availableLineExtent(layoutStack, containerBox, cLine);
		if (cyclic && !ratioLine && this.params.naturalLineSize > 0) {
			// A form control: its natural width for max-content, 0 for min-content (compressible)
			lineExtent = net.zamasoft.foliojet.layout.sizing.CyclicPercent.maxContent() ? this.params.naturalLineSize
					: 0;
		} else if ((this.size.getLineType(flow) == LengthType.AUTO || cyclic) && !ratioLine) {
			final IntrinsicSize intrinsic = table ? null : this.params.intrinsicLine;
			if (intrinsic != null) {
				// Intrinsic sizing keywords (2026-08-29): max-content/min-content use the measured values themselves;
				// fit-content(L) uses shrink-to-fit with L as the upper bound.
				// Do not apply the paper-width limit (the column-count inflation clamp below):
				// the author explicitly specifies the content width, so any overflow follows the specification.
				lineExtent = this.resolveIntrinsicLine(intrinsic, minLineAxis, maxLineAxis, limitLine, cLine);
			} else if (sameAxisFlow) {
				// Normal flow with width:auto and intrinsic sizing only in min/max: fill the containing block
				// as usual, then clamp with min/max below.
				lineExtent = limitLine;
			} else {
				lineExtent = Sizing.fitContent(minLineAxis, lineExtent, limitLine);
				if (!table && sizes.columnInflated() && limitLine > 0 && lineExtent > limitLine) {
					// **Do not let the minimum content size inflated by the column count exceed the paper's line axis**
					// (2026-07-28).
					//
					// `fit-content` is `max(min-content, min(available, max-content))`,
					// so **if the minimum content size exceeds the available space, it is used as is**.
					// This is correct for an on-screen browser: the overflow remains readable by scrolling.
					// Paper, however, has no continuation beyond its edge. Moreover,
					// **the line axis cannot be split** (pagination applies only to the page axis),
					// so content overflowing the line axis is drawn at coordinates outside the paper
					// instead of moving to the next page.
					//
					// The minimum content size of a multi-column layout is "column count × minimum content size + gaps":
					// **it grows with the column count**, and nesting multiplies the effect. Measurements
					// (2026-07-28, seed 25503) showed an 823 pt tall float on 200 pt paper
					// (= 4 columns × 196 pt + 3 × 13 pt), with content drawn at y=-623.
					// **Columns can be narrowed** (simply divide the line axis by the column count again),
					// so this lower bound need not be honored. The columns become narrower but fit on the paper,
					// matching horizontal writing, which never exhibited this defect.
					//
					// It is essential to apply this **only when column-count inflation occurs**
					// ({@code columnInflated}). If the minimum content size instead comes from an explicitly sized,
					// indivisible box such as an image with `height:150mm`, shrinking it only shrinks the box,
					// not its contents, **increasing overflow**. Measurements showed that a
					// `writing-mode:vertical-rl` box on 400 pt paper shrank from 425.2 to 316 pt,
					// and its image overflowed in place instead of moving to the next column.
					// (`WritingModeColumnTest`).
					//
					// An explicit `min-*` overrides this value below, so the author's setting
					// still takes effect as before.
					lineExtent = limitLine;
				}
			}
		}
		// min/max-width use the box-sizing scale. lineExtent is the content width,
		// so subtract borders + padding for border-box before comparing
		// (2026-08-29). Previously, these were not subtracted, so a pill with `min-width:100px; padding-inline:8px;
		// box-sizing:border-box` grew to 116px (exposed by support for padding-inline;
		// 0510-flex/min-width-nested-container expects 75 pt).
		final double borderBoxLine = this.params.boxSizing == BoxSizingMode.BORDER_BOX
				? this.frame.getBorderLineExtent(flow)
				: 0;
		// Cyclic percentages of max-width/min-width count as none/0 while an intrinsic size is measured (2026-10-09)
		double maxLine = cyclicLength(this.params.maxSize, flow, cLine) ? LayoutUtils.NONE
				: LayoutUtils.computeDimensionLine(this.params.maxSize, flow, cLine);
		if (!LayoutUtils.isNone(maxLine)) {
			maxLine = Math.max(0, maxLine - borderBoxLine);
		}
		if (!table && this.params.intrinsicMaxLine != null) {
			// max-width: max-content, etc. (2026-08-29). The Dimension value is AUTO (none).
			maxLine = this.resolveIntrinsicLine(this.params.intrinsicMaxLine, minLineAxis, maxLineAxis, limitLine,
					cLine);
		}
		if (!LayoutUtils.isNone(maxLine) && lineExtent > maxLine) {
			lineExtent = maxLine;
		}
		double minLine = cyclicLength(this.minSize, flow, cLine) ? LayoutUtils.NONE
				: LayoutUtils.computeDimensionLine(this.minSize, flow, cLine);
		if (!LayoutUtils.isNone(minLine)) {
			minLine = Math.max(0, minLine - borderBoxLine);
		}
		if (!table && this.params.intrinsicMinLine != null) {
			// min-width: max-content, etc. (2026-08-29). The Dimension value is AUTO (0).
			minLine = this.resolveIntrinsicLine(this.params.intrinsicMinLine, minLineAxis, maxLineAxis, limitLine,
					cLine);
		}
		if (!LayoutUtils.isNone(minLine) && lineExtent < minLine) {
			lineExtent = minLine;
		}

		// Column footnotes fill the host's line length regardless of short text or the author's inline min/max.
		if (columnFootnote) lineExtent = limitLine;

		// Page axis: min/max and the specified size. Resolve percentages only when percentBasePage is definite.
		double minPage;
		switch (this.minSize.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				minPage = this.minSize.getPageLength(flow) * context.percentBasePage();
				break;
			}
			// Fall through to AUTO if percentBasePage is indefinite (existing intentional behavior)
		case AUTO:
			minPage = 0;
			break;
		case ABSOLUTE:
			minPage = this.minSize.getPageLength(flow);
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				minPage = this.minSize.getPageLength(flow) + this.minSize.getPageRatio(flow) * context.percentBasePage();
				break;
			}
			minPage = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		double maxPage;
		switch (this.params.maxSize.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				maxPage = this.params.maxSize.getPageLength(flow) * context.percentBasePage();
				break;
			}
			// Fall through to AUTO if percentBasePage is indefinite (existing intentional behavior)
		case AUTO:
			maxPage = Double.MAX_VALUE;
			break;
		case ABSOLUTE:
			maxPage = this.params.maxSize.getPageLength(flow);
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				maxPage = this.params.maxSize.getPageLength(flow)
						+ this.params.maxSize.getPageRatio(flow) * context.percentBasePage();
				break;
			}
			maxPage = Double.MAX_VALUE;
			break;
		default:
			throw new IllegalStateException();
		}
		// Page-axis min/max also use the box-sizing scale. For border-box, subtract the frame
		// to match the inner-size scale before comparing (2026-08-29; paired with line-axis
		// borderBoxLine). Previously, minPageAxis/maxPageAxis still included the frame,
		// so setPageAxis raised the content height to the lower bound including the frame.
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			final double borderBoxPage = this.getFrame().getBorderPageExtent(flow);
			minPage = Math.max(0, minPage - borderBoxPage);
			if (maxPage != Double.MAX_VALUE) {
				maxPage = Math.max(0, maxPage - borderBoxPage);
			}
		}
		double pageExtent = flow.isVertical() ? this.width : this.height;
		switch (this.size.getPageType(flow)) {
		case RELATIVE:
			if (context.isPagePercentDefinite()) {
				pageExtent = this.size.getPageLength(flow) * context.percentBasePage();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					pageExtent = Math.max(0, pageExtent - this.getFrame().getBorderPageExtent(flow));
				}
				pageExtent = Math.max(pageExtent, minPage);
				pageExtent = Math.min(pageExtent, maxPage);
				minPage = maxPage = pageExtent;
				break;
			}
			// Fall through to AUTO if percentBasePage is indefinite (existing intentional behavior)
		case AUTO:
			// Ledger #4 resolved (2026-07-17): The old implementation retained the existing value only
			// for tables in vertical writing. Start from the min size, as in horizontal writing (content determines
			// it later). Starting from 0 lost min-height on an empty box, as setPageAxis applies it only when content
			// grows the box (2026-10-09: an empty inline-block, a select without options).
			pageExtent = Math.min(minPage, maxPage);
			break;
		case ABSOLUTE:
			pageExtent = this.size.getPageLength(flow);
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				pageExtent = Math.max(0, pageExtent - this.getFrame().getBorderPageExtent(flow));
			}
			pageExtent = Math.max(pageExtent, minPage);
			pageExtent = Math.min(pageExtent, maxPage);
			minPage = maxPage = pageExtent;
			break;
		case MIXED:
			if (context.isPagePercentDefinite()) {
				pageExtent = this.size.getPageLength(flow) + this.size.getPageRatio(flow) * context.percentBasePage();
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					pageExtent = Math.max(0, pageExtent - this.getFrame().getBorderPageExtent(flow));
				}
				pageExtent = Math.max(pageExtent, minPage);
				pageExtent = Math.min(pageExtent, maxPage);
				minPage = maxPage = pageExtent;
				break;
			}
			pageExtent = Math.min(minPage, maxPage);
			break;
		default:
			throw new IllegalStateException();
		}
		if (this.params.aspectRatio > 0 && !this.specifiedPageAxis) {
			// aspect-ratio: If the page-axis size is auto, derive it from the line axis and the ratio
			// (2026-08-29). With overflow:visible, grow to fit content taller than the ratio-derived height
			// (an approximation of the specified min-height:auto = content size).
			// Set minPage to the ratio-derived height, leaving maxPage unbounded when overflow is visible.
			double page = this.aspectRatioPageExtent(lineExtent);
			page = Math.max(page, minPage);
			page = Math.min(page, maxPage);
			pageExtent = page;
			minPage = page;
			if (this.params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
				maxPage = page;
			}
			this.specifiedPageAxis = true;
		}
		this.minPageAxis = minPage;
		this.maxPageAxis = maxPage;

		// Write back to physical dimensions
		if (flow.isVertical()) {
			this.height = lineExtent;
			this.width = pageExtent;
		} else {
			this.width = lineExtent;
			this.height = pageExtent;
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
		// Leave auto margins in normal flow (centering with margin:0 auto) untouched here:
		// BlockBuilder.addBound resolves them together with resolvedAlign
		// (the same path as orthogonal flow, 2026-08-29).
	}

	/**
	 * Returns the available line-axis size (content-box). Extracted from {@link #shrinkToFit}
	 * on 2026-08-29; also used to resolve intrinsic sizes for min/max.
	 *
	 * @param layoutStack  the layout stack
	 * @param containerBox the containing block
	 * @param cLine        the containing block's line-axis size
	 * @return the available size
	 */
	private double availableLineExtent(final LayoutStack layoutStack, final AbstractContainerBox containerBox,
			final double cLine) {
		final WritingMode flow = this.params.flow;
		if (containerBox.getBlockParams().flow.isVertical() == flow.isVertical() || containerBox.isSpecifiedPageSize()) {
			return cLine - this.frame.getFrameLineExtent(flow);
		}
		// If the parent's width is indefinite, use the page size as the limit. The basis is
		// the page's **content area** (inside the margins): using physical dimensions would
		// let fit-content extend into the margins
		// (2026-08-10: measured on reference illustration pages in a book in vertical writing).
		final AbstractContainerBox fixedLineBox = flow.isVertical() ? layoutStack.getFixedHeightFlowBox()
				: layoutStack.getFixedWidthFlowBox();
		if (DebugFlags.LINE_BASIS) {
			System.err.println("[lineBasis] box=" + this.getClass().getSimpleName() + " element="
					+ (this.params == null ? "-" : String.valueOf(this.params.element)) + " flowVertical=" + flow.isVertical()
					+ " containerFlowVertical=" + containerBox.getBlockParams().flow.isVertical() + " cLine=" + cLine
					+ " fixedLineBox=" + (fixedLineBox == null ? "null" : fixedLineBox.getClass().getSimpleName())
					+ " fixedLineExtent=" + (fixedLineBox == null ? Double.NaN : fixedLineBox.getInnerLineExtent(flow))
					+ " orthogonalBasis=" + layoutStack.getOrthogonalLineBasis(flow)
					+ " frame=" + this.frame.getFrameLineExtent(flow));
		}
		return (fixedLineBox != null ? fixedLineBox.getInnerLineExtent(flow)
				// If no ancestor has an explicit size, use the fragmentainer (page) content area
				// (2026-09-16, LayoutStack.getOrthogonalLineBasis)
				: layoutStack.getOrthogonalLineBasis(flow))
				- this.frame.getFrameLineExtent(flow);
	}

	/**
	 * Whether the containing block is the root of a builder laying out the content of a shrink-to-fit box
	 * (inline-block, float, absolutely positioned box) after its line-axis size is determined (2026-10-09).
	 * getFixedWidth() skips such a root, whose specified width is auto, so a percentage resolved against the nearest
	 * ancestor with a specified width: a width: 100% input in an inline-block button became as wide as the page. Once
	 * sized, the box is the percentage basis for its content (CSS Sizing 3 §5.2). A two-pass builder records content
	 * before the size is known, so it keeps the old basis. Footnotes, page floats and margin notes leave the box for
	 * an area of the page, so they keep it too.
	 */
	private boolean sizedShrinkToFitRoot(final LayoutStack layoutStack, final AbstractContainerBox containerBox,
			final WritingMode flow) {
		final Pos pos = this.getPos();
		if (pos instanceof net.zamasoft.foliojet.layout.box.params.FootnotePos
				|| pos instanceof net.zamasoft.foliojet.layout.box.params.PageFloatPos
				|| pos instanceof net.zamasoft.foliojet.layout.box.params.PageMarginNotePos) {
			return false;
		}
		if (!(layoutStack instanceof net.zamasoft.foliojet.layout.builder.Builder builder) || builder.isTwoPass()
				|| containerBox != layoutStack.getRootBox()
				|| containerBox.getBlockParams().flow.isVertical() != flow.isVertical()) {
			return false;
		}
		final PosType type = containerBox.getPos().getType();
		return type == PosType.INLINE || type == PosType.FLOAT || type == PosType.ABSOLUTE;
	}

	/**
	 * Derives the constraint space for fit-content sizing from the containing context.
	 * specifiedPageAxis must be determined before this call.
	 *
	 * @param layoutStack  the layout stack
	 * @param containerBox the containing block
	 * @param table        true in a table context
	 * @return the constraint space
	 */
	private SizingContext fitContentContext(LayoutStack layoutStack, AbstractContainerBox containerBox, boolean table) {
		final WritingMode flow = this.params.flow;
		// Reference box for the page axis.
		AbstractContainerBox fixedPageBox = flow.isVertical() ? layoutStack.getFixedWidthFlowBox()
				: layoutStack.getFixedHeightFlowBox();
		if (fixedPageBox == null) {
			fixedPageBox = containerBox;
		}
		// Ledger #3 resolved (2026-07-17): The old implementation referenced InnerHeight even in vertical
		// writing. Page-axis percentages use the logical page-axis inner size (width in vertical writing).
		// However, an orthogonal block's page axis matches the parent's line axis, so use the containing
		// block's inner line-axis size (2026-08-10; paired with the orthogonal condition in specifiedPageAxis).
		final BlockParams cParams = containerBox.getBlockParams();
		final double cPage = (cParams.flow.isVertical() != flow.isVertical())
				? containerBox.getInnerLineExtent(cParams.flow)
				: fixedPageBox.getInnerPageExtent(flow);
		double cLine = table || sizedShrinkToFitRoot(layoutStack, containerBox, flow)
				? containerBox.getInnerLineExtent(flow)
				// Same as above (0 in orthogonal flow makes fit-content produce zero width)
				: layoutStack.getOrthogonalLineBasis(flow);
		if (LayoutUtils.isNone(cLine) || LayoutUtils.compare(cLine, 0) <= 0) {
			// Even in a table context, the containing block's inner line-axis size cannot be the basis in orthogonal
			// flow (the "width" of a body in vertical writing is passed as 0). Using 0 as the line-axis percentage
			// basis makes `max-width: 90%` zero, causing content to overflow from a zero-width table
			// beyond the paper (measured on 2026-09-16; "all drawing outside the paper" in the sweep).
			cLine = layoutStack.getOrthogonalLineBasis(flow);
		}
		// Resolve page-axis percentages only when the basis is definite: this box's own page-axis size, or for min and
		// max sizes the containing block's (2026-10-10: a float's min-height: 60% in a box of height: 100pt was
		// dropped while its height was auto; Chrome takes 60pt).
		final double pagePercentBase = !table && (this.isSpecifiedPageSize()
				|| (cParams.flow.isVertical() == flow.isVertical() && containerBox.isSpecifiedPageSize())) ? cPage
						: LayoutUtils.NONE;
		return new SizingContext(SizingMode.FIT_CONTENT, cLine, cLine, pagePercentBase);
	}

	public void finishLayoutSelf(IFramedBox containerBox) {
		// Calculate the position
		AbstractStaticPos pos = this.getStaticPos();
		if (pos.offset != null) {
			//
			// ■ Calculate the relative position
			//
			this.offsetX = LayoutUtils.computeOffsetX(pos.offset, containerBox);
			this.offsetY = LayoutUtils.computeOffsetY(pos.offset, containerBox);
		}
	}
}
