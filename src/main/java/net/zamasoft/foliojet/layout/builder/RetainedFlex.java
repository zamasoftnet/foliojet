package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;

/**
 * Contract for a Flex retained execution plan to incorporate itself into the host builder
 * (Flex F1f, 2026-08-02; consult-codex-2026-08-02-flexbox.txt. Analogous to {@link RetainedGrid}).
 *
 * <p>
 * In normal flow (a BlockBuilder host), {@code addFlex} immediately calls {@link #bind}.
 * A TwoPass host registers the plan in the ownership ledger and releases it when absorbed into the
 * parent range. Range replay reconstructs the plan from the same source and places it using
 * the same {@link #bind}.
 * </p>
 *
 * @see Builder#addFlex(RetainedFlex)
 */
public interface RetainedFlex extends TwoPass {

	public FlexBox getFlexBox();

	/**
	 * Incorporates the constructed Flex into the host (§9.7 resolution → item bind → row placement →
	 * parent cursor synchronization). Call while the host's active flow is this FlexBox.
	 */
	public void bind(Builder host);

}
