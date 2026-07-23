package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;

/**
 * {@link TableRowGroupPos}({@code AbstractBlockLevelPos}を直接継承、
 * {@link BoxKind#TABLE_ROW_GROUP}が使う)の内容をfreezeし、呼び出し
 * ごとに独立した新品の{@code TableRowGroupPos}をmaterializeする
 * テンプレートです(2026-07-22新設、M6d-A3b)。
 *
 * <p>
 * 祖先のフィールドは{@link BlockLevelPosFields}(`TableRowPosTemplate`・
 * `TableCellPosTemplate`とも共有する)が担う。{@code rowGroupType}
 * (enum)はそのまま保持する(2026-07-22 Stage2で不変recordへ置換)。
 * </p>
 */
public record TableRowGroupPosTemplate(BlockLevelPosFields common, RowGroupType rowGroupType) {
	public static TableRowGroupPosTemplate freeze(final TableRowGroupPos source) {
		return new TableRowGroupPosTemplate(BlockLevelPosFields.freeze(source), source.rowGroupType);
	}

	/** 呼び出しごとに新品の{@code TableRowGroupPos}を返す(複数回呼んでも互いに影響しない)。 */
	public TableRowGroupPos materialize() {
		final TableRowGroupPos pos = new TableRowGroupPos();
		this.common.materializeInto(pos);
		pos.rowGroupType = this.rowGroupType;
		return pos;
	}
}
