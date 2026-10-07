package net.zamasoft.foliojet.layout.fragment;

/**
 * Reason for how {@code RootBuilder.pageBreak()}'s collectable-prefix preflight classified each ancestor-chain
 * level (added 2026-07-21, M6b Phase B B1).
 *
 * <p>
 * Behavior is exactly as before: only {@link #PLAIN_FLOW} is collectable; all others stop prefix scanning. This
 * enum makes the reason for non-collectability visible without changing approval conditions (B1 scope established
 * in the ChatGPT Pro consultation, design consultation).
 * </p>
 *
 * @see PaginationContract canonical source for atomic classification by writing direction
 *      (breakDepth barriers and splitPageAxis axis checks consolidated there, 2026-07-30)
 */
public enum ContinuationCapability {
	/** Plain {@code FlowBlockBox} with the same writing direction as the root; collectable. */
	PLAIN_FLOW,

	/**
	 * Multi-column layout ({@code MulticolumnBlockBox}, equivalent to {@code column-count>1}). Previously coexisted
	 * with {@code FLOW_SUBTYPE} for non-column subclasses of {@code FlowBlockBox}. Its only implementation, {@code
	 * RubyBodyBox}, disappeared when ruby switched to annotated text (specification decision on 2026-07-25, the development records), so
	 * that classification was removed. Multi-column layout is now the only {@code FlowBlockBox} subtype.
	 */
	MULTICOL,

	/**
	 * Same axis as the root (horizontal/vertical writing), but a different direction or glyph-rotation variant (e.g.
	 * {@code vertical-lr} within {@code vertical-rl} ancestors, or switching normal and sideways-rl within the same RL
	 * flow). Under the pagination contract of 2026-07-22 (see the development records
	 * -contract-consultation.md), this classification is non-collectable (atomic), like {@link #ORTHOGONAL_FLOW}. It
	 * was temporarily enabled (B5b, 2026-07-21), then withdrawn when policy shifted toward implementation simplicity.
	 * The {@code breakDepth} barrier in {@code BreakableBuilder.startFlowBlock()} was simultaneously extended to
	 * require exact {@code WritingMode} equality, not merely {@code isVertical()}, so this box's ancestor chain is
	 * actually atomic (fits as a whole or moves as a whole to the next page).
	 */
	SAME_AXIS_DIRECTION_CHANGE,

	/** Axis itself differs from the root (horizontal ⇄ vertical writing). */
	ORTHOGONAL_FLOW,

	/** A box other than {@code FlowBlockBox} (table, floating element, etc.). */
	UNSUPPORTED_BOX;

	/**
	 * Equivalent to the existing {@code collectable} check (true only for this enum value, independent of mode).
	 * Represents B0.5–B2 behavior unchanged; use {@link #supportsPageSplitThrough} for actual collection approval from
	 * B3 onward.
	 */
	public boolean isCollectable() {
		return this == PLAIN_FLOW;
	}

	/**
	 * Determines whether split-through via PAGE is allowed (added 2026-07-21, M6b Phase B B3). Separates what the
	 * target is (this enum) from whether collection is allowed in the current break mode; confirmed in the ChatGPT Pro
	 * consultation, see design consultation.
	 *
	 * <p>
	 * B3a allowed {@link #MULTICOL} only for automatic page breaks: when a selected chain member's {@code
	 * splitForContinuation} returned {@code KEEP}/{@code MOVE} at a forced break, {@code FlowContainer.splitPageAxis}
	 * then had a path unconditionally throwing {@code AssertionError("force break failed")} (confirmed in the
	 * forced-break branch of `FlowContainer.java`). B3b-2 (2026-07-21) replaced this raw {@code AssertionError} with
	 * the normal KEEP/MOVE handling documented by {@code AbstractBlockBox.splitForContinuation()}, matching the main
	 * automatic-break loop and {@code AbstractContainerBox.split()}, removing the barrier (B3b-1, 2026-07-21). For
	 * both forced page and column breaks, a selected chain member's {@code KEEP}/{@code MOVE}/{@code Frame} uses the
	 * same path for every {@link ContinuationCapability}, so no reason remains for a mode-dependent restriction.
	 * </p>
	 *
	 * <p>
	 * {@link #SAME_AXIS_DIRECTION_CHANGE} was temporarily enabled on 2026-07-21 (B5b), then withdrawn under the
	 * pagination contract of 2026-07-22 (atomic; see the development records).
	 * </p>
	 */
	public boolean supportsPageSplitThrough(final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return switch (this) {
		case PLAIN_FLOW, MULTICOL -> true;
		default -> false;
		};
	}

	/**
	 * Determines whether split-through via COLUMN (descendants inside the multi-column owner) is allowed (added
	 * 2026-07-21, M6b Phase B B4; B3b-1 also enabled forced column breaks). For the same reason as PAGE's {@link
	 * #supportsPageSplitThrough}, B3b-2 moved KEEP/MOVE handling for selected chain members to the normal
	 * mode-independent path, leaving no reason to restrict only forced column breaks. Descendant {@link #MULTICOL}
	 * (another multi-column layout inside the owner) remains a {@code LegacyOpen} barrier (the owner itself is not
	 * classified; only another MULTICOL appearing inside it reaches this branch). {@link #SAME_AXIS_DIRECTION_CHANGE}
	 * was withdrawn for the same reason as in {@link #supportsPageSplitThrough}.
	 */
	public boolean supportsColumnSplitThrough(final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return switch (this) {
		case PLAIN_FLOW -> true;
		default -> false;
		};
	}

	/**
	 * Classifies a box relative to anchorFlow.
	 *
	 * @param b          target to classify (one open-path level)
	 * @param anchorFlow writing direction of the anchor (PAGE root or COLUMN owner)
	 */
	public static ContinuationCapability classify(final net.zamasoft.foliojet.layout.box.AbstractContainerBox b,
			final net.zamasoft.foliojet.layout.box.params.WritingMode anchorFlow) {
		return classify(b, anchorFlow,
				net.zamasoft.foliojet.layout.box.params.WritingModeVariant.NORMAL);
	}

	/**
	 * Classifies a box relative to the anchor's writing direction and glyph-rotation variant.
	 *
	 * @param b target to classify (one open-path level)
	 * @param anchorFlow anchor writing direction
	 * @param anchorWritingModeVariant anchor glyph-rotation variant
	 */
	public static ContinuationCapability classify(final net.zamasoft.foliojet.layout.box.AbstractContainerBox b,
			final net.zamasoft.foliojet.layout.box.params.WritingMode anchorFlow,
			final net.zamasoft.foliojet.layout.box.params.WritingModeVariant anchorWritingModeVariant) {
		if (!(b instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox)) {
			return UNSUPPORTED_BOX;
		}
		// 2026-07-21 (B5): check the axis before the subtype. Previously, the exact-class check
		// came first, so MulticolumnBlockBox and similar boxes with orthogonal writing-mode
		// were misclassified as MULTICOL rather than ORTHOGONAL_FLOW
		// (found in the codex design consultation). For every FlowBlockBox subtype,
		// first check axis equality uniformly, then proceed to subtype-specific
		// classification only if the axes match.
		final net.zamasoft.foliojet.layout.box.params.BlockParams params = ((net.zamasoft.foliojet.layout.box.impl.FlowBlockBox) b)
				.getBlockParams();
		final net.zamasoft.foliojet.layout.box.params.WritingMode flow = params.flow;
		if (flow != anchorFlow || params.writingModeVariant != anchorWritingModeVariant) {
			return flow.isVertical() != anchorFlow.isVertical() ? ORTHOGONAL_FLOW : SAME_AXIS_DIRECTION_CHANGE;
		}
		// The only current FlowBlockBox subtype is MulticolumnBlockBox (multi-column layout)
		// (the old RubyBodyBox disappeared when ruby switched to annotated text on 2026-07-25).
		// Keep exact-class checks so future unknown subtypes do not fall through
		// to PLAIN_FLOW (the most permissive classification).
		return b.getClass() == net.zamasoft.foliojet.layout.box.impl.FlowBlockBox.class ? PLAIN_FLOW : MULTICOL;
	}
}
