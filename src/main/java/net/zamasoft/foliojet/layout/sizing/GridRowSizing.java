package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

/**
 * Resolves Grid row heights (Grid G4d, 2026-07-31:
 * consult-codex-2026-07-31-grid-g4.txt Q2). A pure calculation independent of boxes.
 * First aggregates maxima for items with rowSpan=1, then processes spans in ascending order.
 * Adds "deficit=actual item height−internal rowGap−sum of spanned row heights" equally to each row
 * as {@code deficit/rowSpan}. Items with the same span length accumulate into planned increases
 * (maximum required increments), applied together, so item traversal order does not matter.
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridRowSizing {

	/** A contribution to row sizing from outside the items (rows are local to the caller). */
	public record Contribution(int row, int span, double extent) {
	}

	private GridRowSizing() {
		// static
	}

	/**
	 * Resolves row heights.
	 *
	 * @param areas       Definite area of each item (source order)
	 * @param itemExtents Actual page-axis height of each item (after bind)
	 * @param rowCount    Total row count
	 * @param rowGap      Gap between rows
	 * @return Height of each row (0 for empty rows)
	 */
	public static double[] resolve(final List<GridPlacementResolver.GridArea> areas, final double[] itemExtents,
			final int rowCount, final double rowGap) {
		return resolve(areas, itemExtents, rowCount, rowGap, List.of());
	}

	/**
	 * Resolves row heights from items and extra contributions. For each span, processes extra
	 * contributions after items with the same planned-increase arithmetic (2026-09-03).
	 *
	 * @param areas       Definite area of each item (source order)
	 * @param itemExtents Actual page-axis height of each item (after bind)
	 * @param rowCount    Total row count
	 * @param rowGap      Gap between rows
	 * @param extra       Extra contributions from outside the items (source order)
	 * @return Height of each row (0 for empty rows)
	 */
	public static double[] resolve(final List<GridPlacementResolver.GridArea> areas, final double[] itemExtents,
			final int rowCount, final double rowGap, final List<Contribution> extra) {
		final double[] heights = new double[Math.max(1, rowCount)];
		int maxSpan = 1;
		for (int i = 0; i < areas.size(); ++i) {
			final GridPlacementResolver.GridArea area = areas.get(i);
			maxSpan = Math.max(maxSpan, area.rowSpan());
			if (area.rowSpan() == 1) {
				heights[area.row()] = Math.max(heights[area.row()], itemExtents[i]);
			}
		}
		for (final Contribution contribution : extra) {
			maxSpan = Math.max(maxSpan, contribution.span());
			if (contribution.span() == 1) {
				heights[contribution.row()] = Math.max(heights[contribution.row()], contribution.extent());
			}
		}
		for (int span = 2; span <= maxSpan; ++span) {
			final double[] planned = new double[heights.length];
			for (int i = 0; i < areas.size(); ++i) {
				final GridPlacementResolver.GridArea area = areas.get(i);
				if (area.rowSpan() != span) {
					continue;
				}
				double current = rowGap * (span - 1);
				for (int r = area.row(); r < area.row() + span; ++r) {
					current += heights[r] + planned[r];
				}
				final double deficit = itemExtents[i] - current;
				if (deficit <= 0) {
					continue;
				}
				final double share = deficit / span;
				for (int r = area.row(); r < area.row() + span; ++r) {
					planned[r] = Math.max(planned[r], share);
				}
			}
			for (final Contribution contribution : extra) {
				if (contribution.span() != span) {
					continue;
				}
				double current = rowGap * (span - 1);
				for (int r = contribution.row(); r < contribution.row() + span; ++r) {
					current += heights[r] + planned[r];
				}
				final double deficit = contribution.extent() - current;
				if (deficit <= 0) {
					continue;
				}
				final double share = deficit / span;
				for (int r = contribution.row(); r < contribution.row() + span; ++r) {
					planned[r] = Math.max(planned[r], share);
				}
			}
			for (int r = 0; r < heights.length; ++r) {
				heights[r] += planned[r];
			}
		}
		return heights;
	}
}
