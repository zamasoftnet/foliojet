package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;

/**
 * Gridのトラック幅解決です(Grid G3b、2026-07-31——
 * consult-codex-2026-07-31-grid-g3.txt Q2)。boxに依存しない純粋計算。
 * CSS Grid仕様§11の印刷向けサブセット: 各トラックはbase(下限)と
 * growth limit(成長上限)を持つ。
 *
 * <table border="1">
 * <tr><th>track</th><th>base</th><th>growth limit</th></tr>
 * <tr><td>fixed</td><td>指定長</td><td>指定長</td></tr>
 * <tr><td>auto</td><td>列内itemの最大min-content</td><td>最大max-content</td></tr>
 * </table>
 *
 * <p>
 * 解決手順: (1)base合計+gapが利用可能幅を超えたら縮めずそのまま
 * overflow(min-content床は常に守る=内容欠落よりはみ出しの安全側)。
 * (2)正の残余はauto列をgrowth limitまで均等成長。(3)なお残る残余は
 * 既定のstretch相当としてauto列へ均等加算。(4)auto列が無ければ残余は
 * 末尾に残す(G1の固定列と同じ)。fr列はG3c(このクラスへ追加予定)。
 * </p>
 *
 * <p>
 * 仕様との既知の差(答申Q2): automatic minimumの厳密解決
 * (width/aspect-ratio/max-width考慮)・spanning item分配・%トラック・
 * fit-content/minmax・alignment一般はやらない。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class BasicGridTrackSizing {

	private BasicGridTrackSizing() {
		// static
	}

	/**
	 * トラック幅を解決します。
	 *
	 * @param tracks    列テンプレート(fixed/autoのみ。frは適格判定で除外)
	 * @param colMin    列ごとのitem min-contentの最大(空列は0)
	 * @param colMax    列ごとのitem max-contentの最大(空列は0)
	 * @param available Gridコンテナのcontent-box行幅
	 * @param columnGap 列間gap
	 * @return 各列の確定幅(NaN・負値を返さない)
	 */
	public static double[] resolve(final List<GridTrackListValue.TrackSize> tracks, final double[] colMin,
			final double[] colMax, final double available, final double columnGap) {
		final int n = tracks.size();
		final double[] widths = new double[n];
		final double[] limits = new double[n];
		final boolean[] auto = new boolean[n];
		double base = columnGap * (n - 1);
		int autoCount = 0;
		for (int i = 0; i < n; ++i) {
			switch (tracks.get(i)) {
			case GridTrackListValue.Fixed fixed -> {
				widths[i] = fixed.length();
				limits[i] = fixed.length();
			}
			case GridTrackListValue.Auto ignore -> {
				widths[i] = Math.max(0, colMin[i]);
				limits[i] = Math.max(widths[i], colMax[i]);
				auto[i] = true;
				++autoCount;
			}
			case GridTrackListValue.Fr fr -> throw new IllegalArgumentException("frはG3c: " + fr);
			}
			base += widths[i];
		}
		double free = available - base;
		if (free <= 0 || autoCount == 0) {
			// (1)(4) 縮めない(overflow)。auto列が無ければ残余は末尾に残す
			return widths;
		}
		// (2) auto列をgrowth limitまで均等成長(飽和列を凍結して反復)
		int active = autoCount;
		while (free > 1e-9 && active > 0) {
			final double share = free / active;
			boolean grew = false;
			active = 0;
			for (int i = 0; i < n; ++i) {
				if (!auto[i] || widths[i] >= limits[i]) {
					continue;
				}
				final double grow = Math.min(share, limits[i] - widths[i]);
				widths[i] += grow;
				free -= grow;
				if (grow > 0) {
					grew = true;
				}
				if (widths[i] < limits[i]) {
					++active;
				}
			}
			if (!grew) {
				break;
			}
		}
		// (3) 残余は既定のstretch相当としてauto列へ均等加算
		if (free > 1e-9) {
			final double share = free / autoCount;
			for (int i = 0; i < n; ++i) {
				if (auto[i]) {
					widths[i] += share;
				}
			}
		}
		return widths;
	}
}
