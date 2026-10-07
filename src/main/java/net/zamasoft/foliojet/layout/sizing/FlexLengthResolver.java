package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

/**
 * A pure calculation of flexible lengths for one Flex line
 * (css-flexbox-1 §9.7 Resolving Flexible Lengths; Flex F1c, 2026-08-02:
 * consult-codex-2026-08-02-flexbox.txt Q3). Takes a sequence of {@link FlexItemMetrics},
 * the container's inner main size, and the main gap; returns each item's used inner main size in source order.
 *
 * <p>
 * Key specification rules (all implemented; verified by FlexLengthResolverTest):
 * free space and factor selection use outer size; scaled shrink factors use inner flex base size (§9.7.6).
 * When the factor sum is &lt;1, use whichever has the smaller absolute value:
 * initial free space×sum or remaining free space (§9.7.9.b).
 * The signed sum of min/max violations selects items to freeze (§9.7.9.e).
 * At most itemCount+1 iterations; each iteration must freeze at least one item
 * (an invariant: violation is an implementation defect, so use IllegalStateException instead of assert).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexLengthResolver {

	private FlexLengthResolver() {
	}

	/**
	 * @param items Items in the line (source order)
	 * @param innerMainSize Container's inner main size
	 * @param mainGap Gap between items (always 0 until F2c)
	 * @return Each item's used inner main size (clamped)
	 */
	public static double[] resolve(final List<FlexItemMetrics> items, final double innerMainSize,
			final double mainGap) {
		final int n = items.size();
		final double[] target = new double[n];
		if (n == 0) {
			return target;
		}
		// Distributable space after subtracting total gaps (§9.7 excludes gaps from free space).
		final double available = innerMainSize - mainGap * (n - 1);
		// Grow or shrink (compare with the sum of outer hypothetical sizes; §9.7.1).
		double outerHypotheticalSum = 0;
		for (final FlexItemMetrics item : items) {
			outerHypotheticalSum += item.outerHypotheticalMain();
		}
		final boolean growing = outerHypotheticalSum < available;
		// Initial freezing (§9.7.3): factor 0 or a direction mismatch
		// (growing with base>hypothetical, already clamped down; shrinking with base<hypothetical).
		final boolean[] frozen = new boolean[n];
		for (int i = 0; i < n; ++i) {
			final FlexItemMetrics item = items.get(i);
			target[i] = item.hypotheticalMain();
			final double factor = growing ? item.grow() : item.shrink();
			if (factor == 0 || (growing ? item.flexBaseMain() > item.hypotheticalMain()
					: item.flexBaseMain() < item.hypotheticalMain())) {
				frozen[i] = true;
			}
		}
		// Initial free space (§9.7.4: target for frozen items, outer base for unfrozen items).
		final double initialFree = available - occupied(items, target, frozen);
		for (int iteration = 0; iteration <= n; ++iteration) {
			// Finish when all items are frozen (§9.7.5).
			boolean allFrozen = true;
			for (final boolean f : frozen) {
				allFrozen &= f;
			}
			if (allFrozen) {
				return target;
			}
			// Remaining free space (§9.7.9.b; reduce if factor sum<1).
			double remaining = available - occupied(items, target, frozen);
			double sumFactors = 0;
			for (int i = 0; i < n; ++i) {
				if (!frozen[i]) {
					sumFactors += growing ? items.get(i).grow() : items.get(i).shrink();
				}
			}
			if (sumFactors < 1) {
				final double magnitude = initialFree * sumFactors;
				if (Math.abs(magnitude) < Math.abs(remaining)) {
					remaining = magnitude;
				}
			}
			// Distribute (§9.7.9.c): grow in proportion to factors, shrink in proportion to scaled factors
			// (factor×inner base).
			if (remaining != 0) {
				double sumScaled = 0;
				if (!growing) {
					for (int i = 0; i < n; ++i) {
						if (!frozen[i]) {
							sumScaled += items.get(i).shrink() * items.get(i).flexBaseMain();
						}
					}
				}
				for (int i = 0; i < n; ++i) {
					if (frozen[i]) {
						continue;
					}
					final FlexItemMetrics item = items.get(i);
					if (growing) {
						target[i] = item.flexBaseMain() + remaining * (item.grow() / sumFactors);
					} else if (sumScaled > 0) {
						final double scaled = item.shrink() * item.flexBaseMain();
						target[i] = item.flexBaseMain() - Math.abs(remaining) * (scaled / sumScaled);
					} else {
						target[i] = item.flexBaseMain();
					}
				}
			} else {
				for (int i = 0; i < n; ++i) {
					if (!frozen[i]) {
						target[i] = items.get(i).flexBaseMain();
					}
				}
			}
			// min/max violation(§9.7.9.d/e)
			double totalViolation = 0;
			final double[] clamped = new double[n];
			for (int i = 0; i < n; ++i) {
				if (frozen[i]) {
					clamped[i] = target[i];
					continue;
				}
				final FlexItemMetrics item = items.get(i);
				clamped[i] = Math.min(Math.max(target[i], Math.max(0, item.minMain())), item.maxMain());
				totalViolation += clamped[i] - target[i];
			}
			int frozenThisRound = 0;
			for (int i = 0; i < n; ++i) {
				if (frozen[i]) {
					continue;
				}
				final boolean violated = clamped[i] != target[i];
				final boolean freeze;
				if (totalViolation == 0) {
					freeze = true;
				} else if (totalViolation > 0) {
					freeze = violated && clamped[i] > target[i]; // min violation
				} else {
					freeze = violated && clamped[i] < target[i]; // max violation
				}
				target[i] = clamped[i];
				if (freeze) {
					frozen[i] = true;
					++frozenThisRound;
				}
			}
			if (frozenThisRound == 0) {
				throw new IllegalStateException("§9.7の反復がfreezeなしで一巡しました: iteration=" + iteration
						+ " available=" + available + " remaining=" + remaining + " factors=" + sumFactors
						+ " violation=" + totalViolation + " items=" + items);
			}
		}
		throw new IllegalStateException("§9.7の反復が上限(" + (n + 1) + ")を超えました");
	}

	/** Sums targets (outer) for frozen items and outer flex base sizes for unfrozen items (§9.7.4). */
	private static double occupied(final List<FlexItemMetrics> items, final double[] target,
			final boolean[] frozen) {
		double sum = 0;
		for (int i = 0; i < items.size(); ++i) {
			final FlexItemMetrics item = items.get(i);
			sum += (frozen[i] ? target[i] : item.flexBaseMain()) + item.outerMainExtra();
		}
		return sum;
	}
}
