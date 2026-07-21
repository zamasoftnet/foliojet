package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.CellAlign;
import net.zamasoft.foliojet.layout.box.params.EmptyCellsMode;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;

/**
 * {@link TableCellPos}({@code AbstractBlockLevelPos}を直接継承、
 * {@link BoxKind#TABLE_CELL}が使う)の内容をfreezeし、呼び出しごとに
 * 独立した新品の{@code TableCellPos}をmaterializeするテンプレートです
 * (2026-07-22新設、M6d-A3b)。
 *
 * <p>
 * 祖先のフィールドは{@link BlockLevelPosFields}(`TableRowPosTemplate`・
 * `TableRowGroupPosTemplate`とも共有する)が担う。{@code colspan}/
 * {@code rowspan}(プリミティブ)・{@code emptyCells}/
 * {@code verticalAlign}(enum)はそのまま保持する。
 * </p>
 */
public final class TableCellPosTemplate {
	private final BlockLevelPosFields common;
	private final int colspan;
	private final int rowspan;
	private final EmptyCellsMode emptyCells;
	private final CellAlign verticalAlign;

	private TableCellPosTemplate(final BlockLevelPosFields common, final int colspan, final int rowspan,
			final EmptyCellsMode emptyCells, final CellAlign verticalAlign) {
		this.common = common;
		this.colspan = colspan;
		this.rowspan = rowspan;
		this.emptyCells = emptyCells;
		this.verticalAlign = verticalAlign;
	}

	public static TableCellPosTemplate freeze(final TableCellPos source) {
		return new TableCellPosTemplate(BlockLevelPosFields.freeze(source), source.colspan, source.rowspan,
				source.emptyCells, source.verticalAlign);
	}

	/** 呼び出しごとに新品の{@code TableCellPos}を返す(複数回呼んでも互いに影響しない)。 */
	public TableCellPos materialize() {
		final TableCellPos pos = new TableCellPos();
		this.common.materializeInto(pos);
		pos.colspan = this.colspan;
		pos.rowspan = this.rowspan;
		pos.emptyCells = this.emptyCells;
		pos.verticalAlign = this.verticalAlign;
		return pos;
	}
}
