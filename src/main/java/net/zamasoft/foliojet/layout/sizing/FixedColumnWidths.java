package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Distributes column widths for fixed layout (table-layout: fixed). SPEC CSS2.1 17.5.2.1.
 * Merges colgroup and first-row cell specifications (colgroup takes priority), reduces percentage
 * specifications that exceed the table width, and distributes the remainder equally among AUTO columns
 * (adds equally to all columns if none are AUTO; truncates to content width if no space remains).
 * A pure function that does not touch boxes.
 *
 * @author MIYABE Tatsuhiko
 */
public final class FixedColumnWidths {
	/**
	 * A column-width specification. A null array element represents an unspecified width (AUTO).
	 *
	 * @param length     Specified width (resolved)
	 * @param percentage True if derived from a percentage (subject to reduction on overflow)
	 */
	public record Spec(double length, boolean percentage) {
	}

	/**
	 * Distribution result.
	 *
	 * @param sizes     Column widths
	 * @param innerSize Inner table size adjusted to fit content
	 */
	public record Result(double[] sizes, double innerSize) {
	}

	private FixedColumnWidths() {
		// utility
	}

	/**
	 * Derives a column-width specification from a cell's line-axis specification
	 * (P2-2: consolidation of identical OnePass/TwoPass implementations). AUTO is null (unspecified).
	 *
	 * @param cellBox   Cell
	 * @param colspan   Column span (divide the specified width equally by colspan)
	 * @param tableFlow Table's writing direction
	 * @param refSize   Reference size for percentage specifications
	 */
	public static Spec cellSpec(final net.zamasoft.foliojet.layout.box.impl.TableCellBox cellBox, final int colspan,
			final net.zamasoft.foliojet.layout.box.params.WritingMode tableFlow, final double refSize) {
		final net.zamasoft.foliojet.layout.box.params.BlockParams cellParams = cellBox.getBlockParams();
		switch (cellParams.size.getLineType(tableFlow)) {
		case AUTO:
			return null;
		case MIXED:
			// Table-cell widths mixing absolute lengths and percentages with calc() (e.g., calc(50% + 10px))
			// do not fit the fixed-layout column-width distribution algorithm's assumption
			// of "absolute or percentage" and are unsupported. Treat them as AUTO (unspecified)
			// to stay safe (avoid crashes or incorrect width calculations; see the development plan).
			return null;
		case ABSOLUTE: {
			double fix = cellParams.size.getLineLength(tableFlow);
			if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
				fix += cellBox.getFrame().getFrameLineExtent(tableFlow);
			}
			return new Spec(fix / colspan, false);
		}
		case RELATIVE: {
			double fix = refSize * cellParams.size.getLineLength(tableFlow);
			if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
				fix += cellBox.getFrame().getFrameLineExtent(tableFlow);
			}
			return new Spec(fix / colspan, true);
		}
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * Distributes column widths.
	 *
	 * @param colgroupSpecs Column specifications from colgroup (take priority; null if absent)
	 * @param cellSpecs     Column specifications from first-row cells (colspan expanded; null if absent)
	 * @param innerSize     Inner table size
	 * @return Column widths and adjusted inner size
	 */
	public static Result distribute(Spec[] colgroupSpecs, Spec[] cellSpecs, double innerSize) {
		final int n = colgroupSpecs.length;
		assert cellSpecs.length == n;
		final double[] sizes = new double[n];
		final Spec[] merged = new Spec[n];
		int autoCount = 0;
		double sizeSum = 0, percentSizeSum = 0;
		for (int i = 0; i < n; ++i) {
			final Spec spec = colgroupSpecs[i] != null ? colgroupSpecs[i] : cellSpecs[i];
			merged[i] = spec;
			if (spec == null) {
				++autoCount;
				sizes[i] = LayoutUtils.NONE;
			} else {
				sizes[i] = spec.length();
				sizeSum += spec.length();
				if (spec.percentage()) {
					percentSizeSum += spec.length();
				}
			}
		}

		// Reduce percentage widths that exceed the table width.
		if (percentSizeSum > 0 && sizeSum > innerSize) {
			final double removeSize = Math.min(percentSizeSum, sizeSum - innerSize);
			for (int i = 0; i < n; ++i) {
				final Spec spec = merged[i];
				if (spec != null && spec.percentage()) {
					final double sizeDiff = removeSize * spec.length() / percentSizeSum;
					sizes[i] -= sizeDiff;
					sizeSum -= sizeDiff;
				}
			}
		}

		if (autoCount > 0) {
			// Distribute the remainder equally among AUTO columns.
			final double each;
			if (innerSize > sizeSum) {
				each = (innerSize - sizeSum) / autoCount;
			} else {
				innerSize = sizeSum;
				each = 0;
			}
			for (int i = 0; i < n; ++i) {
				if (LayoutUtils.isNone(sizes[i])) {
					sizes[i] = each;
				}
			}
		} else if (innerSize > sizeSum) {
			// Without AUTO columns, add equally to all columns.
			final double each = (innerSize - sizeSum) / n;
			for (int i = 0; i < n; ++i) {
				sizes[i] += each;
			}
		} else {
			// If no space remains, truncate to content width.
			innerSize = 0;
			for (int i = 0; i < n; ++i) {
				innerSize += sizes[i];
			}
		}
		return new Result(sizes, innerSize);
	}
}
