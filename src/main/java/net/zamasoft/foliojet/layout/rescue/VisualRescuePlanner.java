package net.zamasoft.foliojet.layout.rescue;

import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * The <b>entry point</b> for visual rescue splitting: pure functions centralizing its decisions,
 * and the collected specification and design decisions for the feature
 * (introduced 2026-07-25; development record (agreed specification),
 * design consultation (design recommendation)).
 *
 * <h2>1. What the feature does</h2>
 *
 * <p>
 * To <b>prevent information loss</b> from unsplittable elements overflowing a page,
 * <b>geometrically (mechanically) cuts</b> the border box and sends fragments to the next fragmentainer.
 * It never considers line gaps or block boundaries; this is distinct from normal page-break splitting.
 * Implemented with clipping and coordinate shifts, content stays vector-based (no conversion to images).
 * Cut surfaces receive no decorations. Only the first fragment's clip includes the top margin and top border;
 * only the final fragment's clip includes the bottom border and bottom margin.
 * This produces the same result as CSS {@code box-decoration-break: slice}
 * <b>without</b> special edge-removal processing.
 * Continuation fragments are emitted as PDF {@code /Artifact} , preventing duplicate text extraction,
 * read-aloud output, and structure tags.
 * </p>
 *
 * <h2>2. Why it replaces only this one point</h2>
 *
 * <p>
 * Triggers only when content has been moved to the next fragmentainer once and still does not fit at its start:
 * the <b>sole point of nonprogress</b> that currently falls back to drawing with overflow
 * (two sites: {@code FlowContainer.rescueSplit} for normal flow and {@code FloatSplitPlan.rescue} for floats).
 * There are three reasons for this choice.
 * </p>
 *
 * <ul>
 * <li><b>Normal paths remain completely unchanged</b>.
 * Content that fits immediately or after one postponement never reaches this point,
 * so existing goldens do not change at all.</li>
 * <li><b>The replacement targets precisely where information is lost</b>.
 * Failure is already certain here; rescue restores information rather than degrading it.</li>
 * <li><b>No classifier is needed</b>.
 * Defining unsplittable content by enumerating classes inevitably misses cases.
 * Using the <b>result</b> that the engine's own normal splitting made no progress as the authority
 * captures replaced elements, huge lines, inline blocks, inline tables, ruby,
 * and boxes with mismatched writing directions through the same single path.</li>
 * </ul>
 *
 * <h2>3. Why absolute positioning is excluded</h2>
 *
 * <p>
 * {@link #isRescuablePos(PosType)} explicitly rejects {@code PosType.ABSOLUTE}
 * (the agreed specification).
 * </p>
 *
 * <ul>
 * <li>Absolute positioning often <b>intentionally</b> overflows (watermarks, decorations, bleed).
 * Automatically cutting it is clearly wrong.</li>
 * <li>The reference box may be on another page, making bookkeeping of the continuation start ambiguous.</li>
 * <li>A workaround exists: wrap it in a normal block to make it eligible.
 * Conversely, absolutely positioned children inside a normal block undergoing rescue are cut according
 * to that block's clip.
 * </li>
 * </ul>
 *
 * <h2>4. Why whole-table rescue is deferred</h2>
 *
 * <p>
 * <b>No path is provided</b> for geometrically cutting {@code BoxType.TABLE} .
 * Tables have their own row, row-group, and cell splitting mechanisms;
 * current return values cannot distinguish whether {@code Keep} /{@code Move} means
 * the internal mechanism handled it or progress is genuinely impossible.
 * Cutting without that distinction would clearly degrade tables whose rows split normally by slicing them
 * into strips. <b>Cell contents</b> use the same path with the cell as fragmentainer,
 * so harmful cases (huge images that do not fit cells) are already covered.
 * The corpus also has no whole-table candidates, so this is deferred under the policy of gradual expansion.
 * </p>
 *
 * <h2>5. Two lower bounds against blank pages</h2>
 *
 * <p>
 * Avoiding unintended blank pages is an absolute requirement alongside freedom from infinite loops.
 * Author-intended blank pages (hidden objects or forced page breaks) are valid;
 * only <b>blank or effectively blank pages caused by the engine</b> are defects.
 * Left unchecked, rescue splitting could produce many pages containing only a few pt of content,
 * so {@link #planInFragmentainer} imposes lower bounds on <b>both ends</b>.
 * Both differ from {@link #MIN_RESCUE_ADVANCE} (1 pt):
 * 1 pt is the lower bound for avoiding infinite loops, not permission to cut 1 pt at a time.
 * </p>
 *
 * <ul>
 * <li><b>Start side</b> ({@link #minUsefulSlice(double)}): Do not rescue if the extent available
 * <b>now</b> is too small. Starting cuts when only part of the fragmentainer is free,
 * for example due to float exclusion areas, can produce an endless succession of pages with
 * fragments of a few tens of pt. Uses the greater of an absolute 20 pt and one quarter of capacity;
 * below that, falls back to the existing terminal path and delegates to the outer fragmentainer.</li>
 * <li><b>End side</b> ({@link #MIN_RESCUE_SLICE}): Do not rescue if <b>overflow</b> is too small.
 * Adding a whole page to rescue a few pt makes that page effectively blank.
 * No proportional lower bound applies here because it would also reject the <b>intended use case</b>
 * of an image slightly too tall for A4.</li>
 * </ul>
 *
 * <p>
 * Both affect only <b>whether to begin rescue</b> ({@code offset == 0}).
 * Stopping after cutting has begun because a fragment is too small would lose the remaining content.
 * Once started, slicing therefore always continues while maintaining only the progress guarantee.
 * </p>
 *
 * <p>
 * <b>There is one exception</b> (2026-07-25, independent review).
 * Even for a continuation fragment ({@code offset > 0}), if the destination fragmentainer has less than
 * {@link #MIN_RESCUE_ADVANCE} available, returns
 * {@link RescueDecision.Reason#INSUFFICIENT_CAPACITY} and ends the rescue chain there
 * (the remainder is drawn with overflow as before).
 * <b>This termination is intentional.</b>
 * Choosing to move to the outer fragmentainer here would yield the same decision on every move,
 * because fragmentainer capacity does not change from page to page, causing an <b>infinite loop</b>.
 * The absolute requirement of no infinite loops takes precedence over the absolute requirement of no
 * information loss.
 * Since rescue cannot begin unless {@link #minUsefulSlice(double)} (at least 20 pt) is satisfied,
 * only a degenerate fragmentainer with capacity below 1 pt reaches this termination.
 * </p>
 *
 * <h2>6. Progress guarantee (no infinite loops)</h2>
 *
 * <p>
 * Does not depend on retry counters. The following structure guarantees progress (recommendation §1).
 * </p>
 *
 * <ul>
 * <li>A fragment creating a tail always consumes at least {@link #MIN_RESCUE_ADVANCE} .</li>
 * <li>Creates a fragment only when {@code nextOffset > offset} holds strictly
 * (no rescue if NaN, Infinity, or rounding of extremely large doubles stalls progress).</li>
 * <li>Once the remainder fits capacity, it becomes {@code lastFragment} and creates no tail.</li>
 * </ul>
 *
 * <p>
 * Thus every fragment for which rescue causes a page break consumes a positive amount,
 * and the remainder strictly decreases. A {@link RescueDecision.None} decision falls back to the
 * existing terminal behavior (draw with overflow at the page start), with no retry.
 * Runtime code also double-checks this ({@code tailOffset > offset} in the integration code).
 * Although the invariant check is redundant, it protects an absolute requirement.
 * </p>
 *
 * <h2>7. What is deliberately excluded (a guard against future temptations)</h2>
 *
 * <p>
 * Recommendation §7 ruled out the following. As of increment 8 on 2026-07-25,
 * <b>none is implemented</b>. Before adding one, first consider whether this list already argues against it.
 * Rescue must remain no more than monotonic geometric slicing at the point where failure is certain.
 * </p>
 *
 * <ul>
 * <li><b>Cut-position optimization</b>: No clever cuts seeking glyph or line boundaries.
 * Always cut exactly the available extent. Choosing positions by inspecting content would grow
 * an inferior copy of the normal page-break algorithm.</li>
 * <li><b>Cut-surface decorations</b>: No added lines, marks, whitespace, or duplicate decorations.
 * Although {@code AbsoluteRectFrame} provides {@code cut()} to remove edges, rescue does not use it.
 * Clipping while preserving the original geometry suffices and remains accurate for background images
 * and transforms.</li>
 * <li><b>Rasterization</b>: No page images or rescue-specific Form XObjects; content stays vector-based.
 * No original-box resizing, scale-to-fit, or image recompression.</li>
 * <li><b>Rescue-specific page-span limits</b>: No dedicated cutoff such as "up to 10 pages."
 * The structural progress guarantee makes it unnecessary, and a cutoff would silently lose content.
 * The global {@code output.page-limit} remains honored.
 * </li>
 * <li><b>Precreating all fragments</b>: Creates only one head and one tail at each page break.
 * No fragment list, ThreadLocal, or undo log (the streaming requirement).</li>
 * <li><b>Rollback or retry with another algorithm after failure</b>:
 * A no-rescue decision falls back to the existing terminal path once, with no retry.</li>
 * <li><b>Rescue offsets in Params or LayoutSource recipes</b>:
 * Rescue boxes are short-lived pagination state derived from laid-out boxes, not source events
 * (a replay barrier with {@code sourceAnchor = -1} ).</li>
 * <li><b>Rescue of absolute/fixed positioning itself</b> (see §3 above).</li>
 * <li><b>Annotations (links, image maps, forms) following fragments</b>:
 * Annotation rectangles of rescued elements <b>retain the original box dimensions</b>,
 * and continuation fragments emit no annotations because they are artifacts
 * (2026-07-25, independent review; {@code AbstractVisitor} builds rectangles from box geometry, not clips).
 * <b>Accepted as a known limitation</b>: rescue acts on huge images or lines that do not fit a page,
 * where real examples carrying links or forms are hard to envisage.
 * Duplicating annotations per fragment, meanwhile, creates a separate PDF structure issue:
 * the same link on multiple pages. If a real example appears, reconsider with clipping to the first
 * fragment as the first candidate.</li>
 * </ul>
 *
 * <h2>8. Responsibilities of this type</h2>
 *
 * <p>
 * Centralizes decisions here instead of scattering them (recommendation §4). Inputs are:
 * </p>
 *
 * <ul>
 * <li>whether this is the fragment start ({@code atFragmentStart})</li>
 * <li>available page-direction extent ({@code available})</li>
 * <li>original box's page-direction occupancy ({@code sourcePageExtent})</li>
 * <li>already consumed extent ({@code offset})</li>
 * </ul>
 *
 * <p>
 * Only these four inputs (plus positioning method and capacity) are used;
 * neither boxes nor containers are touched.
 * {@link VisualRescueBox} represents fragments; {@code FlowContainer.rescueSplit} integrates normal flow;
 * {@code FloatSplitPlan.rescue} integrates floats; {@link RescueStats} provides observation;
 * {@link RescuePolicy} supplies the test-only switch.
 * </p>
 */
public final class VisualRescuePlanner {

	private VisualRescuePlanner() {
		// unused
	}

	/**
	 * The minimum extent rescue splitting must consume in one step
	 * ({@code 2 * LayoutUtils.THRESHOLD}, equivalent to 1 pt).
	 *
	 * <p>
	 * {@link LayoutUtils#compare(double, double)} treats differences below {@code THRESHOLD} as equal,
	 * so progress below that cannot be distinguished from no progress.
	 * This value is therefore the minimum for a fragment that creates a tail.
	 * </p>
	 */
	public static final double MIN_RESCUE_ADVANCE = 2 * LayoutUtils.THRESHOLD;

	/**
	 * The <b>practical</b> minimum extent rescue splitting must consume in one step
	 * (2026-07-25, added in increment 4).
	 * It prevents <b>tiny-fragment pages</b> as part of the absolute requirement to avoid unintended
	 * blank or effectively blank pages.
	 *
	 * <p>
	 * {@link #MIN_RESCUE_ADVANCE} (1 pt) is the minimum for avoiding infinite loops,
	 * not permission to cut 1 pt at a time and create dozens of pages.
	 * The value 20 pt equals {@code BreakableBuilder.MIN_PAGE_LIMIT} ,
	 * the sole existing threshold at which the engine ignores smaller page-direction capacities as degenerate.
	 * Aligns with the existing criterion instead of adding a new magic number.
	 * The constant is defined here rather than referenced to avoid duplication because
	 * {@code layout.rescue} must not depend on {@code layout.builder.impl} .
	 * </p>
	 */
	public static final double MIN_RESCUE_SLICE = 20;

	/**
	 * The minimum fraction of fragmentainer capacity rescue splitting must consume in one step
	 * (2026-07-25, increment 4).
	 *
	 * <p>
	 * An absolute lower bound ({@link #MIN_RESCUE_SLICE}) alone still risks an endless succession of pages
	 * with fragments of a few tens of pt when float exclusion areas, for example, leave very little space
	 * on a large page. Also imposes a proportional lower bound:
	 * if less than one quarter of the fragmentainer can be used, do not rescue
	 * (= fall back to the existing terminal behavior).
	 * </p>
	 */
	public static final double MIN_RESCUE_FRACTION = 0.25;

	/**
	 * The minimum available extent required to begin rescue for a given fragmentainer capacity.
	 *
	 * @param capacity fragmentainer's (page/column/cell) inner page-direction extent
	 * @return the lower bound
	 */
	public static double minUsefulSlice(final double capacity) {
		if (!isDefined(capacity) || !(capacity > 0)) {
			return MIN_RESCUE_SLICE;
		}
		return Math.max(MIN_RESCUE_SLICE, capacity * MIN_RESCUE_FRACTION);
	}

	/**
	 * Returns whether the positioning method can be eligible for rescue splitting.
	 *
	 * <p>
	 * Absolute positioning is excluded; the reasons are collected in §3 of this type's class documentation.
	 * {@code BreakableBuilder.addBound()} also immediately sends {@code PosType.ABSOLUTE} to normal placement,
	 * so exclusion is checked twice (recommendation §4).
	 * </p>
	 *
	 * @param posType positioning method (may be {@code null} ; unknown is treated as eligible)
	 * @return true if it can be eligible for rescue splitting
	 */
	public static boolean isRescuablePos(final PosType posType) {
		return posType != PosType.ABSOLUTE;
	}

	/**
	 * Determines the next fragment, including positioning-method exclusions.
	 *
	 * @param posType target box's positioning method
	 * @param atFragmentStart whether this is the start of the fragment (page/column/cell)
	 * @param available available page-direction extent
	 * @param sourcePageExtent original box's page-direction occupancy (immutable)
	 * @param offset page-direction extent already consumed
	 * @return the decision
	 */
	public static RescueDecision plan(final PosType posType, final boolean atFragmentStart, final double available,
			final double sourcePageExtent, final double offset) {
		if (!isRescuablePos(posType)) {
			return new RescueDecision.None(RescueDecision.Reason.ABSOLUTE);
		}
		return plan(atFragmentStart, available, sourcePageExtent, offset);
	}

	/**
	 * Determines the next fragment accounting for fragmentainer capacity
	 * (<b>integration code uses this method</b>).
	 *
	 * <p>
	 * In addition to the progress guarantee (no infinite loops) of
	 * {@link #plan(boolean, double, double, double)} , imposes <b>two lower bounds against blank pages</b>:
	 * {@link #minUsefulSlice(double)} at the start and {@link #MIN_RESCUE_SLICE} at the end.
	 * Their meanings, and the reason for no proportional lower bound at the end,
	 * are collected in §5 of this type's class documentation.
	 * </p>
	 *
	 * <p>
	 * Both lower bounds affect only <b>whether to begin rescue</b> ({@code offset == 0}).
	 * For a fragment already being sliced ({@code offset > 0}), stopping because it is too small would lose
	 * the remaining content (= overflow and discard it as before).
	 * Once started, slicing always continues while maintaining only the progress guarantee.
	 * </p>
	 *
	 * @param posType target box's positioning method
	 * @param atFragmentStart whether this is the start of the fragment (page/column/cell)
	 * @param capacity fragmentainer's inner page-direction extent (capacity)
	 * @param available available page-direction extent
	 * @param sourcePageExtent original box's page-direction occupancy (immutable)
	 * @param offset page-direction extent already consumed
	 * @return the decision
	 */
	public static RescueDecision planInFragmentainer(final PosType posType, final boolean atFragmentStart,
			final double capacity, final double available, final double sourcePageExtent, final double offset) {
		final RescueDecision decision = plan(posType, atFragmentStart, available, sourcePageExtent, offset);
		if (!(decision instanceof RescueDecision.Slice slice)) {
			return decision;
		}
		if (!slice.firstFragment()) {
			// After starting, enforce only the progress guarantee (stopping would lose content).
			return decision;
		}
		if (available < minUsefulSlice(capacity)) {
			return new RescueDecision.None(RescueDecision.Reason.SLIVER_CAPACITY);
		}
		if (sourcePageExtent - slice.nextOffset() < MIN_RESCUE_SLICE) {
			// Guard the tail: the overflow is too small to be useful. Adding a page
			// for a few pt would make it effectively blank.
			return new RescueDecision.None(RescueDecision.Reason.SLIVER_REMAINDER);
		}
		return decision;
	}

	/**
	 * Determines the next fragment (the core decision, checking only the progress guarantee).
	 *
	 * <p>
	 * Does not rescue when {@code atFragmentStart} is false.
	 * The normal option of moving to the next fragment remains available.
	 * Rescue replaces only <b>the point where that option has been exhausted</b>
	 * (= the point currently drawing with overflow).
	 * </p>
	 *
	 * @param atFragmentStart whether this is the start of the fragment (page/column/cell)
	 * @param available available page-direction extent
	 * @param sourcePageExtent original box's page-direction occupancy (immutable)
	 * @param offset page-direction extent already consumed
	 * @return the decision
	 */
	public static RescueDecision plan(final boolean atFragmentStart, final double available,
			final double sourcePageExtent, final double offset) {
		if (!atFragmentStart) {
			return new RescueDecision.None(RescueDecision.Reason.NOT_FIRST);
		}
		if (!isDefined(available) || !isDefined(sourcePageExtent) || !isDefined(offset)) {
			return new RescueDecision.None(RescueDecision.Reason.UNDEFINED_GEOMETRY);
		}
		if (offset < 0 || !(sourcePageExtent > 0)) {
			return new RescueDecision.None(RescueDecision.Reason.INVALID_GEOMETRY);
		}
		final double remaining = sourcePageExtent - offset;
		// Also judge the remainder using LayoutUtils.compare (2026-07-25, increment 4).
		// A raw `remaining > 0` would create an effectively blank fragment page
		// when rounding leaves a remainder such as 0.1 pt. Treat a remainder at or below
		// the difference the engine considers equal (THRESHOLD) as already consumed.
		if (LayoutUtils.compare(remaining, 0) <= 0) {
			return new RescueDecision.None(RescueDecision.Reason.EXHAUSTED);
		}

		// Does the remainder fit capacity (with THRESHOLD tolerance)? If so, this is the last fragment;
		// no tail is created, so no minimum advance is required. Otherwise cut the full capacity,
		// and a tail must follow.
		final boolean fits = LayoutUtils.compare(remaining, available) <= 0;
		if (fits) {
			if (offset == 0) {
				// Already fits at the start; no rescue needed (normal path).
				return new RescueDecision.None(RescueDecision.Reason.FITS);
			}
			final double nextOffset = offset + remaining;
			if (!(nextOffset > offset)) {
				// For example, rounding of extremely large doubles. No progress means no rescue.
				return new RescueDecision.None(RescueDecision.Reason.NO_PROGRESS);
			}
			return new RescueDecision.Slice(offset, remaining, nextOffset, false, true);
		}

		if (!(available >= MIN_RESCUE_ADVANCE)) {
			// Zero, sub-1 pt, or negative capacity. Delegate to the outer fragmentainer.
			return new RescueDecision.None(RescueDecision.Reason.INSUFFICIENT_CAPACITY);
		}
		final double nextOffset = offset + available;
		if (!(nextOffset > offset)) {
			return new RescueDecision.None(RescueDecision.Reason.NO_PROGRESS);
		}
		return new RescueDecision.Slice(offset, available, nextOffset, offset == 0, false);
	}

	/**
	 * Returns true for a finite real number that is not unresolved.
	 * {@code LayoutUtils.NONE} is a magic value representing unresolved values such as AUTO,
	 * so it cannot be treated as geometry even though it is numerically finite.
	 */
	private static boolean isDefined(final double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v) && !LayoutUtils.isNone(v);
	}
}
