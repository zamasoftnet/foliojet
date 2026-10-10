package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.ParamsType;
import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.IntrinsicSize;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.draw.AbsoluteRectFrameDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.layout.util.DebugFlags;
import net.zamasoft.pdfg2d.gc.text.TextClip;

/**
 * A block box implementation.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractBlockBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public abstract class AbstractBlockBox extends AbstractContainerBox {

	protected final BlockParams params;

	public AbstractBlockBox(final BlockParams params) {
		super(params.getType() == ParamsType.TABLE ? Dimension.AUTO_DIMENSION : params.size,
				params.getType() == ParamsType.TABLE ? Dimension.ZERO_DIMENSION : params.minSize, new FlowContainer());
		this.params = params;
		final RectFrame frame;
		if (params.getType() == ParamsType.TABLE) {
			frame = RectFrame.NULL_FRAME;
		} else {
			frame = params.frame;
		}
		this.frame = new AbsoluteRectFrame(frame);
		assert this.params.fontStyle != null;
	}

	protected AbstractBlockBox(BlockParams params, Dimension size, Dimension minSize, AbsoluteRectFrame frame,
			Container container) {
		super(size, minSize, container);
		this.params = params;
		this.frame = frame;
		assert this.params.fontStyle != null;
	}

	public BoxType getType() {
		return BoxType.BLOCK;
	}

	public Params getParams() {
		return this.params;
	}

	public BlockParams getBlockParams() {
		return this.params;
	}

	/**
	 * Resolves intrinsic size keywords (2026-08-29) to a used inline size (content-box).
	 *
	 * <p>
	 * For border-box sizing, subtracts borders and padding from the {@code fit-content(L)}
	 * argument L before using it as the limit. max-content/min-content are actual content measurements,
	 * so {@code box-sizing} does not affect them (css-sizing-3 §4.1).
	 * </p>
	 *
	 * @param intrinsic   the keyword
	 * @param minContent  the min-content size
	 * @param maxContent  the max-content size
	 * @param available   the limit for argument-free fit-content (available size)
	 * @param percentBase the percentage reference for argument L
	 * @return the used size
	 */
	protected final double resolveIntrinsicLine(final IntrinsicSize intrinsic, final double minContent,
			final double maxContent, final double available, final double percentBase) {
		double bound = available;
		if (intrinsic.hasArgument()) {
			final double argument = LayoutUtils.computeLength(intrinsic.argument(), percentBase);
			if (!LayoutUtils.isNone(argument)) {
				bound = argument;
				if (this.params.boxSizing == BoxSizingMode.BORDER_BOX) {
					bound -= this.frame.getBorderLineExtent(this.params.flow);
				}
			}
		}
		return intrinsic.resolve(minContent, maxContent, bound);
	}

	public void firstPassLayout(AbstractContainerBox containerBox) {
		BlockParams containerParams = containerBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();

		//
		// ■ Calculate padding.
		//
		LayoutUtils.computePaddings(this.frame.padding, this.frame.frame.padding, lineSize);
		//
		// ■ Calculate margins.
		//
		LayoutUtils.computeMarginsAutoToZero(this.frame.margin, this.frame.frame.margin, lineSize);

		//
		// ■ Calculate width and height.
		//
		// width/min/max-width use the box-sizing scale. This determines content width,
		// so subtract borders + padding for border-box
		// (2026-08-29). This base implementation is also used for measurement passes with zero inline size.
		// Previously, no subtraction occurred, so pills with `min-width:100px; padding-inline:8px;
		// box-sizing:border-box` measured as 116px
		// (exposed by padding-inline support).
		final double borderBoxLine = params.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
				? this.frame.getBorderLineExtent(containerParams.flow)
				: 0;
		switch (containerParams.flow) {
		case WritingMode.TB:
			// Horizontal writing.
			this.width = LayoutUtils.computeDimensionWidth(this.size, lineSize);
			if (LayoutUtils.isNone(this.width)) {
				this.width = 0;
			} else {
				this.width = Math.max(0, this.width - borderBoxLine);
			}
			double maxWidth = LayoutUtils.computeDimensionWidth(params.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxWidth)) {
				this.width = Math.min(this.width, Math.max(0, maxWidth - borderBoxLine));
			}
			double minWidth = LayoutUtils.computeDimensionWidth(this.minSize, lineSize);
			if (!LayoutUtils.isNone(minWidth)) {
				this.width = Math.max(this.width, Math.max(0, minWidth - borderBoxLine));
			}
			break;
		case WritingMode.RL:
		case WritingMode.LR:
			// Vertical writing.
			this.height = LayoutUtils.computeDimensionHeight(this.size, lineSize);
			if (LayoutUtils.isNone(this.height)) {
				this.height = 0;
			} else {
				this.height = Math.max(0, this.height - borderBoxLine);
			}
			double maxHeight = LayoutUtils.computeDimensionWidth(params.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxHeight)) {
				this.height = Math.min(this.height, Math.max(0, maxHeight - borderBoxLine));
			}
			double minHeight = LayoutUtils.computeDimensionWidth(this.minSize, lineSize);
			if (!LayoutUtils.isNone(minHeight)) {
				this.height = Math.max(this.height, Math.max(0, minHeight - borderBoxLine));
			}
			break;
		default:
			throw new IllegalStateException();
		}
		assert !LayoutUtils.isNone(this.width);
		assert !LayoutUtils.isNone(this.height);
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		// Resolve relative-positioning offsets here too (the values do not use the containing block,
		// so any call produces the same result; see resolveRelativeOffset for the reason).
		if (this.getPos() instanceof net.zamasoft.foliojet.layout.box.params.AbstractStaticPos sp) {
			this.resolveRelativeOffset(sp.offset);
		}
		// SPEC CSS 2.1 9.9.1 #1
		x += this.offsetX;
		y += this.offsetY;

		transform = this.transform(transform, x, y);
		drawer.adoptTransform(this.params, transform);

		this.visitFrame(pageBox, drawer, clip, transform, x, y,
				this.textClipped() ? AbsoluteRectFrame.Part.DECORATIONS : AbsoluteRectFrame.Part.ALL);

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);
		this.container.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	/**
	 * Whether the background is clipped to the text ({@code background-clip: text}). Such a background is drawn at
	 * the start of the box's content, while the rest of the frame stays with the frames (2026-10-09): in PDF the
	 * clip shows the text, which is then the copy that text extraction finds, in the place of the box's text
	 * instead of ahead of all the text of the page. Only what overlaps the text from the box's descendant blocks
	 * or later siblings comes out in a different order from CSS's.
	 */
	private boolean textClipped() {
		return this.getBlockParams().frame.background.getBackgroundClip() == Background.TEXT;
	}

	private void visitFrame(final PageBox pageBox, final Drawer drawer, final Shape clip,
			final AffineTransform transform, final double x, final double y, final AbsoluteRectFrame.Part part) {
		final RectFrame f = this.frame.frame;
		final boolean visible = switch (part) {
		case ALL -> f.isVisible();
		case DECORATIONS -> f.border.isVisible() || f.shadows != null || f.outline != null;
		case BACKGROUND -> f.background.isVisible();
		};
		if (this.params.opacity == 0f || !visible) {
			return;
		}
		// clip-path also clips the box's own background and border (unlike overflow).
		final Shape frameClip = this.clipWithClipPath(clip, x, y);
		final AbsoluteRectFrameDrawable frameDrawable = switch (part) {
		case ALL -> new AbsoluteRectFrameDrawable(pageBox, frameClip, this.params.opacity, transform, this.frame,
				this.getWidth(), this.getHeight(), null);
		case DECORATIONS -> new AbsoluteRectFrameDrawable.WithoutBackground(pageBox, frameClip, this.params.opacity,
				transform, this.frame, this.getWidth(), this.getHeight());
		case BACKGROUND -> new AbsoluteRectFrameDrawable.BackgroundOnly(pageBox, frameClip, this.params.opacity,
				transform, this.frame, this.getWidth(), this.getHeight(), this.textClip(pageBox, x, y));
		};
		final Drawable drawable = frameDrawable.withBlendMode(this.params.blendMode).withFilter(this.params.filter);
		drawer.visitDrawable(drawable, x, y);
	}
	

	/**
	 * The text of this box's content for {@code background-clip: text}, in the space the frame is drawn in
	 * ({@code x}, {@code y}: the frame's origin, as in {@link #pushFramesSteps}). The walk starts inside this box,
	 * so the box's own transform, which the frame drawable applies, is not applied again (2026-10-09).
	 */
	private TextClip textClip(final PageBox pageBox, final double x, final double y) {
		final TextClip textClip = new TextClip();
		final TextShapeSink sink = TextShapeSink.backgroundClip(pageBox, textClip);
		final Deque<TextShapeStep> worklist = new java.util.ArrayDeque<>();
		this.container.pushTextShapeSteps(pageBox, sink, new AffineTransform(),
				this.blockAlignedX(x + this.frame.getFrameLeft()), this.blockAlignedY(y + this.frame.getFrameTop()),
				worklist);
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
		return textClip;
	}

	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, final Deque<DrawStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);

		transform = this.transform(transform, x, y);
		drawer.adoptTransform(this.params, transform);

		visitor.visitBox(transform, this, drawer, x, y);

		if (this.textClipped()) {
			this.visitFrame(pageBox, drawer, clip, transform, x, y, AbsoluteRectFrame.Part.BACKGROUND);
		}

		clip = this.clip(clip, x, y);

		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		assert !LayoutUtils.isNone(x);
		assert !LayoutUtils.isNone(y);
		final double contentBoxX = x;
		final double contentBoxY = y;
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);

		final boolean contextBox = this.isContextBox();
		if (contextBox) {
			contextX = contentBoxX - this.frame.padding.left;
			contextY = contentBoxY - this.frame.padding.top;
		}

		// Tagged PDF: open a structure element for a mappable HTML block so the
		// content drawn inside attaches to it. Zero-size markers, no-op when
		// untagged or non-PDF; deduplicated per element by PageBox.
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);

		final Shape absolutesClip = contextBox ? clip : null;
		final double fx = x, fy = y;
		// Push onto the stack in reverse order to preserve the original execution order
		// (floatings→flows→absolutes→endStruct).
		worklist.push(w -> pageBox.endStruct(drawer, this.params.element, structCount, fx, fy));
		this.container.pushDrawAbsolutes(pageBox, drawer, visitor, absolutesClip, transform, contextX, contextY,
				contentBoxX, contentBoxY, worklist);
		this.container.pushDrawFlows(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y, worklist);
		this.container.pushDrawFloatings(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
	}

	/**
	 * Returns a reconstruction recipe for a fragment box (C1d-B). Implementations must capture
	 * the required values without retaining a reference to this (see the FragmentRecipe contract).
	 * Obtain it before splitPageState invalidates the anchor.
	 */
	public abstract net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe();

	protected final AbstractContainerBox splitPage(final Container container, final double pageLimit,
			final boolean columnSpanning) {
		return this.splitPage(container, pageLimit, pageLimit, columnSpanning);
	}

	@Override
	protected final AbstractContainerBox splitPage(final Container container, final double contentLimit,
			final double ownerExtent, final boolean columnSpanning) {
		final boolean vertical = this.params.flow.isVertical();
		final double crossExtent = vertical ? this.height : this.width;
		final net.zamasoft.foliojet.layout.fragment.FragmentState state = this.splitPageState(contentLimit,
				ownerExtent, columnSpanning, this.shouldPreserveSpecifiedPageSize(container));
		return this.continueFragment(state, container, crossExtent);
	}

	/**
	 * Cuts a chain member into a continuation (C1d-C). When the plan selects this box
	 * and the break cuts through its interior, returns a ContinuationFrame in {@link SplitResult.Frame}
	 * without constructing a fragment box (the parent propagates it outward via return values).
	 * KEEP/MOVE remain unchanged. Never returns Split(box).
	 */
	public final net.zamasoft.foliojet.layout.fragment.SplitResult splitForContinuation(double pageLimit,
			final net.zamasoft.foliojet.layout.box.content.BreakMode mode, final byte flags,
			final net.zamasoft.foliojet.layout.fragment.BreakPlan plan) {
		assert plan.selects(this);
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final net.zamasoft.foliojet.layout.box.content.BreakMode xmode = net.zamasoft.foliojet.layout.box.content.BreakMode
				.absorbColumn(mode, this.getColumnCount());
		final double intrusion = pageEndIntrusion(mode);
		final net.zamasoft.foliojet.layout.fragment.ContainerCut cut = this.container.splitPageAxis(pageLimit, xmode,
				flags, plan.next());
		final Container nextContainer;
		final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame childFrame;
		if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.PlainWithChainStop(
				final Container chainStopContainer, final net.zamasoft.foliojet.layout.fragment.ChainStopReason reason)) {
			// If chainStopContainer actually retains flows/floats
			// (always possible for MOVE, and also for KEEP if sibling floats overflow:
			// the force-branch splitFloatings(pageLimit, flags,
			// index) examines sibling floats for both KEEP and MOVE),
			// discarding it loses content. Return the reason as bare KEEP/MOVE
			// only when the container is empty; otherwise, route actual content
			// through the common Frame construction below (tail is always OpenTailShape:
			// both MOVE and KEEP represent the same still-open continuation. The dedicated
			// MovedOpen type was removed on 2026-07-22;
			// see the development log
			// for reference). For details,
			// see the development log
			// for reference.
			final boolean hasContent = chainStopContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
					&& (fc.hasFlows() || fc.hasFloatings());
			if (!hasContent) {
				return reason == net.zamasoft.foliojet.layout.fragment.ChainStopReason.KEEP
						? net.zamasoft.foliojet.layout.fragment.SplitResult.KEEP
						: net.zamasoft.foliojet.layout.fragment.SplitResult.MOVE;
			}
			nextContainer = chainStopContainer;
			childFrame = null;
		} else if (cut instanceof net.zamasoft.foliojet.layout.fragment.ContainerCut.WithFrame(final Container c,
				final net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame f)) {
			nextContainer = c;
			childFrame = f;
		} else {
			nextContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) cut).container();
			childFrame = null;
		}
		if (nextContainer == null) {
			return net.zamasoft.foliojet.layout.fragment.SplitResult.KEEP;
		}
		if (nextContainer == this.splitMoveSentinel()) {
			// A multi-column container delegates cutting to the last column, so the MOVE sentinel
			// is the last column (see splitMoveSentinel). Comparing only with this.container
			// misread that last column as a remainder, rebuilding it as a continuation fragment
			// **while it still remained inside the multi-column container**
			// (2026-07-28, local/shrink/strict-739-min.html).
			assert childFrame == null;
			return net.zamasoft.foliojet.layout.fragment.SplitResult.MOVE;
		}
		// An interior cut becomes a continuation. Obtain the recipe before splitPageState invalidates
		// the anchor (C1d-B). pageBreak absorbs prefixItems after calculating the watermark.
		final boolean vertical = this.getBlockParams().flow.isVertical();
		final double crossExtent = vertical ? this.getInnerHeight() : this.getInnerWidth();
		final net.zamasoft.foliojet.layout.fragment.FragmentRecipe recipe = this.fragmentRecipe();
		final double kept = keptPastCut(mode, intrusion);
		final net.zamasoft.foliojet.layout.fragment.FragmentState state = this.splitPageState(
				plan.contentLimit(this, pageLimit) + kept, pageLimit + kept,
				mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ColumnBreakMode,
				this.shouldPreserveSpecifiedPageSize(nextContainer));
		final net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail tail = childFrame != null
				? new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.Child(childFrame)
				: new net.zamasoft.foliojet.layout.fragment.Continuation.OpenTail.OpenTailShape(
						net.zamasoft.foliojet.layout.fragment.OpenShape.of(plan.openTailDepth()));
		return new net.zamasoft.foliojet.layout.fragment.SplitResult.Frame(
				new net.zamasoft.foliojet.layout.fragment.Continuation.ContinuationFrame(recipe, state, nextContainer,
						crossExtent, java.util.List.of(), tail));
	}

	/**
	 * A page-axis cut exclusively for block floats (2026-07-24, exclusion area A-3a).
	 * A float variant of {@link AbstractContainerBox#split}: for an interior cut (Split),
	 * returns the ingredients ({@link net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment})
	 * without immediately constructing the remainder box ({@code splitPage}). The remainder box
	 * is constructed only once, when attached to the receiving {@code Floatings}.
	 *
	 * <p>
	 * Obtains the ingredients exactly as the immediate path does: the container's {@code splitPageAxis}
	 * decides the cut; mutation of the preceding fragment and fragment state come from the actual
	 * output of {@code splitPageState} (no recalculation); crossExtent is the raw size before mutation
	 * (as in {@code splitPage}). Obtains the recipe before {@code splitPageState}, following the same
	 * C1d-B contract as {@code splitForContinuation}. The immediate path obtains it after mutation,
	 * but FloatBlockBox's recipe captures only immutable params/pos, so the two are equivalent.
	 * </p>
	 *
	 * @param serial the float identifier managed by the caller ({@code Floatings})
	 */
	public net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit splitFloatFragment(final int serial,
			double pageLimit, final net.zamasoft.foliojet.layout.box.content.BreakMode mode, final byte flags) {
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final net.zamasoft.foliojet.layout.box.content.BreakMode xmode = net.zamasoft.foliojet.layout.box.content.BreakMode
				.absorbColumn(mode, this.getColumnCount());
		// Cuts without a plan always use Plain (legacy contract: null=KEEP/sentinel=MOVE/other=remainder).
		// The old three-argument splitPageAxis wrapped this Plain mapping (unified in increment 5).
		final Container nextContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) this.container
				.splitPageAxis(pageLimit, xmode, flags, null)).container();
		if (DebugFlags.FLOAT_TRACE) {
			final String kind = nextContainer == null ? "KEEP"
					: (nextContainer == this.splitMoveSentinel() ? "MOVE" : "SPLIT");
			System.err.println("[float-split] " + kind + " el=" + this.params.element + " innerLimit=" + pageLimit
					+ " flags=" + flags);
		}
		if (nextContainer == null) {
			return net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.KEEP;
		}
		if (nextContainer == this.splitMoveSentinel()) {
			return net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.MOVE;
		}
		final boolean vertical = this.params.flow.isVertical();
		final double crossExtent = vertical ? this.height : this.width;
		final net.zamasoft.foliojet.layout.fragment.FragmentRecipe recipe = this.fragmentRecipe();
		final net.zamasoft.foliojet.layout.fragment.FragmentState state = this.splitPageState(pageLimit,
				mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.ColumnBreakMode,
				this.shouldPreserveSpecifiedPageSize(nextContainer));
		return new net.zamasoft.foliojet.layout.fragment.FloatFragmentSplit.Prepared(
				new net.zamasoft.foliojet.layout.fragment.PreparedFloatFragment(serial, recipe, state, nextContainer,
						crossExtent));
	}

	/**
	 * Finalizes the preceding fragment of a page-axis cut and returns the continuation fragment's state (C1a).
	 * This is the first half of the former unified splitPage operation (which also constructed
	 * the fragment box): trims this box to page usage and removes the end-side frame.
	 * {@link #continueFragment} constructs the continuation fragment, at resume time if necessary.
	 */
	public final net.zamasoft.foliojet.layout.fragment.FragmentState splitPageState(final double pageLimit,
			final boolean columnSpanning) {
		return this.splitPageState(pageLimit, columnSpanning, false);
	}

	/** Separates the check for whether any content was taken from the owner's fragment dimensions. */
	public final net.zamasoft.foliojet.layout.fragment.FragmentState splitPageState(final double contentLimit,
			final double ownerExtent, final boolean columnSpanning) {
		return this.splitPageState(contentLimit, ownerExtent, columnSpanning, false);
	}

	/**
	 * The page-axis size a continuation fragment took from what its split left of a definite size or min-size, or NaN
	 * (2026-10-10, set by {@code AbstractStaticBlockBox.continuePageAxis}). Its content may grow it beyond.
	 */
	protected double continuedPageAxis = Double.NaN;

	private net.zamasoft.foliojet.layout.fragment.FragmentState splitPageState(final double pageLimit,
			final boolean columnSpanning, final boolean preserveSpecifiedPageSize) {
		return this.splitPageState(pageLimit, pageLimit, columnSpanning, preserveSpecifiedPageSize);
	}

	private net.zamasoft.foliojet.layout.fragment.FragmentState splitPageState(final double contentLimit,
			final double ownerExtent, final boolean columnSpanning, final boolean preserveSpecifiedPageSize) {
		// Fragments of a split box are continuations (frame cutting and content consumption have advanced),
		// so do not replay them fresh from source. SourceAnchor belongs to each individual
		// box (P0), and recipe-built fragments have no anchor from the outset,
		// making the old params.sourceEventId=-1 invalidation unnecessary.
		// (The risk of replay rewinding split progress and causing infinite page breaks
		// is structurally prevented because fragments do not inherit anchors.)
		//
		// 2026-07-28: the structural prevention above **applies only to the trailing half**.
		// The preceding fragment (this) keeps its anchor, so source replay rebuilds
		// the entire element, including the remainder held by the continuation fragment,
		// duplicating content when the continuation resumes. Normally, the preceding fragment
		// stays on the previous page/column and never resumes, hiding the issue. However,
		// in nested multi-column balancing, `ColumnsContainer.restyle` rebuilds all columns
		// as one stack, placing the preceding and continuation fragments **in the same remainder**.
		// A column break there treats the preceding fragment as a closed subtree moving wholly,
		// so `stampRanges`→`replayFromSource` replays the entire element
		// (measured: `<li>` drawn twice on the same page in
		// local/shrink/strict-347-min.html). Block this on the side that was cut.
		this.markFragmented();
		final boolean vertical = this.params.flow.isVertical();
		// A page-axis min-size with a percentage (or a calc() with one) splits as the length it resolved to (2026-10-10):
		// FragmentState reads the length of the Dimension, which holds the ratio for a percentage, so the rest of a
		// float's min-height: 60% came out 0 on the next page.
		net.zamasoft.foliojet.layout.box.params.Dimension minSize = this.minSize;
		final net.zamasoft.foliojet.layout.box.params.LengthType minType = minSize.getPageType(this.params.flow);
		if (minType == net.zamasoft.foliojet.layout.box.params.LengthType.RELATIVE
				|| minType == net.zamasoft.foliojet.layout.box.params.LengthType.MIXED) {
			final double resolved = this.minPageAxis + (this.params.boxSizing == BoxSizingMode.BORDER_BOX
					? this.frame.getBorderPageExtent(this.params.flow)
					: 0);
			minSize = vertical
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(resolved, 0, minSize.getHeight(),
							minSize.getHeightRatio(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE,
							minSize.getHeightType())
					: net.zamasoft.foliojet.layout.box.params.Dimension.create(minSize.getWidth(),
							minSize.getWidthRatio(), resolved, 0, minSize.getWidthType(),
							net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE);
		}
		// The rest of a definite size that a float's continuation keeps is a floor its content may outgrow
		// (continuePageAxis): the next split goes on with what is left of that floor, not of the content (2026-10-10,
		// sweep seed 11599998: a float of width: 0pt in vertical writing grew to its content, 538pt, and each page
		// carried the rest of that on as a definite width, 732 pages).
		final double extent = vertical ? this.width : this.height;
		net.zamasoft.foliojet.layout.fragment.FragmentState state = net.zamasoft.foliojet.layout.fragment.FragmentState
				.of(this.params.flow, columnSpanning, this.frame, this.size,
						minSize, Double.isNaN(this.continuedPageAxis) ? extent : Math.min(extent, this.continuedPageAxis),
						contentLimit, ownerExtent, this.container.getContentSize(), this.isSpecifiedPageSize(),
						preserveSpecifiedPageSize);
		if (!columnSpanning && !this.isSpecifiedPageSize() && !this.params.flow.isVertical()
				&& !(this instanceof net.zamasoft.foliojet.layout.box.PageAtomicBox)
				&& this.params.maxSize.getPageType(this.params.flow) != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
				&& this.maxPageAxis < Double.MAX_VALUE) {
			// What the max-size leaves goes on with the continuation (2026-10-10, FragmentState#withMaxPageExtent). Held
			// by its max-size, the box itself ends at it; ending with its end frame (not counting its margin) before the
			// cut, it keeps that frame. Only a max-size of its own (column balancing and aspect-ratio set the maximum
			// too), and horizontal writing only: an opposite-progression region checks its floats by the box, not by
			// what they paint, so capping a vertical continuation could hide the content still to go. Not a flex or
			// grid container either: their restyle sets the size from their items again, and a capped grid left its
			// overflowing rows over what follows (radix-ui's code block).
			if (LayoutUtils.compare(extent, this.maxPageAxis) >= 0) {
				final double end = this.frame.getFrameBottom() - this.frame.margin.bottom;
				state = LayoutUtils.compare(extent + end, state.prevPageExtent()) <= 0
						? state.withBoxEndingBefore(this.frame, this.params.flow, extent, this.maxPageAxis)
						: state.withMaxPageExtent(Math.min(extent, state.prevPageExtent()), this.maxPageAxis);
			} else {
				state = state.withMaxPageExtent(state.prevPageExtent(), this.maxPageAxis);
			}
		}
		if (vertical) {
			this.width = state.prevPageExtent();
		} else {
			this.height = state.prevPageExtent();
		}
		this.frame = state.prevFrame();
		return state;
	}

	/**
	 * Whether this is a fixed-size box whose forced split took no actual content, moving all content to the next fragment.
	 * Does not duplicate backgrounds, borders, or space actually consumed.
	 */
	private boolean shouldPreserveSpecifiedPageSize(final Container nextContainer) {
		final boolean specified = this.isSpecifiedPageSize();
		final boolean frameVisible = this.frame.isVisible();
		final boolean previousContent = this.container.hasNonDecorationContent();
		final double consumed = this.container.getConsumedPageSizeForFragmentation();
		final boolean nextContent = nextContainer.hasNonDecorationContent();
		final boolean preserve = specified && !frameVisible && !previousContent
				&& LayoutUtils.compare(consumed, 0) <= 0 && nextContent;
		if (!preserve) {
			return false;
		}
		// Inline blocks sit inside line boxes; a check traversing only normal-flow BLOCKs
		// cannot reach replaced images. Use the fragment boundary itself as the condition:
		// the preceding fragment has zero content and position, while the continuation has actual content.
		return true;
	}

	/**
	 * Constructs a continuation fragment box from fragment state (C1a).
	 *
	 * @param state       fragment state (returned by {@link #splitPageState})
	 * @param container   the continuation fragment's content
	 * @param crossExtent the cross-axis (inline) size at the cut
	 * @return the continuation fragment
	 */
	public final AbstractBlockBox continueFragment(final net.zamasoft.foliojet.layout.fragment.FragmentState state,
			final Container container, final double crossExtent) {
		return continueFragment(this.fragmentRecipe(), state, container, crossExtent);
	}

	/**
	 * Constructs a continuation fragment box from a recipe (C1d-B; resume uses this
	 * to consume a ContinuationFrame, without a virtual call on the old box).
	 */
	public static AbstractBlockBox continueFragment(final net.zamasoft.foliojet.layout.fragment.FragmentRecipe recipe,
			final net.zamasoft.foliojet.layout.fragment.FragmentState state, final Container container,
			final double crossExtent) {
		final AbstractBlockBox nextBlock = recipe.instantiate(state, container);
		if (state != null && state.nextMaxPageExtent() < Double.MAX_VALUE
				&& nextBlock instanceof AbstractStaticBlockBox staticBlock) {
			staticBlock.limitContinuationPageAxis(state.nextMaxPageExtent());
		}
		if (nextBlock.params.flow.isVertical()) {
			nextBlock.height = crossExtent;
		} else {
			nextBlock.width = crossExtent;
		}
		return nextBlock;
	}
}
