package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractBlockLevelPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

/**
 * Freeze/materialize processing for the fields {@code AbstractBlockLevelPos} adds to
 * {@code AbstractStaticPos} ({@code pageBreakBefore}/{@code pageBreakAfter}).
 * Introduced 2026-07-22, M6d-A3b; package-private and shared by {@link NormalFlowPosFields}
 * (for `FlowPos`/`FloatPos`), {@link TableRowGroupPosTemplate} ,
 * {@link TableRowPosTemplate} , and {@link TableCellPosTemplate} .
 * Delegates ancestor (`AbstractStaticPos`) fields to {@link PosFields} (composition).
 */
record BlockLevelPosFields(PosFields staticFields, PageBreakMode pageBreakBefore, PageBreakMode pageBreakAfter,
		String pageName) {
	static BlockLevelPosFields freeze(final AbstractBlockLevelPos source) {
		return new BlockLevelPosFields(PosFields.freeze(source), source.pageBreakBefore, source.pageBreakAfter,
				source.pageName);
	}

	void materializeInto(final AbstractBlockLevelPos target) {
		this.staticFields.materializeInto(target);
		target.pageBreakBefore = this.pageBreakBefore;
		target.pageBreakAfter = this.pageBreakAfter;
		target.pageName = this.pageName;
	}
}
