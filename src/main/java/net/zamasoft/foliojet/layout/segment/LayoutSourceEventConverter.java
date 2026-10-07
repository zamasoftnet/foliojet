package net.zamasoft.foliojet.layout.segment;

import java.util.Optional;

import net.zamasoft.foliojet.layout.fragment.LayoutSource;

/**
 * An adapter converting {@link LayoutSource.Event} to {@link SegmentEvent}
 * (introduced 2026-07-22, M6d-A3c).
 *
 * <p>
 * Maintains a 1:1 event count to preserve M6d-A2 ordinal correspondence and shadow comparison
 * (confirmed in the codex design consultation).
 * {@code Start} /{@code Replaced} simply wrap recipes already frozen at recording time ({@code StyleBuilder})
 * in the corresponding {@link SegmentEvent.BeginBox} /{@link SegmentEvent.Replaced}
 * (E-6 increments 3b-3/3b-4; the transitional live-box-retaining variant {@code ReplacedLive} was removed
 * in 3b-6, and {@code ReplacedBoxImage} also freezes through duplication).
 * {@code Opaque} (a non-replayable-range marker) always becomes {@link SegmentEvent.Barrier} ,
 * with an explicit reason ({@link BarrierReason#NOT_YET_SUPPORTED}) to avoid silent fallback.
 * </p>
 *
 * <p>
 * Does not interact with the {@code LayoutSource} itself or {@code SourceReplayer} ;
 * performs read-only conversion only.
 * </p>
 */
public final class LayoutSourceEventConverter {
	private LayoutSourceEventConverter() {
	}

	/**
	 * Determines whether {@code event} becomes {@link SegmentEvent.Barrier} through {@link #convert}
	 * (= non-replayable) without decoding payloads or allocating conversion objects
	 * (E-6 increment 3b-5; for single-pass streaming scans of range eligibility).
	 * Must always stay synchronized with {@link #convert} 's classification.
	 * After the live variant's removal in 3b-6, {@code Opaque} is the only Barrier source.
	 */
	public static boolean convertsToBarrier(final LayoutSource.Event event) {
		return event instanceof LayoutSource.Opaque;
	}

	/** Converts one {@link LayoutSource.Event} to its corresponding {@link SegmentEvent}. */
	public static SegmentEvent convert(final LayoutSource.Event event) {
		return switch (event) {
		case LayoutSource.Assignment(final long order) -> new SegmentEvent.Assignment(order);
		// E-6 increment 3b-4: Wrap the recipe already frozen at recording time
		// (StyleBuilder.startBox); conversion-time freezing was moved forward to recording time.
		// PlacedTable uses the same 1:1 conversion; no extra ordinal is added for the table host.
		case LayoutSource.Start(final BoxRecipe recipe) -> new SegmentEvent.BeginBox(recipe);
		case LayoutSource.EndBlock endBlock -> new SegmentEvent.EndBox();
		case LayoutSource.AnonymousItemStart(final long anchor) -> new SegmentEvent.AnonymousItemStart(anchor);
		case LayoutSource.AnonymousItemEnd end -> new SegmentEvent.AnonymousItemEnd();
		case LayoutSource.Chars(final int charOffset, final LayoutSource.TextPayload payload, final boolean fixed) ->
			// freshChars(): Inline=clone, Spilled=decode from the store (E-6 increment 3b-2)
			new SegmentEvent.Text(charOffset, new String(payload.freshChars()), fixed);
		// E-6 increment 3b-3: Wrap the recipe already frozen at recording time
		// (StyleBuilder.addReplacedBox); conversion-time freezing was moved forward to recording time.
		case LayoutSource.Replaced(final ReplacedRecipe recipe) -> new SegmentEvent.Replaced(recipe);
		// Opaque carries no kind information (a fieldless position-occupying
		// marker), so always convert it to a Barrier.
		case LayoutSource.Opaque opaque ->
			new SegmentEvent.Barrier(Optional.empty(), BarrierReason.NOT_YET_SUPPORTED);
		// leader() L1: Immutable payload containing only the pattern string (1:1 conversion)
		case LayoutSource.Leader(final String pattern) -> new SegmentEvent.Leader(pattern);
		};
	}
}
