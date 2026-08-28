package net.zamasoft.foliojet.layout.box.params;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;

/**
 * Gridコンテナのパラメータです(Grid G0、2026-07-31——
 * consult-codex-2026-07-31-grid.txt §3.1。{@code TableParams}と同型の
 * {@code BlockParams}拡張)。トラックはcomputed済み
 * ({@link GridTrackListValue.TrackSize}=Fixed(絶対長)/Auto/Fr)。
 *
 * @author MIYABE Tatsuhiko
 */
public class GridParams extends BlockParams {

	/** 明示列トラック(空=implicit 1列auto)。 */
	public List<GridTrackListValue.TrackSize> templateColumns = List.of();

	/** 明示行トラック(空=全行implicit auto)。 */
	public List<GridTrackListValue.TrackSize> templateRows = List.of();

	/** 明示列の線名(templateColumns.size()+1要素。2026-08-29)。 */
	public List<List<String>> columnLineNames = List.of(List.of());

	/** 明示行の線名(templateRows.size()+1要素。2026-08-29)。 */
	public List<List<String>> rowLineNames = List.of(List.of());

	/** {@code grid-template-areas}(2026-08-29。noneはNONE_VALUE)。 */
	public net.zamasoft.foliojet.css.value.GridTemplateAreasValue templateAreas = net.zamasoft.foliojet.css.value.GridTemplateAreasValue.NONE_VALUE;

	/** {@code grid-auto-columns}(2026-08-29。空=auto)。 */
	public List<GridTrackListValue.TrackSize> autoColumns = List.of();

	/** {@code grid-auto-rows}(2026-08-29。空=auto)。 */
	public List<GridTrackListValue.TrackSize> autoRows = List.of();

	/** {@code grid-auto-flow}が{@code column}か(2026-08-29)。 */
	public boolean autoFlowColumn = false;

	/** {@code grid-auto-flow}に{@code dense}があるか(2026-08-29)。 */
	public boolean autoFlowDense = false;

	/**
	 * {@code grid-template-columns: subgrid}か(css-grid-2、2026-08-29)。
	 * trueのとき{@link #templateColumns}は空で、{@link #columnLineNames}は
	 * {@code subgrid [a] [b] ...}の線名列(要素数は任意)。親の跨ぐトラックを
	 * bind時に継ぐ({@code GridBuilder.bind})。
	 */
	public boolean columnsSubgrid = false;

	/**
	 * {@code grid-template-rows: subgrid}か(2026-08-29)。行軸は親の行が
	 * item bind後に決まるため継げない——行gapだけ親のものにする
	 * ({@code GridBuilder}のjavadoc)。
	 */
	public boolean rowsSubgrid = false;

	/** 行間隔(絶対長)。 */
	public double rowGap = 0;

	/** 列間隔(絶対長。columnGapのnormalはGridでは0)。 */
	public double columnGap = 0;

	/** itemの行方向既定配置(G5a。normalはGridではstretch)。 */
	public BoxAlignment justifyItems = BoxAlignment.NORMAL;

	/** itemのページ方向既定配置(G5a)。 */
	public BoxAlignment alignItems = BoxAlignment.NORMAL;

	/** トラック群の行方向配置(G5a)。 */
	public BoxAlignment justifyContent = BoxAlignment.NORMAL;

	/** 行群のページ方向配置(G5a。明示高Gridで意味を持つ)。 */
	public BoxAlignment alignContent = BoxAlignment.NORMAL;
}
