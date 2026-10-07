package net.zamasoft.foliojet.layout.segment;

/**
 * A replay event in the canonical Segment model (introduced on 2026-07-22, M6d-A3a;
 * used by {@link LayoutSourceEventConverter}, {@code SegmentExecutor}, and {@code SourceReplayer}
 * as of 2026-07-25).
 *
 * <p>
 * Maintains a 1:1 event count with the former
 * {@code net.zamasoft.foliojet.layout.fragment.LayoutSource.Event}
 * (five variants: {@code Start}/{@code Replaced}/{@code Chars}/
 * {@code EndBlock}/{@code Opaque}) to preserve the M6d-A2 ordinal mapping and future shadow comparisons
 * (confirmed in the codex design consultation), but normalizes their meaning:
 * </p>
 * <ul>
 * <li>{@code Start} → {@link BeginBox} (recipe only, without child structure:
 * a recipe describes "how to create a box," not "structure"; keep these separate)</li>
 * <li>{@code EndBlock} → {@link EndBox} (renamed to reflect its actual role)</li>
 * <li>{@code Chars} → {@link Text} ({@code char[]} becomes {@code String},
 * preventing callers from obtaining and modifying a mutable array through the record)</li>
 * <li>{@code Replaced} → {@link Replaced} (holds a {@link ReplacedRecipe} instead of a live box)</li>
 * <li>{@code Opaque} → {@link Barrier} (always specifies a reason through {@link BarrierReason}
 * instead of occupying a position without explanation, avoiding silent fallback)</li>
 * </ul>
 */
public sealed interface SegmentEvent {
	/** An assignment position in the main flow. Does not produce layout input during replay either. */
	record Assignment(long order) implements SegmentEvent {
	}
	/**
	 * The start of a box. Does not include child content structure (references to children);
	 * the sequence through {@link EndBox} represents that structure.
	 * This single BeginBox also includes the placement host of a PlacedTable, and a single EndBox closes it.
	 */
	record BeginBox(BoxRecipe recipe) implements SegmentEvent {
	}

	/** The end of a box (formerly {@code EndBlock}, renamed to reflect its actual role). */
	record EndBox() implements SegmentEvent {
	}

	/** Maps 1:1 to a synthetic LayoutSource boundary. Holds no recipe for an authored box. */
	record AnonymousItemStart(long anchor) implements SegmentEvent {
	}

	record AnonymousItemEnd() implements SegmentEvent {
	}

	/**
	 * Text.
	 *
	 * @param sourceOffset Source character offset ({@code -1} for generated content,
	 *                     following the same convention as the former {@code Chars.charOffset})
	 * @param text         Text content (an immutable {@code String}, preventing callers from obtaining
	 *                     and modifying the former {@code char[]} through the record)
	 * @param fixed        Fixed-text flag in the doc protocol
	 */
	record Text(int sourceOffset, String text, boolean fixed) implements SegmentEvent {
	}

	/** A replaced element. Holds a {@link ReplacedRecipe} instead of a live box. */
	record Replaced(ReplacedRecipe recipe) implements SegmentEvent {
	}

	/**
	 * A placeholder for content that does not yet support normalization or replay
	 * (equivalent to the former {@code Opaque}; during A3c implementation, the former {@code Replaced}
	 * also turned out to convert to this while the contents of {@code ReplacedRecipe} remained undesigned).
	 * Always carries a reason ({@link BarrierReason}) to avoid silent fallback.
	 *
	 * @param kind   Original box kind, if known (set here when the former {@code Start} held an
	 *               unsupported {@code BoxKind}). Empty ({@link
	 *               java.util.Optional#empty()}) for the former {@code Opaque} and {@code Replaced},
	 *               which never held kind information.
	 * @param reason Reason
	 */
	record Barrier(java.util.Optional<BoxKind> kind, BarrierReason reason) implements SegmentEvent {
	}

	/**
	 * {@code leader()} (css-content-3, leader() L1:
	 * consult-codex-2026-07-31-leader.txt; maps 1:1 to the former {@code LayoutSource.Leader}).
	 * The payload contains only the normalized pattern string; shaping and width allocation run on each execution.
	 */
	record Leader(String pattern) implements SegmentEvent {
	}
}
