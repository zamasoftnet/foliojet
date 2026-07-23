package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.TableRowPos;

/**
 * {@link TableRowPos}({@code AbstractBlockLevelPos}を直接継承、
 * 固有フィールドを持たない、{@link BoxKind#TABLE_ROW}が使う)の内容を
 * freezeし、呼び出しごとに独立した新品の{@code TableRowPos}を
 * materializeするテンプレートです(2026-07-22新設、M6d-A3b)。
 *
 * <p>
 * 祖先({@code AbstractStaticPos}/{@code AbstractBlockLevelPos})の
 * フィールドは{@link BlockLevelPosFields}(`TableRowGroupPosTemplate`・
 * `TableCellPosTemplate`とも共有する)が担う(2026-07-22 Stage2で
 * 不変recordへ置換)。
 * </p>
 */
public record TableRowPosTemplate(BlockLevelPosFields common) {
	public static TableRowPosTemplate freeze(final TableRowPos source) {
		return new TableRowPosTemplate(BlockLevelPosFields.freeze(source));
	}

	/** 呼び出しごとに新品の{@code TableRowPos}を返す(複数回呼んでも互いに影響しない)。 */
	public TableRowPos materialize() {
		final TableRowPos pos = new TableRowPos();
		this.common.materializeInto(pos);
		return pos;
	}
}
