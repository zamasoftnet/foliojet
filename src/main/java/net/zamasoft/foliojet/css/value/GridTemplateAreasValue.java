package net.zamasoft.foliojet.css.value;

import java.util.List;

/**
 * {@code grid-template-areas}の値です(css-grid-1 §7.3、2026-08-29)。
 * 文字列の行列から求めた名前付き領域(zero-basedのトラック範囲)と、
 * 行列が定める明示グリッドの行数・列数を持つ。解析時に矩形性と
 * 行ごとの列数一致を検証済み(不正は宣言無効)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridTemplateAreasValue implements Value {
	/** {@code none}(既定)。 */
	public static final GridTemplateAreasValue NONE_VALUE = new GridTemplateAreasValue(List.of(), 0, 0);

	/**
	 * 名前付き領域(zero-basedトラック番号、endは排他的)。
	 */
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

	/** 行列が定める行数。 */
	public int getRowCount() {
		return this.rowCount;
	}

	/** 行列が定める列数。 */
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
