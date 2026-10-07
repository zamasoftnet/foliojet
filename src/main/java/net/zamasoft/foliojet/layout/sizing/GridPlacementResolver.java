package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;

/**
 * Resolves Grid item placement (Grid G4a, 2026-07-31:
 * consult-codex-2026-07-31-grid-g4.txt Q2/Q3). A pure calculation independent of boxes.
 * Resolves a mix of explicit line numbers (positive/negative), spans, and auto using a subset of
 * CSS Grid §8.3.1 (conflict normalization) and §8.5 (auto-placement).
 *
 * <p>
 * Extensions on 2026-08-29: {@code grid-auto-flow} {@code column} (a column-flow cursor that fills
 * rows before moving to the next column, adding implicit columns as needed) and {@code dense}
 * (starts each item's search at the beginning of the grid); explicit row counts from
 * {@code grid-template-rows}/{@code grid-template-areas} (the basis for negative row numbers).
 * Receives line names already converted to numbers by {@link GridLineNameResolver}.
 * </p>
 *
 * <p>
 * Fail closed (consultation Q5): unsupported specifications (lines/spans outside explicit columns in
 * row flow, exceeded limits, etc.) return {@link Result.Unsupported} instead of throwing.
 * <b>Do not turn just one item into auto</b>: occupancy and cursor effects propagate to all subsequent
 * items. The caller falls back to source-order placement for the entire container
 * (G3: col=i%n, row=i/n). For implicit columns in row flow, the caller ({@code GridBuilder})
 * adds tracks beforehand and includes them in {@code columnCount}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridPlacementResolver {

	/** Resource limits for rows, columns, and spans (same as the repeat expansion limit). */
	public static final int LIMIT = 4096;

	private GridPlacementResolver() {
		// static
	}

	/** One item's definite area (zero-based track indices). */
	public record GridArea(int column, int row, int columnSpan, int rowSpan) {
	}

	/** Placement result (source order). */
	public record Plan(List<GridArea> areas, int columnCount, int rowCount) {
	}

	/** Resolution result. Unsupported is not an exception (for fallback before bind). */
	public sealed interface Result {
		record Resolved(Plan plan) implements Result {
		}

		record Unsupported(int itemIndex, Reason reason) implements Result {
		}
	}

	/** Reasons for unsupported placement (for observation and tests). */
	public enum Reason {
		/** Lines/spans outside the explicit grid (requires implicit columns). */
		NEEDS_IMPLICIT_COLUMN,
		/** Row, column, or span limit exceeded. */
		LIMIT_EXCEEDED,
		/** Negative row number (outside the subset for Grids with no explicit rows). */
		NEGATIVE_ROW
	}

	/** Normalized result for one axis (zero-based start track; null means auto). */
	private record AxisPlacement(Integer definiteStart, int span) {
	}

	/**
	 * Resolves placement of all items (row flow, sparse; compatible with prior behavior).
	 *
	 * @param items       Item specifications in source order
	 * @param columnCount Explicit column count (positive)
	 * @return Resolution result
	 */
	public static Result resolve(final List<GridItemSpec> items, final int columnCount) {
		return resolve(items, columnCount, 0, false, false);
	}

	/**
	 * Resolves placement of all items (2026-08-29).
	 *
	 * @param items        Item specifications in source order (line names already converted to numbers)
	 * @param columnCount  Explicit column count (positive; include implicit columns for row flow)
	 * @param explicitRows Explicit row count (0 means no explicit rows; negative row numbers are disallowed)
	 * @param columnFlow   Whether {@code grid-auto-flow: column} applies
	 * @param dense        Whether {@code dense} applies
	 * @return Resolution result (in column flow, {@code Plan.columnCount} is the number of columns used)
	 */
	public static Result resolve(final List<GridItemSpec> items, final int columnCount, final int explicitRows,
			final boolean columnFlow, final boolean dense) {
		return resolve(items, columnCount, explicitRows, columnFlow, dense, null);
	}

	/**
	 * Placement for subgrid with the row axis restricted to a fixed range (2026-09-03).
	 * Places into virtual implicit rows as usual, then clamps only the completed areas to
	 * {@code boundRows} rows.
	 *
	 * @param boundRows Number of rows to return (at least 1)
	 */
	public static Result resolve(final List<GridItemSpec> items, final int columnCount, final int explicitRows,
			final boolean columnFlow, final boolean dense, final int boundRows) {
		if (boundRows <= 0 || boundRows > LIMIT) {
			throw new IllegalArgumentException("boundRows: " + boundRows);
		}
		return resolve(items, columnCount, explicitRows, columnFlow, dense, Integer.valueOf(boundRows));
	}

	private static Result resolve(final List<GridItemSpec> items, final int columnCount, final int explicitRows,
			final boolean columnFlow, final boolean dense, final Integer boundRows) {
		final int n = columnCount;
		final AxisPlacement[] cols = new AxisPlacement[items.size()];
		final AxisPlacement[] rows = new AxisPlacement[items.size()];
		for (int i = 0; i < items.size(); ++i) {
			final GridItemSpec spec = items.get(i);
			final Object col = normalizeAxis(spec.columnStart(), spec.columnEnd(), n);
			if (col instanceof Reason reason) {
				return new Result.Unsupported(i, reason);
			}
			cols[i] = (AxisPlacement) col;
			// Rows: negative numbers are relative to the explicit row end, if present; otherwise disallowed.
			final Object row = normalizeAxis(spec.rowStart(), spec.rowEnd(), explicitRows > 0 ? explicitRows : -1);
			if (row instanceof Reason reason) {
				return new Result.Unsupported(i, reason);
			}
			rows[i] = (AxisPlacement) row;
			final AxisPlacement c = cols[i];
			if (c.definiteStart != null && c.definiteStart < 0) {
				return new Result.Unsupported(i, Reason.NEEDS_IMPLICIT_COLUMN);
			}
			if (!columnFlow && (c.span > n || (c.definiteStart != null && c.definiteStart + c.span > n))) {
				// Validate column bounds (the caller adds implicit columns first; consultation Q5: no clamping).
				return new Result.Unsupported(i, Reason.NEEDS_IMPLICIT_COLUMN);
			}
			if (c.span > LIMIT || (c.definiteStart != null && c.definiteStart + c.span > LIMIT)) {
				return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
			}
			final AxisPlacement r = rows[i];
			if (boundRows == null && r.definiteStart != null && r.definiteStart < 0) {
				// Went before the first row, e.g., by calculating backward from span/line (implicit leading rows unsupported).
				return new Result.Unsupported(i, Reason.NEGATIVE_ROW);
			}
			if (r.span > LIMIT || (r.definiteStart != null && r.definiteStart + r.span > LIMIT)) {
				return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
			}
		}

		final GridArea[] areas = new GridArea[items.size()];
		final Map<Integer, BitSet> occupancy = new HashMap<>();

		// (1) Both axes definite: place directly (overlap allowed; render in source order).
		for (int i = 0; i < items.size(); ++i) {
			if (cols[i].definiteStart != null && rows[i].definiteStart != null) {
				areas[i] = new GridArea(cols[i].definiteStart, rows[i].definiteStart, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
			}
		}
		if (columnFlow) {
			final Result result = resolveColumnFlow(items.size(), cols, rows, areas, occupancy, n, explicitRows, dense);
			return boundRows == null ? result : clampRows(result, boundRows);
		}
		// (2) Row definite, column auto: sparse cursor in the specified row (only forward per row; dense starts at the beginning).
		final Map<Integer, Integer> rowCursor = new HashMap<>();
		for (int i = 0; i < items.size(); ++i) {
			if (cols[i].definiteStart == null && rows[i].definiteStart != null) {
				final int row = rows[i].definiteStart;
				int col = dense ? 0 : rowCursor.getOrDefault(row, 0);
				while (col + cols[i].span <= n && occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++col;
				}
				if (col + cols[i].span > n) {
					// No room in the row: do not create implicit columns.
					return new Result.Unsupported(i, Reason.NEEDS_IMPLICIT_COLUMN);
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				rowCursor.put(row, col + cols[i].span);
			}
		}
		// (3)(4) Place remaining items (column definite/row auto, or both auto) in source order
		// with an auto-placement cursor (sparse: never backtrack; dense: start at the beginning each time).
		int curRow = 0, curCol = 0;
		for (int i = 0; i < items.size(); ++i) {
			if (areas[i] != null) {
				continue;
			}
			if (dense) {
				curRow = 0;
				curCol = 0;
			}
			if (cols[i].definiteStart != null) {
				// Column definite: move to the next row if this goes backward from the cursor's column.
				final int col = cols[i].definiteStart;
				if (col < curCol) {
					++curRow;
				}
				int row = curRow;
				while (occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++row;
					if (row + rows[i].span > LIMIT) {
						return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
					}
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curRow = row;
				curCol = col + cols[i].span;
			} else {
				// Both axes auto: find a free rectangle ahead of the cursor.
				int row = curRow, col = curCol;
				while (true) {
					if (col + cols[i].span > n) {
						++row;
						col = 0;
						if (row + rows[i].span > LIMIT) {
							return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
						}
						continue;
					}
					if (!occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
						break;
					}
					++col;
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curRow = row;
				curCol = col + cols[i].span;
			}
		}

		int rowCount = 0;
		for (final GridArea area : areas) {
			rowCount = Math.max(rowCount, area.row() + area.rowSpan());
		}
		final Result result = new Result.Resolved(new Plan(List.of(areas), n, Math.max(rowCount, explicitRows)));
		return boundRows == null ? result : clampRows(result, boundRows);
	}

	/** Clamps placed areas to the bounded row axis. */
	private static Result clampRows(final Result result, final int boundRows) {
		if (!(result instanceof Result.Resolved resolved)) {
			return result;
		}
		final List<GridArea> clamped = new ArrayList<>(resolved.plan().areas().size());
		for (final GridArea area : resolved.plan().areas()) {
			final int start = Math.max(0, Math.min(boundRows, area.row()));
			final int end = Math.max(0, Math.min(boundRows, area.row() + area.rowSpan()));
			if (start >= end) {
				clamped.add(new GridArea(area.column(), boundRows - 1, area.columnSpan(), 1));
			} else {
				clamped.add(new GridArea(area.column(), start, area.columnSpan(), end - start));
			}
		}
		return new Result.Resolved(new Plan(List.copyOf(clamped), resolved.plan().columnCount(), boundRows));
	}

	/**
	 * Auto-placement in column flow ({@code grid-auto-flow: column}; 2026-08-29,
	 * css-grid-1 §8.5 applied in the column direction). Fixes the row count at the larger of
	 * the explicit row count and the end row of definite placements (at least 1).
	 * Adds implicit columns as needed ({@code Plan.columnCount}=number of columns used).
	 */
	private static Result resolveColumnFlow(final int count, final AxisPlacement[] cols,
			final AxisPlacement[] rows, final GridArea[] areas, final Map<Integer, BitSet> occupancy, final int n,
			final int explicitRows, final boolean dense) {
		int rowCount = Math.max(1, explicitRows);
		for (final GridArea area : areas) {
			if (area != null) {
				rowCount = Math.max(rowCount, area.row() + area.rowSpan());
			}
		}
		// (2') Column definite, row auto: cursor within the specified column (advance through rows; add implicit rows if needed).
		final Map<Integer, Integer> colCursor = new HashMap<>();
		for (int i = 0; i < count; ++i) {
			if (cols[i].definiteStart != null && rows[i].definiteStart == null) {
				final int col = cols[i].definiteStart;
				int row = dense ? 0 : colCursor.getOrDefault(col, 0);
				while (occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++row;
					if (row + rows[i].span > LIMIT) {
						return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
					}
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				colCursor.put(col, row + rows[i].span);
				rowCount = Math.max(rowCount, row + rows[i].span);
			}
		}
		// (3')(4') Place remaining items (row definite/column auto, or both auto) with a column-flow cursor.
		int curRow = 0, curCol = 0, usedColumns = n;
		for (int i = 0; i < count; ++i) {
			if (areas[i] != null) {
				usedColumns = Math.max(usedColumns, areas[i].column() + areas[i].columnSpan());
				continue;
			}
			if (dense) {
				curRow = 0;
				curCol = 0;
			}
			if (rows[i].definiteStart != null) {
				// Row definite: move to the next column if this goes backward from the cursor's row.
				final int row = rows[i].definiteStart;
				if (row < curRow) {
					++curCol;
				}
				int col = curCol;
				while (occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
					++col;
					if (col + cols[i].span > LIMIT) {
						return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
					}
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curCol = col;
				curRow = row + rows[i].span;
			} else {
				int row = curRow, col = curCol;
				while (true) {
					if (row + rows[i].span > rowCount) {
						++col;
						row = 0;
						if (col + cols[i].span > LIMIT) {
							return new Result.Unsupported(i, Reason.LIMIT_EXCEEDED);
						}
						continue;
					}
					if (!occupied(occupancy, row, rows[i].span, col, cols[i].span)) {
						break;
					}
					++row;
				}
				areas[i] = new GridArea(col, row, cols[i].span, rows[i].span);
				mark(occupancy, areas[i]);
				curCol = col;
				curRow = row + rows[i].span;
			}
			usedColumns = Math.max(usedColumns, areas[i].column() + areas[i].columnSpan());
		}
		return new Result.Resolved(new Plan(List.of(areas), usedColumns, rowCount));
	}

	/**
	 * Normalizes one axis (CSS Grid §8.3.1 conflict handling).
	 * Returns {@link AxisPlacement} or {@link Reason} (unsupported).
	 *
	 * @param explicitTracks Explicit track count used as the basis for negative numbers.
	 *                       If negative, this axis does not support negative numbers
	 *                       (rows: {@link Reason#NEGATIVE_ROW})
	 */
	private static Object normalizeAxis(final GridLineValue start, final GridLineValue end,
			final int explicitTracks) {
		final Integer startLine = lineIndex(start, explicitTracks);
		final Integer endLine = lineIndex(end, explicitTracks);
		if (startLine != null && startLine == Integer.MIN_VALUE || endLine != null && endLine == Integer.MIN_VALUE) {
			return Reason.NEGATIVE_ROW; // Sentinel: negative row-axis number (outside the subset).
		}
		if (startLine != null && startLine > LIMIT || endLine != null && endLine > LIMIT) {
			return Reason.LIMIT_EXCEEDED;
		}
		if (startLine != null && endLine != null) {
			// line / line: swap if reversed; for the same line, remove end to give span 1.
			int a = startLine, b = endLine;
			if (a == b) {
				return new AxisPlacement(a, 1);
			}
			if (a > b) {
				final int t = a;
				a = b;
				b = t;
			}
			return new AxisPlacement(a, b - a);
		}
		if (startLine != null) {
			if (end.isSpan()) {
				return new AxisPlacement(startLine, Math.min(LIMIT, end.getNumber()));
			}
			return new AxisPlacement(startLine, 1); // line / auto
		}
		if (endLine != null) {
			if (start.isSpan()) {
				final int span = Math.min(LIMIT, start.getNumber());
				return new AxisPlacement(endLine - span, span); // span / line: calculate backward.
			}
			return new AxisPlacement(endLine - 1, 1); // auto / line
		}
		// Both auto/span: for span/span, ignore the end side (§8.3.1).
		final int span = start.isSpan() ? Math.min(LIMIT, start.getNumber())
				: end.isSpan() ? Math.min(LIMIT, end.getNumber()) : 1;
		return new AxisPlacement(null, span);
	}

	/**
	 * Zero-based line index for a line number (null for auto/span/unresolved line names).
	 * Negative numbers are relative to the explicit end (-1→N).
	 * A negative number on an axis (rows) with {@code explicitTracks<0} returns {@code MIN_VALUE}
	 * (a sentinel that the caller converts to Reason).
	 */
	private static Integer lineIndex(final GridLineValue value, final int explicitTracks) {
		if (value.isAuto() || value.isSpan() || value.isNamed()) {
			return null;
		}
		final int number = value.getNumber();
		if (number > 0) {
			return number - 1;
		}
		if (explicitTracks < 0) {
			return Integer.MIN_VALUE;
		}
		// -K → 1-based line N+2-K → zero-based N+1-K (-1=end line N, -2=N-1).
		return explicitTracks + 1 + number;
	}

	private static void mark(final Map<Integer, BitSet> occupancy, final GridArea area) {
		for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
			occupancy.computeIfAbsent(r, key -> new BitSet()).set(area.column(), area.column() + area.columnSpan());
		}
	}

	private static boolean occupied(final Map<Integer, BitSet> occupancy, final int row, final int rowSpan, final int col,
			final int colSpan) {
		for (int r = row; r < row + rowSpan; ++r) {
			final BitSet bits = occupancy.get(r);
			if (bits == null) {
				continue;
			}
			final int next = bits.nextSetBit(col);
			if (next >= 0 && next < col + colSpan) {
				return true;
			}
		}
		return false;
	}
}
