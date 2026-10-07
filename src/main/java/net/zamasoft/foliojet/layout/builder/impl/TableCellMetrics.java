package net.zamasoft.foliojet.layout.builder.impl;

import java.util.function.Supplier;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Shared calculation of cell axis sizes (A-3, 2026-07-30). Consolidates equivalent blocks
 * from the Incremental/Retained builders. Preserves the old order of operations.
 */
final class TableCellMetrics {
	private TableCellMetrics() {
	}

	/**
	 * Sums column widths over the colspan window. Adds from the left, as in the old implementation.
	 */
	static double spannedLineSize(final double[] columnSizes, final int column, final int span) {
		double size = columnSizes[column];
		for (int j = 1; j < span; ++j) {
			assert !LayoutUtils.isNone(columnSizes[column + j]);
			size += columnSizes[column + j];
		}
		return size;
	}

	/**
	 * Applies the line-axis size. For cells with an orthogonal writing mode, applies the measured
	 * content size to the orthogonal axis (a convention shared by both builders; the old implementation
	 * used the provisional size fontSize*10, corrected in 0390-writing-mode/orthogonal-cell-fixed).
	 *
	 * @param intrinsics evaluated only for orthogonal cells (lazy:
	 *                   does not reference the measurement builder for non-orthogonal cells)
	 */
	static void applyLineAxis(final TableCellBox cellBox, final Supplier<IntrinsicSizes> intrinsics,
			final double lineSize, final boolean vertical, final TableParams tableParams) {
		if (vertical) {
			cellBox.setHeight(lineSize);
			if (!cellBox.getBlockParams().flow.isVertical()) {
				cellBox.setWidth(intrinsics.get().maxContent() + cellBox.getFrame().getFrameWidth()
						+ tableParams.borderSpacingH);
			}
		} else {
			cellBox.setWidth(lineSize);
			if (cellBox.getBlockParams().flow.isVertical()) {
				cellBox.setHeight(intrinsics.get().maxContent() + cellBox.getFrame().getFrameHeight()
						+ tableParams.borderSpacingV);
			}
		}
	}
}
