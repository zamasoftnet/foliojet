package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractBlockLevelPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

/**
 * {@code AbstractBlockLevelPos}が{@code AbstractStaticPos}に加えて
 * 持つフィールド({@code pageBreakBefore}/{@code pageBreakAfter})の
 * freeze/materialize処理です(2026-07-22新設、M6d-A3b、
 * package-private——{@link NormalFlowPosFields}(`FlowPos`/
 * `FloatPos`用)・{@link TableRowGroupPosTemplate}・
 * {@link TableRowPosTemplate}・{@link TableCellPosTemplate}が共有
 * する)。祖先(`AbstractStaticPos`)のフィールドは{@link PosFields}へ
 * 委譲する(合成)。
 */
record BlockLevelPosFields(PosFields staticFields, PageBreakMode pageBreakBefore, PageBreakMode pageBreakAfter) {
	static BlockLevelPosFields freeze(final AbstractBlockLevelPos source) {
		return new BlockLevelPosFields(PosFields.freeze(source), source.pageBreakBefore, source.pageBreakAfter);
	}

	void materializeInto(final AbstractBlockLevelPos target) {
		this.staticFields.materializeInto(target);
		target.pageBreakBefore = this.pageBreakBefore;
		target.pageBreakAfter = this.pageBreakAfter;
	}
}
