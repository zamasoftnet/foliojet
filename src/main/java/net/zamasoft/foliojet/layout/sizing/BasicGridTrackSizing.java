package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;

/**
 * Resolves Grid track widths (Grid G3b/G3c/G4d, 2026-07-31:
 * consult-codex-2026-07-31-grid-g3.txt Q2, -grid-g4.txt Q2).
 * A pure calculation independent of boxes. A print-oriented subset of CSS Grid specification §11/§12:
 * each track has a base (lower bound) and a growth limit (upper bound).
 *
 * <table border="1">
 * <tr><th>track</th><th>base</th><th>growth limit</th></tr>
 * <tr><td>fixed</td><td>Specified length</td><td>Specified length</td></tr>
 * <tr><td>auto</td><td>Largest span1 item min-content + distributed span deficit</td>
 * <td>Largest max-content + distributed span deficit</td></tr>
 * <tr><td>fr</td><td>Same as above (min-content floor)</td><td>∞ (remaining-space distribution)</td></tr>
 * <tr><td>minmax(min,max)</td><td>min: fixed length→that value/min-content or auto→content min/max-content→content max</td>
 * <td>max: fixed length→that value/fr→∞/auto or max-content→content max/min-content→content min</td></tr>
 * </table>
 *
 * <p>
 * 2026-08-29: decomposed every track into a (min sizing function, max sizing function) pair
 * (the table in css-grid-1 §11.5: {@code auto}={@code minmax(auto,auto)},
 * {@code <fr>}={@code minmax(auto,<fr>)}, fixed length={@code minmax(L,L)},
 * and {@code minmax()} stays as is). "Grow to the growth limit" in step (2) is maximize tracks
 * in specification §12.6: distribute equally to all tracks whose limit exceeds their base width
 * (fixed length, min-content, and max-content have limit=base width, so they do not change).
 * Stretch in step (4) applies only to tracks whose max side is {@code auto} (§12.8).
 * </p>
 *
 * <p>
 * Distributing spanning-item deficits (G4d, a simplification of specification §12.5):
 * aggregate span1 first, then process spans in ascending order. Distribute
 * "deficit=contribution−internal gaps−sum of current sizes of spanned tracks" to auto tracks equally
 * or to fr tracks by weight (when spanning fr), without growing fixed tracks.
 * Items with the same span length accumulate into planned increases (maximum required increments),
 * applied together, so item traversal order does not matter. If no track can grow,
 * leave tracks unchanged and allow item overflow.
 * </p>
 *
 * <p>
 * Resolution steps: (1) If total base+gaps exceeds the available width, allow overflow without shrinking
 * (always honor the min-content floor, choosing overflow over lost content).
 * (2) Use positive remaining space to grow auto columns equally to their growth limits.
 * (3) If there are fr columns, distribute the remainder after fixing non-fr columns with find-fr
 * and a base floor (a standalone {@code 1fr} is equivalent to the specification's
 * {@code minmax(auto,1fr)}; a weight sum below 1 gives partial fill).
 * (4) If there are no fr columns but there are auto columns, add any remainder equally,
 * equivalent to the default stretch. (5) If neither exists, leave the remainder at the end.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class BasicGridTrackSizing {

	private BasicGridTrackSizing() {
		// static
	}

	/** One item's column-axis contribution (G4d: span support). */
	public record ItemContribution(int column, int span, double minContent, double maxContent) {
	}

	/** Line-axis intrinsic sizes (content-box contribution of the entire Grid). */
	public record Intrinsics(double min, double max) {
	}

	/** Resolves with {@code stretchAutoTracks=true} (default: justify-content:normal). */
	public static double[] resolve(final List<GridTrackListValue.TrackSize> tracks,
			final List<ItemContribution> items, final double available, final double columnGap) {
		return resolve(tracks, items, available, columnGap, true);
	}

	/**
	 * Resolves track widths.
	 *
	 * @param tracks            Column templates (fixed/auto/fr)
	 * @param items             Column contributions of each item
	 * @param available         Grid container's content-box line width
	 * @param columnGap         Gap between columns
	 * @param stretchAutoTracks Whether step (4) stretches auto columns using remaining space
	 *                          (G5c: false for justify-content start/center/end; auto columns stop
	 *                          growing at the max-content limit, leaving the remainder for content offset)
	 * @return Definite widths of each column (never NaN or negative)
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
			// (1)(5) Do not shrink (overflow). Without flexible columns, leave the remainder at the end.
			//
			// However, **shrink the expansion caused by the min-content of items spanning multiple columns**
			// (2026-08-19). Items containing long unbreakable tokens (gene names, URLs, identifiers) can have
			// min-content far exceeding the available width, and distributing their deficit (G4d)
			// widens every column uniformly. As a result, **the entire grid, not just the item, exceeded
             // the type area, widening even the wrapping width of unrelated siblings (body paragraphs)
			// and pushing the whole document off to the right** (measured on elife-art:
			// with 487 pt available, a span10 item's min of 889 pt pushed each column to 82 pt,
			// totaling 822 pt for the grid; the source of 53 body-text edge-cut/text-lost cases).
			// CSS also lets this item overflow, but the item itself overflows, not the tracks.
			// Honor the minimum of single-column items (span1, floor below),
			// and fit only the span-derived excess into the available width.
			// **Only for grids consisting entirely of flexible columns** (2026-08-19). With fixed-length
			// columns, the floor from distributing spanning-item deficits is the minimum width that
			// content requires outside the fixed columns. Reducing it violates the specification
			// (and the behavior fixed by testSpanDeficitToFr). The harmful case is
			// an entirely flexible grid such as repeat(12,1fr), where a spanning item's
			// min-content widens every column uniformly.
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
					// Shrink only span-derived increments proportionally to fit the available width.
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
			// (2') Grow bounded columns alongside fr to their growth limits, then give the remainder to fr.
			growAutos(widths, sized.limit, sized.growable, sized.growableCount, free);
			distributeFr(widths, sized.base, sized.fr, sized.frWeight, available, columnGap, n);
			return widths;
		}
		// (2) Grow bounded columns equally to their growth limits (maximize tracks); (4) stretch
		// auto columns equally with the remainder (for positional justify-content,
		// leave the remainder without stretching; G5c).
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
	 * Base widths <b>without</b> spanning-item deficit distribution (2026-08-19).
	 * Accumulates only single-column item min-content and fixed lengths; {@link #resolve} uses these
	 * as floors when shrinking only span-derived expansion.
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
	 * Line-axis content-box intrinsic sizes of the entire Grid (G3d2/G4d).
	 * min=gap+Σbase (including span deficit distribution),
	 * max=gap+Σ(fixed length|max contribution).
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
	 * Resolves percentage tracks (2026-08-29). Uses {@code Fixed} (width×ratio) when available width
	 * is definite, or {@code Auto} when indefinite (intrinsic-size calculation, NaN)
	 * (css-grid-1 §11.1: percentages against indefinite sizes are treated as auto).
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
				// Resolve a percentage on either side of minmax() by the same rule (2026-08-29).
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
				: new GridTrackListValue.Fixed(percent.resolve(available));
	}

	/**
	 * Aggregated base/limit/maxContrib (including span deficit distribution).
	 *
	 * @param auto         Tracks whose max side is auto (subject to stretch)
	 * @param fr           Tracks whose max side is fr
	 * @param growable     Tracks with a finite limit greater than the base width
	 *                     (subject to maximize tracks)
	 * @param intrinsicMax Each track's width used for the entire Grid's max-content contribution
	 */
	private record Sized(double[] base, double[] limit, double[] maxContrib, boolean[] auto, boolean[] fr,
			double[] frWeight, int autoCount, int frCount, boolean[] growable, int growableCount,
			double[] intrinsicMax) {
	}

	/** The min-side sizing function (css-grid-1 §11.5). */
	private enum MinKind {
		FIXED, MIN_CONTENT, MAX_CONTENT
	}

	/** The max-side sizing function. */
	private enum MaxKind {
		FIXED, FR, MIN_CONTENT, MAX_CONTENT, AUTO
	}

	/** Decomposes one track into (min, max) (2026-08-29). */
	private record Functions(MinKind min, double fixedMin, MaxKind max, double fixedMax, double frWeight) {
		static Functions of(final GridTrackListValue.TrackSize track) {
			return switch (track) {
			case GridTrackListValue.Fixed f -> new Functions(MinKind.FIXED, f.length(), MaxKind.FIXED, f.length(), 0);
			case GridTrackListValue.Auto ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.AUTO, 0, 0);
			// Treat percentages with an indefinite reference width (intrinsic measurement) as auto (2026-08-29).
			// At bind time, GridBuilder.sizingTracks has already resolved them to Fixed.
			case GridTrackListValue.Percentage ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.AUTO, 0, 0);
			// Unexpanded forms do not reach here (GridBuilder.placementPlan expands them).
			// If one does, treat it as auto to avoid breakage.
			case GridTrackListValue.AutoRepeat ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.AUTO, 0, 0);
			case GridTrackListValue.MinContent ignore -> new Functions(MinKind.MIN_CONTENT, 0, MaxKind.MIN_CONTENT,
					0, 0);
			case GridTrackListValue.MaxContent ignore -> new Functions(MinKind.MAX_CONTENT, 0, MaxKind.MAX_CONTENT,
					0, 0);
			// Standalone fr is minmax(auto, fr) in the specification.
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
		// Tracks receiving content contributions (min or max side depends on content; 2026-08-29).
		final boolean[] content = new boolean[n];
		// Fixed base width (e.g., minmax(0,<fr>): content min-content does not expand it.
		// The 2026-08-19 ZeroMinFr folded into the general minmax form).
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
				// Even with a fixed-length max, a content-dependent min receives contributions (content above).
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
		// Aggregate span1 contributions first.
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
				// Content does not expand a fixed min.
			}
			}
			minContrib[c] = Math.max(minContrib[c], itemMin);
			maxContrib[c] = Math.max(maxContrib[c], itemMax);
		}
		// Spanning-item deficit distribution (G4d): ascending span order; equal span lengths
		// accumulate into planned increases (maximum required increments), then apply together.
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
					continue; // Spans only fixed tracks: overflow without growing tracks.
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
						// Spans fr: distribute to fr tracks by weight (equally if the weight sum is zero).
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
		// Growth limit ("limit≧base width" after §11.5 initialization and §12.5 content resolution).
		final boolean[] growable = new boolean[n];
		final double[] intrinsicMax = new double[n];
		int growableCount = 0;
		for (int i = 0; i < n; ++i) {
			switch (fn[i].max) {
			case FIXED -> {
				// For minmax with max<min, ignore max as specified (= align to min).
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
	 * Grows bounded columns equally to their growth limits and returns the amount consumed
	 * (iterates while freezing saturated columns; each pass either saturates at least one column
	 * or consumes all remaining space; specification §12.6 maximize tracks).
	 * Since 2026-08-29, also covers columns with finite limits such as minmax(min, fixed length),
	 * not just auto columns.
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
	 * Distributes remaining space to fr columns (G3c: find-fr with a base floor, consultation Q2).
	 * After non-fr columns are fixed, fr columns take weighted shares of the entire remainder.
	 * Freezes columns whose {@code oneFr*weight} falls below their base floor (including span deficit
	 * distribution) at that floor and recalculates. If the weight sum is below 1, raises it to 1
	 * to fill only part of the remainder (the specification's partial fill: 0.5fr takes 50% of the remainder).
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
