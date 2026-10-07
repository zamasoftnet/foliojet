package net.zamasoft.foliojet.layout.rescue;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.ArtifactVisitor;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * A fragment for visual rescue splitting
 * (introduced 2026-07-25, increment 3; design consultation §2;
 * <b>not yet wired into production paths</b>).
 *
 * <p>
 * A short-lived drawing decorator that draws the same box shifted by the consumed extent and clips
 * the remainder. It changes <b>none</b> of the original box's ({@code source}) dimensions, Params, Pos,
 * or Container. Fragments change only page-direction <b>occupancy</b>;
 * line-direction dimensions stay the same as the original box.
 * This separates the original box's layout dimensions from its occupied dimensions on the fragment.
 * </p>
 *
 * <h2>Coordinates</h2>
 *
 * <p>
 * Relative to the fragment's physical origin {@code (x, y)} , the original box's drawing origin is:
 * </p>
 *
 * <pre>
 * Horizontal writing (TB):             sourceX = x
 *                                      sourceY = y - offset
 * Vertical writing, right to left (RL): sourceX = x - (sourcePageExtent - offset - sliceExtent)
 *                                      sourceY = y
 * Vertical writing, left to right (LR): sourceX = x - offset
 *                                      sourceY = y
 * </pre>
 *
 * <p>
 * The description of {@link LayoutUtils#drawX} is authoritative for page-axis <b>direction</b>;
 * the formulas above derive from it. TB and LR, whose directions are positive, both use
 * {@code - offset} . Only RL, whose direction is negative, subtracts the unconsumed remainder,
 * {@code sourcePageExtent - offset - sliceExtent}
 * (in vertical RL writing, the page-direction start is the original box's right edge;
 * that remainder is the distance from the fragment's left edge {@code x} to the original box's left edge).
 * </p>
 *
 * <h2>Borders and margins</h2>
 *
 * <p>
 * Simply clipping to the fragment rectangle naturally slices decorations
 * (as with CSS {@code box-decoration-break: slice} ):
 * only the first fragment includes the top margin and top border;
 * only the final fragment includes the bottom border and bottom margin.
 * Intermediate fragments include neither, and no new line is drawn on a cut surface.
 * <b>Does not use</b> {@code AbsoluteRectFrame.cut()} :
 * clipping while preserving the original geometry is more accurate, including background images
 * and transforms (recommendation §2).
 * </p>
 *
 * <h2>Semantics</h2>
 *
 * <p>
 * Continuation fragments ({@code offset > 0}) are visually content but semantically belong to the first
 * fragment. Only the first fragment delegates {@link #pushGetTextSteps} to the original box;
 * continuation fragments return nothing.
 * The drawing side handles PDF artifacts (without opening structure tags):
 * the artifact attribute of {@code Drawer} plus {@code GC.beginArtifactScope()} .
 * </p>
 *
 * <h2>SourceAnchor</h2>
 *
 * <p>
 * Rescue fragments are pagination state derived from laid-out boxes, not source events,
 * so {@code sourceAnchor} remains {@code -1} (the default in {@link AbstractBox} ).
 * Since {@code stampRanges()} selects only boxes with nonnegative anchors for replay,
 * recipe replay and rescue tails do not duplicate each other.
 * </p>
 */
public class VisualRescueBox extends AbstractBox {

	private final IBox source;

	private final WritingMode progression;

	private final double sourcePageExtent;

	private final double offset;

	private final double sliceExtent;

	/**
	 * @param source laid-out original box
	 * @param progression writing direction determining the page axis (that of the containing block)
	 * @param sourcePageExtent original box's page-direction occupancy (immutable)
	 * @param offset page-direction position where this fragment starts
	 * @param sliceExtent this fragment's occupancy
	 */
	public VisualRescueBox(final IBox source, final WritingMode progression, final double sourcePageExtent,
			final double offset, final double sliceExtent) {
		if (source == null) {
			throw new IllegalArgumentException("source");
		}
		if (source instanceof VisualRescueBox) {
			// Do not create fragments of fragments. Represent the interval solely with offset/sliceExtent.
			throw new IllegalArgumentException("救済断片を入れ子にしない: " + source);
		}
		if (progression == null) {
			throw new IllegalArgumentException("progression");
		}
		if (!(sourcePageExtent > 0)) {
			throw new IllegalArgumentException("sourcePageExtent=" + sourcePageExtent);
		}
		if (!(offset >= 0)) {
			throw new IllegalArgumentException("offset=" + offset);
		}
		if (!(sliceExtent > 0)) {
			throw new IllegalArgumentException("sliceExtent=" + sliceExtent);
		}
		if (LayoutUtils.compare(offset + sliceExtent, sourcePageExtent) > 0) {
			throw new IllegalArgumentException(
					"断片が元ボックスをはみ出す: offset=" + offset + " sliceExtent=" + sliceExtent + " source=" + sourcePageExtent);
		}
		this.source = source;
		this.progression = progression;
		this.sourcePageExtent = sourcePageExtent;
		this.offset = offset;
		this.sliceExtent = sliceExtent;
	}

	/**
	 * Creates a fragment from a decision. Selects a normal-flow or float adapter according to the original box
	 * kind.
	 *
	 * @param source laid-out original box
	 * @param progression writing direction determining the page axis
	 * @param sourcePageExtent original box's page-direction occupancy
	 * @param slice decision from {@link VisualRescuePlanner}
	 * @return the fragment
	 */
	public static VisualRescueBox of(final IBox source, final WritingMode progression, final double sourcePageExtent,
			final RescueDecision.Slice slice) {
		if (source instanceof IFloatBox floatBox) {
			return new VisualRescueFloatBox(floatBox, progression, sourcePageExtent, slice.offset(),
					slice.sliceExtent());
		}
		if (source instanceof IFlowBox flowBox) {
			return new VisualRescueFlowBox(flowBox, progression, sourcePageExtent, slice.offset(),
					slice.sliceExtent());
		}
		return new VisualRescueBox(source, progression, sourcePageExtent, slice.offset(), slice.sliceExtent());
	}

	public final IBox getSource() {
		return this.source;
	}

	public final WritingMode getProgression() {
		return this.progression;
	}

	/** The original box's page-direction occupancy (unchanged across fragments). */
	public final double getSourcePageExtent() {
		return this.sourcePageExtent;
	}

	/** The page-direction position where this fragment starts (consumed extent). */
	public final double getOffset() {
		return this.offset;
	}

	/** This fragment's occupancy. */
	public final double getSliceExtent() {
		return this.sliceExtent;
	}

	/** Returns true for the first fragment (with top margin and top border). */
	public final boolean isFirstFragment() {
		return this.offset == 0;
	}

	/** Returns true for the final fragment (with bottom border and bottom margin). */
	public final boolean isLastFragment() {
		return LayoutUtils.compare(this.offset + this.sliceExtent, this.sourcePageExtent) >= 0;
	}

	/**
	 * Returns true for a continuation fragment (emitted as a PDF artifact).
	 * As specified in recommendation §3, {@code offset > 0} is the sole criterion.
	 */
	public final boolean isContinuation() {
		return !this.isFirstFragment();
	}

	public final BoxType getType() {
		return BoxType.RESCUE;
	}

	/**
	 * The margin on the original box's <b>end side</b> in the page direction (2026-07-25, increment 5).
	 *
	 * <p>
	 * Used to resume margin collapsing immediately after the fragment.
	 * The original box's bottom margin is <b>within the final fragment's geometry</b>,
	 * so it is already included in the fragment's occupancy.
	 * Returns the amount that may collapse with the next content's top margin
	 * (uses the same axis selection as setting {@code poLastMargin} in {@code BlockBuilder.addBound} ).
	 * </p>
	 *
	 * @param flow writing direction determining the axes
	 * @return margin eligible for collapsing (0 if the original box has no frame)
	 */
	public final double sourceCollapsibleEndMargin(final WritingMode flow) {
		if (!(this.source instanceof IFramedBox framed)) {
			return 0;
		}
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = framed.getFrame();
		return flow.isVertical() ? frame.margin.left : frame.margin.bottom;
	}

	public final Params getParams() {
		return this.source.getParams();
	}

	public Pos getPos() {
		return this.source.getPos();
	}

	public final double getWidth() {
		return this.progression.isVertical() ? this.sliceExtent : this.source.getWidth();
	}

	public final double getHeight() {
		return this.progression.isVertical() ? this.source.getHeight() : this.sliceExtent;
	}

	/**
	 * A fragment has no frame of its own, so its inner dimensions use the original box's inner dimension
	 * on the line axis and this fragment's occupancy on the page axis.
	 * These values only satisfy the IBox contract; layout does not use them.
	 */
	public final double getInnerWidth() {
		return this.progression.isVertical() ? this.sliceExtent : this.source.getInnerWidth();
	}

	/** @see #getInnerWidth() */
	public final double getInnerHeight() {
		return this.progression.isVertical() ? this.source.getInnerHeight() : this.sliceExtent;
	}

	/**
	 * The original box's drawing origin X relative to the fragment's physical origin {@code fragmentX} .
	 */
	public final double sourceDrawX(final double fragmentX) {
		// Handle page-axis direction according to the same rule as LayoutUtils.drawX.
		// (2026-07-25, vertical-lr support. Previously isVertical() treated RL and LR
		// as identical and provided only the RL formula.)
		return switch (this.progression) {
		case TB -> fragmentX;
		case RL -> fragmentX - (this.sourcePageExtent - this.offset - this.sliceExtent);
		case LR -> fragmentX - this.offset;
		};
	}

	/**
	 * The original box's drawing origin Y relative to the fragment's physical origin {@code fragmentY} .
	 */
	public final double sourceDrawY(final double fragmentY) {
		return this.progression.isVertical() ? fragmentY : fragmentY - this.offset;
	}

	/**
	 * The fragment's physical rectangle (nothing is drawn outside it).
	 */
	public final Rectangle2D.Double fragmentRect(final double x, final double y) {
		return new Rectangle2D.Double(x, y, this.getWidth(), this.getHeight());
	}

	/**
	 * Intersects the fragment rectangle with the existing clip.
	 * Follows the same approach as {@code AbstractContainerBox.clip()}
	 * ({@link Rectangle2D#createIntersection(Rectangle2D)}).
	 *
	 * @param clip existing clip (may be {@code null} )
	 * @param x fragment's physical X
	 * @param y fragment's physical Y
	 * @return the intersected clip
	 */
	public final Shape clip(final Shape clip, final double x, final double y) {
		final Rectangle2D.Double newClip = this.fragmentRect(x, y);
		if (clip == null) {
			return newClip;
		}
		return newClip.createIntersection((Rectangle2D) clip);
	}

	/**
	 * Does nothing because the original box is already laid out.
	 * Fragments never calculate dimensions; this is the core of the design that leaves layout calculations
	 * unchanged.
	 */
	public void finishLayoutSelf(final IFramedBox containerBox) {
	}

	/**
	 * Pushes no children, because running finishLayout again on the original box would calculate twice.
	 */
	public void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
	}

	/**
	 * Clips to the fragment rectangle and draws the original box from a position shifted by the consumed
	 * extent.
	 * Does not alter drawing of the original box itself (content stays vector-based; no rasterization).
	 */
	public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
			final AffineTransform transform, final double contextX, final double contextY, final double x,
			final double y, final Deque<DrawStep> worklist) {
		final Shape sliceClip = this.clip(clip, x, y);
		// Emit continuation fragments ({@code offset > 0}) as artifacts (recommendation §3).
		// The sole criterion is offset>0, which switches both (a) the display-list artifact flag
		// and (b) a side-effect-free Visitor. The first fragment uses
		// the real Visitor and real content as usual.
		final Drawer sliceDrawer = this.isContinuation() ? drawer.artifactView() : drawer;
		final Visitor sliceVisitor = this.isContinuation() ? ArtifactVisitor.INSTANCE : visitor;
		worklist.push(IBox.drawStep(this.source, pageBox, sliceDrawer, sliceVisitor, sliceClip, transform, contextX,
				contextY, this.sourceDrawX(x), this.sourceDrawY(y)));
	}

	/**
	 * Pushes drawing steps for the original box's frame (background/borders) using the fragment's clip
	 * and coordinates (2026-07-25, increment 6).
	 *
	 * <p>
	 * Block frames are drawn in a <b>frame pass separate from the drawing pass</b>
	 * ({@code AbstractBlockBox.pushFramesSteps}; the parent-container traversal draws backgrounds/borders
	 * before content). Merely delegating the original box to {@link #pushDrawSteps} therefore leaves
	 * the background/borders of block-derived fragments <b>absent from every fragment</b>.
	 * This entry point delegates the frame-pass side.
	 * </p>
	 *
	 * <p>
	 * Simply cutting the frame with {@link #clip(Shape, double, double)} naturally slices decorations,
	 * just as in the drawing pass (only the first fragment contains the top border, only the final fragment
	 * contains the bottom border, and no line appears at the cut).
	 * Continuation fragments receive the same artifact treatment.
	 * </p>
	 *
	 * <p>
	 * Pushes nothing if the original box kind has no frame pass (text blocks/replaced elements).
	 * Replaced elements draw their frames in their own {@code pushDrawSteps} , so this avoids drawing twice.
	 * </p>
	 *
	 * @param x fragment's physical X
	 * @param y fragment's physical Y
	 */
	public final void pushSourceFramesSteps(final PageBox pageBox, final Drawer drawer, final Shape clip,
			final AffineTransform transform, final double x, final double y,
			final Deque<net.zamasoft.foliojet.layout.box.FramesStep> worklist) {
		if (!(this.source instanceof net.zamasoft.foliojet.layout.box.AbstractContainerBox containerBox)) {
			return;
		}
		worklist.push(net.zamasoft.foliojet.layout.box.AbstractContainerBox.framesStep(containerBox, pageBox,
				this.isContinuation() ? drawer.artifactView() : drawer, this.clip(clip, x, y), transform,
				this.sourceDrawX(x), this.sourceDrawY(y)));
	}

	/**
	 * Only the first fragment returns the entire original box's text once.
	 * Continuation fragments are empty, preventing duplicate text extraction and read-aloud output.
	 */
	public void pushGetTextSteps(final StringBuilder textBuff, final Deque<GetTextStep> worklist) {
		if (this.isFirstFragment()) {
			worklist.push(IBox.getTextStep(this.source, textBuff));
		}
	}

	/**
	 * Outlines are visual, so every fragment delegates to the original box (shifting only coordinates).
	 * Note that this criterion differs from text extraction ({@link #pushGetTextSteps}).
	 */
	public void pushTextShapeSteps(final PageBox pageBox, final GeneralPath path, final AffineTransform transform,
			final double x, final double y, final Deque<TextShapeStep> worklist) {
		worklist.push(IBox.textShapeStep(this.source, pageBox, path, transform, this.sourceDrawX(x),
				this.sourceDrawY(y)));
	}

	public String toString() {
		return this.getClass().getName() + "[offset=" + this.offset + ",sliceExtent=" + this.sliceExtent
				+ ",sourcePageExtent=" + this.sourcePageExtent + ",progression=" + this.progression + ",source="
				+ this.source + "]";
	}
}
