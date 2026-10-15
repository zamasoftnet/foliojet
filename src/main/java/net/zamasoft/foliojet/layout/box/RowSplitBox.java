package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * Marks a {@link PageAtomicBox} whose own {@code split} performs pagination by row
 * (with the same contract as table rows) only when it has a row-boundary ledger.
 * Generalized from a flex-only check when grid row splitting was introduced on 2026-08-10.
 *
 * <p>
 * Only {@code PaginationContract.splitsInPageAxis} checks this marker to override
 * PageAtomicBox's "always atomic" behavior.
 * It does not override {@code isChainAtomicBoundary}: if the chain continuation
 * {@code BreakPlan} selects it as a member, processing detours from directly calling
 * {@code split} to generic source-replay continuation, corrupting the positions of continuation
 * items created by forced row splitting (measured on 2026-08-07).
 * This asymmetry is intentional, for the same reason as tables.
 * </p>
 */
public interface RowSplitBox {

	/**
	 * Whether row splitting applies. If false, use the existing PageAtomicBox path
	 * (move the whole box / visual rescue).
	 */
	boolean hasRowSplitLines();

	/**
	 * Returns a snapshot of the row ledger (in visual order; each row is
	 * {first flow index, item count, row start, row extent, gap before the row}), or null if there is no ledger. The
	 * gap was added on 2026-10-09; a ledger without it has its gaps inferred from the starts and extents.
	 * The push-down when restoring {@code RowSplitContainer}
	 * (see {@code restoreAnchoredPageAxis}) uses it to group rows
	 * (2026-08-19).
	 */
	double[][] rowLedgerSnapshot();

	/**
	 * Writes row starts back to the ledger after push-down (same order and count as
	 * {@link #rowLedgerSnapshot}). Keeps the ledger consistent with actual drawing positions
	 * so subsequent splits search for boundaries correctly (2026-08-19).
	 */
	void syncRowStarts(double[] starts);

	/**
	 * Writes row starts and row extents back to the ledger after the push-down (2026-10-09). The extent of a row whose
	 * content came out taller when laid out again is raised to that content, so the next split sees where the row
	 * really ends. The default keeps the extents, as {@link #syncRowStarts} did.
	 */
	default void syncRowGeometry(final double[] starts, final double[] extents) {
		this.syncRowStarts(starts);
	}

	/**
	 * The page-axis extent an item takes for its content alone (2026-10-09): its frame and content, without the free
	 * space its container gave it (a column's flex main size, stretch, a definite height). A row follows its items'
	 * content only when one of them reaches the row's extent this way; then the remainder of the row is what is left
	 * of that content after the kept side. An item in another writing mode, or with no content (all frame), counts as
	 * taking nothing: what is left of it is the line less what the kept side took, as before.
	 */
	static double contentPageExtent(final FlowBlockBox item, final double extent, final WritingMode flow) {
		final double content = item.getContainer().getContentSize();
		if (item.getBlockParams().flow != flow || content <= 0) {
			return 0;
		}
		// Its frame and content, not its extent less the free space inside it: the inner size of an item is not always
		// set (an item of inline boxes in a vertical-lr table cell measured 0 inside an extent of 26.88pt)
		return Math.min(extent,
				item.getFrame().getFramePageStart(flow) + content + item.getFrame().getFramePageEnd(flow));
	}

	/**
	 * Whether the cut line crosses an item of {@code break-inside: avoid} that fits in a fragmentainer (2026-10-15,
	 * TECH-20261015-006 ②). The row then goes to the next page whole, as a table row with such a cell does
	 * ({@code TableCutter.rowPreDecide}). Before, a grid or flex container split the item at the cut line: it calls the
	 * item's split directly, past the avoid check that a block child gets from its parent's FlowContainer. The caller
	 * leaves out a row at the page start (the avoid yields there) and a split after the parent relaxed its avoids
	 * ({@code FLAGS_AVOID_SPENT}); an item taller than a fragmentainer splits as before.
	 *
	 * @param items     the row's items
	 * @param offsets   each item's page-axis offset from the row start
	 * @param extents   each item's page-axis extent before any split
	 * @param below     whether each item lies wholly below the cut line
	 * @param remaining the cut line's distance from the row start
	 */
	static boolean avoidsBreakInside(final FlowBlockBox[] items, final double[] offsets, final double[] extents,
			final boolean[] below, final double remaining, final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		final double capacity = mode instanceof net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode auto
				? auto.fragmentCapacity : -1;
		for (int k = 0; k < items.length; ++k) {
			if (!below[k]
					&& items[k].getBlockParams().pageBreakInside == net.zamasoft.foliojet.layout.box.params.PageBreakMode.AVOID
					&& net.zamasoft.foliojet.layout.util.LayoutUtils.compare(offsets[k] + extents[k], remaining) > 0
					&& (capacity <= 0
							|| net.zamasoft.foliojet.layout.util.LayoutUtils.compare(extents[k], capacity) <= 0)) {
				return true;
			}
		}
		return false;
	}
}
