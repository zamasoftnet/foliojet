package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;

/**
 * Gridのトラック幅解決です(Grid G3b/G3c/G4d、2026-07-31——
 * consult-codex-2026-07-31-grid-g3.txt Q2、-grid-g4.txt Q2)。
 * boxに依存しない純粋計算。CSS Grid仕様§11/§12の印刷向けサブセット:
 * 各トラックはbase(下限)とgrowth limit(成長上限)を持つ。
 *
 * <table border="1">
 * <tr><th>track</th><th>base</th><th>growth limit</th></tr>
 * <tr><td>fixed</td><td>指定長</td><td>指定長</td></tr>
 * <tr><td>auto</td><td>span1 itemの最大min-content+span不足分配</td><td>最大max-content+span不足分配</td></tr>
 * <tr><td>fr</td><td>同上(min-content床)</td><td>∞(残余分配)</td></tr>
 * </table>
 *
 * <p>
 * span itemの不足分配(G4d、仕様§12.5の簡約): span1を先に集約し、
 * spanの小さい順に「不足=contribution−内側gap−跨ぐトラックの現寸の
 * 合計」を、fixedを増やさずauto(均等)またはfr(weight比——frを跨ぐ
 * 場合)へ分配する。同一span長のitemはplanned increase(最大必要増分)
 * へ蓄積してまとめて反映=item走査順に依存しない。増やせるトラックが
 * 無ければトラックは変えずitem overflowを許容。
 * </p>
 *
 * <p>
 * 解決手順: (1)base合計+gapが利用可能幅を超えたら縮めずそのまま
 * overflow(min-content床は常に守る=内容欠落よりはみ出しの安全側)。
 * (2)正の残余はauto列をgrowth limitまで均等成長。(3)fr列があれば
 * 非fr確定後の残余をbase床付きfind-frで分配(単独{@code 1fr}は仕様の
 * {@code minmax(auto,1fr)}相当、weight合計1未満はpartial fill)。
 * (4)frが無くauto列があれば、なお残る残余を既定stretch相当で均等加算。
 * (5)どちらも無ければ残余は末尾に残す。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class BasicGridTrackSizing {

	private BasicGridTrackSizing() {
		// static
	}

	/** item 1件の列方向contribution(G4d——span対応)。 */
	public record ItemContribution(int column, int span, double minContent, double maxContent) {
	}

	/** 行方向の固有寸法(Grid全体のcontent-box contribution)。 */
	public record Intrinsics(double min, double max) {
	}

	/** {@code stretchAutoTracks=true}での解決(既定=justify-content:normal)。 */
	public static double[] resolve(final List<GridTrackListValue.TrackSize> tracks,
			final List<ItemContribution> items, final double available, final double columnGap) {
		return resolve(tracks, items, available, columnGap, true);
	}

	/**
	 * トラック幅を解決します。
	 *
	 * @param tracks            列テンプレート(fixed/auto/fr)
	 * @param items             各itemの列contribution
	 * @param available         Gridコンテナのcontent-box行幅
	 * @param columnGap         列間gap
	 * @param stretchAutoTracks 手順(4)のauto列への残余stretchを行うか
	 *                          (G5c——justify-contentがstart/center/endの
	 *                          ときfalse: auto列はmax-content上限までの
	 *                          成長で止め、残余をcontent offsetに残す)
	 * @return 各列の確定幅(NaN・負値を返さない)
	 */
	public static double[] resolve(final List<GridTrackListValue.TrackSize> tracks,
			final List<ItemContribution> items, final double available, final double columnGap,
			final boolean stretchAutoTracks) {
		final int n = tracks.size();
		final Sized sized = size(tracks, items, columnGap);
		final double[] widths = sized.base.clone();
		double base = columnGap * (n - 1);
		for (int i = 0; i < n; ++i) {
			base += widths[i];
		}
		double free = available - base;
		if (free <= 0 || (sized.autoCount == 0 && sized.frCount == 0)) {
			// (1)(5) 縮めない(overflow)。可変列が無ければ残余は末尾に残す。
			//
			// ただし**複数列を跨ぐitemのmin-contentで膨らんだ分は縮める**
			// (2026-08-19)。分割不能な長トークン(遺伝子名・URL・識別子)を
			// 含むitemはmin-contentが利用可能幅を大きく超えることがあり、
			// その不足分配(G4d)で全列が一様に太る。結果、**そのitemだけで
             // なくgrid全体が版面を超え、無関係な兄弟(本文段落)の折り返し幅
			// まで広がって文書全体が右へはみ出していた**(elife-artで実測:
			// available 487ptに対しspan10 itemのmin 889ptが各列を82ptへ
			// 押し上げ、grid合計822pt。本文53件のedge-cut/text-lostの源)。
			// CSSでも当該itemはあふれるが、あふれるのはitem自身であって
			// トラック群ではない——単一列itemのmin(span1、下のfloor)は
			// 尊重したまま、span由来の超過だけを利用可能幅へ収める。
			// **可変列だけで構成されるgridに限る**(2026-08-19)。固定長列を
			// 含む場合、span itemの不足分配で得た床は「固定列の外側で
			// 内容が要求する最小幅」であり、これを削ると仕様(および
			// testSpanDeficitToFrが固定する挙動)に反する。実害のある形は
			// 「repeat(12,1fr)のような全可変gridで、span itemの
			// min-contentが全列を一様に太らせる」ケース
			boolean allFlexible = true;
			for (int i = 0; i < n && allFlexible; ++i) {
				allFlexible = sized.auto[i] || sized.fr[i];
			}
			if (allFlexible && available > 0 && base > available) {
				final double[] floor = spanFreeBase(tracks, items, columnGap, n);
				double floorSum = columnGap * (n - 1);
				for (int i = 0; i < n; ++i) {
					floorSum += floor[i];
				}
				if (floorSum <= available) {
					// span由来の増分だけを比例縮小して利用可能幅へ収める
					double excess = 0;
					for (int i = 0; i < n; ++i) {
						excess += Math.max(0, widths[i] - floor[i]);
					}
					if (excess > 0) {
						final double keep = (available - floorSum) / excess;
						for (int i = 0; i < n; ++i) {
							final double add = Math.max(0, widths[i] - floor[i]);
							widths[i] = floor[i] + add * keep;
						}
					}
				}
			}
			return widths;
		}
		if (sized.frCount > 0) {
			// (2') frと共存するauto列はgrowth limitまで成長してから残余をfrへ
			growAutos(widths, sized.limit, sized.auto, sized.autoCount, free);
			distributeFr(widths, sized.base, sized.fr, sized.frWeight, available, columnGap, n);
			return widths;
		}
		// (2) auto列をgrowth limitまで均等成長→(4) なお残る分は均等stretch
		// (justify-contentがpositionalのときはstretchせず残余を残す——G5c)
		free -= growAutos(widths, sized.limit, sized.auto, sized.autoCount, free);
		if (stretchAutoTracks && free > 1e-9) {
			final double share = free / sized.autoCount;
			for (int i = 0; i < n; ++i) {
				if (sized.auto[i]) {
					widths[i] += share;
				}
			}
		}
		return widths;
	}

	/**
	 * span itemの不足分配を<b>行わない</b>基礎幅です(2026-08-19)。
	 * 単一列itemのmin-contentと固定長だけを積む——{@link #resolve}が
	 * 「span由来で膨らんだ分だけを縮める」ときの床に使う。
	 */
	private static double[] spanFreeBase(final List<GridTrackListValue.TrackSize> tracks,
			final List<ItemContribution> items, final double columnGap, final int n) {
		final List<ItemContribution> span1 = new java.util.ArrayList<>(items.size());
		for (final ItemContribution item : items) {
			if (item.span() == 1) {
				span1.add(item);
			}
		}
		return size(tracks, span1, columnGap).base;
	}

	/**
	 * Grid全体の行方向content-box固有寸法です(G3d2/G4d)。
	 * min=gap+Σbase(span不足分配込み)、max=gap+Σ(fixed長|max
	 * contribution)。
	 */
	public static Intrinsics intrinsics(final List<GridTrackListValue.TrackSize> tracks,
			final List<ItemContribution> items, final double columnGap) {
		final int n = tracks.size();
		final Sized sized = size(tracks, items, columnGap);
		double min = columnGap * (n - 1);
		double max = min;
		for (int i = 0; i < n; ++i) {
			min += sized.base[i];
			max += tracks.get(i) instanceof GridTrackListValue.Fixed fixed ? fixed.length() : sized.maxContrib[i];
		}
		return new Intrinsics(min, max);
	}

	/** base/limit/maxContribの集約結果(span不足分配込み)。 */
	private record Sized(double[] base, double[] limit, double[] maxContrib, boolean[] auto, boolean[] fr,
			double[] frWeight, int autoCount, int frCount) {
	}

	private static Sized size(final List<GridTrackListValue.TrackSize> tracks, final List<ItemContribution> items,
			final double columnGap) {
		final int n = tracks.size();
		final double[] base = new double[n];
		final double[] limit = new double[n];
		final double[] maxContrib = new double[n];
		final boolean[] auto = new boolean[n];
		final boolean[] fr = new boolean[n];
		final double[] frWeight = new double[n];
		int autoCount = 0, frCount = 0;
		for (int i = 0; i < n; ++i) {
			switch (tracks.get(i)) {
			case GridTrackListValue.Fixed f -> {
				base[i] = f.length();
				limit[i] = f.length();
				maxContrib[i] = f.length();
			}
			case GridTrackListValue.Auto ignore -> {
				auto[i] = true;
				++autoCount;
			}
			case GridTrackListValue.Fr flex -> {
				fr[i] = true;
				frWeight[i] = Math.max(0, flex.weight());
				++frCount;
			}
			}
		}
		// span1のcontributionを先に集約
		int maxSpan = 1;
		for (final ItemContribution item : items) {
			maxSpan = Math.max(maxSpan, item.span());
			if (item.span() != 1 || !(auto[item.column()] || fr[item.column()])) {
				continue;
			}
			base[item.column()] = Math.max(base[item.column()], Math.max(0, item.minContent()));
			maxContrib[item.column()] = Math.max(maxContrib[item.column()], Math.max(0, item.maxContent()));
		}
		// span itemの不足分配(G4d): spanの小さい順・同一span長は
		// planned increase(最大必要増分)へ蓄積してまとめて反映
		for (int span = 2; span <= maxSpan; ++span) {
			final double[] plannedBase = new double[n];
			final double[] plannedMax = new double[n];
			for (final ItemContribution item : items) {
				if (item.span() != span) {
					continue;
				}
				final int from = item.column(), to = item.column() + span;
				final double gaps = columnGap * (span - 1);
				boolean spansFr = false;
				double frWeightSum = 0;
				int growable = 0;
				double curBase = 0, curMax = 0;
				for (int c = from; c < to; ++c) {
					curBase += base[c] + plannedBase[c];
					curMax += (auto[c] || fr[c] ? maxContrib[c] : base[c]) + plannedMax[c];
					if (fr[c]) {
						spansFr = true;
						frWeightSum += frWeight[c];
					}
					if (auto[c] || fr[c]) {
						++growable;
					}
				}
				if (growable == 0) {
					continue; // fixedのみを跨ぐ——トラックを増やさずoverflow
				}
				final double deficitMin = item.minContent() - gaps - curBase;
				final double deficitMax = item.maxContent() - gaps - curMax;
				for (int c = from; c < to; ++c) {
					if (!(auto[c] || fr[c])) {
						continue;
					}
					final double shareMin;
					final double shareMax;
					if (spansFr) {
						// frを跨ぐ: fr trackへweight比(weight合計0は均等)
						if (!fr[c]) {
							continue;
						}
						final double ratio = frWeightSum > 0 ? frWeight[c] / frWeightSum : 1.0 / growable;
						shareMin = Math.max(0, deficitMin) * ratio;
						shareMax = Math.max(0, deficitMax) * ratio;
					} else {
						shareMin = Math.max(0, deficitMin) / growable;
						shareMax = Math.max(0, deficitMax) / growable;
					}
					plannedBase[c] = Math.max(plannedBase[c], shareMin);
					plannedMax[c] = Math.max(plannedMax[c], shareMax);
				}
			}
			for (int c = 0; c < n; ++c) {
				base[c] += plannedBase[c];
				maxContrib[c] += plannedMax[c];
			}
		}
		for (int i = 0; i < n; ++i) {
			if (auto[i]) {
				limit[i] = Math.max(base[i], maxContrib[i]);
			} else if (fr[i]) {
				limit[i] = Double.POSITIVE_INFINITY;
			}
		}
		return new Sized(base, limit, maxContrib, auto, fr, frWeight, autoCount, frCount);
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
	 * {@code oneFr*weight}がbase床(span不足分配込み)を割る列は床で
	 * 凍結して再計算。weight合計が1未満のときは1へ切り上げ、残余の
	 * 一部だけを充填する(仕様のpartial fill——0.5frは残余の50%)。
	 */
	private static void distributeFr(final double[] widths, final double[] floors, final boolean[] fr,
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
				final double floor = Math.max(0, floors[i]);
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
