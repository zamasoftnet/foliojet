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
 * {@code verticalAlign}(enum)はそのまま保持する(2026-07-22 Stage2で
 * 不変recordへ置換)。
 * </p>
 */
public record TableCellPosTemplate(BlockLevelPosFields common, int colspan, int rowspan, EmptyCellsMode emptyCells,
		CellAlign verticalAlign, boolean breakInsideDeclaredAuto) {
	public static TableCellPosTemplate freeze(final TableCellPos source) {
		return new TableCellPosTemplate(BlockLevelPosFields.freeze(source), source.colspan, source.rowspan,
				source.emptyCells, source.verticalAlign, source.breakInsideDeclaredAuto);
	}

	/** 呼び出しごとに新品の{@code TableCellPos}を返す(複数回呼んでも互いに影響しない)。 */
	public TableCellPos materialize() {
		final TableCellPos pos = new TableCellPos();
		this.common.materializeInto(pos);
		pos.colspan = this.colspan;
		pos.rowspan = this.rowspan;
		pos.emptyCells = this.emptyCells;
		pos.verticalAlign = this.verticalAlign;
		pos.breakInsideDeclaredAuto = this.breakInsideDeclaredAuto;
		return pos;
	}
}
