package net.zamasoft.foliojet.layout.builder.impl;

import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

/**
 * Column width resolution for {@code table-layout:fixed} (A-3, 2026-07-30).
 * Shared pure function for the equivalent blocks in the Incremental/Retained builders.
 *
 * <p>
 * <b>Does not modify Boxes.</b> The caller still attaches colgroup to the table,
 * updates {@code columnSizes}, and applies the table width. Addition and distribution
 * preserve the old loops' operation order (keep floating-point results unchanged —
 * the golden default).
 * </p>
 */
final class FixedTableSizing {
	private FixedTableSizing() {
	}

	/**
	 * Derives a Spec from first-row cells. Incremental has the side effect of
	 * {@code prepareLayout} on cells with specified sizes, so the caller injects
	 * the derivation itself.
	 */
	interface CellSpecFactory {
		FixedColumnWidths.Spec spec(CellContent cell, double refSize);
	}

	/**
	 * Distributes column widths from colgroup and first-row cell specifications.
	 *
	 * @param innerSize line-axis size of the table content area (frame already subtracted)
	 */
	static FixedColumnWidths.Result resolve(final TableColumnGroupBox columnGroup,
			final List<CellContent> firstRowCells, final int columnCount, final double innerSize,
			final boolean separateBorders, final double lineBorderSpacing, final CellSpecFactory cellSpec) {
		double refSize = innerSize;
		if (separateBorders) {
			// Separate borders
			refSize -= columnCount * lineBorderSpacing;
		}
		refSize = Math.max(0, refSize);
		final FixedColumnWidths.Spec[] colgroupSpecs;
		if (columnGroup != null) {
			colgroupSpecs = TableColumnSpecs.colgroupSpecs(columnGroup, columnCount, refSize,
					separateBorders ? lineBorderSpacing : 0);
		} else {
			colgroupSpecs = new FixedColumnWidths.Spec[columnCount];
		}
		final FixedColumnWidths.Spec[] cellSpecs = new FixedColumnWidths.Spec[columnCount];
		if (firstRowCells != null) {
			for (int i = 0; i < columnCount; ++i) {
				if (i >= firstRowCells.size()) {
					continue;
				}
				final CellContent cell = firstRowCells.get(i);
				final FixedColumnWidths.Spec spec = cellSpec.spec(cell, refSize);
				cellSpecs[i] = spec;
				for (int j = 1; j < cell.colspan; ++j) {
					++i;
					if (i < columnCount) {
						cellSpecs[i] = spec;
					}
				}
			}
		}
		return FixedColumnWidths.distribute(colgroupSpecs, cellSpecs, innerSize);
	}
}
