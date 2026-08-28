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
 * <tr><td>minmax(min,max)</td><td>min側: 固定長→その値/min-content・auto→内容min/max-content→内容max</td>
 * <td>max側: 固定長→その値/fr→∞/auto・max-content→内容max/min-content→内容min</td></tr>
 * </table>
 *
 * <p>
 * 2026-08-29: 全トラックを(min sizing function, max sizing function)の対へ
 * 分解して扱う(css-grid-1 §11.5の表——{@code auto}={@code minmax(auto,auto)}、
 * {@code <fr>}={@code minmax(auto,<fr>)}、固定長={@code minmax(L,L)}、
 * {@code minmax()}はそのまま)。手順(2)の「growth limitまでの成長」は
 * 仕様§12.6のmaximize tracks——上限が基礎幅より大きい全トラックへ均等に
 * 配る(固定長・min-content・max-contentは上限=基礎幅なので変わらない)。
 * 手順(4)のstretchはmax側が{@code auto}のトラックだけ(§12.8)。
 * </p>
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
	public static double[] resolve(final List<GridTrackListValue.TrackSize> tracks0,
			final List<ItemContribution> items, final double available, final double columnGap,
			final boolean stretchAutoTracks) {
		final List<GridTrackListValue.TrackSize> tracks = resolvePercents(tracks0, available);
		final int n = tracks.size();
		final Sized sized = size(tracks, items, columnGap);
		final double[] widths = sized.base.clone();
		double base = columnGap * (n - 1);
		for (int i = 0; i < n; ++i) {
			base += widths[i];
		}
		double free = available - base;
		if (free <= 0 || (sized.autoCount == 0 && sized.frCount == 0 && sized.growableCount == 0)) {
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
			// (2') frと共存する上限つき列はgrowth limitまで成長してから残余をfrへ
			growAutos(widths, sized.limit, sized.growable, sized.growableCount, free);
			distributeFr(widths, sized.base, sized.fr, sized.frWeight, available, columnGap, n);
			return widths;
		}
		// (2) 上限つき列をgrowth limitまで均等成長(maximize tracks)→(4) なお
		// 残る分はauto列へ均等stretch(justify-contentがpositionalのときは
		// stretchせず残余を残す——G5c)
		free -= growAutos(widths, sized.limit, sized.growable, sized.growableCount, free);
		if (stretchAutoTracks && sized.autoCount > 0 && free > 1e-9) {
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
	public static Intrinsics intrinsics(final List<GridTrackListValue.TrackSize> tracks0,
			final List<ItemContribution> items, final double columnGap) {
		final List<GridTrackListValue.TrackSize> tracks = resolvePercents(tracks0, Double.NaN);
		final int n = tracks.size();
		final Sized sized = size(tracks, items, columnGap);
		double min = columnGap * (n - 1);
		double max = min;
		for (int i = 0; i < n; ++i) {
			min += sized.base[i];
			max += sized.intrinsicMax[i];
		}
		return new Intrinsics(min, max);
	}

	/**
	 * %トラック(2026-08-29)を畳みます。利用可能幅が定まっていれば
	 * {@code Fixed}(幅×割合)、未定(固有寸法の計算、NaN)なら{@code Auto}
	 * (css-grid-1 §11.1: 不定寸法に対する%はautoとして扱う)。
	 */
	private static List<GridTrackListValue.TrackSize> resolvePercents(
			final List<GridTrackListValue.TrackSize> tracks, final double available) {
		List<GridTrackListValue.TrackSize> resolved = null;
		for (int i = 0; i < tracks.size(); ++i) {
			final GridTrackListValue.TrackSize t = tracks.get(i);
			final GridTrackListValue.TrackSize r;
			if (t instanceof GridTrackListValue.Percentage percent) {
				r = resolvePercent(percent, available);
			} else if (t instanceof GridTrackListValue.MinMax minMax
					&& (minMax.min() instanceof GridTrackListValue.Percentage
							|| minMax.max() instanceof GridTrackListValue.Percentage)) {
				// minmax()の片側の%も同じ規則で畳む(2026-08-29)
				r = new GridTrackListValue.MinMax(
						minMax.min() instanceof GridTrackListValue.Percentage p ? resolvePercent(p, available)
								: minMax.min(),
						minMax.max() instanceof GridTrackListValue.Percentage p ? resolvePercent(p, available)
								: minMax.max());
			} else {
				continue;
			}
			if (resolved == null) {
				resolved = new java.util.ArrayList<>(tracks);
			}
			resolved.set(i, r);
		}
		return resolved == null ? tracks : resolved;
	}

	private static GridTrackListValue.TrackSize resolvePercent(final GridTrackListValue.Percentage percent,
			final double available) {
		return Double.isNaN(available) ? GridTrackListValue.Auto.INSTANCE
				: new GridTrackListValue.Fixed(Math.max(0, available * percent.ratio()));
	}

	/**
	 * base/limit/maxContribの集約結果(span不足分配込み)。
	 *
	 * @param auto         max側がautoのトラック(stretch対象)
	 * @param fr           max側がfrのトラック
	 * @param growable     上限が基礎幅より大きい有限上限のトラック(maximize
	 *                     tracksの対象)
	 * @param intrinsicMax Grid全体のmax-content寄与に使う各トラック幅
	 */
	private record Sized(double[] base, double[] limit, double[] maxContrib, boolean[] auto, boolean[] fr,
			double[] frWeight, int autoCount, int frCount, boolean[] growable, int growableCount,
			double[] intrinsicMax) {
	}

	/** min側のsizing function(css-grid-1 §11.5)。 */
	private enum MinKind {
		FIXED, MIN_CONTENT, MAX_CONTENT
	}

	/** max側のsizing function。 */
	private enum MaxKind {
		FIXED, FR, MIN_CONTENT, MAX_CONTENT, AUTO
	}

	/** トラック1本の(min, max)分解(2026-08-29)。 */
	private record Functions(MinKind min, double fixedMin, MaxKind max, double fixedMax, double frWeight) {
		static Functions of(final GridTrackListValue.TrackSize track) {
			return switch (track) {
			case GridTrackListValue.Fixed f -> new Functions(MinKind.FIXED, f.length(), MaxKind.FIXED, f.length(), 0);
			case GridTrackListValue.Auto ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.AUTO, 0, 0);
			// 基準幅が未確定(固有寸法計測)の%はautoとして扱う(2026-08-29。
			// bind時はGridBuilder.sizingTracksがFixedへ解決済み)
			case GridTrackListValue.Percentage ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.AUTO, 0, 0);
			// 展開前の形はここへ来ない(GridBuilder.placementPlanが展開する)。
			// 万一来てもautoとして壊れないようにする
			case GridTrackListValue.AutoRepeat ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.AUTO, 0, 0);
			case GridTrackListValue.MinContent ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.MIN_CONTENT,
					0, 0);
			case GridTrackListValue.MaxContent ignore -> new Functions(MinKind.MAX_CONTENT, 0, MaxKind.MAX_CONTENT,
					0, 0);
			// 単独frは仕様のminmax(auto, fr)
			case GridTrackListValue.Fr flex -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.FR, 0,
					Math.max(0, flex.weight()));
			case GridTrackListValue.MinMax minMax -> {
				final Functions lo = of(minMax.min());
				final Functions hi = of(minMax.max());
				yield new Functions(lo.min, lo.fixedMin, hi.max, hi.fixedMax, hi.frWeight);
			}
			};
		}
	}

	private static Sized size(final List<GridTrackListValue.TrackSize> tracks, final List<ItemContribution> items,
			final double columnGap) {
		final int n = tracks.size();
		final double[] base = new double[n];
		final double[] limit = new double[n];
		final double[] maxContrib = new double[n];
		final double[] minContrib = new double[n];
		final boolean[] auto = new boolean[n];
		final boolean[] fr = new boolean[n];
		// 内容寄与を受けるトラック(min側かmax側が内容依存。2026-08-29)
		final boolean[] content = new boolean[n];
		// 基礎幅が固定(minmax(0,<fr>)等——内容のmin-contentで膨らまない。
		// 2026-08-19のZeroMinFrをminmax一般形へ畳んだもの)
		final boolean[] fixedMin = new boolean[n];
		final Functions[] fn = new Functions[n];
		final double[] frWeight = new double[n];
		int autoCount = 0, frCount = 0;
		for (int i = 0; i < n; ++i) {
			fn[i] = Functions.of(tracks.get(i));
			switch (fn[i].min) {
			case FIXED -> {
				fixedMin[i] = true;
				base[i] = fn[i].fixedMin;
			}
			case MIN_CONTENT, MAX_CONTENT -> content[i] = true;
			}
			switch (fn[i].max) {
			case FIXED -> {
				// max側が固定長でもmin側が内容依存なら寄与を受ける(上のcontent)
			}
			case FR -> {
				fr[i] = true;
				frWeight[i] = fn[i].frWeight;
				++frCount;
				content[i] = true;
			}
			case AUTO -> {
				auto[i] = true;
				++autoCount;
				content[i] = true;
			}
			case MIN_CONTENT, MAX_CONTENT -> content[i] = true;
			}
		}
		// span1のcontributionを先に集約
		int maxSpan = 1;
		for (final ItemContribution item : items) {
			maxSpan = Math.max(maxSpan, item.span());
			if (item.span() != 1 || !content[item.column()]) {
				continue;
			}
			final int c = item.column();
			final double itemMin = Math.max(0, item.minContent());
			final double itemMax = Math.max(0, item.maxContent());
			switch (fn[c].min) {
			case MIN_CONTENT -> base[c] = Math.max(base[c], itemMin);
			case MAX_CONTENT -> base[c] = Math.max(base[c], itemMax);
			case FIXED -> {
				// 固定minは内容で膨らまない
			}
			}
			minContrib[c] = Math.max(minContrib[c], itemMin);
			maxContrib[c] = Math.max(maxContrib[c], itemMax);
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
					curMax += (content[c] ? maxContrib[c] : base[c]) + plannedMax[c];
					if (fr[c]) {
						spansFr = true;
						frWeightSum += frWeight[c];
					}
					if (content[c]) {
						++growable;
					}
				}
				if (growable == 0) {
					continue; // fixedのみを跨ぐ——トラックを増やさずoverflow
				}
				final double deficitMin = item.minContent() - gaps - curBase;
				final double deficitMax = item.maxContent() - gaps - curMax;
				for (int c = from; c < to; ++c) {
					if (!content[c]) {
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
					if (!fixedMin[c]) {
						plannedBase[c] = Math.max(plannedBase[c], shareMin);
					}
					plannedMax[c] = Math.max(plannedMax[c], shareMax);
				}
			}
			for (int c = 0; c < n; ++c) {
				base[c] += plannedBase[c];
				maxContrib[c] += plannedMax[c];
			}
		}
		// growth limit(§11.5の初期化+§12.5の内容解決後の「上限≧基礎幅」)
		final boolean[] growable = new boolean[n];
		final double[] intrinsicMax = new double[n];
		int growableCount = 0;
		for (int i = 0; i < n; ++i) {
			switch (fn[i].max) {
			case FIXED -> {
				// max<minのminmaxは仕様どおりmaxを無視(=minに揃う)
				limit[i] = Math.max(base[i], fn[i].fixedMax);
				intrinsicMax[i] = limit[i];
			}
			case FR -> {
				limit[i] = Double.POSITIVE_INFINITY;
				intrinsicMax[i] = maxContrib[i];
			}
			case AUTO, MAX_CONTENT -> {
				limit[i] = Math.max(base[i], maxContrib[i]);
				intrinsicMax[i] = maxContrib[i];
			}
			case MIN_CONTENT -> {
				limit[i] = Math.max(base[i], minContrib[i]);
				intrinsicMax[i] = limit[i];
			}
			}
			if (!fr[i] && limit[i] > base[i] + 1e-9) {
				growable[i] = true;
				++growableCount;
			}
		}
		return new Sized(base, limit, maxContrib, auto, fr, frWeight, autoCount, frCount, growable, growableCount,
				intrinsicMax);
	}

	/**
	 * 上限つき列をgrowth limitまで均等成長させ、消費した量を返します
	 * (飽和列を凍結して反復——1passごとに少なくとも1列が飽和するか
	 * 残余を使い切る。仕様§12.6 maximize tracks。2026-08-29からauto列に
	 * 限らずminmax(min, 固定長)等の有限上限を持つ列も対象)。
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
