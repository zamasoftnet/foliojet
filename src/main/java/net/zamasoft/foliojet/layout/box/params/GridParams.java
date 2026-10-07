package net.zamasoft.foliojet.layout.box.params;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;

/**
 * Grid container parameters (Grid G0, 2026-07-31;
 * consult-codex-2026-07-31-grid.txt §3.1. A {@code BlockParams} extension analogous to
 * {@code TableParams}). Tracks are already computed
 * ({@link GridTrackListValue.TrackSize} = Fixed (absolute length)/Auto/Fr).
 *
 * @author MIYABE Tatsuhiko
 */
public class GridParams extends BlockParams {

	/** Explicit column tracks (empty = one implicit auto column). */
	public List<GridTrackListValue.TrackSize> templateColumns = List.of();

	/** Explicit row tracks (empty = all rows implicit auto). */
	public List<GridTrackListValue.TrackSize> templateRows = List.of();

	/** Explicit column line names (templateColumns.size()+1 entries; 2026-08-29). */
	public List<List<String>> columnLineNames = List.of(List.of());

	/** Explicit row line names (templateRows.size()+1 entries; 2026-08-29). */
	public List<List<String>> rowLineNames = List.of(List.of());

	/** {@code grid-template-areas} (2026-08-29; none is NONE_VALUE). */
	public net.zamasoft.foliojet.css.value.GridTemplateAreasValue templateAreas = net.zamasoft.foliojet.css.value.GridTemplateAreasValue.NONE_VALUE;

	/** {@code grid-auto-columns} (2026-08-29; empty = auto). */
	public List<GridTrackListValue.TrackSize> autoColumns = List.of();

	/** {@code grid-auto-rows} (2026-08-29; empty = auto). */
	public List<GridTrackListValue.TrackSize> autoRows = List.of();

	/** Whether {@code grid-auto-flow} is {@code column} (2026-08-29). */
	public boolean autoFlowColumn = false;

	/** Whether {@code grid-auto-flow} includes {@code dense} (2026-08-29). */
	public boolean autoFlowDense = false;

	/**
	 * Whether {@code grid-template-columns: subgrid} applies (css-grid-2, 2026-08-29).
	 * When true, {@link #templateColumns} is empty and {@link #columnLineNames} is the line-name sequence
	 * in {@code subgrid [a] [b] ...} (any number of entries). Inherits the spanned parent tracks
	 * at bind time ({@code GridBuilder.bind}).
	 */
	public boolean columnsSubgrid = false;

	/**
	 * Whether {@code grid-template-rows: subgrid} applies (2026-08-29/09-03). Passes descendant contributions
	 * to the parent and inherits row geometry after the parent resolves its rows ({@code GridBuilder} Javadoc).
	 */
	public boolean rowsSubgrid = false;

	/** Row gap (absolute length). */
	public double rowGap = 0;

	/** Whether {@code row-gap} is {@code normal} or unspecified (2026-09-03). */
	public boolean rowGapNormal = true;

	/** Column gap (absolute length; normal for columnGap is 0 in Grid). */
	public double columnGap = 0;

	/** Whether {@code column-gap} is {@code normal} or unspecified (2026-09-03). */
	public boolean columnGapNormal = true;

	/** Default item alignment in the line direction (G5a; normal is stretch in Grid). */
	public BoxAlignment justifyItems = BoxAlignment.NORMAL;

	/** Default item alignment in the page direction (G5a). */
	public BoxAlignment alignItems = BoxAlignment.NORMAL;

	/** Track-group alignment in the line direction (G5a). */
	public BoxAlignment justifyContent = BoxAlignment.NORMAL;

	/** Row-group alignment in the page direction (G5a; meaningful for Grids with explicit height). */
	public BoxAlignment alignContent = BoxAlignment.NORMAL;
}
