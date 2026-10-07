package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;

/**
 * Converts Grid line names to numbers (css-grid-1 §8.3, 2026-08-29).
 * A pure calculation independent of boxes. Combines {@code [name]} from
 * {@code grid-template-columns/rows} and the implicit {@code name-start}/{@code name-end}
 * created by {@code grid-template-areas} into one line-name table
 * (zero-based line index→set of names). Maps named {@link GridLineValue} entries in
 * {@link GridItemSpec} to integer line numbers, {@code span N}, or {@code auto}.
 * {@link GridPlacementResolver} handles only numbers (separation of responsibilities).
 *
 * <p>
 * Following the specification: a bare {@code name} first looks for {@code name-start} on the start side
 * or {@code name-end} on the end side, then for {@code name} itself ({@code 1 name}) if absent.
 * {@code N name} is the Nth line with that name (negative counts from the end).
 * {@code span N name} is the Nth named line counting from the opposite definite line,
 * or {@code span N} if absent. <b>Approximation</b>: the specification treats all implicit lines
 * outside the explicit grid as having that name when there are too few matching lines.
 * Here, undefined names fall back to {@code auto} (for print, auto-placement is less likely to
 * disrupt appearance than creating implicit columns for undefined names; recorded decision).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridLineNameResolver {

	private GridLineNameResolver() {
		// static
	}

	/**
	 * Returns a {@link GridItemSpec} with named lines converted to numbers
	 * (unchanged if there are no names).
	 *
	 * @param spec        Item specification
	 * @param columnLines Column-axis line-name table (zero-based line index→names)
	 * @param rowLines    Row-axis line-name table
	 */
	public static GridItemSpec resolve(final GridItemSpec spec, final List<List<String>> columnLines,
			final List<List<String>> rowLines) {
		if (!spec.columnStart().isNamed() && !spec.columnEnd().isNamed() && !spec.rowStart().isNamed()
				&& !spec.rowEnd().isNamed()) {
			return spec;
		}
		final GridLineValue[] cols = resolveAxis(spec.columnStart(), spec.columnEnd(), columnLines);
		final GridLineValue[] rows = resolveAxis(spec.rowStart(), spec.rowEnd(), rowLines);
		return GridItemSpec.of(cols[0], cols[1], rows[0], rows[1], spec.justifySelf(), spec.alignSelf());
	}

	/** Converts [start, end] on one axis to numbers. */
	static GridLineValue[] resolveAxis(final GridLineValue start, final GridLineValue end,
			final List<List<String>> lines) {
		// Resolve the non-span side first, then count span N name from that opposite side.
		GridLineValue s = start.isNamed() && !start.isSpan() ? resolveLine(start, lines, true) : start;
		GridLineValue e = end.isNamed() && !end.isSpan() ? resolveLine(end, lines, false) : end;
		if (start.isNamed() && start.isSpan()) {
			s = resolveSpan(start, e, lines, true);
		}
		if (end.isNamed() && end.isSpan()) {
			e = resolveSpan(end, s, lines, false);
		}
		return new GridLineValue[] { s, e };
	}

	/** Converts bare {@code name} or {@code N name} to a 1-based line number (auto if absent). */
	private static GridLineValue resolveLine(final GridLineValue value, final List<List<String>> lines,
			final boolean startSide) {
		final String name = value.getName();
		if (value.getNumber() == 0) {
			// Bare name: try name-start/name-end first, then name if absent.
			int index = nth(lines, name + (startSide ? "-start" : "-end"), 1);
			if (index < 0) {
				index = nth(lines, name, 1);
			}
			return index < 0 ? GridLineValue.AUTO_VALUE : GridLineValue.line(index + 1);
		}
		final int index = nth(lines, name, value.getNumber());
		return index < 0 ? GridLineValue.AUTO_VALUE : GridLineValue.line(index + 1);
	}

	/**
	 * {@code span N name}: the Nth named line from the opposite side if that side is definite
	 * ({@code span N} if not found).
	 */
	private static GridLineValue resolveSpan(final GridLineValue value, final GridLineValue opposite,
			final List<List<String>> lines, final boolean startSide) {
		final int count = Math.max(1, value.getNumber());
		if (opposite.isAuto() || opposite.isSpan() || opposite.isNamed()) {
			return GridLineValue.span(count);
		}
		final int from = opposite.getNumber() - 1; // Zero-based line index (only positive numbers have been resolved).
		if (from < 0) {
			return GridLineValue.span(count);
		}
		int found = 0;
		if (startSide) {
			// From end toward earlier (smaller) indices.
			for (int i = from - 1; i >= 0; --i) {
				if (i < lines.size() && lines.get(i).contains(value.getName()) && ++found == count) {
					return GridLineValue.line(i + 1);
				}
			}
		} else {
			for (int i = from + 1; i < lines.size(); ++i) {
				if (lines.get(i).contains(value.getName()) && ++found == count) {
					return GridLineValue.line(i + 1);
				}
			}
		}
		return GridLineValue.span(count);
	}

	/** Zero-based index of the Nth named line (1-based; negative counts from the end; -1 if absent). */
	private static int nth(final List<List<String>> lines, final String name, final int n) {
		if (n > 0) {
			int found = 0;
			for (int i = 0; i < lines.size(); ++i) {
				if (lines.get(i).contains(name) && ++found == n) {
					return i;
				}
			}
			return -1;
		}
		int found = 0;
		for (int i = lines.size() - 1; i >= 0; --i) {
			if (lines.get(i).contains(name) && --found == n) {
				return i;
			}
		}
		return -1;
	}
}
