package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * Declares writing-direction decisions for the pagination contract in one place
 * (ARCHITECTURE.md §5.10, finalized 2026-07-22; introduced 2026-07-30).
 *
 * <p>
 * Previously, deciding which boxes are atomic (fit entirely or move entirely to the next page) relied on
 * <b>two writing-direction comparisons coincidentally agreeing</b>: the {@code breakDepth} barrier in
 * {@code BreakableBuilder.startFlowBlock()} and the axis comparison in
 * {@code FlowContainer.splitPageAxis()} . No type exposed the contract for queries.
 * When adding new layouts such as CSS Grid/Flexbox (no fragmentation = atomic),
 * add the decision here instead of adding more writing-direction comparisons.
 * </p>
 *
 * <p>
 * Both predicates require an exact {@link WritingMode} match: a box whose block flow runs the other way on the
 * same axis (RL⇄LR) is atomic like an orthogonal one. {@link #isChainAtomicBoundary} prevents automatic page
 * breaking from starting inside the box at all (commit {@code 77eef99} , same treatment as
 * {@link ContinuationCapability#SAME_AXIS_DIRECTION_CHANGE} ), and {@link #splitsInPageAxis} keeps it from being
 * cut in place: it fits entirely or moves entirely to the next page, as in Chrome.
 * {@link #splitsInPageAxis} compared only the axis until 2026-10-09. Cutting a reversed box put its start (where
 * its content begins) past the cut, and column balancing cut it into column-high pieces it could not fill: in a
 * column-count: 3 or more multicol that left the box in a column narrower than the page's rest, and its text off
 * the page (sweep defect R).
 * </p>
 *
 * @see ContinuationCapability classification for ancestor-chain collection (open chain)
 */
public final class PaginationContract {
	private PaginationContract() {
		// Static utility
	}

	/**
	 * Determines whether a child block's writing direction {@code inner} forms an atomic boundary for
	 * chain continuation relative to the parent flow's writing direction {@code outer} .
	 *
	 * <p>
	 * Automatic page breaks do not start inside this boundary (§5.10 rule 3:
	 * absolute boxes and boxes whose writing direction differs from the main flow are atomic).
	 * {@code WritingMode} is an enum, so reference comparison suffices.
	 * Comparing only {@code isVertical()} misses RL/LR differences on the same axis.
	 * </p>
	 */
	public static boolean isChainAtomicBoundary(final WritingMode outer, final WritingMode inner) {
		return outer != inner;
	}

	/**
	 * Variant that examines the box itself (Grid G0, 2026-07-31).
	 * Boxes marked with {@link net.zamasoft.foliojet.layout.box.PageAtomicBox}
	 * (always unsplittable, such as Grid) form atomic boundaries regardless of writing direction.
	 *
	 * @param outer parent flow's writing direction
	 * @param box child block
	 */
	public static boolean isChainAtomicBoundary(final WritingMode outer,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox box) {
		if (box instanceof net.zamasoft.foliojet.layout.box.PageAtomicBox atomic && atomic.isPageAtomicNow()) {
			// **This remains true even for flex row splitting (2026-08-07, Bug C)**:
			// isChainAtomicBoundary and splitsInPageAxis intentionally differ in strength
			// (see the comment at the top of this file). Also treating a row-splittable FlexBox
			// as non-atomic here would allow the open-chain continuation's
			// {@code BreakPlan} to select FlexBox as a chain member,
			// making {@code FlowContainer.splitPageAxis} take a detour
			// instead of directly calling {@code FlexBox.split}:
			// it would use {@code splitForContinuation} (generic source-replay-based continuation).
			// As a result, positions of continuation items created by forced row splitting
			// would be overwritten independently by BlockBuilder's sequential cursor, breaking
			// cross-axis alignment (confirmed by measurement: three cards formed a staircase).
			// For the same reason as tables, retain the asymmetry: relax only splitsInPageAxis,
			// allow direct calls to split(), and never admit the box into the chain.
			return true;
		}
		return isChainAtomicBoundary(outer, box.getBlockParams().flow);
	}

	/**
	 * A limited escape from PageAtomicBox (2026-08-07, Bug C, flex row splitting;
	 * generalized to {@link net.zamasoft.foliojet.layout.box.RowSplitBox} for grid row splitting on
	 * 2026-08-10).
	 *
	 * <p>
	 * Only when row-boundary bookkeeping is finalized does the box's own {@code split} implement forced
	 * row-by-row splitting (analogous to table rows). Only for {@link #splitsInPageAxis} ,
	 * this overrides PageAtomicBox's always-atomic rule.
	 * Configurations without that bookkeeping (flex column-direction, grid rowSpan&gt;1, etc.)
	 * remain excluded and follow the existing atomic path.
	 * </p>
	 */
	private static boolean isRowSplitEligible(final net.zamasoft.foliojet.layout.box.AbstractContainerBox box) {
		return box instanceof net.zamasoft.foliojet.layout.box.RowSplitBox rowSplit && rowSplit.hasRowSplitLines();
	}

	/**
	 * Determines whether a child block with writing direction {@code inner} can be cut in place in a context with
	 * writing direction {@code outer}.
	 *
	 * <p>
	 * Children on a different axis (equivalent to {@link ContinuationCapability#ORTHOGONAL_FLOW} ) or in the other
	 * direction on the same axis (equivalent to {@link ContinuationCapability#SAME_AXIS_DIRECTION_CHANGE} ) are not
	 * cut; they follow the same atomic path as replaced elements (keep entirely or move entirely to the next page).
	 * </p>
	 */
	public static boolean splitsInPageAxis(final WritingMode outer, final WritingMode inner) {
		return outer == inner;
	}

	/**
	 * Variant that examines the box itself (Grid G0). {@code PageAtomicBox} also skips geometric cutting
	 * in place (move entirely → visual rescue).
	 */
	public static boolean splitsInPageAxis(final WritingMode outer,
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox box) {
		if (box instanceof net.zamasoft.foliojet.layout.box.PageAtomicBox atomic && atomic.isPageAtomicNow()
				&& !isRowSplitEligible(box)) {
			return false;
		}
		return splitsInPageAxis(outer, box.getBlockParams().flow);
	}
}
