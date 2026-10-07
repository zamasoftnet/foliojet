package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.fragment.ScratchReplayScope;

/**
 * Measurement primitive for table Pass B (row measurement) (E-6 increment 5b-1, 2026-07-24 —
 * design consultation
 * §4.4: "Replay cell ranges one by one at the resolved column widths, obtain only the used
 * page-axis size and first ascent, then discard the cell box tree").
 *
 * <p>
 * <b>The measurement context is the bind context itself</b>: Pass B runs at table end
 * (after column widths resolve, at the same point as {@code RetainedTableBuilder.bindRows}).
 * Measure with <b>the same mechanism as the real bind</b>, rather than a generic wrapper
 * and scratch page (MeasurePageGenerator in {@code SourceReplayer.measure}): SegmentExecutor
 * drives a {@code BlockBuilder} on a replica of the cell box with its column width applied
 * ({@link TableCellBox#newMeasureReplica}), using the same live {@code LayoutStack} and
 * {@code PageGenerator}. The scratch-page approach inherently cannot fully reproduce wrapper
 * parameters (line-height, text-indent, etc.) or ancestor context (the
 * {@code getFixedWidth/Height} chain for % resolution). Reject it because it introduces
 * difficulties that Pass C does not actually require. Build the box tree on the replica;
 * after collecting values it becomes unreachable (discarded). "Fixed width, infinite height"
 * is the cell bind structure itself (cells do not break across pages during bind).
 * </p>
 *
 * <p>
 * <b>Non-destructive</b>: replay recaptures the range (while retaining the lease) and leaves
 * live box/builder state untouched. Thus the same cell can be replayed twice, first for
 * measurement and then for the real bind (the shadow check
 * {@code RetainedCellPassBShadowTest} also enforces this through display list parity).
 * </p>
 */
final class CellPassBMeasurer {
	/**
	 * Pass B measurement result. All values that row height calculation ({@code bindRows})
	 * reads from the bound cell box:
	 * <ul>
	 * <li>{@code pageAxisSize}: used page-axis size ({@code getHeight()} for horizontal tables,
	 * {@code getWidth()} for vertical tables). This is the outer size including the frame;
	 * both the rowspan minimum size and the row height maximum read this value.</li>
	 * <li>{@code firstAscent}: first ascent ({@code getFirstAscent()}).
	 * Read by {@code maxFirstAscent} for baseline alignment; NaN if absent.</li>
	 * </ul>
	 * The content page-axis size ({@code TableCellBox.pageSize}) read by cell content alignment
	 * ({@code verticalAlign()}) needs no transfer (is not collected), because the real bind
	 * in Pass C sets it again itself.
	 */
	record Result(double pageAxisSize, double firstAscent) {
	}

	private CellPassBMeasurer() {
		// static functions
	}

	/**
	 * Replays a sealed cell's body range in scratch state at the resolved column width and
	 * measures the values needed by row height calculation.
	 *
	 * @param cell        cell to measure (column width already applied:
	 *                    after setWidth/setHeight in {@code bindRows})
	 * @param layoutStack the same live layoutStack as the real bind
	 * @param vertical    true for a vertical table (selects the page axis)
	 * @return measurement result, or null for cells outside Pass B (unsealed cells retaining records,
	 *         or multi-column cells). Unsealed cells with empty records (empty cells —
	 *         E-6 increment 5b-2) are measured by close-only, as they do not depend on the body
	 */
	static Result measure(final CellContent cell, final LayoutStack layoutStack, final boolean vertical) {
		final TwoPassBlockBuilder.DeferredBind body = cell.rangeBody();
		if (body == null && !cell.isPassBMeasurable()) {
			// Unsealed cells retaining records and extended cells are outside Pass B.
			return null;
		}
		final TableCellBox replica = cell.getCellBox().newMeasureReplica();
		if (replica == null) {
			return null;
		}
		final RetainedTextLimit limit = RetainedTextLimit.get(layoutStack);
		try (var retained = limit == null ? null
				: limit.measurement(RetainedTextLimit.elementName(replica.getParams(), "table-cell"));
				ScratchReplayScope scope = new ScratchReplayScope()) {
			final BlockBuilder builder = new BlockBuilder(layoutStack, replica);
			if (body != null) {
				body.measureInto(builder);
			}
			// An eligible cell with a null body has empty records (bind replays nothing): close-only.
			builder.close();
		}
		return new Result(vertical ? replica.getWidth() : replica.getHeight(), replica.getFirstAscent());
	}
}
