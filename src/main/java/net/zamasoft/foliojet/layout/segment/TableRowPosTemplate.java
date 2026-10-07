package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.TableRowPos;

/**
 * A template that freezes the contents of {@link TableRowPos} (directly extends
 * {@code AbstractBlockLevelPos}, has no fields of its own, and is used by {@link BoxKind#TABLE_ROW})
 * and materializes a fresh, independent {@code TableRowPos} on each call
 * (introduced on 2026-07-22, M6d-A3b).
 *
 * <p>
 * {@link BlockLevelPosFields} (also shared with `TableRowGroupPosTemplate` and `TableCellPosTemplate`)
 * handles the ancestor fields ({@code AbstractStaticPos}/{@code AbstractBlockLevelPos})
 * (replaced with an immutable record in Stage2 on 2026-07-22).
 * </p>
 */
public record TableRowPosTemplate(BlockLevelPosFields common) {
	public static TableRowPosTemplate freeze(final TableRowPos source) {
		return new TableRowPosTemplate(BlockLevelPosFields.freeze(source));
	}

	/** Returns a fresh {@code TableRowPos} on each call (multiple calls do not affect one another). */
	public TableRowPos materialize() {
		final TableRowPos pos = new TableRowPos();
		this.common.materializeInto(pos);
		return pos;
	}
}
