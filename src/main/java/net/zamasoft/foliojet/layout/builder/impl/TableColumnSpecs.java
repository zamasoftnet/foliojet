package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

/**
 * Helper that builds fixed-layout column specifications from a colgroup structure.
 * Shared by IncrementalTableBuilder / RetainedTableBuilder.
 *
 * @author MIYABE Tatsuhiko
 */
final class TableColumnSpecs {
	private TableColumnSpecs() {
		// utility
	}

	/**
	 * Counts columns under colgroup (including spans).
	 *
	 * @param root the column group root
	 * @return the number of columns
	 */
	static int countColumns(TableColumnGroupBox root) {
		final int[] count = { 0 };
		root.forEachColumn(column -> count[0] += column.getTableColumnPos().span);
		return count[0];
	}

	/**
	 * Builds column specifications from colgroup.
	 *
	 * @param root            the column group root
	 * @param columnCount     the number of columns
	 * @param refSize         the reference size for % values
	 * @param separateSpacing border spacing added to each specification in the separate border model
	 *                        (0 otherwise)
	 * @return column specifications (null for AUTO)
	 */
	static FixedColumnWidths.Spec[] colgroupSpecs(TableColumnGroupBox root, int columnCount, double refSize,
			double separateSpacing) {
		final FixedColumnWidths.Spec[] specs = new FixedColumnWidths.Spec[columnCount];
		final int[] k = { 0 };
		root.forEachColumn(column -> {
			final FixedColumnWidths.Spec spec = switch (column.getInnerTableParams().size.getType()) {
			case AUTO -> null;
			case ABSOLUTE -> new FixedColumnWidths.Spec(column.getInnerTableParams().size.getLength() + separateSpacing,
					false);
			case RELATIVE -> new FixedColumnWidths.Spec(
					refSize * column.getInnerTableParams().size.getLength() + separateSpacing, true);
			// Mixed absolute lengths and percentages in calc() (e.g., calc(50% + 10px)). refSize is already
			// resolved at this point, so treat this as a resolved value, just like ABSOLUTE.
			case MIXED -> new FixedColumnWidths.Spec(column.getInnerTableParams().size.getLength()
					+ refSize * column.getInnerTableParams().size.getRatio() + separateSpacing, false);
			};
			final int span = column.getTableColumnPos().span;
			for (int j = 0; j < span; ++j) {
				specs[k[0]++] = spec;
			}
		});
		return specs;
	}
}
