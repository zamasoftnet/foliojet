package net.zamasoft.foliojet.layout.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public interface IBox {

	/** Enumerates placed direct children in logical order for page-assignment commit. */
	public default void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
	}

	/**
	 * Returns the LayoutSource event ID that produced this content
	 * (SourceAnchor; assigned once during recording and immutable; -1 for fragments or unrecorded content).
	 */
	public long getSourceAnchor();

	/** The logical anchor of the assignment source, retained even if reflow rebuilds the box. */
	public default long getAssignmentAnchor() {
		return this.getSourceAnchor();
	}

	/**
	 * Assigns the SourceAnchor (only once; for the recording/replay driver only).
	 */
	public void setSourceAnchor(long id);

	/**
	 * Returns whether this box <b>may be replayed in its entirety from source</b>
	 * (added 2026-07-28). It must have an anchor and <b>must not yet have been split</b>.
	 *
	 * <p>
	 * The preceding fragment of a split box retains its anchor (an asymmetry: the anchor belongs
	 * to the box instance, while the continuation fragment has no anchor when built from the recipe).
	 * Replaying that preceding fragment from its anchor rebuilds <b>the entire element</b>,
	 * duplicating content with the continuation fragment that resumes separately. This actually
	 * occurs in nested column balancing, where the preceding and continuation fragments
	 * appear together in the same remainder.
	 * </p>
	 */
	public boolean isSourceReplayable();

	/**
	 * Records that this box has been split and passed its remaining content to a continuation
	 * fragment (for the split executor only). Afterward, {@link #isSourceReplayable()} is false.
	 */
	public void markFragmented();

	/**
	 * Returns the box type.
	 *
	 * @return
	 */
	public BoxType getType();

	/**
	 * Returns the content parameters.
	 *
	 * @return
	 */
	public Params getParams();

	/**
	 * Returns the position parameters.
	 *
	 * @return
	 */
	public Pos getPos();

	/**
	 * Returns the current width of the box.
	 *
	 * @return
	 */
	public double getWidth();

	/**
	 * Returns the current height of the box.
	 *
	 * @return
	 */
	public double getHeight();

	/**
	 * Returns the current inner width of the box.
	 *
	 * @return
	 */
	public double getInnerWidth();

	/**
	 * Returns the current inner height of the box.
	 *
	 * @return
	 */
	public double getInnerHeight();

	/**
	 * Returns the line-axis size for the given writing direction (width in horizontal writing, height in vertical).
	 *
	 * @param flow the writing direction that determines the axes (normally that of the containing block)
	 * @return the line-axis size
	 */
	public default double getLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getHeight() : this.getWidth();
	}

	/**
	 * Returns the page-axis size for the given writing direction (height in horizontal writing, width in vertical).
	 *
	 * @param flow the writing direction that determines the axes (normally that of the containing block)
	 * @return the page-axis size
	 */
	public default double getPageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getWidth() : this.getHeight();
	}

	/**
	 * Returns the page-axis extent that this box <b>actually paints on paper</b> (added 2026-07-27).
	 *
	 * <p>
	 * Where {@link #getPageExtent(WritingMode)} returns <b>box geometry</b>, this returns
	 * <b>measured painting extent</b>. The default is "paint throughout the box," which is correct
	 * for text, replaced elements, and boxes with borders or backgrounds. Only boxes whose contents
	 * can be queried ({@link AbstractContainerBox}) reduce it to the extent their contents paint.
	 * </p>
	 *
	 * <p>
	 * <b>Returning 0 means "paints nothing."</b>
	 * Used to determine whether anything is painted in the part overflowing the page.
	 * Creating a fragment when there is nothing to paint merely adds a blank page
	 * (css-break-3 §4.4).
	 * </p>
	 *
	 * @param flow the writing direction that determines the axes (normally that of the containing block)
	 * @return the page-axis extent of painting (0 if nothing is painted)
	 */
	public default double paintedPageExtent(WritingMode flow) {
		return this.getPageExtent(flow);
	}

	/**
	 * Returns whether this box <b>paints anything on paper</b> (added 2026-07-28).
	 *
	 * <p>
	 * Where {@link #paintedPageExtent(WritingMode)} answers a <b>distance</b>, "how far painting
	 * extends on the page axis," this answers only <b>presence</b>, "whether anything is painted
	 * at all." These are different questions, so they have separate methods. Distance depends
	 * on the axis (writing direction); a different axis requires answering "cannot measure, so
	 * use the entire box." Presence does not depend on the axis. Also, distance returns 0 for
	 * a box that paints only along the line axis (a framed box with zero page-axis size),
	 * but that does not mean it paints nothing.
	 * </p>
	 *
	 * <p>
	 * <b>Always err on the safe side (= paints).</b> Boxes whose contents cannot be queried
	 * unconditionally return {@code true}. Incorrectly returning {@code false} would discard
	 * a page with painting and <b>lose content</b>.
	 * Used only for the css-break-3 §4.4 check: <b>do not output pages that paint nothing</b>
	 * ({@code StyleBuilder.drawPage}).
	 * </p>
	 *
	 * @return true if the box paints (or may paint) anything on paper
	 */
	public default boolean paintsAnything() {
		return true;
	}

	/**
	 * Returns the inner line-axis size for the given writing direction.
	 *
	 * @param flow the writing direction that determines the axes
	 * @return the inner line-axis size
	 */
	public default double getInnerLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getInnerHeight() : this.getInnerWidth();
	}

	/**
	 * Returns the inner page-axis size for the given writing direction.
	 *
	 * @param flow the writing direction that determines the axes
	 * @return the inner page-axis size
	 */
	public default double getInnerPageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getInnerWidth() : this.getInnerHeight();
	}

	/**
	 * Finalizes the page-axis size (2026-07-20, converted to iteration: ARCHITECTURE.md invariant 6).
	 * The old implementation used polymorphic mutual recursion, causing StackOverflowError in deeply
	 * nested documents (over 1000 levels; confirmed on an actual legislation page). Replaced with
	 * iterative DFS using an explicit {@link Deque} worklist instead of the JVM call stack.
	 * Each box type only needs to implement {@link #finishLayoutSelf} (local processing) and
	 * {@link #pushFinishLayoutChildren} (registering children); there is no need to override
	 * this default method itself.
	 *
	 * @param containerBox
	 */
	public default void finishLayout(IFramedBox containerBox) {
		final Deque<FinishLayoutStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.step(this, containerBox));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * Creates one {@link FinishLayoutStep} that calls {@link #pushFinishLayoutChildren}
	 * after {@link #finishLayoutSelf} on {@code box}
	 * (a common helper for pushing IBox children onto the worklist).
	 */
	public static FinishLayoutStep step(final IBox box, final IFramedBox containerBox) {
		return worklist -> {
			box.finishLayoutSelf(containerBox);
			box.pushFinishLayoutChildren(containerBox, worklist);
		};
	}

	/**
	 * Performs only this box's local processing (finalizing position and size, etc., excluding children).
	 * {@link #pushFinishLayoutChildren} handles child processing separately.
	 */
	public void finishLayoutSelf(IFramedBox containerBox);

	/**
	 * Pushes processing steps for child boxes (or {@code Container}) onto {@code worklist}.
	 * For multiple children, push in **reverse order** to preserve the original recursive
	 * traversal order (the worklist is a stack).
	 */
	public void pushFinishLayoutChildren(IFramedBox containerBox, Deque<FinishLayoutStep> worklist);

	/**
	 * Adds drawable content (2026-07-20, converted to iteration for the same reason as finishLayout).
	 * Replaced with iterative DFS using an explicit {@link Deque} worklist to avoid
	 * StackOverflowError with deep nesting.
	 *
	 * <p>
	 * The supplied coordinate system has its origin at the page's top-left corner.
	 * </p>
	 *
	 * @param pageBox
	 *            TODO
	 * @param drawer
	 * @param clip
	 * @param transform
	 *            TODO
	 * @param contextX
	 *            TODO
	 * @param contextY
	 *            TODO
	 */
	public default void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		final Deque<DrawStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.drawStep(this, pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * Creates one {@link DrawStep} that calls {@link #pushDrawSteps} on {@code box}
	 * (a common helper for pushing child box drawing onto the worklist).
	 */
	public static DrawStep drawStep(final IBox box, final PageBox pageBox, final Drawer drawer, final Visitor visitor,
			final Shape clip, final AffineTransform transform, final double contextX, final double contextY,
			final double x, final double y) {
		return worklist -> box.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
	}

	/**
	 * Pushes drawing steps for this box (and its descendants) onto {@code worklist}.
	 * To preserve the original {@code draw} execution order, assemble local drawing
	 * (this box's background, frame, text runs, etc.) and child box drawing steps in execution
	 * order, then push in **reverse order** (the worklist is a stack).
	 * When local drawing alternates with child drawing, local drawing must also be pushed
	 * as a step at the correct position instead of executing immediately;
	 * see {@link AbstractTextBox}.
	 */
	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist);

	/**
	 * Returns the text within (2026-07-20, converted to iteration for the same reason as draw).
	 */
	public default void getText(StringBuilder textBuff) {
		final Deque<GetTextStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.getTextStep(this, textBuff));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * Creates one {@link GetTextStep} that calls {@link #pushGetTextSteps} on {@code box}.
	 */
	public static GetTextStep getTextStep(final IBox box, final StringBuilder textBuff) {
		return worklist -> box.pushGetTextSteps(textBuff, worklist);
	}

	/**
	 * Pushes text extraction steps for this box (and its descendants) onto {@code worklist}.
	 * Follow the same convention as {@link #pushDrawSteps}: push in **reverse order**
	 * to preserve the original traversal order.
	 */
	public void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist);

	/**
	 * Walks the text of this box and its in-flow descendants, handing each run to {@code sink} as
	 * {@link net.zamasoft.pdfg2d.gc.GC#drawText} would draw it at the origin of the given transform
	 * (2026-07-20, converted to iteration for the same reason as draw; 2026-10-09, runs instead of glyph outlines,
	 * so that {@code background-clip: text} also clips with fonts that have no local outlines).
	 */
	public default void textShape(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double d) {
		final Deque<TextShapeStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.textShapeStep(this, pageBox, sink, transform, x, d));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * Collects glyph outlines into {@code path}, for measurement. Fonts that have no local glyph outlines are
	 * left out without a warning.
	 */
	public default void textShapeQuiet(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d) {
		this.textShape(pageBox, TextShapeSink.outlines(path), transform, x, d);
	}

	/**
	 * Creates one {@link TextShapeStep} that calls {@link #pushTextShapeSteps} on {@code box}.
	 */
	public static TextShapeStep textShapeStep(final IBox box, final PageBox pageBox, final TextShapeSink sink,
			final AffineTransform transform, final double x, final double y) {
		return worklist -> box.pushTextShapeSteps(pageBox, sink, transform, x, y, worklist);
	}

	/**
	 * Pushes text walking steps for this box (and its descendants) onto {@code worklist}.
	 * Follow the same convention as {@link #pushDrawSteps}: push in **reverse order**
	 * to preserve the original traversal order.
	 */
	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist);
}
