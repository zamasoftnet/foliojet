package net.zamasoft.foliojet.layout.fragment;

/**
 * Reason that the {@code SplitResult} returned by a plan-selected (collectable) chain member's own {@code
 * splitForContinuation}/{@code split} was not a {@code Frame} (added 2026-07-21, M6b Phase B5c-2). Along with
 * {@code ContainerCut.PlainWithChainStop}, this type is permanently required for page breaks across multi-column
 * layout (pagination contract §5.10 rule 4) and is not scheduled for removal. Distinct from the MOVE-only types
 * declared unnecessary by §5.10 rule 2 ({@code MovedOpen} family, removed 2026-07-22): this type carries both KEEP
 * and MOVE (see the Javadoc for {@code ContainerCut.PlainWithChainStop}).
 */
public enum ChainStopReason {
	/** The chain member returned {@code SplitResult.Keep} (keep the whole box on the current side). */
	KEEP,
	/** The chain member returned {@code SplitResult.Move} (send the whole box to the next side). */
	MOVE
}
