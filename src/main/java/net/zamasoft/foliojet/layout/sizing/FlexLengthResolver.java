package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

/**
 * 1行ぶんのFlex伸縮解決(css-flexbox-1 §9.7 Resolving Flexible Lengths)の
 * 純粋計算です(Flex F1c、2026-08-02——consult-codex-2026-08-02-flexbox.txt
 * Q3)。入力は{@link FlexItemMetrics}列とコンテナ主軸内寸・main gap、
 * 出力は各itemの使用主軸内寸(ソース順)。
 *
 * <p>
 * 仕様の要点(全て実装——検証はFlexLengthResolverTest):
 * free spaceとfactor選択はouter size、scaled shrink factorは
 * inner flex base size(§9.7.6)。factor合計&lt;1のときは
 * initial free space×合計とremainingの絶対値が小さい方(§9.7.9.b)。
 * min/max violationの符号合計でfreeze対象を選ぶ(§9.7.9.e)。
 * 反復は最大itemCount+1回で、各反復は必ず1件以上freezeする
 * (不変条件——破れは実装欠陥なのでassertではなくIllegalStateException)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexLengthResolver {

	private FlexLengthResolver() {
	}

	/**
	 * @param items 行内のitem(ソース順)
	 * @param innerMainSize コンテナの主軸内寸
	 * @param mainGap item間のgap(F2cまで常に0)
	 * @return 各itemの使用主軸内寸(clamp済み)
	 */
	public static double[] resolve(final List<FlexItemMetrics> items, final double innerMainSize,
			final double mainGap) {
		final int n = items.size();
		final double[] target = new double[n];
		if (n == 0) {
			return target;
		}
		// gap合計を先に控除した配分可能量(§9.7はgapをfree spaceに含めない)
		final double available = innerMainSize - mainGap * (n - 1);
		// 伸長か収縮か(outer hypothetical合計との比較——§9.7.1)
		double outerHypotheticalSum = 0;
		for (final FlexItemMetrics item : items) {
			outerHypotheticalSum += item.outerHypotheticalMain();
		}
		final boolean growing = outerHypotheticalSum < available;
		// 事前freeze(§9.7.3): factor 0、または方向不一致
		// (伸長なのにbase>hypothetical=既にclamp減、収縮なのにbase<hypothetical)
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
		// initial free space(§9.7.4: frozenはtarget、unfrozenはouter base)
		final double initialFree = available - occupied(items, target, frozen);
		for (int iteration = 0; iteration <= n; ++iteration) {
			// 全freezeで終了(§9.7.5)
			boolean allFrozen = true;
			for (final boolean f : frozen) {
				allFrozen &= f;
			}
			if (allFrozen) {
				return target;
			}
			// remaining free space(§9.7.9.b。factor合計<1なら縮小)
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
			// 配分(§9.7.9.c): growはfactor比例、shrinkはscaled factor
			// (factor×inner base)比例
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

	/** frozenはtarget(outer)、unfrozenはouter flex base sizeの合計(§9.7.4)。 */
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
