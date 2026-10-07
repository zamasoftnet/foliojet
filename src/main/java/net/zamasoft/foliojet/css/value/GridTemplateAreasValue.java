package net.zamasoft.foliojet.css.value;

import java.util.List;

/**
 * A {@code grid-template-areas} value (css-grid-1 §7.3, 2026-08-29).
 * Holds named areas (zero-based track ranges) derived from the string matrix,
 * and the explicit grid's row and column counts defined by that matrix. Parsing has already
 * validated rectangularity and equal column counts in each row (invalid input invalidates the declaration).
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridTemplateAreasValue implements Value {
	/** {@code none} (the default). */
	public static final GridTemplateAreasValue NONE_VALUE = new GridTemplateAreasValue(List.of(), 0, 0);

	/** A named area (zero-based track indices, with an exclusive end). */
	public record Area(String name, int rowStart, int columnStart, int rowEnd, int columnEnd) {
	}

	private final List<Area> areas;

	private final int rowCount, columnCount;

	private GridTemplateAreasValue(final List<Area> areas, final int rowCount, final int columnCount) {
		this.areas = List.copyOf(areas);
		this.rowCount = rowCount;
		this.columnCount = columnCount;
	}

	public static GridTemplateAreasValue create(final List<Area> areas, final int rowCount,
			final int columnCount) {
		if (rowCount == 0 || columnCount == 0) {
			return NONE_VALUE;
		}
		return new GridTemplateAreasValue(areas, rowCount, columnCount);
	}

	public boolean isNone() {
		return this.rowCount == 0;
	}

	public List<Area> getAreas() {
		return this.areas;
	}

	/** The row count defined by the matrix. */
	public int getRowCount() {
		return this.rowCount;
	}

	/** The column count defined by the matrix. */
	public int getColumnCount() {
		return this.columnCount;
	}

	@Override
	public String toString() {
		if (this.isNone()) {
			return "none";
		}
		return this.rowCount + "x" + this.columnCount + this.areas;
	}
}
