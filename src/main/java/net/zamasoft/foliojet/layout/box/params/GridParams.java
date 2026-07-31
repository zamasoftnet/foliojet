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
