package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * A cut result returned by {@code AbstractContainerBox.prepareColumnCut()} that has not yet been committed
 * to the owner (introduced 2026-07-21, M6b Phase B B4).
 *
 * <p>
 * "Prepared" does not mean a completely side-effect-free dry run:
 * {@code ownerContainer.splitPageAxis()} has already cut the original active column.
 * Its precise meaning is a cut result whose addition of a new column to the owner and start of builder
 * resume have not yet been committed (confirmed in the ChatGPT Pro consultation; see the design consultation).
 * As on the PAGE path, structural validation failure after split must abort the entire conversion;
 * do not roll back to the old path and rerun it.
 * </p>
 *
 * @param owner multi-column owner box (checked against this for identity at commit)
 * @param expectedOwnerContainer owner.container at prepare time (for identity validation at commit)
 * @param expectedActiveColumn active column at prepare time
 * @param expectedActualColumnCount number of materialized columns at prepare time
 * @param newPageExtent new page-axis extent to set on the owner at commit
 * @param ownerRemainder remainder container directly under the owner (content carried to the next column)
 * @param childFrame continuation frame if the cut passes through (null otherwise)
 */
public record PreparedColumnCut(AbstractContainerBox owner, Container expectedOwnerContainer,
		Container expectedActiveColumn, int expectedActualColumnCount, double newPageExtent, Container ownerRemainder,
		Continuation.ContinuationFrame childFrame) {
}
