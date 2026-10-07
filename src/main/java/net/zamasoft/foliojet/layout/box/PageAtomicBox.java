package net.zamasoft.foliojet.layout.box;

/**
 * Marks a box that does not split structurally along the page axis
 * (Grid G0, 2026-07-31: consult-codex-2026-07-31-grid.txt §1.2).
 *
 * <p>
 * Unlike {@code page-break-inside: avoid} (overridden at the page start to split content),
 * this typed contract <b>always</b> prohibits splitting. It affects both predicates in
 * {@link net.zamasoft.foliojet.layout.fragment.PaginationContract}
 * (chain atomic boundary = suppress automatic page breaks from within / whether geometric
 * splitting in place is allowed). Applying it to only one does not make the box truly atomic.
 * If it does not fit on the page, it follows the replaced-element path: move the whole box
 * to the next page, then use visual rescue strip splitting if it still does not fit.
 * </p>
 */
public interface PageAtomicBox {

	/**
	 * Returns whether the atomic contract is currently active (added 2026-08-10 for G6 row splitting).
	 *
	 * <p>
	 * The default is true (always atomic). GridBox returns true only if track placement
	 * (GridBuilder.bind) actually ran. A nested grid that falls back to G0 (single-column normal flow)
	 * with TwoPass inactive has no track placement to protect; treating it as atomic merely
	 * sinks all its content onto the next page (measured on an actual gigazine.net page:
	 * the nested .content grid inside #main did this, leaving the head fragment empty even
	 * after row splitting was added to the outer #main).
	 * </p>
	 */
	default boolean isPageAtomicNow() {
		return true;
	}
}
