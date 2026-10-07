package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Common contract for construction coordinators that turn direct children into items and lay them
 * out together at the end (2026-08-02). Generalizes the DocumentBuilder hooks previously mirrored
 * in Grid/Flex: checking for pending direct children, anonymous items, folding element items,
 * and finish at the end. Excludes {@code TableBuilder}, which has its own row/cell protocol.
 *
 * <p>
 * Implementations are pushed onto {@code DocumentBuilder.builderStack} but are not {@code Builder}s.
 * Item builders returned by {@code requireAnonymousItem(anchor)}, etc. (usually
 * {@code TwoPassBlockBuilder}) receive item content; the coordinator itself only retains recordings
 * and performs final placement.
 * </p>
 */
public interface ItemCoordinator {

	/** Container box being constructed by the coordinator (for matching boxStack when checking pending direct children). */
	public IBox getItemHostBox();

	/** Whether an item is open (element or anonymous). */
	public boolean hasOpenItem();

	/** Whether an element item is open. */
	public boolean hasOpenElementItem();

	/**
	 * Opens an anonymous item for direct text/inlines. Returns null if an anonymous item is already
	 * open (no need to push it again). sourceAnchor is the EventId of the synthetic Start;
	 * -1 for independent replay.
	 */
	public Builder requireAnonymousItem(long sourceAnchor);

	/** Finalizes the open item (recording completion point). */
	public void itemClosed();

	/** Container end (passes an execution plan to the host). */
	public void finish();
}
