package net.zamasoft.foliojet.layout.fragment;

/**
 * Result of cutting a container in the page direction (C1d-C).
 *
 * <p>
 * Adds continuation-frame propagation ({@link WithFrame}) to the existing {@code Container} return contract
 * (null=KEEP / this=MOVE / other=remainder). Legacy calls without BreakPlan return only {@link Plain}.
 * </p>
 *
 * <p>
 * <b>Mapping to {@link SplitResult} (box granularity)</b>; this type is at container granularity:
 * {@code Plain(null)}=Keep, {@code Plain(creating container itself)}=Move,
 * {@code Plain(other)}=Split (remainder container), {@code WithFrame}=Frame.
 * </p>
 */
public sealed interface ContainerCut {
	/**
	 * Result under the existing contract (container: null=KEEP / self=MOVE / other=remainder).
	 *
	 * <p>
	 * <b>Sentinel semantics and interpretation contract (made explicit in 2026-07-24 E-4)</b>:
	 * "Self" means the {@code FlowContainer} that constructed this Plain; consumers interpret it by identity
	 * comparison with <b>the target container on which they called splitPageAxis</b>. {@code ColumnsContainer}
	 * delegates the cut to the last column and returns the result unchanged, so the MOVE sentinel is <b>the last
	 * column (lastColumn)</b>. Consumers comparing against the multi-column owner's container (such as {@code
	 * AbstractBlockBox.splitForContinuation}) do not match and take the remainder path; consumers comparing against
	 * the column (the activeColumn comparison in {@code AbstractContainerBox.prepareColumnCut}) correctly identify
	 * MOVE. The former behavior is being observed through {@code ContinuationStats.recordLastColumnMoveCandidate}.
	 * Turning sentinels into typed {@code Keep}/{@code Move} variants would collapse these layer-specific
	 * interpretations (which object is compared by identity) into a single meaning and could change behavior.
	 * Introduce those types together with removal of the legacy three-argument {@code Container.splitPageAxis}
	 * contract (B6 family).
	 * </p>
	 */
	record Plain(net.zamasoft.foliojet.layout.box.content.Container container) implements ContainerCut {
	}

	/**
	 * Result explicitly carrying that a plan-selected chain member itself returned {@code Keep}/{@code Move} (added
	 * 2026-07-21, M6b Phase B5c-2). Added in the forced-page-break branch and also in the main automatic-page-break
	 * loop only when nextBox contains the chain member alone (same form as the force branch, indistinguishable by
	 * container identity comparison). Applied in the B5c-2 Step3 retry on 2026-07-22; see the final return of {@code
	 * FlowContainer.splitPageAxis}.
	 *
	 * <p>
	 * <b>Relation to pagination contract §5.10 (made explicit in 2026-07-24 E-4)</b>:
	 * §5.10 rule 2 declared <b>MOVE-only</b> continuation types unnecessary ({@code OpenTail.MovedOpen}/{@code
	 * ResumeTail.MovedOpen}, removed 2026-07-22). This type is not MOVE-only: it marks the chain member's own decision
	 * for both KEEP and MOVE. It is permanently required to convey results to the parent that container identity
	 * comparisons cannot distinguish during page breaks across multi-column layout (§5.10 rule 4). No removal is
	 * planned. As rule 2 requires, continuation of a remainder created by MOVE introduces no dedicated type; it joins
	 * the existing {@code OpenTailShape}/{@code ContinuationFrame} mechanism (the merge branch for containers with
	 * actual content; see {@code AbstractBlockBox.splitForContinuation}, etc.).
	 * </p>
	 */
	record PlainWithChainStop(net.zamasoft.foliojet.layout.box.content.Container container, ChainStopReason reason)
			implements ContainerCut {
	}

	/**
	 * The cut passed through the chain, returning a continuation frame along with the remainder container (the chain
	 * child is not carried as a box).
	 */
	record WithFrame(net.zamasoft.foliojet.layout.box.content.Container container,
			Continuation.ContinuationFrame frame) implements ContainerCut {
	}
}
