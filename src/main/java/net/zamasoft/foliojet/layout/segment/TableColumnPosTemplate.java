package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.TableColumnPos;

/**
 * A template that freezes the contents of {@link TableColumnPos} (directly implements {@code Pos}
 * with no shareable ancestor; used by {@link BoxKind#TABLE_COLUMN_GROUP}/{@link BoxKind#TABLE_COLUMN})
 * and materializes a fresh, independent {@code TableColumnPos} on each call
 * (introduced on 2026-07-22, M6d-A3b).
 *
 * <p>
 * Holds only {@code span} (a primitive), so it needs none of the composition used by other Pos templates
 * (replaced with an immutable record in Stage2 on 2026-07-22).
 * </p>
 */
public record TableColumnPosTemplate(int span) {
	public static TableColumnPosTemplate freeze(final TableColumnPos source) {
		return new TableColumnPosTemplate(source.span);
	}

	/** Returns a fresh {@code TableColumnPos} on each call (multiple calls do not affect one another). */
	public TableColumnPos materialize() {
		final TableColumnPos pos = new TableColumnPos();
		pos.span = this.span;
		return pos;
	}
}
