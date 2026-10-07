package net.zamasoft.foliojet.layout.box.content;

/**
 * The destination of a moved-float ledger specified by the caller of
 * {@code Container.splitFloatings} (added 2026-07-24, exclusion area P2, P2-4;
 * the type from design consultation §2.2).
 * Replaces the sentinels in the old API's nullable {@code Container nextBox} argument
 * (null / this / existing container).
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatTransferTarget {
	/** Destination container undecided (formerly null). Creates a new FlowContainer if any floats move. */
	FloatTransferTarget KEEP = new Keep();

	/** A context where the entire owner moves to the next fragment (formerly this). */
	FloatTransferTarget MOVE_OWNER = new MoveOwner();

	record Keep() implements FloatTransferTarget {
	}

	record MoveOwner() implements FloatTransferTarget {
	}

	/**
	 * Attaches to an existing next-fragment container (formerly specifying nextBox).
	 *
	 * @param container the next-fragment container
	 */
	record Existing(FlowContainer container) implements FloatTransferTarget {
	}
}
