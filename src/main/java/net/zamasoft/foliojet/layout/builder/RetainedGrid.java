package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.impl.GridBox;

/**
 * Contract for a Grid retained execution plan to incorporate itself into the host builder
 * (Grid G3d1, 2026-07-31; consult-codex-2026-07-31-grid-g3.txt Q3. Analogous to {@link RetainedTable}).
 *
 * <p>
 * In normal flow (a BlockBuilder host), {@code addGrid} immediately calls {@link #bind}.
 * A TwoPass host registers the plan in the ownership ledger and releases it when absorbed into the
 * parent range. Range replay reconstructs the plan from the same source and places it using
 * the same {@link #bind}.
 * </p>
 *
 * @see Builder#addGrid(RetainedGrid)
 */
public interface RetainedGrid extends TwoPass {

	public GridBox getGridBox();

	/**
	 * Incorporates the constructed Grid into the host (track-width resolution → item bind → row placement →
	 * parent cursor synchronization). Call while the host's active flow is this GridBox.
	 */
	public void bind(Builder host);

}
