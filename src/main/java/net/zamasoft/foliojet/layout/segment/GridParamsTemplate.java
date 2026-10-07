package net.zamasoft.foliojet.layout.segment;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * A template that freezes the contents of {@link GridParams}
 * (directly extends {@code BlockParams} , used by {@code BoxKind#GRID} ) and materializes an independent,
 * fresh {@code GridParams} on each call
 * (Grid G0c, 2026-07-31; consult-codex-2026-07-31-grid.txt §3.7).
 *
 * <p>
 * Tracks ({@link GridTrackListValue.TrackSize}) are immutable lists of immutable records,
 * and gaps are primitives, so both can be retained unchanged
 * (analogous to {@code TableParamsTemplate} ).
 * </p>
 */
public record GridParamsTemplate(BlockParamsFields common, List<GridTrackListValue.TrackSize> templateColumns,
		List<GridTrackListValue.TrackSize> templateRows, double rowGap, boolean rowGapNormal, double columnGap,
		boolean columnGapNormal,
		net.zamasoft.foliojet.layout.box.params.BoxAlignment justifyItems,
		net.zamasoft.foliojet.layout.box.params.BoxAlignment alignItems,
		net.zamasoft.foliojet.layout.box.params.BoxAlignment justifyContent,
		net.zamasoft.foliojet.layout.box.params.BoxAlignment alignContent,
		List<List<String>> columnLineNames, List<List<String>> rowLineNames,
		net.zamasoft.foliojet.css.value.GridTemplateAreasValue templateAreas,
		List<GridTrackListValue.TrackSize> autoColumns, List<GridTrackListValue.TrackSize> autoRows,
		boolean autoFlowColumn, boolean autoFlowDense, boolean columnsSubgrid, boolean rowsSubgrid) {
	public static GridParamsTemplate freeze(final GridParams source) {
		return new GridParamsTemplate(BlockParamsFields.freeze(source), source.templateColumns,
				source.templateRows, source.rowGap, source.rowGapNormal, source.columnGap, source.columnGapNormal,
				source.justifyItems, source.alignItems,
				source.justifyContent, source.alignContent, source.columnLineNames, source.rowLineNames,
				source.templateAreas, source.autoColumns, source.autoRows, source.autoFlowColumn,
				source.autoFlowDense, source.columnsSubgrid, source.rowsSubgrid);
	}

	/** Returns the frozen writing direction (for {@code containsMixedFlow}). */
	public WritingMode flow() {
		return this.common.common().text().flow();
	}

	/** Returns a fresh {@code GridParams} on each call. */
	public GridParams materialize() {
		final GridParams p = new GridParams();
		this.common.materializeInto(p);
		p.templateColumns = this.templateColumns;
		p.templateRows = this.templateRows;
		p.rowGap = this.rowGap;
		p.rowGapNormal = this.rowGapNormal;
		p.columnGap = this.columnGap;
		p.columnGapNormal = this.columnGapNormal;
		p.justifyItems = this.justifyItems;
		p.alignItems = this.alignItems;
		p.justifyContent = this.justifyContent;
		p.alignContent = this.alignContent;
		// Grid extensions from 2026-08-29 (line names, areas, implicit tracks, auto-flow).
		// All are immutable values, so sharing references is sufficient.
		p.columnLineNames = this.columnLineNames;
		p.rowLineNames = this.rowLineNames;
		p.templateAreas = this.templateAreas;
		p.autoColumns = this.autoColumns;
		p.autoRows = this.autoRows;
		p.autoFlowColumn = this.autoFlowColumn;
		p.autoFlowDense = this.autoFlowDense;
		p.columnsSubgrid = this.columnsSubgrid;
		p.rowsSubgrid = this.rowsSubgrid;
		return p;
	}
}
