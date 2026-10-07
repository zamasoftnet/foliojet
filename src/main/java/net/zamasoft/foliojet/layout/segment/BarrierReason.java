package net.zamasoft.foliojet.layout.segment;

/**
 * The reason a {@link SegmentEvent.Barrier} occurs
 * (introduced 2026-07-22, M6d-A3a; actually produced by {@link LayoutSourceEventConverter}
 * and consumed by {@code SourceReplayer} as of 2026-07-25).
 *
 * <p>
 * The former {@code LayoutSource.Opaque} was merely a position-occupying marker without any reason.
 * Carrying it unchanged into the canonical model would make it impossible to trace why replay failed,
 * inviting silent fallback (noted in the codex design consultation).
 * This enum requires an explicit reason.
 * </p>
 */
public enum BarrierReason {
	/** Content not yet represented as {@code SegmentEvent}, such as tables/replaced elements (former {@code Opaque}). */
	NOT_YET_SUPPORTED,
	/** Conversion detected an unknown type and took the safe path (fail closed). */
	UNKNOWN_TYPE,
	/** The corresponding {@code Start} is not yet closed (subtree not finalized). */
	UNCLOSED_SUBTREE;
}
