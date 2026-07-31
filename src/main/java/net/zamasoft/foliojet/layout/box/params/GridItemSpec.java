package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.css.value.GridLineValue;

/**
 * Grid itemの明示配置指定(grid-column/grid-rowの4 longhand)です
 * (Grid G4a、2026-07-31——consult-codex-2026-07-31-grid-g4.txt Q1)。
 * {@link FlowPos}に1参照として載り、FlowPosTemplate経由でソース再生・
 * レシピにも運ばれる(再生決定性)。全autoは{@link #AUTO} singletonを
 * 共有するため、非Grid要素の常時保持コストは参照1個。
 *
 * @author MIYABE Tatsuhiko
 */
public record GridItemSpec(GridLineValue columnStart, GridLineValue columnEnd, GridLineValue rowStart,
		GridLineValue rowEnd) {

	/** 全auto(既定)。 */
	public static final GridItemSpec AUTO = new GridItemSpec(GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE,
			GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE);

	public static GridItemSpec of(final GridLineValue columnStart, final GridLineValue columnEnd,
			final GridLineValue rowStart, final GridLineValue rowEnd) {
		if (columnStart.isAuto() && columnEnd.isAuto() && rowStart.isAuto() && rowEnd.isAuto()) {
			return AUTO;
		}
		return new GridItemSpec(columnStart, columnEnd, rowStart, rowEnd);
	}

	public boolean isAuto() {
		return this == AUTO;
	}
}
