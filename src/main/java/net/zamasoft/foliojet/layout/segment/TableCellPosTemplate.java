package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.CellAlign;
import net.zamasoft.foliojet.layout.box.params.EmptyCellsMode;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;

/**
 * A template that freezes the contents of {@link TableCellPos} (directly extends
 * {@code AbstractBlockLevelPos}; used by {@link BoxKind#TABLE_CELL}) and materializes a fresh,
 * independent {@code TableCellPos} on each call (introduced on 2026-07-22, M6d-A3b).
 *
 * <p>
 * {@link BlockLevelPosFields} (also shared with `TableRowPosTemplate` and `TableRowGroupPosTemplate`)
 * handles ancestor fields. Holds {@code colspan}/{@code rowspan} (primitives) and
 * {@code emptyCells}/{@code verticalAlign} (enums) directly
 * (replaced with an immutable record in Stage2 on 2026-07-22).
 * </p>
 */
public record TableCellPosTemplate(BlockLevelPosFields common, int colspan, int rowspan, EmptyCellsMode emptyCells,
		CellAlign verticalAlign, boolean breakInsideDeclaredAuto) {
	public static TableCellPosTemplate freeze(final TableCellPos source) {
		return new TableCellPosTemplate(BlockLevelPosFields.freeze(source), source.colspan, source.rowspan,
				source.emptyCells, source.verticalAlign, source.breakInsideDeclaredAuto);
	}

	/** Returns a fresh {@code TableCellPos} on each call (multiple calls do not affect one another). */
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
