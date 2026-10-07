package net.zamasoft.foliojet.layout.box.content;

/**
 * The typed result of {@code Container.splitFloatings} (added 2026-07-24, exclusion area P2,
 * P2-4; the type from design consultation §2.2). Replaces the old API's return sentinels
 * (nextBox unchanged / this / new container). The authoritative branch table is the
 * "public three-argument version" table in the development log.
 *
 * <p>
 * <b>Terminology map (E-4)</b>: The {@code Owner} suffix refers to the called owner container itself:
 * {@code KeepOwner} means no floats move (nothing happens to the owner),
 * and {@code MoveOwner} means the entire owner moves to the next fragment.
 * These are intentionally distinct from KeepAll/MoveAll in {@link FloatSplitResult}
 * for the whole ledger, and {@code SplitResult.Keep}/{@code Move} for one box.
 * {@code Remainder} carries the transfer destination: a container with the remainder ledger
 * from {@link FloatSplitResult.Partition} attached. See the {@code SplitResult} Javadoc for the full map.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatTransferResult {
	/**
	 * No floats move (formerly returning the nextBox argument unchanged).
	 * The caller continues to use the container indicated by its {@link FloatTransferTarget}.
	 */
	FloatTransferResult KEEP_OWNER = new KeepOwner();

	/**
	 * The entire owner moves to the next fragment (formerly this).
	 * Used for MoveAll with {@code MOVE_OWNER}, and for the special case of moving an entire
	 * empty container together with its floats ({@code KEEP}, non-FIRST, innerPageExtent&lt;=0).
	 * The float ledger remains attached to the owner.
	 */
	FloatTransferResult MOVE_OWNER = new MoveOwner();

	record KeepOwner() implements FloatTransferResult {
	}

	record MoveOwner() implements FloatTransferResult {
	}

	/**
	 * A container with the moved-float ledger attached (formerly returning nextBox or a new
	 * FlowContainer). With {@link FloatTransferTarget.Existing}, this is that same container;
	 * otherwise, it is a new FlowContainer.
	 *
	 * @param container the container with the moved-float ledger attached
	 */
	record Remainder(FlowContainer container) implements FloatTransferResult {
	}
}
