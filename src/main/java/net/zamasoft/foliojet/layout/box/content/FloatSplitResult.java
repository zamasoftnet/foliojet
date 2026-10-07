package net.zamasoft.foliojet.layout.box.content;

/**
 * The typed pagination result for {@link Floatings} (added 2026-07-24, exclusion area P2,
 * P2-3; the type from design consultation §2.2). Replaces the old sentinels
 * (null=KeepAll / this=MoveAll / new=Partition).
 *
 * <p>
 * <b>Terminology map (E-4)</b>: The {@code All} suffix means the entire ledger
 * ({@link Floatings}), intentionally distinct from {@code SplitResult.Keep}/{@code Move}
 * for one box and {@link FloatSplitPlan.FloatItemPlan} for one float.
 * {@code Partition} divides the ledger into a retained side and a moved side ({@code remainder});
 * this differs from {@code SplitResult.Split} for one box. See the {@code SplitResult} Javadoc
 * for the full map.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatSplitResult {
	/** Keeps all floats in the original fragment (formerly null). Leaves the original list untouched. */
	FloatSplitResult KEEP_ALL = new KeepAll();

	/**
	 * Sends all floats in their entirety to the next fragment (formerly this). A deferred representation:
	 * floats remain in the original {@link Floatings}; the caller (owner) reassigns the whole ledger.
	 */
	FloatSplitResult MOVE_ALL = new MoveAll();

	record KeepAll() implements FloatSplitResult {
	}

	record MoveAll() implements FloatSplitResult {
	}

	/**
	 * Sends part of the ledger to the next fragment (formerly new Floatings).
	 * The original {@link Floatings} retains KEEP and SPLIT sources in their original order;
	 * {@code remainder} contains moved original Floatings and SPLIT remainders
	 * (at coordinates (0,0), with inherited serials) in their original order.
	 * {@code remainder} is never empty.
	 *
	 * @param remainder the nonempty ledger to send to the next fragment
	 */
	record Partition(Floatings remainder) implements FloatSplitResult {
	}
}
