package net.zamasoft.foliojet.layout.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.ColumnsContainer;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.builder.impl.ColumnBuilder;
import net.zamasoft.foliojet.layout.fragment.BreakPlan;
import net.zamasoft.foliojet.layout.fragment.ColumnBalancer;
import net.zamasoft.foliojet.layout.fragment.ColumnCutResult;
import net.zamasoft.foliojet.layout.fragment.ContainerCut;
import net.zamasoft.foliojet.layout.fragment.Continuation;
import net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException;
import net.zamasoft.foliojet.layout.fragment.PreparedColumnCut;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * A box that can contain normal flow.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractContainerBox.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public abstract class AbstractContainerBox extends AbstractBox
		implements IPageBreakableBox, INonReplacedBox, IFramedBox {

	protected Dimension size, minSize;

	protected AbsoluteRectFrame frame;

	protected double width = 0;
	protected double height = 0;
	protected double minPageAxis = 0, maxPageAxis = Double.MAX_VALUE;
	protected double offsetX = 0, offsetY = 0;

	/**
	 * <b>Flex/Grid main-axis placement (line/track start position)</b> (added on 2026-08-06).
	 *
	 * <p>
	 * {@code offsetX}/{@code offsetY} were originally reserved for {@code position:relative} offsets,
	 * but {@code FlexItemBox.setFlexLineOffset}/{@code GridItemBox.setGridLineOffset} wrote directly
	 * to the same fields. Flex/Grid items have no normal-flow cursor position, so reusing
	 * {@code offsetX} was convenient. If {@link #resolveRelativeOffset} <b>assigns</b>
	 * {@code position:relative} offsets there, the entire placement computed by Flex/Grid disappears
	 * (observed: search buttons swapped left/right and icons gathered at the origin).
	 * Keep Flex/Grid placement in {@code baseOffsetX}/{@code baseOffsetY}, and make
	 * {@code resolveRelativeOffset} <b>add</b> its offsets on top, allowing both to coexist.
	 * </p>
	 */
	protected double baseOffsetX = 0, baseOffsetY = 0;

	/**
	 * <b>Resolves relative-positioning offsets</b> (added on 2026-08-06).
	 *
	 * <p>
	 * This calculation <b>does not need the containing block at all</b>: {@code LayoutUtils.computeOffsetX/Y}
	 * does not use its container argument, simply returning the value for absolute lengths and zero
	 * for percentages or auto (percentages remain unimplemented, an existing TODO). Thus,
	 * <b>the result is the same whenever called</b>. It converges to the same result relative to
	 * {@link #baseOffsetX}/{@link #baseOffsetY} each time, so repeated calls are safe.
	 * </p>
	 *
	 * <p>
	 * <b>Why not leave this to the sizing traversal?</b> In streaming, containers on finalized pages
	 * can leave that traversal, so their boxes never pass through {@code finishLayoutSelf}.
	 * Offsets have no sentinel value, so <b>they silently remain zero and go unnoticed</b>
	 * (measured: {@code top:20pt} had no effect in github-readme). Values that do not need
	 * a containing block need not depend on traversal; resolve them immediately before drawing too.
	 * </p>
	 */
	protected final void resolveRelativeOffset(final net.zamasoft.foliojet.layout.box.params.Offset offset) {
		if (offset == null) {
			return;
		}
		this.offsetX = this.baseOffsetX + net.zamasoft.foliojet.layout.util.LayoutUtils.computeOffsetX(offset, this);
		this.offsetY = this.baseOffsetY + net.zamasoft.foliojet.layout.util.LayoutUtils.computeOffsetY(offset, this);
	}

	protected Container container;

	protected AbstractContainerBox(Dimension size, Dimension minSize, Container container) {
		assert size != null;
		this.size = size;
		this.minSize = minSize;
		this.container = container;
		container.setBox(this);
	}

	public final Container getContainer() {
		return this.container;
	}

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		this.container.forEachAssignmentChild(action);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Boxes with visible borders/backgrounds draw across the whole box. Invisible boxes draw
	 * only <b>as far as their contents draw</b>, or <b>zero</b> if contents draw nothing
	 * (unused specified size and trailing space after content draw nothing). If the box's writing
	 * direction differs from the caller's, the page axes do not match, so conservatively
	 * returns the geometric size unchanged.
	 * </p>
	 *
	 * <p>
	 * When contents draw beyond the box, returns <b>the full overflow extent</b> even for boxes
	 * with visible borders/backgrounds (2026-10-02). Previously, visible boxes were capped
	 * at their own size, so placement checks ({@code FloatMeasurement.occupiedPageExtent})
	 * ignored overflowing content of bordered floats with explicit page-axis sizes
	 * ({@code float:right;height:96pt;border:1pt solid}). Content was placed off the paper
	 * without splitting. Invisible boxes already used this value.
	 * </p>
	 */
	@Override
	public double paintedPageExtent(final WritingMode flow) {
		final double full = this.getPageExtent(flow);
		final BlockParams params = this.getBlockParams();
		if (params.flow != flow) {
			return full;
		}
		final boolean framed = this.frame.isVisible();
		final double inner = this.container.paintedPageEnd();
		if (LayoutUtils.compare(inner, 0) <= 0) {
			// Contents draw nothing: draw only borders/backgrounds (nothing if they are invisible).
			return framed ? full : 0;
		}
		final double painted = this.frame.getFramePageStart(flow) + inner;
		if (params.clipsOverflowPaint()) {
			return framed ? full : Math.min(full, painted);
		}
		return framed ? Math.max(full, painted) : painted;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Boxes with borders/backgrounds draw. Otherwise, the answer is <b>whether their contents draw</b>
	 * (unused specified size and trailing space after content draw nothing). Zero opacity means
	 * no drawing, but deliberately ignores it to stay conservative.
	 * </p>
	 */
	@Override
	public boolean paintsAnything() {
		return this.frame.isVisible() || this.container.paintsAnything();
	}

	/**
	 * Returns the container box parameters.
	 *
	 * @return
	 */
	public abstract BlockParams getBlockParams();

	/**
	 * Expands along the page axis.
	 *
	 * @param newSize
	 */

	public void setPageAxis(final double newSize) {
		final BlockParams params = this.getBlockParams();
		if (params.flow.isVertical()) {
			// Vertical writing.
			if (newSize <= this.width) {
				return;
			}
			this.width = Math.max(this.minPageAxis, newSize);
			this.width = Math.min(this.maxPageAxis, this.width);
		} else {
			// Horizontal writing.
			if (newSize <= this.height) {
				return;
			}
			this.height = Math.max(this.minPageAxis, newSize);
			this.height = Math.min(this.maxPageAxis, this.height);
		}
	}

	/**
	 * Returns true if the page-axis size is explicitly specified.
	 *
	 * @return
	 */
	public abstract boolean isSpecifiedPageSize();

	/**
	 * Returns true for a box that serves as the reference for absolute positioning.
	 *
	 * @return
	 */
	public abstract boolean isContextBox();

	/**
	 * Draws frames (made iterative on 2026-07-20, for the same reason as draw).
	 * Replaced recursion with iterative DFS using an explicit {@link Deque} worklist
	 * to avoid StackOverflowError in deep nesting.
	 *
	 * @param pageBox
	 *            TODO
	 * @param drawer
	 * @param clip
	 * @param transform
	 *            TODO
	 * @param x
	 * @param y
	 */
	public final void frames(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y) {
		final java.util.Deque<FramesStep> worklist = new java.util.ArrayDeque<>();
		worklist.push(AbstractContainerBox.framesStep(this, pageBox, drawer, clip, transform, x, y));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/** Creates one {@link FramesStep} that executes {@code box}'s {@link #pushFramesSteps}. */
	public static FramesStep framesStep(final AbstractContainerBox box, final PageBox pageBox, final Drawer drawer,
			final Shape clip, final AffineTransform transform, final double x, final double y) {
		return worklist -> box.pushFramesSteps(pageBox, drawer, clip, transform, x, y, worklist);
	}

	/**
	 * Pushes frame-drawing steps for this box and its descendants onto {@code worklist}.
	 * Follow the same convention as {@link IBox#pushDrawSteps}: push in **reverse order**
	 * to preserve the original traversal order.
	 */
	public abstract void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform,
			double x, double y, java.util.Deque<FramesStep> worklist);

	/**
	 * Returns the inline size.
	 *
	 * @return
	 */
	public final double getLineSize() {
		final BlockParams params = this.getBlockParams();
		double lineSize = LayoutUtils.getMaxAdvance(this);
		final int columnCount = this.getColumnCount();
		if (columnCount >= 2) {
			// Multi-column layout. **Never go below zero** (css-multicol requires nonnegative
			// used column-width). In nested multi-column layout with gaps wider than the container,
			// a negative inline size makes line layout run backward and places content off the paper
			// (2026-08-21, sweep seed 615921).
			lineSize = Math.max(0, (lineSize + params.columns.gap) / columnCount - params.columns.gap);
		}
		return lineSize;
	}

	public int getColumnCount() {
		return 1;
	}

	/** Returns true if the inline size is AUTO (content-dependent) (M2c). */
	public final boolean isAutoLineSize() {
		return this.size.getLineType(this.getBlockParams().flow) == LengthType.AUTO;
	}

	public final boolean canColumnBreak() {
		final int columnCount = this.getColumnCount();
		if (columnCount < 2) {
			return false;
		}
		if (this.isSpecifiedPageSize()) {
			return true;
		}
		return this.getActualColumnCount() < columnCount;
	}

	public int getActualColumnCount() {
		if (this.container instanceof ColumnsContainer columns) {
			return columns.getColumnCount();
		}
		return 1;
	}

	public final boolean isFixedMulticolumn() {
		if (this.getColumnCount() <= 1) {
			return false;
		}
		BlockParams params = this.getBlockParams();
		if (params.flow.isVertical()) {
			if (this.size.getWidthType() == LengthType.AUTO) {
				return false;
			}
		} else {
			if (this.size.getHeightType() == LengthType.AUTO) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Discards laid-out contents, leaving the box empty again (2026-10-05). When shrink-to-fit sizes
	 * are remeasured by actual layout ({@code DocumentBuilder}), clears contents added by measurement replay
	 * before actual layout. Replaces the container with a fresh FlowContainer, as in {@link #balance}.
	 */
	public final void resetContentForRelayout() {
		this.container = new FlowContainer();
		this.container.setBox(this);
	}

	/** The inline extent of orthogonal descendants in laid-out contents ({@link FlowContainer#orthogonalLineExtent}). */
	public final double orthogonalContentLineExtent() {
		return this.container instanceof FlowContainer flows ? flows.orthogonalLineExtent(this.getBlockParams().flow)
				: 0;
	}

	public final void balance(final BlockBuilder builder) {
		final Container oldCont = this.container;

		final int acc = this.getActualColumnCount();
		final int columnCount = this.getColumnCount();
		final boolean vertical = this.getBlockParams().flow.isVertical();
		double pageSize;
		if (acc >= 2) {
			// If already built across multiple columns, the original single stack is lost,
			// so use the old equal division (one-shot total/column-count estimate).
			final double total = oldCont.getContentSize() + (vertical ? this.width : this.height) * (acc - 1);
			pageSize = oldCont.getCutPoint(total / columnCount);
		} else {
			// Simulate actual cuts at the single stack's rounded-down boundaries and search
			// for the minimum capacity that fits all columns (M5-B).
			pageSize = ColumnBalancer.balance(oldCont::getCutPointBelow, oldCont.getContentSize(), columnCount);
		}
		// Atomic children on the same axis with reverse progression cannot split at column boundaries.
		// Prefer the floor over ColumnBalancer's approximation that assumes progress to the proposed
		// position when no boundary exists (see Container.balancePageSizeFloor; 2026-08-22).
		pageSize = Math.max(pageSize, oldCont.balancePageSizeFloor());

		// 2026-07-25 (withdrawal of exclusion area P2): removed all balance probes (M6c-2 through
		// M6c-5) that rebuilt all contents in an isolated session in addition to the capacity calculation above.
		// They solved the same question (minimum capacity fitting the columns) twice, not justifying
		// the cost of 20 expensive trials and the isolation machinery (dedicated builder/PageGenerator/
		// execution-pass ThreadLocal): unanimous agreement of three independent reviewers + user's decision.
		// Height alignment becomes less precise because ColumnBalancer gets only one attempt,
		// but some unevenness with floats is acceptable (multi-column × float handling permits compromises).
		if (vertical) {
			this.maxPageAxis = this.width = pageSize;
		} else {
			this.maxPageAxis = this.height = pageSize;
		}

		this.container = new FlowContainer();
		this.container.setBox(this);

		final ColumnBuilder columnBuilder = new ColumnBuilder(builder, this);
		// At balancing time, this multicol is a closed subtree (the SAX head follows immediately),
		// so its contents can be rebuilt from source (M6c). Unlike box replay, this is nondestructive
		// and could support repeated capacity probes in the future. Fall back to box replay for
		// unknown ranges, Opaque contents, or already-split boxes (invalid anchors).
		// Since anchor invalidation affects only continuation fragments, explicitly exclude
		// preceding fragments with isSourceReplayable() (2026-07-28). Rebuilding a split
		// multicol from its child range would put the remainder held by the continuation
		// fragment into this fragment too, duplicating content.
		final net.zamasoft.foliojet.layout.builder.impl.RootBuilder root = builder.getPageContext();
		final net.zamasoft.foliojet.layout.RetainedTextLimit limit =
				net.zamasoft.foliojet.layout.RetainedTextLimit.get(builder);
		// Content was counted during initial layout. Neither source nor box replay adds to the total.
		try (var suspended = limit == null ? null : limit.suspend()) {
			final boolean replayed = root != null && this.isSourceReplayable()
					&& net.zamasoft.foliojet.layout.SourceReplayer.replayChildren(
							root.getPageGenerator().getLayoutSource(), this.getSourceAnchor(), columnBuilder,
							root.getPageGenerator());
			if (!replayed) {
				oldCont.restyle(columnBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED, true);
			}
		}
	}

	@Override
	protected net.zamasoft.foliojet.layout.part.AbsoluteInsets transformReferenceMargin() {
		return this.frame == null ? null : this.frame.margin;
	}

	public final AbsoluteRectFrame getFrame() {
		return this.frame;
	}

	public final double getFirstAscent() {
		return this.container.getFirstAscent();
	}

	/**
	 * Returns the distance from the baseline of the last line box to the block-end outer edge, which places an
	 * inline-block on the line (CSS 2.1 §10.8.1: the baseline of its last line box).
	 *
	 * <p>
	 * The container measures from its last flow, which is only right when the content fills the content box. A
	 * fixed or minimum block size leaves space after the content, and a smaller one lets the content overflow;
	 * either way the space between the end of the content and the end of the content box is added here, so the
	 * baseline stays on the last line wherever that line is. Before 2026-10-07 a one-line inline-block with
	 * {@code height:30pt} sat with its bottom on the baseline instead of its text (sweep seed 11942560, vertical
	 * writing, where the overflowing content was pushed off the paper). Flex, grid and multi-column boxes keep
	 * their own rules.
	 * </p>
	 *
	 * <p>
	 * A scroll container ({@code overflow} other than {@code visible}) in horizontal writing takes its bottom
	 * margin edge as the baseline instead (CSS 2.1 §10.8.1), as browsers do.
	 * </p>
	 */
	public final double getLastDescent() {
		final BlockParams params = this.getBlockParams();
		if (!params.flow.isVertical() && params.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE) {
			return 0;
		}
		final double descent = this.container.getLastDescent();
		// Ruby and warichu units keep a fixed baseline in an empty container: there is no last flow to measure from
		if (LayoutUtils.isNone(descent) || !(this.container instanceof FlowContainer flowContainer)
				|| !flowContainer.hasFlows()
				|| params instanceof net.zamasoft.foliojet.layout.box.params.FlexParams
				|| params instanceof net.zamasoft.foliojet.layout.box.params.GridParams) {
			return descent;
		}
		return descent + this.getInnerPageExtent(params.flow) - this.container.getContentSize()
				- this.blockContentAlignmentOffset();
	}

	/**
	 * Returns the descent of this box placed on a line as an atomic inline (an inline-block): the part on the
	 * line-under side of the baseline. The ascent is the rest of the box.
	 *
	 * <p>
	 * {@link #getLastDescent()} measures toward this box's block end: the left in {@code vertical-rl}, the right in
	 * {@code vertical-lr}. On a vertical line the line-under side is the left, except on a {@code sideways-lr}
	 * line, where the glyphs face left and the line-under side is the right. When the two sides differ, the value
	 * is the ascent, and the descent is the rest. Until 2026-10-07 four copies of this code took it as the descent
	 * in every case, which put a {@code vertical-lr} inline-block on its first line instead of its last (sweep seed
	 * 11942560). Ruby and warichu units return their descent directly (they lay out no flows) and are used as is.
	 * </p>
	 *
	 * @param lineParams the parameters of the line
	 * @return the descent on the line
	 */
	public final double inlineDescent(final net.zamasoft.foliojet.layout.box.params.AbstractTextParams lineParams) {
		final BlockParams params = this.getBlockParams();
		if (lineParams.flow.isVertical()) {
			if (!params.flow.isVertical()) {
				// Tate-chu-yoko: centered on the line
				return this.getWidth() / 2.0;
			}
			final double last = this.getLastDescent();
			if (LayoutUtils.isNone(last)) {
				return this.getWidth() / 2.0;
			}
			if (!(this.container instanceof FlowContainer flowContainer) || !flowContainer.hasFlows()) {
				return last;
			}
			final boolean lineUnderRight = lineParams.writingModeVariant == net.zamasoft.foliojet.layout.box.params.WritingModeVariant.SIDEWAYS_CCW;
			final boolean blockEndRight = params.flow == WritingMode.LR;
			return lineUnderRight == blockEndRight ? last : this.getWidth() - last;
		}
		if (params.flow.isVertical()) {
			// Yoko-chu-tate: sits on the baseline
			return 0;
		}
		final double last = this.getLastDescent();
		return LayoutUtils.isNone(last) ? 0 : last;
	}

	public final double getWidth() {
		return this.width + this.frame.getFrameWidth();
	}

	public double getHeight() {
		return this.height + this.frame.getFrameHeight();
	}

	public final double getTextIndent() {
		final double textIndent;
		final BlockParams params = this.getBlockParams();
		switch (params.textIndent.getType()) {
		case ABSOLUTE:
			textIndent = params.textIndent.getLength();
			break;
		case RELATIVE:
			textIndent = params.textIndent.getLength() * this.getLineSize();
			break;
		case MIXED:
			textIndent = params.textIndent.getLength() + params.textIndent.getRatio() * this.getLineSize();
			break;
		default:
			throw new IllegalStateException();
		}
		return textIndent;
	}

	public final double getInnerWidth() {
		return this.width;
	}

	public final double getInnerHeight() {
		return this.height;
	}

	/**
	 * The logical block-axis offset that a normal block's {@code align-content} adds to its content origin.
	 * Applies only when explicit sizing leaves free space; overflow falls back to start (0), following
	 * the specification's safe default. Excludes Flex/Grid, already placed by their dedicated builders.
	 */
	protected final double blockContentAlignmentOffset() {
		final BlockParams params = this.getBlockParams();
		if (params instanceof net.zamasoft.foliojet.layout.box.params.FlexParams
				|| params instanceof net.zamasoft.foliojet.layout.box.params.GridParams) {
			return 0;
		}
		final net.zamasoft.foliojet.layout.box.params.BoxAlignment alignment = params.blockAlignContent;
		if (alignment == net.zamasoft.foliojet.layout.box.params.BoxAlignment.NORMAL
				|| alignment == net.zamasoft.foliojet.layout.box.params.BoxAlignment.START
				|| alignment == net.zamasoft.foliojet.layout.box.params.BoxAlignment.STRETCH
				|| alignment == net.zamasoft.foliojet.layout.box.params.BoxAlignment.AUTO) {
			return 0;
		}
		final double free = this.getInnerPageExtent(params.flow) - this.container.getContentSize();
		if (LayoutUtils.compare(free, 0) <= 0) {
			return 0;
		}
		return alignment == net.zamasoft.foliojet.layout.box.params.BoxAlignment.CENTER ? free / 2 : free;
	}

	/** Maps the logical block-axis alignment offset to physical coordinates. */
	protected double blockAlignedX(final double x) {
		final WritingMode flow = this.getBlockParams().flow;
		return flow.isVertical() ? x + LayoutUtils.pageAxisSign(flow) * this.blockContentAlignmentOffset() : x;
	}

	/** Maps the logical block-axis alignment offset to physical coordinates. */
	protected double blockAlignedY(final double y) {
		return this.getBlockParams().flow.isVertical() ? y : y + this.blockContentAlignmentOffset();
	}

	public final void addFlow(IFlowBox box, double pageAxis) {
		this.container.addFlow(box, pageAxis);
	}

	public final void addAbsolute(IAbsoluteBox box, double staticX, double staticY) {
		final BlockParams params = this.getBlockParams();
		if (params.flow.isVertical()
				&& params.writingModeVariant != net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL
				&& TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
						params.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
			final double logicalLine = staticX, logicalPage = staticY;
			staticX = LayoutUtils.drawX(params.flow, 0, this.getInnerWidth(), logicalPage, logicalPage,
					logicalLine);
			staticY = LayoutUtils.inlineToPhysical(params, this.getInnerHeight(), logicalLine, logicalLine);
		} else if (params.flow.isVertical()) {
			final double logicalLine = staticX, logicalPage = staticY;
			// The absolute ledger stores physical coordinates, so map logical line/page in vertical writing
			// to Y/X here. In LR, physical X = logical page position. In RL, retain the logical
			// page position from the right edge, then map it to physical X at drawing time using
			// the owner box and absolutely positioned box widths (both are still unknown at registration).
			// See blockStartAnchored in Absolutes.pushDraw, 2026-09-05.
			staticX = logicalPage;
			staticY = logicalLine;
			this.container.addAbsolute(box, staticX, staticY,
					params.flow == net.zamasoft.foliojet.layout.box.params.WritingMode.RL);
			return;
		}
		this.container.addAbsolute(box, staticX, staticY);
	}

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis) {
		this.container.addFloating(box, lineAxis, pageAxis);
	}

	/**
	 * This class itself has no local processing (2026-07-20, iterative finishLayout).
	 * Concrete subclasses with local processing override this.
	 */
	public void finishLayoutSelf(IFramedBox containerBox) {
	}

	public void pushFinishLayoutChildren(final IFramedBox containerBox,
			final java.util.Deque<FinishLayoutStep> worklist) {
		final Container container = this.container;
		worklist.push(w -> container.pushFinishLayoutChildren(containerBox, w));
	}

	protected final Shape clip(Shape clip, double x, double y) {
		final BlockParams params = this.getBlockParams();
		final boolean rectClip = params.overflow.clipsPaint() || params.paintClip;
		final net.zamasoft.foliojet.layout.box.params.ClipPathShape clipPath = params.clipPath;
		if (!rectClip && clipPath == null) {
			return clip;
		}
		Shape newClip = null;
		if (rectClip) {
			newClip = new Rectangle2D.Double(
					x + this.frame.frame.border.getLeft().width + this.frame.margin.left,
					y + this.frame.frame.border.getTop().width + this.frame.margin.top,
					this.width + this.frame.padding.getFrameWidth(),
					this.height + this.frame.padding.getFrameHeight());
		}
		if (clipPath != null) {
			// clip-path (2026-08-22): resolve the shape using the reference box's actual size.
			final Rectangle2D.Double ref = this.clipPathReferenceRect(clipPath.referenceBox, x, y);
			final Shape shape = clipPath.resolve(ref.x, ref.y, ref.width, ref.height);
			newClip = newClip == null ? shape : intersectClips(newClip, shape);
		}
		if (clip == null) {
			return newClip;
		}
		return intersectClips(newClip, clip);
	}

	/**
	 * A clip combining only {@code clip-path}, excluding the overflow clip. overflow does not clip
	 * the box's own background/frame, whereas clip-path clips all of its drawing, including
	 * background and borders (css-masking-1). Use this before creating the frame Drawable.
	 */
	protected final Shape clipWithClipPath(final Shape clip, final double x, final double y) {
		final net.zamasoft.foliojet.layout.box.params.ClipPathShape clipPath = this.getBlockParams().clipPath;
		if (clipPath == null) {
			return clip;
		}
		final Rectangle2D.Double ref = this.clipPathReferenceRect(clipPath.referenceBox, x, y);
		final Shape shape = clipPath.resolve(ref.x, ref.y, ref.width, ref.height);
		return clip == null ? shape : intersectClips(shape, clip);
	}

	/** The {@code clip-path} reference-box rectangle (physical coordinates). */
	private Rectangle2D.Double clipPathReferenceRect(
			final net.zamasoft.foliojet.layout.box.params.ClipPathShape.ReferenceBox box, final double x,
			final double y) {
		final double ml = this.frame.margin.left, mt = this.frame.margin.top;
		final double bl = this.frame.frame.border.getLeft().width, bt = this.frame.frame.border.getTop().width;
		final double pl = this.frame.padding.left, pt = this.frame.padding.top;
		return switch (box) {
		case MARGIN_BOX -> new Rectangle2D.Double(x, y,
				this.width + this.frame.padding.getFrameWidth() + this.frame.frame.border.getFrameWidth()
						+ this.frame.margin.getFrameWidth(),
				this.height + this.frame.padding.getFrameHeight() + this.frame.frame.border.getFrameHeight()
						+ this.frame.margin.getFrameHeight());
		case BORDER_BOX -> new Rectangle2D.Double(x + ml, y + mt,
				this.width + this.frame.padding.getFrameWidth() + this.frame.frame.border.getFrameWidth(),
				this.height + this.frame.padding.getFrameHeight() + this.frame.frame.border.getFrameHeight());
		case PADDING_BOX -> new Rectangle2D.Double(x + ml + bl, y + mt + bt,
				this.width + this.frame.padding.getFrameWidth(),
				this.height + this.frame.padding.getFrameHeight());
		case CONTENT_BOX -> new Rectangle2D.Double(x + ml + bl + pl, y + mt + bt + pt, this.width, this.height);
		};
	}

	/**
	 * Intersects clips. Uses lightweight rectangle intersection if both are rectangles;
	 * if either is an arbitrary shape, intersects with {@link java.awt.geom.Area}
	 * (2026-08-22: generalized the rectangle assumption for clip-path support).
	 */
	private static Shape intersectClips(final Shape a, final Shape b) {
		if (a instanceof Rectangle2D ra && b instanceof Rectangle2D rb) {
			return ra.createIntersection(rb);
		}
		final java.awt.geom.Area area = new java.awt.geom.Area(a);
		area.intersect(new java.awt.geom.Area(b));
		return area;
	}

	protected abstract AbstractContainerBox splitPage(Container container, double pageLimit, boolean columnSpanning);

	protected AbstractContainerBox splitPage(final Container container, final double contentLimit,
			final double ownerExtent, final boolean columnSpanning) {
		return this.splitPage(container, ownerExtent, columnSpanning);
	}

	/**
	 * Performs only the column-break cut, without yet committing addition of a new column to the owner
	 * or starting builder resume (added on 2026-07-21, M6b Phase B4-Step2).
	 *
	 * <p>
	 * Prepare does not mean a completely side-effect-free dry run: {@code ownerContainer.splitPageAxis()}
	 * already cuts the original active column here. Precisely, it is a cut result whose addition
	 * of a new column to the owner and builder resume have not yet been committed
	 * (ChatGPT Pro consultation; see the design consultation). Structural validation failure
	 * after splitting must abort the entire conversion; do not roll back to the legacy path and retry.
	 * </p>
	 *
	 * @param contentLimit the content cut limit starting at the inner edge
	 * @param ownerExtent the owner's page-axis size before subtracting column reservations
	 * @param mode      the column-break mode
	 * @param flags     {@code IPageBreakableBox.FLAGS_*}
	 * @param plan      the collectable-prefix plan (null while unsupported)
	 */
	public ColumnCutResult prepareColumnCut(final double contentLimit, final double ownerExtent,
			final BreakMode mode, final byte flags,
			final BreakPlan plan) {
		final Container ownerContainer = this.container;
		final Container activeColumn = ownerContainer instanceof ColumnsContainer columns ? columns.getLastColumn()
				: ownerContainer;
		final int actualColumns = this.getActualColumnCount();

		final ContainerCut cut = ownerContainer.splitPageAxis(contentLimit, mode, flags, plan);
		final Container remainder;
		final Continuation.ContinuationFrame childFrame;
		if (cut instanceof ContainerCut.PlainWithChainStop(final Container chainStopContainer,
				final net.zamasoft.foliojet.layout.fragment.ChainStopReason reason)) {
			// Same reason as AbstractBlockBox.splitForContinuation (risk of content loss).
			// Return bare KEEP/MOVE only for an empty container; actual content
			// joins the common Cut construction below
			// (childFrame is always null; the dedicated MovedOpen type was removed on 2026-07-22,
			// see the development log
			// -consultation.md). For details,
			// see the development log
			// for reference.
			final boolean hasContent = chainStopContainer instanceof net.zamasoft.foliojet.layout.box.content.FlowContainer fc
					&& (fc.hasFlows() || fc.hasFloatings());
			if (!hasContent) {
				return reason == net.zamasoft.foliojet.layout.fragment.ChainStopReason.KEEP ? new ColumnCutResult.Keep()
						: new ColumnCutResult.Move();
			}
			remainder = chainStopContainer;
			childFrame = null;
		} else if (cut instanceof ContainerCut.WithFrame(final Container c, final Continuation.ContinuationFrame f)) {
			remainder = c;
			childFrame = f;
		} else {
			remainder = ((ContainerCut.Plain) cut).container();
			childFrame = null;
		}

		if (remainder == null) {
			return new ColumnCutResult.Keep();
		}
		if (remainder == activeColumn) {
			return new ColumnCutResult.Move();
		}

		return new ColumnCutResult.Cut(
				new PreparedColumnCut(this, ownerContainer, activeColumn, actualColumns, ownerExtent, remainder, childFrame));
	}

	/**
	 * Commits the {@link ColumnCutResult.Cut} returned by {@link #prepareColumnCut} to the owner
	 * (added on 2026-07-21, M6b Phase B4-Step2). Actually wraps the owner in {@link ColumnsContainer}
	 * if not already wrapped, updates its page-axis size, and adds a new empty column.
	 *
	 * <p>
	 * Before committing, validates that all owner state remains unchanged since prepare
	 * (owner identity, container identity, and actual column count). Fixed the omission noted
	 * in codex review: retaining {@code expectedActiveColumn} without checking it. After committing,
	 * also verifies that exactly one new column was added.
	 * </p>
	 */
	public void commitPreparedColumn(final PreparedColumnCut cut) {
		if (cut.owner() != this) {
			throw new ContinuationInvariantViolationException("column owner changed");
		}
		if (this.container != cut.expectedOwnerContainer()) {
			throw new ContinuationInvariantViolationException("owner container changed before column commit");
		}
		final Container currentActiveColumn = this.container instanceof ColumnsContainer columns
				? columns.getLastColumn()
				: this.container;
		if (currentActiveColumn != cut.expectedActiveColumn()) {
			throw new ContinuationInvariantViolationException("active column changed before column commit");
		}
		if (this.getActualColumnCount() != cut.expectedActualColumnCount()) {
			throw new ContinuationInvariantViolationException("actual column count changed before commit");
		}

		final ColumnsContainer columns;
		if (this.container instanceof ColumnsContainer existing) {
			columns = existing;
		} else {
			this.container = columns = new ColumnsContainer((FlowContainer) this.container);
			columns.setBox(this);
		}

		if (this.getBlockParams().flow.isVertical()) {
			this.width = cut.newPageExtent();
		} else {
			this.height = cut.newPageExtent();
		}

		columns.newColumn();

		if (this.getActualColumnCount() != cut.expectedActualColumnCount() + 1) {
			throw new ContinuationInvariantViolationException("new column was not added exactly once");
		}
	}

	/**
	 * Returns the container to compare with the <b>MOVE sentinel</b> returned by
	 * {@code this.container.splitPageAxis()} (a self-reference meaning everything moved)
	 * (added on 2026-07-28).
	 *
	 * <p>
	 * {@link ColumnsContainer} <b>delegates cutting to the last column and returns its result
	 * unchanged</b>, so the MOVE sentinel is <b>the last column</b>, not the multi-column container
	 * itself. Comparing with {@code this.container} does not match, causing <b>the last column
	 * to be interpreted as a remainder container</b>. Yet that column <b>still remains inside
	 * the multi-column container</b>, so the same content is drawn by both the previous column
	 * and the continuation fragment (measured on 2026-07-28, local/shrink/strict-739-min.html:
	 * T4 drawn twice in nested multi-column layout).
	 * </p>
	 *
	 * <p>
	 * {@link #prepareColumnCut} has always made this comparison ({@code activeColumn}).
	 * The {@code ContainerCut.Plain} Javadoc noted that identity-comparison targets differ by layer;
	 * this corrects the erroneous side of that difference to match.
	 * </p>
	 */
	protected final Container splitMoveSentinel() {
		return this.container instanceof ColumnsContainer columns ? columns.getLastColumn() : this.container;
	}

	public SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		return this.split(pageLimit, mode, flags, null);
	}

	/** Passes the target column's content limit to ordinary splitting too. The plan's continuation chain is empty. */
	public SplitResult split(double pageLimit, final BreakMode mode, final byte flags, final BreakPlan plan) {
		pageLimit -= this.frame.getFramePageStart(this.getBlockParams().flow);
		final BreakMode xmode = BreakMode.absorbColumn(mode, this.getColumnCount());
		final double intrusion = pageEndIntrusion(mode);
		// Centralize interpretation of the container's three-way return value here (typing inside containers is M4-A3b).
		// Cuts without a plan always use Plain; the old three-argument splitPageAxis wrapped
		// this Plain mapping (unified in increment 5).
		final Container nextContainer = ((net.zamasoft.foliojet.layout.fragment.ContainerCut.Plain) this.container
				.splitPageAxis(pageLimit, xmode, flags, plan)).container();
		if (nextContainer == null) {
			return SplitResult.KEEP;
		}
		if (nextContainer == this.splitMoveSentinel()) {
			return SplitResult.MOVE;
		}
		final double kept = keptPastCut(mode, intrusion);
		return new SplitResult.Split(
				this.splitPage(nextContainer, (plan == null ? pageLimit : plan.contentLimit(this, pageLimit)) + kept,
						pageLimit + kept, mode instanceof BreakMode.ColumnBreakMode));
	}

	/**
	 * How far monolithic content kept at the start of this page break's fragment has run past the cut line so far
	 * ({@link BreakMode.PageEndUse}); 0 unless footnotes are reserved on the page.
	 */
	protected static double pageEndIntrusion(final BreakMode mode) {
		return mode instanceof BreakMode.AutoBreakMode auto && auto.pageEndUse != null ? auto.pageEndUse.intrusion()
				: 0;
	}

	/**
	 * How far past its cut line a fragment reaches because its own split kept monolithic content there (2026-10-08;
	 * codex review). A figure kept whole at the start of the page, whose later call takes its footnote to the next page,
	 * belongs to the fragment that holds it: cut at the cut line, an {@code overflow: hidden} wrapper clipped the figure
	 * there and its bottom was lost.
	 *
	 * @param before {@link #pageEndIntrusion} before the split of the content
	 */
	protected static double keptPastCut(final BreakMode mode, final double before) {
		final double after = pageEndIntrusion(mode);
		return after > before ? after : 0;
	}

	/**
	 * Extracts text from container contents.
	 *
	 * <p>
	 * 2026-07-25: removed {@code final}. {@code RubyUnitBox} is a synthetic box with no child boxes
	 * (an empty container) that holds its own shaped glyph sequence. Without overriding extraction,
	 * iterative extraction from the parent (link alternative text, string-set's content(),
	 * bookmark headings, target-text()) loses the entire ruby base text.
	 * </p>
	 */
	public void pushGetTextSteps(StringBuilder textBuff, java.util.Deque<GetTextStep> worklist) {
		this.container.pushGetTextSteps(textBuff, worklist);
	}
	
	public final void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x,
			double y, java.util.Deque<TextShapeStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		transform = this.transform(transform, x, y);
		x += this.frame.getFrameLeft();
		y += this.frame.getFrameTop();
		x = this.blockAlignedX(x);
		y = this.blockAlignedY(y);
		this.container.pushTextShapeSteps(pageBox, path, transform, x, y, worklist);
	}

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape) {
		this.container.restyle(builder, shape, false);
	}
}
