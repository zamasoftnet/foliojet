package net.zamasoft.foliojet.layout.box.content;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.TextShapeSink;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public interface Container {

	/** Enumerates placed direct children in logical order for page-assignment commit. */
	public void forEachAssignmentChild(java.util.function.Consumer<IBox> action);

	public void setBox(AbstractContainerBox box);

	public void addFlow(IFlowBox box, double pageAxis);

	public void addAbsolute(IAbsoluteBox box, double staticX, double staticY);

	/**
	 * Registers a static position where {@code staticX} points to the box's right (block-start) edge in vertical RL.
	 * By default, delegates to the normal {@link #addAbsolute(IAbsoluteBox, double, double)}.
	 */
	public default void addAbsolute(IAbsoluteBox box, double staticX, double staticY, boolean blockStartAnchored) {
		this.addAbsolute(box, staticX, staticY);
	}

	public void addFloating(IFloatBox box, double lineAxis, double pageAxis);

	public boolean hasFlows();

	public boolean hasFloatings();

	/** True if the specified normal flow is first in this fragment. */
	public default boolean isFirstFlow(final IFlowBox box) {
		return false;
	}

	/**
	 * Returns whether the preceding fragment took actual content when fragmenting a fixed-size box.
	 * Decoration consisting only of {@code ::before}/{@code ::after} does not count as consumed content.
	 */
	public default boolean hasNonDecorationContent() {
		return this.paintsAnything();
	}

	/**
	 * Returns whether there is non-decorative content, excluding only the specified placed floats.
	 * Used to detect empty pages with PageBox's page-float ledger.
	 */
	public default boolean hasNonDecorationContentExcludingFloatings(
			final java.util.Set<? extends IFloatBox> excluded) {
		return this.hasNonDecorationContent();
	}

	public double getFirstAscent();

	public double getLastDescent();

	public double getContentSize();

	/**
	 * The page-axis size actually consumed in the preceding fragment, subtracted from a fixed-size
	 * box's continuation height. Normally equals {@link #getContentSize()}, but an empty fragment
	 * shell left only as a result of moving all content to the next page does not count as consumption.
	 */
	public default double getConsumedPageSizeForFragmentation() {
		return this.getContentSize();
	}

	/**
	 * The minimum page-axis capacity for column balancing (2026-08-22).
	 * Children that are atomic under the pagination contract (same-axis reverse progression or orthogonal
	 * writing directions, including those nested in children with the same writing direction; 2026-10-03)
	 * cannot split internally at column boundaries, so their full extent sets the capacity floor.
	 * 0 if there are no such children.
	 */
	public default double balancePageSizeFloor() {
		return 0;
	}

	/**
	 * Returns the page-axis end (relative to the inner edge) of what this content
	 * <b>actually paints on paper</b>.
	 *
	 * <p>
	 * Where {@link #getContentSize()} returns <b>box geometry</b>, "the end of the last flow box,"
	 * this returns <b>measured painting extent</b>. They differ in two ways:
	 * </p>
	 * <ul>
	 * <li>Floats are excluded from {@code getContentSize()} but are painted on paper</li>
	 * <li>Unused size in a box without borders or background (specified size larger than its content,
	 * or space after its content) exists geometrically but <b>paints nothing</b></li>
	 * </ul>
	 *
	 * <p>
	 * Use this to determine whether there is anything to paint beyond the page.
	 * Creating a fragment with nothing to paint <b>merely adds one blank page</b>
	 * (violating css-break-3 §4.4: each fragmentainer takes a nonzero amount of content).
	 * </p>
	 *
	 * @return the page-axis end of painting (relative to the inner edge; 0 if nothing is painted)
	 */
	public double paintedPageEnd();

	/**
	 * Returns whether this content <b>paints anything on paper</b> (added 2026-07-28).
	 *
	 * <p>
	 * Where {@link #paintedPageEnd()} answers a distance, "how far painting extends on the page axis,"
	 * this answers only presence. A distance of 0 is almost equivalent to painting nothing,
	 * but only <b>almost</b> (frames extending only along the line axis, column rules).
	 * Use this method, which has no such discrepancy, to omit pages that paint nothing
	 * ({@code StyleBuilder.drawPage}, css-break-3 §4.4).
	 * </p>
	 *
	 * <p>
	 * <b>Always err on the safe side (= paints).</b>
	 * </p>
	 *
	 * @return true if the content paints (or may paint) anything on paper
	 */
	public boolean paintsAnything();

	public double getCutPoint(double pageAxis);

	/**
	 * Returns the feasible cut position immediately before the proposed position (M5-B).
	 * Where getCutPoint rounds up to the next boundary, this rounds down to estimate the actual
	 * split (moving overflowing content to the next fragment) without mutating boxes.
	 * Returns 0 if there is no boundary before the proposed position.
	 *
	 * @param pageAxis the proposed position (distance from the content start)
	 * @return the preceding cut position (0 if none)
	 */
	public double getCutPointBelow(double pageAxis);

	public boolean avoidBreakBefore();

	public boolean avoidBreakAfter();

	/**
	 * Iterative {@code finishLayout} (2026-07-20, for the same reason as IBox.finishLayout).
	 * Pushes child processing steps (flows/floatings/absolutes, or columns) onto {@code worklist}
	 * in **reverse order** to preserve the original recursive traversal order.
	 */
	public void pushFinishLayoutChildren(IFramedBox containerBox, Deque<FinishLayoutStep> worklist);

	/**
	 * Iterative frames (2026-07-20, for the same reason as IBox.pushDrawSteps). Pushes normal-flow
	 * child frame-drawing steps onto {@code worklist} in **reverse order** to preserve traversal order.
	 */
	public void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist);

	/**
	 * Iterative draw (2026-07-20, for the same reason as IBox.draw). Pushes normal-flow child
	 * drawing steps onto {@code worklist} in **reverse order** to preserve traversal order.
	 */
	public void pushDrawFlows(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist);

	/**
	 * The equivalent of {@link #pushDrawFlows} for float boxes.
	 */
	public void pushDrawFloatings(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist);

	/**
	 * The equivalent of {@link #pushDrawFlows} for absolutely positioned boxes.
	 */
	public void pushDrawAbsolutes(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			Deque<DrawStep> worklist);

	/**
	 * Paginates float boxes (directly held floats plus recursive aggregation from child flows)
	 * and returns a typed destination for the moved portion (2026-07-24, P2-4).
	 */
	public FloatTransferResult splitFloatings(FloatTransferTarget target, double pageLimit, byte flags);

	/**
	 * Paginates float boxes, detaches the ledger of moved floats from this container, and returns it
	 * (an internal contract only for child-flow recursion, called only by the parent's
	 * {@code FlowContainer.aggregateFloatings}). Replaces the old two-argument {@code splitFloatings}
	 * nullable return (null = no movement) with Optional (2026-07-24 E-4).
	 * Unlike the three-argument {@link #splitFloatings(FloatTransferTarget, double, byte)}, returns
	 * the raw moved-float ledger without attaching it to a container (the parent chooses the destination).
	 *
	 * @return empty if no floats move, otherwise the detached nonempty ledger
	 */
	public java.util.Optional<Floatings> detachMovedFloatings(double pageLimit, byte flags);

	/**
	 * Iterative getText (2026-07-20, for the same reason as IBox.pushDrawSteps). Pushes child text
	 * extraction steps onto {@code worklist} in **reverse order** to preserve traversal order.
	 */
	public void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist);

	/**
	 * Iterative textShape (2026-07-20, for the same reason as IBox.pushDrawSteps).
	 * Pushes child outline steps onto {@code worklist}.
	 */
	public void pushTextShapeSteps(PageBox pageBox, TextShapeSink sink, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist);

	public void restyle(BlockBuilder builder, net.zamasoft.foliojet.layout.fragment.OpenShape shape,
			boolean restyleAbsolutes);

	/**
	 * Page-axis splitting with a continuation plan (C1d-C). Fragments of chain members selected
	 * by plan propagate in the return value as ContinuationFrames.
	 * If plan is null, uses the existing split behavior (Plain only).
	 */
	public net.zamasoft.foliojet.layout.fragment.ContainerCut splitPageAxis(double pageLimit, BreakMode mode,
			byte flags, net.zamasoft.foliojet.layout.fragment.BreakPlan plan);

	/**
	 * Passes normal-flow child boxes in order (for M6b diagnostics).
	 */
	public void eachFlowBox(java.util.function.Consumer<IFlowBox> consumer);

	/**
	 * Passes placed absolutely positioned boxes in order (2026-09-02, for footnote-call traversal).
	 * The default passes none.
	 */
	public default void eachAbsoluteBox(java.util.function.Consumer<IAbsoluteBox> consumer) {
		// A container without absolutely positioned boxes
	}

	/**
	 * Passes float boxes in order (read-only; for footnote F4 call traversal,
	 * so footnote calls placed inside floats can also be counted).
	 */
	public default void eachFloatingBox(java.util.function.Consumer<IFloatBox> consumer) {
		// No floats by default
	}

	/**
	 * Returns the number of flows held (2026-08-07, for Flex row splitting in {@code FlexBox.split}).
	 */
	public default int getFlowCount() {
		throw new UnsupportedOperationException(this.getClass().getName());
	}

	/**
	 * Moves flows from {@code fromIndex} onward (zero-based, insertion order) to {@code dest},
	 * leaving only the first fromIndex flows in this container (2026-08-07, for Flex row splitting only).
	 * For the same reason table row groups manipulate their own {@code rows} lists directly,
	 * multiple siblings must carry over together to the next fragment. This does not use
	 * the common FragmentRecipe/splitPageAxis path, which assumes a single-child continuation.
	 * Re-register moved flows at {@code pageAxis} minus {@code crossShift}
	 * (the continuation container's origin is the cut line).
	 */
	public default void migrateFlowsFrom(int fromIndex, Container dest, double crossShift) {
		throw new UnsupportedOperationException(this.getClass().getName());
	}
}
