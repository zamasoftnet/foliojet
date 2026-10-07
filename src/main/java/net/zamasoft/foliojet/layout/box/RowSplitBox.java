package net.zamasoft.foliojet.layout.box;

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
	 * {first flow index, item count, row start, row extent}), or null if there is no ledger.
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
}
