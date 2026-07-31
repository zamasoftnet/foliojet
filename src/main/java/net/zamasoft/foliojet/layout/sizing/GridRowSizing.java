package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

/**
 * Gridの行高解決です(Grid G4d、2026-07-31——
 * consult-codex-2026-07-31-grid-g4.txt Q2)。boxに依存しない純粋計算。
 * rowSpan=1のitemを先にmax集約し、spanの小さい順に「不足=item実高−
 * 内側rowGap−跨ぐ行高の合計」を各行へ{@code 不足/rowSpan}ずつ均等
 * 加算する。同一span長のitemはplanned increase(最大必要増分)へ
 * 蓄積してまとめて反映=item走査順に依存しない。
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridRowSizing {

	private GridRowSizing() {
		// static
	}

	/**
	 * 行高を解決します。
	 *
	 * @param areas       各itemの確定area(source order)
	 * @param itemExtents 各itemのページ方向実高(bind後)
	 * @param rowCount    総行数
	 * @param rowGap      行間gap
	 * @return 各行の高さ(空行は0)
	 */
	public static double[] resolve(final List<GridPlacementResolver.GridArea> areas, final double[] itemExtents,
			final int rowCount, final double rowGap) {
		final double[] heights = new double[Math.max(1, rowCount)];
		int maxSpan = 1;
		for (int i = 0; i < areas.size(); ++i) {
			final GridPlacementResolver.GridArea area = areas.get(i);
			maxSpan = Math.max(maxSpan, area.rowSpan());
			if (area.rowSpan() == 1) {
				heights[area.row()] = Math.max(heights[area.row()], itemExtents[i]);
			}
		}
		for (int span = 2; span <= maxSpan; ++span) {
			final double[] planned = new double[heights.length];
			for (int i = 0; i < areas.size(); ++i) {
				final GridPlacementResolver.GridArea area = areas.get(i);
				if (area.rowSpan() != span) {
					continue;
				}
				double current = rowGap * (span - 1);
				for (int r = area.row(); r < area.row() + span; ++r) {
					current += heights[r] + planned[r];
				}
				final double deficit = itemExtents[i] - current;
				if (deficit <= 0) {
					continue;
				}
				final double share = deficit / span;
				for (int r = area.row(); r < area.row() + span; ++r) {
					planned[r] = Math.max(planned[r], share);
				}
			}
			for (int r = 0; r < heights.length; ++r) {
				heights[r] += planned[r];
			}
		}
		return heights;
	}
}
