package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;

/**
 * A template that freezes the contents of {@link TableRowGroupPos} (directly extends
 * {@code AbstractBlockLevelPos}; used by {@link BoxKind#TABLE_ROW_GROUP}) and materializes a fresh,
 * independent {@code TableRowGroupPos} on each call (introduced on 2026-07-22, M6d-A3b).
 *
 * <p>
 * {@link BlockLevelPosFields} (also shared with `TableRowPosTemplate` and `TableCellPosTemplate`)
 * handles ancestor fields. Holds {@code rowGroupType} (an enum) directly
 * (replaced with an immutable record in Stage2 on 2026-07-22).
 * </p>
 */
public record TableRowGroupPosTemplate(BlockLevelPosFields common, RowGroupType rowGroupType) {
	public static TableRowGroupPosTemplate freeze(final TableRowGroupPos source) {
		return new TableRowGroupPosTemplate(BlockLevelPosFields.freeze(source), source.rowGroupType);
	}

	/** Returns a fresh {@code TableRowGroupPos} on each call (multiple calls do not affect one another). */
	public TableRowGroupPos materialize() {
		final TableRowGroupPos pos = new TableRowGroupPos();
		this.common.materializeInto(pos);
		pos.rowGroupType = this.rowGroupType;
		return pos;
	}
}
