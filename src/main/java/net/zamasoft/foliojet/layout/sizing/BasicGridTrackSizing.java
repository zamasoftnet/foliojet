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
 * <tr><td>fr</td><td>列内itemの最大min-content</td><td>∞(残余分配)</td></tr>
 * </table>
 *
 * <p>
 * 解決手順: (1)base合計+gapが利用可能幅を超えたら縮めずそのまま
 * overflow(min-content床は常に守る=内容欠落よりはみ出しの安全側)。
 * (2)正の残余はauto列をgrowth limitまで均等成長。(3)fr列があれば
 * 非fr確定後の残余をbase床付きfind-frで分配(G3c——単独{@code 1fr}は
 * 仕様の{@code minmax(auto,1fr)}相当でmin-content床を持ち、
 * weight合計が1未満なら残余の一部だけを充填する)。(4)frが無く
 * auto列があれば、なお残る残余を既定stretch相当で均等加算。
 * (5)どちらも無ければ残余は末尾に残す(G1の固定列と同じ)。
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
	 * @param tracks    列テンプレート(fixed/auto/fr)
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
		final double[] frWeight = new double[n];
		final boolean[] fr = new boolean[n];
		double base = columnGap * (n - 1);
		int autoCount = 0, frCount = 0;
		for (int i = 0; i < n; ++i) {
			switch (tracks.get(i)) {
			case GridTrackListValue.Fixed f -> {
				widths[i] = f.length();
				limits[i] = f.length();
			}
			case GridTrackListValue.Auto ignore -> {
				widths[i] = Math.max(0, colMin[i]);
				limits[i] = Math.max(widths[i], colMax[i]);
				auto[i] = true;
				++autoCount;
			}
			case GridTrackListValue.Fr flex -> {
				// 単独frはminmax(auto,1fr)相当——min-content床を持つ
				widths[i] = Math.max(0, colMin[i]);
				limits[i] = Double.POSITIVE_INFINITY;
				fr[i] = true;
				frWeight[i] = Math.max(0, flex.weight());
				++frCount;
			}
			}
			base += widths[i];
		}
		double free = available - base;
		if (free <= 0 || (autoCount == 0 && frCount == 0)) {
			// (1)(5) 縮めない(overflow)。可変列が無ければ残余は末尾に残す
			return widths;
		}
		if (frCount > 0) {
			// (2') frと共存するauto列はgrowth limitまで成長してから残余をfrへ
			growAutos(widths, limits, auto, autoCount, free);
			distributeFr(widths, colMin, fr, frWeight, available, columnGap, n);
			return widths;
		}
		// (2) auto列をgrowth limitまで均等成長→(4) なお残る分は均等stretch
		free -= growAutos(widths, limits, auto, autoCount, free);
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

	/**
	 * auto列をgrowth limitまで均等成長させ、消費した量を返します
	 * (飽和列を凍結して反復——1passごとに少なくとも1列が飽和するか
	 * 残余を使い切る)。
	 */
	private static double growAutos(final double[] widths, final double[] limits, final boolean[] auto,
			final int autoCount, double free) {
		double consumed = 0;
		int active = autoCount;
		while (free > 1e-9 && active > 0) {
			final double share = free / active;
			boolean grew = false;
			active = 0;
			for (int i = 0; i < widths.length; ++i) {
				if (!auto[i] || widths[i] >= limits[i]) {
					continue;
				}
				final double grow = Math.min(share, limits[i] - widths[i]);
				widths[i] += grow;
				free -= grow;
				consumed += grow;
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
		return consumed;
	}

	/**
	 * fr列へ残余を分配します(G3c——base床付きfind-fr、答申Q2)。
	 * 非fr列の確定後、fr列は残余全体からweight比で取る。
	 * {@code oneFr*weight}がmin-content床を割る列は床で凍結して再計算。
	 * weight合計が1未満のときは1へ切り上げ、残余の一部だけを充填する
	 * (仕様のpartial fill——0.5frは残余の50%)。
	 */
	private static void distributeFr(final double[] widths, final double[] colMin, final boolean[] fr,
			final double[] frWeight, final double available, final double columnGap, final int n) {
		double remaining = available - columnGap * (n - 1);
		for (int i = 0; i < n; ++i) {
			if (!fr[i]) {
				remaining -= widths[i];
			}
		}
		final boolean[] frozen = new boolean[n];
		while (true) {
			double factorSum = 0;
			int active = 0;
			for (int i = 0; i < n; ++i) {
				if (fr[i] && !frozen[i]) {
					factorSum += frWeight[i];
					++active;
				}
			}
			if (active == 0) {
				break;
			}
			final double oneFr = Math.max(0, remaining) / Math.max(1, factorSum);
			boolean changed = false;
			for (int i = 0; i < n; ++i) {
				if (!fr[i] || frozen[i]) {
					continue;
				}
				final double floor = Math.max(0, colMin[i]);
				if (oneFr * frWeight[i] < floor) {
					widths[i] = floor;
					frozen[i] = true;
					remaining -= floor;
					changed = true;
				}
			}
			if (!changed) {
				for (int i = 0; i < n; ++i) {
					if (fr[i] && !frozen[i]) {
						widths[i] = oneFr * frWeight[i];
					}
				}
				break;
			}
		}
	}
}
