package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.foliojet.layout.box.IPageBreakableBox;

/**
 * The result of a cut in the page direction (the typed protocol of pillar 2c).
 * Replaces the former three-way splitPageAxis return value (null/this/new object).
 * Internal-cut continuation information travels in two ways:
 * chain members selected by {@code BreakPlan} propagate a {@code ContinuationFrame} to the parent
 * through {@link Frame} (no box is added to the remainder container, C1d-C).
 * For others ({@link Split}, isolated regions such as table splits),
 * the remainder box itself retains continuation state as before.
 *
 * <p>
 * <b>Vocabulary mapping for the cut-result type family (formalized in E-4, 2026-07-24)</b>:
 * Suffixes represent granularity differences, not inconsistent naming.
 * Unsuffixed {@code Keep} /{@code Move} refer to one unit handled by the type (box, float, column);
 * the {@code All} suffix means the entire float ledger, and {@code Owner} means the owner container itself.
 * </p>
 * <table>
 * <caption>Mapping by layer</caption>
 * <tr><th>Type (granularity)</th><th>Keep before</th><th>Move to next</th><th>Internal cut</th></tr>
 * <tr><td>{@code SplitResult}(box)</td><td>Keep</td><td>Move</td>
 * <td>Split(remainder box) / Frame(continuation frame)</td></tr>
 * <tr><td>{@link FloatFragmentSplit}(float box)</td><td>Keep</td><td>Move</td>
 * <td>Prepared(deferred materials)</td></tr>
 * <tr><td>{@link ColumnCutResult}(column)</td><td>Keep</td><td>Move</td>
 * <td>Cut(prepared result before commit)</td></tr>
 * <tr><td>{@code ContainerCut}(container)</td><td>Plain(null)</td>
 * <td>Plain(self)</td><td>Plain(other) / WithFrame</td></tr>
 * <tr><td>{@code FloatSplitPlan.FloatItemPlan}(plan for one float)</td>
 * <td>Keep</td><td>Move</td><td>SplitOnCommit(marker that does not predict the result)</td></tr>
 * <tr><td>{@code FloatSplitResult}(entire float ledger)</td><td>KeepAll</td>
 * <td>MoveAll</td><td>Partition(remainder ledger)</td></tr>
 * <tr><td>{@code FloatTransferResult}(owner container)</td><td>KeepOwner</td>
 * <td>MoveOwner</td><td>Remainder(container with moved ledger attached)</td></tr>
 * </table>
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface SplitResult {
	/** Keeps everything in the preceding fragmentainer (page/column; formerly null). */
	SplitResult KEEP = new Keep();

	/** Moves everything to the next fragmentainer (formerly this). */
	SplitResult MOVE = new Move();

	record Keep() implements SplitResult {
	}

	record Move() implements SplitResult {
	}

	/**
	 * Cut internally. The cut source retains only the preceding page's portion;
	 * remainder is the continuation resumed in the next fragmentainer.
	 *
	 * @param remainder remainder to send to the next fragmentainer
	 */
	record Split(IPageBreakableBox remainder) implements SplitResult {
	}

	/**
	 * Cut internally and returned the continuation fragment as a ContinuationFrame rather than a box
	 * (C1d-C; only chain members selected by BreakPlan).
	 * The caller propagates the frame to the parent through the return value without adding a box to the
	 * remainder container.
	 */
	record Frame(Continuation.ContinuationFrame frame) implements SplitResult {
	}
}
