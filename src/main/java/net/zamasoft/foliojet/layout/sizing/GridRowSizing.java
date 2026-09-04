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

	/** item以外から行サイジングへ渡す寄与(行は呼び出し元ローカル)。 */
	public record Contribution(int row, int span, double extent) {
	}

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
		return resolve(areas, itemExtents, rowCount, rowGap, List.of());
	}

	/**
	 * itemと追加寄与から行高を解決します。追加寄与は各spanについてitemの
	 * 後に、同じplanned increaseの算術で処理します(2026-09-03)。
	 *
	 * @param areas       各itemの確定area(source order)
	 * @param itemExtents 各itemのページ方向実高(bind後)
	 * @param rowCount    総行数
	 * @param rowGap      行間gap
	 * @param extra       item以外からの追加寄与(source order)
	 * @return 各行の高さ(空行は0)
	 */
	public static double[] resolve(final List<GridPlacementResolver.GridArea> areas, final double[] itemExtents,
			final int rowCount, final double rowGap, final List<Contribution> extra) {
		final double[] heights = new double[Math.max(1, rowCount)];
		int maxSpan = 1;
		for (int i = 0; i < areas.size(); ++i) {
			final GridPlacementResolver.GridArea area = areas.get(i);
			maxSpan = Math.max(maxSpan, area.rowSpan());
			if (area.rowSpan() == 1) {
				heights[area.row()] = Math.max(heights[area.row()], itemExtents[i]);
			}
		}
		for (final Contribution contribution : extra) {
			maxSpan = Math.max(maxSpan, contribution.span());
			if (contribution.span() == 1) {
				heights[contribution.row()] = Math.max(heights[contribution.row()], contribution.extent());
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
			for (final Contribution contribution : extra) {
				if (contribution.span() != span) {
					continue;
				}
				double current = rowGap * (span - 1);
				for (int r = contribution.row(); r < contribution.row() + span; ++r) {
					current += heights[r] + planned[r];
				}
				final double deficit = contribution.extent() - current;
				if (deficit <= 0) {
					continue;
				}
				final double share = deficit / span;
				for (int r = contribution.row(); r < contribution.row() + span; ++r) {
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
