package net.zamasoft.foliojet.layout.fragment;

/**
 * Page-axis split result dedicated to block floats (added 2026-07-24, exclusion area A-3a).
 * Differs from {@link SplitResult} only in the Split case: instead of constructing the remainder
 * box immediately, returns its construction material ({@link PreparedFloatFragment}).
 * Keep/Move have the same meanings as in {@link SplitResult}. There is no Frame (chain continuation)
 * because it does not occur for floats.
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatFragmentSplit {
	/** Keeps everything in the preceding fragment. */
	FloatFragmentSplit KEEP = new Keep();

	/** Moves everything to the next fragment. */
	FloatFragmentSplit MOVE = new Move();

	record Keep() implements FloatFragmentSplit {
	}

	record Move() implements FloatFragmentSplit {
	}

	/**
	 * Split internally. The preceding fragment has already been mutated; carries construction
	 * material for the remainder box (the receiver calls {@code materialize} exactly once).
	 *
	 * @param fragment material for the continuation fragment
	 */
	record Prepared(PreparedFloatFragment fragment) implements FloatFragmentSplit {
	}
}
