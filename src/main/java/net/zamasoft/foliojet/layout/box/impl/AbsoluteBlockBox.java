package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.sizing.AbsoluteSizing;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.Fiducial;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.TwoPassBlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Implementation of a block box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteBlockBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class AbsoluteBlockBox extends AbstractBlockBox implements IAbsoluteBox {
	protected final AbsolutePos pos;

	public AbsoluteBlockBox(BlockParams params, AbsolutePos pos) {
		super(params);
		this.pos = pos;
	}

	protected AbsoluteBlockBox(BlockParams params, AbsolutePos pos, Dimension size, Dimension minSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, size, minSize, frame, container);
		this.pos = pos;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final AbsolutePos getAbsolutePos() {
		return this.pos;
	}

	public final boolean isSpecifiedPageSize() {
		return true;
	}

	/**
	 * Portable form of sealed body content (E-6 increment 4e). Holds only an
	 * {@code IntrinsicSizes} snapshot, a LayoutSource range, and a retention lease.
	 */
	private TwoPassBlockBuilder.DeferredBind deferredBind;

	/**
	 * The simulated measurement without the orthogonal contributions, kept when the body holds content of the other
	 * writing mode (2026-10-08; null otherwise). {@link #bindDeferredContent} then measures that content by a trial
	 * layout, as DocumentBuilder.shrinkToFitSizes does for floats and fixed boxes.
	 */
	private net.zamasoft.foliojet.layout.sizing.IntrinsicSizes sizesWithoutOrthogonal;

	public final void prepareBind(TwoPassBlockBuilder builder) {
		this.sizesWithoutOrthogonal = builder.hasOrthogonalContent() ? builder.intrinsicSizesWithoutOrthogonal() : null;
		this.deferredBind = builder.detachDeferredBind();
	}

	/**
	 * Returns whether this box is not anchored to any context builder and has no pending bind
	 * (DeferredBind) (absolute absorption = codex increment 9, 2026-07-30).
	 * An absolute box recorded in TwoPass always has this state: its context is TwoPass,
	 * so it never goes through prepareBind/addBound/inline registration.
	 * The parent may absorb the box into a range only with this proof
	 * (absorbing a box that holds deferredBind would leave nobody to bind/close its lease).
	 */
	public final boolean isUnattachedForParentRange() {
		return this.deferredBind == null;
	}

	public final void shrinkToFit(IFramedBox containerBox, IntrinsicSizes sizes) {
		final double minLineAxis = sizes.minContent(), maxLineAxis = sizes.maxContent();
		double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();
		{
			double lineAxis;
			if (this.params.flow.isVertical()) {
				// Vertical writing
				lineAxis = cHeight;
			} else {
				// Horizontal writing
				lineAxis = cWidth;
			}

			//
			// ■ Calculate padding
			//
			LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineAxis);

			//
			// ■ Calculate margins
			//
			LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineAxis);
		}

		final Insets margin = this.frame.frame.margin;
		final AbsoluteInsets amargin = this.frame.margin;
		final AbsolutePos pos = this.getAbsolutePos();
		final WritingMode flow = this.params.flow;
		final boolean vertical = flow.isVertical();
		final double cLine = vertical ? cHeight : cWidth;
		//
		// ■ Calculate the line-axis size for absolute or fixed positioning (CSS2.1 10.3.7)
		//
		double size = LayoutUtils.computeDimensionLine(this.size, flow, cLine);
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX && !LayoutUtils.isNone(size)) {
			size -= this.frame.getBorderLineExtent(flow);
		}
		// Intrinsic sizing keywords (2026-08-29). fit-content without an argument is exactly
		// auto shrink-to-fit (CSS2.1 §10.3.7), so delegate it to AbsoluteSizing.
		// Resolve other values to lengths here and pass them as the specified width. For min/max,
		// approximate the fit-content upper bound as the containing block width minus the frame.
		final double availableLine = cLine - this.frame.getFrameLineExtent(flow);
		final net.zamasoft.foliojet.layout.box.params.IntrinsicSize intrinsic = this.params.intrinsicLine;
		if (intrinsic != null && (intrinsic.kind() != net.zamasoft.foliojet.layout.box.params.IntrinsicSize.Kind.FIT_CONTENT
				|| intrinsic.hasArgument())) {
			size = this.resolveIntrinsicLine(intrinsic, minLineAxis, maxLineAxis, availableLine, cLine);
		}
		double maxLine = LayoutUtils.computeDimensionLine(this.params.maxSize, flow, cLine);
		if (this.params.intrinsicMaxLine != null) {
			maxLine = this.resolveIntrinsicLine(this.params.intrinsicMaxLine, minLineAxis, maxLineAxis, availableLine,
					cLine);
		}
		double minLine = LayoutUtils.computeDimensionLine(this.minSize, flow, cLine);
		if (this.params.intrinsicMinLine != null) {
			minLine = this.resolveIntrinsicLine(this.params.intrinsicMinLine, minLineAxis, maxLineAxis, availableLine,
					cLine);
		}
		final AbsoluteSizing.Result result = AbsoluteSizing.resolve(new AbsoluteSizing.Input( //
				cLine, size, //
				maxLine, //
				minLine, //
				vertical ? LayoutUtils.computeInsetsTop(pos.location, cLine)
						: LayoutUtils.computeInsetsLeft(pos.location, cLine), //
				vertical ? LayoutUtils.computeInsetsBottom(pos.location, cLine)
						: LayoutUtils.computeInsetsRight(pos.location, cLine), //
				vertical ? amargin.top : amargin.left, //
				vertical ? amargin.bottom : amargin.right, //
				(vertical ? margin.getTopType() : margin.getLeftType()) == LengthType.AUTO, //
				(vertical ? margin.getBottomType() : margin.getRightType()) == LengthType.AUTO, //
				this.frame.getFrameLineExtent(flow), //
				minLineAxis, maxLineAxis));
		// Cross-axis (page-axis) margins: leave auto unresolved (NONE).
		final double crossStart = (vertical ? margin.getLeftType() : margin.getTopType()) == LengthType.AUTO
				? LayoutUtils.NONE
				: (vertical ? amargin.left : amargin.top);
		final double crossEnd = (vertical ? margin.getRightType() : margin.getBottomType()) == LengthType.AUTO
				? LayoutUtils.NONE
				: (vertical ? amargin.right : amargin.bottom);
		assert !LayoutUtils.isNone(result.insetStart());
		if (vertical) {
			this.offsetY = result.insetStart();
			this.frame.margin.top = result.marginStart();
			this.frame.margin.bottom = result.marginEnd();
			this.frame.margin.left = crossStart;
			this.frame.margin.right = crossEnd;
			this.height = result.size();
			this.width = 0;
		} else {
			this.offsetX = result.insetStart();
			this.frame.margin.left = result.marginStart();
			this.frame.margin.right = result.marginEnd();
			this.frame.margin.top = crossStart;
			this.frame.margin.bottom = crossEnd;
			this.width = result.size();
			this.height = 0;
		}
		this.resolveDefinitePageAxis(containerBox);
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	/**
	 * Binds the deferred body content. Called by {@link #finishLayoutSelf} and also
	 * <b>before finishLayout</b> by the footnote call scan at page finalization
	 * ({@code RootBuilder.scanFootnoteCalls}) (2026-09-02).
	 * The scan runs before the page's finishLayout, so otherwise calls inside absolutely
	 * positioned boxes were invisible and their notes were sent to the next page.
	 * Does nothing if already bound.
	 */
	public final void bindDeferredContent(final IFramedBox containerBox) {
		if (this.deferredBind != null) {
			// E-6 increment 4e: SegmentExecutor-driven bind from a sealed range.
			// sizes is a snapshot of simulated measurements (equivalent to the current intrinsicSizesMeasured();
			// see the DeferredBind Javadoc). The lease is released in the bind's finally
			// block.
			this.shrinkToFit(containerBox, this.orthogonalSizes(containerBox, this.deferredBind.sizes()));
			final BlockBuilder absoluteBuilder = new BlockBuilder(this.deferredBind.pageContext(), this);
			final RetainedTextLimit limit = RetainedTextLimit.get(absoluteBuilder);
			try (var retained = limit == null ? null
					: limit.enter(RetainedTextLimit.elementName(this.getParams(), "absolute"))) {
				this.deferredBind.bind(absoluteBuilder);
				absoluteBuilder.close();
			}
			this.deferredBind = null;
		}
	}

	/**
	 * The shrink-to-fit sizes with the actual line-axis extent of orthogonal content (2026-10-08). The simulated
	 * measurement counts a table inside a child of the other writing mode as 0, which shrank the box to its frame and
	 * left the table outside it. The sealed body is replayed into this box without being consumed
	 * ({@code DeferredBind.measureInto}), the orthogonal extent is read, and the contents are discarded before the
	 * real bind.
	 */
	private net.zamasoft.foliojet.layout.sizing.IntrinsicSizes orthogonalSizes(final IFramedBox containerBox,
			final net.zamasoft.foliojet.layout.sizing.IntrinsicSizes measured) {
		if (this.sizesWithoutOrthogonal == null || this.getColumnCount() > 1) {
			return measured;
		}
		this.shrinkToFit(containerBox, measured);
		final RetainedTextLimit limit = RetainedTextLimit.get(this.deferredBind.pageContext());
		try (var retained = limit == null ? null
				: limit.measurement(RetainedTextLimit.elementName(this.getParams(), "absolute"));
				net.zamasoft.foliojet.layout.fragment.ScratchReplayScope scope = new net.zamasoft.foliojet.layout.fragment.ScratchReplayScope()) {
			final BlockBuilder trial = new BlockBuilder(this.deferredBind.pageContext(), this);
			this.deferredBind.measureInto(trial);
			trial.close();
		}
		final double extent = this.orthogonalContentLineExtent();
		this.resetContentForRelayout();
		if (!(extent > 0)) {
			return measured;
		}
		final net.zamasoft.foliojet.layout.sizing.IntrinsicSizes base = this.sizesWithoutOrthogonal;
		return new net.zamasoft.foliojet.layout.sizing.IntrinsicSizes(Math.max(base.minContent(), extent),
				Math.max(base.maxContent(), extent), measured.minPage(), measured.columnInflated());
	}

	/**
	 * The inner page-axis size when it is independent of the contents (a specified size or positions
	 * at both ends; CSS2.1 10.6.4). {@link LayoutUtils#NONE} if undetermined (2026-10-04).
	 *
	 * <p>
	 * Percentage heights (widths in vertical writing) of replaced elements inside use this as their
	 * basis ({@code LayoutUtils}). The box's page-axis size is finalized after laying out its contents
	 * and is 0 during layout, so {@code height: 100%} images became 0 and were not drawn
	 * (images inside frames in a publishing cover template). Do not set the box size itself before
	 * layout (multi-column layout in vertical writing read that page-axis size and changed column placement).
	 * </p>
	 */
	private double definitePageAxis = LayoutUtils.NONE;

	/**
	 * Returns the inner page-axis size if it is independent of the contents,
	 * or {@link LayoutUtils#NONE} if undetermined.
	 */
	public final double getDefinitePageAxis() {
		return this.definitePageAxis;
	}

	/** Whether the inner page-axis size is independent of the contents. */
	public final boolean isPageAxisDefinite() {
		return !LayoutUtils.isNone(this.definitePageAxis);
	}

	private void resolveDefinitePageAxis(final IFramedBox containerBox) {
		final double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		final double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();
		final AbsolutePos pos = this.getAbsolutePos();
		final boolean vertical = this.params.flow.isVertical();
		final double size = vertical ? LayoutUtils.computeDimensionWidth(this.size, cWidth)
				: LayoutUtils.computeDimensionHeight(this.size, cHeight);
		final double start = vertical ? LayoutUtils.computeInsetsLeft(pos.location, cWidth)
				: LayoutUtils.computeInsetsTop(pos.location, cHeight);
		final double end = vertical ? LayoutUtils.computeInsetsRight(pos.location, cWidth)
				: LayoutUtils.computeInsetsBottom(pos.location, cHeight);
		if (LayoutUtils.isNone(size) && (LayoutUtils.isNone(start) || LayoutUtils.isNone(end))) {
			this.definitePageAxis = LayoutUtils.NONE;
			return;
		}
		double resolved = this.resolvePageAxis(containerBox, 0).size();
		if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
			resolved -= vertical ? this.frame.getBorderWidth() : this.frame.getBorderHeight();
		}
		this.definitePageAxis = Math.max(0, resolved);
	}

	/** Page-axis size and position (CSS2.1 10.6.4). {@code contentSize} is the actual content size. */
	private AbsoluteSizing.PageResult resolvePageAxis(final IFramedBox containerBox, final double contentSize) {
		final double cWidth = containerBox.getInnerWidth() + containerBox.getFrame().padding.getFrameWidth();
		final double cHeight = containerBox.getInnerHeight() + containerBox.getFrame().padding.getFrameHeight();
		final AbsolutePos pos = this.getAbsolutePos();
		final AbsoluteInsets margin = this.frame.margin;
		final AbsoluteInsets padding = this.frame.padding;
		final RectBorder border = this.frame.frame.border;
		final boolean vertical = this.params.flow.isVertical();
		final double cPage = vertical ? cWidth : cHeight;
		return AbsoluteSizing.resolvePage(new AbsoluteSizing.PageInput( //
				cPage, //
				vertical ? LayoutUtils.computeDimensionWidth(this.size, cWidth)
						: LayoutUtils.computeDimensionHeight(this.size, cHeight), //
				vertical ? LayoutUtils.computeDimensionWidth(this.params.maxSize, cWidth)
						: LayoutUtils.computeDimensionHeight(this.params.maxSize, cHeight), //
				vertical ? LayoutUtils.computeDimensionWidth(this.minSize, cWidth)
						: LayoutUtils.computeDimensionHeight(this.minSize, cHeight), //
				vertical ? LayoutUtils.computeInsetsLeft(pos.location, cWidth)
						: LayoutUtils.computeInsetsTop(pos.location, cHeight), //
				vertical ? LayoutUtils.computeInsetsRight(pos.location, cWidth)
						: LayoutUtils.computeInsetsBottom(pos.location, cHeight), //
				vertical ? margin.left : margin.top, //
				vertical ? margin.right : margin.bottom, //
				contentSize, //
				vertical ? border.getFrameWidth() + padding.getFrameWidth()
						: border.getFrameHeight() + padding.getFrameHeight()));
	}

	public final void finishLayoutSelf(final IFramedBox containerBox) {
		this.bindDeferredContent(containerBox);

		//
		// ■ Calculate the page-axis size for absolute or fixed positioning (CSS2.1 10.6.4)
		// Consolidated the physical vertical/horizontal mirror cases into AbsoluteSizing.resolvePage (faithful port).
		//
		final AbsoluteInsets margin = this.frame.margin;
		final boolean vertical = this.params.flow.isVertical();
		// Actual content size (faithfully preserves the old formula: equivalent to width in vertical writing).
		final AbsoluteSizing.PageResult result = this.resolvePageAxis(containerBox,
				vertical ? this.getWidth() - this.frame.getFrameWidth() : this.height);

		double size = result.size();
		assert !LayoutUtils.isNone(result.insetStart());
		assert !LayoutUtils.isNone(result.marginStart());
		assert !LayoutUtils.isNone(result.marginEnd());
		assert !LayoutUtils.isNone(size);
		if (vertical) {
			assert !LayoutUtils.isNone(margin.top);
			assert !LayoutUtils.isNone(margin.bottom);
			this.offsetX = result.insetStart();
			this.frame.margin.left = result.marginStart();
			this.frame.margin.right = result.marginEnd();
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				size -= this.frame.getBorderWidth();
			}
			this.width = size;
		} else {
			assert !LayoutUtils.isNone(margin.right);
			assert !LayoutUtils.isNone(margin.left);
			this.offsetY = result.insetStart();
			this.frame.margin.top = result.marginStart();
			this.frame.margin.bottom = result.marginEnd();
			if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
				size -= this.frame.getBorderHeight();
			}
			this.height = size;
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final boolean isContextBox() {
		return true;
	}

	/** Number of static-position fallbacks (defined behavior, but we want to know how often it occurs). */
	public static final java.util.concurrent.atomic.AtomicLong FALLBACK_COUNT =
			new java.util.concurrent.atomic.AtomicLong();

	/**
	 * <b>Falls back to the static position for an absolutely positioned box that has lost its containing block</b>
	 * (promoted from a workaround to defined behavior on 2026-08-06).
	 *
	 * <p>
	 * <b>This is behavior defined for this structure, not suppression of an "impossible state".</b>
	 * Streaming type-area generation does not keep containers of finalized pages alive.
	 * Final resolution of absolute positioning requires {@code containerBox.getInnerWidth()}
	 * (auto margins and percentages), so a box that has lost its containing block
	 * <b>no longer has the information needed for resolution</b>.
	 * A browser retaining a DOM can traverse the tree again; here there is no tree to traverse.
	 * </p>
	 *
	 * <p>
	 * CSS offers no guidance either. The specification does not answer "what is the containing block
	 * for absolute positioning when the containing block spans pages?", and browser print implementations
	 * differ. Thus, <b>there is no correct answer to match</b>; we must choose and document the behavior.
	 * </p>
	 *
	 * <p>
	 * <b>Chosen behavior</b>: treat unresolved margins and dimensions as 0 and place the box at its
	 * <b>static position</b>. This is not arbitrary zero filling; it follows CSS's answer for offset auto
	 * (the static position). Do not fail the conversion (an absolute requirement).
	 * </p>
	 *
	 * <p>
	 * Note that <b>this covers only one of the two cases</b>. A container omitted from traversal skips
	 * all {@code finishLayoutSelf} work, but only two kinds do actual work: resolving absolute positioning
	 * (here) and offsets for {@code position:relative}. The latter <b>does not need a containing block</b>,
	 * so there is no reason to rely on traversal. On 2026-08-06, it was changed to also finalize offsets
	 * immediately before drawing ({@code AbstractContainerBox.resolveRelativeOffset}).
	 * </p>
	 *
	 * <p>
	 * <b>Count each occurrence</b> ({@link #FALLBACK_COUNT}). Even for defined behavior, do not leave
	 * its frequency unknown. Measurements (2026-08-06) found only 16 occurrences in
	 * {@code github-readme} among 235 full-scale corpus documents.
	 * </p>
	 *
	 * <p>
	 * <b>Fixing the cause would still be better</b>: the mechanism that omits containers from traversal
	 * is unidentified. Measurements ruled out three hypotheses (continuation fragments, registration
	 * after traversal, and C1c absorption). Fixing it would stop static-position fallbacks.
	 * However, <b>retain this fallback after fixing it</b>; it is the last safeguard against stopping
	 * conversion if another path causes the same situation.
	 * </p>
	 */
	private void resolveUnfinishedMargins() {
		final AbsoluteInsets margin = this.frame.margin;
		if (LayoutUtils.isNone(margin.top) || LayoutUtils.isNone(margin.bottom) || LayoutUtils.isNone(margin.left)
				|| LayoutUtils.isNone(margin.right) || LayoutUtils.isNone(this.width)
				|| LayoutUtils.isNone(this.height)) {
			FALLBACK_COUNT.incrementAndGet();
		}
		if (LayoutUtils.isNone(margin.top)) {
			margin.top = 0;
		}
		if (LayoutUtils.isNone(margin.bottom)) {
			margin.bottom = 0;
		}
		if (LayoutUtils.isNone(margin.left)) {
			margin.left = 0;
		}
		if (LayoutUtils.isNone(margin.right)) {
			margin.right = 0;
		}
		if (LayoutUtils.isNone(this.width)) {
			this.width = 0;
		}
		if (LayoutUtils.isNone(this.height)) {
			this.height = 0;
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		this.resolveUnfinishedMargins();
		if (this.getAbsolutePos().fiducial != Fiducial.CONTEXT && !pageBox.isReplayPage()) {
			// position:fixed attaches to the viewport (= type area). Since scrolling cannot reach
			// outside the viewport, browsers do not draw beyond any of its four edges.
			// Without clipping, edges of off-canvas UI moved to negative coordinates (such as kanaloco.jp's
			// #site-menu drawer) appear in the paper margins (2026-08-09).
			// Do not apply this to flow content: print bleed, crop marks, and table
			// borders legitimately draw outside the type area (observed in imageTest's marks/border-collapse
			// groups).
			final java.awt.geom.Rectangle2D.Double icb = new java.awt.geom.Rectangle2D.Double(0, 0,
					pageBox.getWidth(), pageBox.getHeight());
			clip = clip == null ? icb : icb.createIntersection((java.awt.geom.Rectangle2D) clip);
		}
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
		final AbsolutePos pos = this.getAbsolutePos();
		return (state, container) -> new AbsoluteBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
