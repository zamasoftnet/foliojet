package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.css.value.GridLineValue;

/**
 * Explicit Grid item placement settings (the four grid-column/grid-row longhands)
 * (Grid G4a, 2026-07-31; consult-codex-2026-07-31-grid-g4.txt Q1).
 * Occupies one reference in {@link FlowPos} and is also carried into source replay and recipes
 * via FlowPosTemplate (replay determinism). All-auto settings share the {@link #AUTO} singleton,
 * so the permanent retention cost for non-Grid elements is one reference.
 *
 * @author MIYABE Tatsuhiko
 */
public record GridItemSpec(GridLineValue columnStart, GridLineValue columnEnd, GridLineValue rowStart,
		GridLineValue rowEnd, BoxAlignment justifySelf, BoxAlignment alignSelf) {

	/** All auto (default: all four lines and both self values are auto). */
	public static final GridItemSpec AUTO = new GridItemSpec(GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE,
			GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE, BoxAlignment.AUTO, BoxAlignment.AUTO);

	public static GridItemSpec of(final GridLineValue columnStart, final GridLineValue columnEnd,
			final GridLineValue rowStart, final GridLineValue rowEnd, final BoxAlignment justifySelf,
			final BoxAlignment alignSelf) {
		if (columnStart.isAuto() && columnEnd.isAuto() && rowStart.isAuto() && rowEnd.isAuto()
				&& justifySelf == BoxAlignment.AUTO && alignSelf == BoxAlignment.AUTO) {
			return AUTO;
		}
		return new GridItemSpec(columnStart, columnEnd, rowStart, rowEnd, justifySelf, alignSelf);
	}

	/** Creates only the four placement values (self properties are auto; for G4 tests). */
	public static GridItemSpec of(final GridLineValue columnStart, final GridLineValue columnEnd,
			final GridLineValue rowStart, final GridLineValue rowEnd) {
		return of(columnStart, columnEnd, rowStart, rowEnd, BoxAlignment.AUTO, BoxAlignment.AUTO);
	}

	public boolean isAuto() {
		return this == AUTO;
	}
}
