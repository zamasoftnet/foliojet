package net.zamasoft.foliojet.layout.sizing;

/**
 * 表の列幅の分配です。 SPEC css-tables-3 [Distributing width to columns]
 *
 * <p>
 * 各列は最低 min[i] を確保した上で、余剰を PERCENT→CONSTRAINED→AUTO の優先順で
 * target[i] まで拡張します(余剰が不足する場合は各列の不足量に比例して配分)。
 * 全列が目標に達してなお余る場合は、AUTO→CONSTRAINED→PERCENT の順で最初に
 * 存在する種別の列へ現在幅に比例して(全て0なら均等に)分配します。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ColumnDistribution {
	/**
	 * 列の種別です。優先順の低い順に並びます。
	 */
	public enum ColumnType {
		/** 幅指定のない列(目標=最大内容幅)。 */
		AUTO,
		/** 幅が長さで指定された列(目標=指定幅)。 */
		CONSTRAINED,
		/** 幅が%で指定された列(目標=解決済みの%幅)。 */
		PERCENT;
	}

	private static final ColumnType[] GROW_ORDER = { ColumnType.PERCENT, ColumnType.CONSTRAINED, ColumnType.AUTO };

	private static final ColumnType[] EXCESS_ORDER = { ColumnType.AUTO, ColumnType.CONSTRAINED, ColumnType.PERCENT };

	private ColumnDistribution() {
		// utility
	}

	/**
	 * 列幅を分配します。
	 *
	 * @param min       列ごとの開始幅(通常は最小内容幅)
	 * @param target    列ごとの目標幅(AUTO=最大内容幅、CONSTRAINED=指定幅、
	 *                  PERCENT=解決済みの%幅)
	 * @param types     列ごとの種別
	 * @param available 分配可能な幅
	 * @return 列幅の配列(合計は max(Σmin, available))
	 */
	public static double[] distribute(double[] min, double[] target, ColumnType[] types, double available) {
		final int n = min.length;
		final double[] sizes = new double[n];
		double sum = 0;
		for (int i = 0; i < n; ++i) {
			sizes[i] = min[i];
			sum += min[i];
		}

		// 優先順に目標幅まで拡張
		for (final ColumnType type : GROW_ORDER) {
			if (available <= sum) {
				return sizes;
			}
			double deficit = 0;
			for (int i = 0; i < n; ++i) {
				if (types[i] == type) {
					deficit += Math.max(0, target[i] - sizes[i]);
				}
			}
			if (deficit <= 0) {
				continue;
			}
			final double rem = available - sum;
			final double ratio = deficit <= rem ? 1 : rem / deficit;
			for (int i = 0; i < n; ++i) {
				if (types[i] != type) {
					continue;
				}
				final double diff = Math.max(0, target[i] - sizes[i]) * ratio;
				sizes[i] += diff;
				sum += diff;
			}
		}

		// 余剰の分配
		if (available > sum) {
			final double rem = available - sum;
			for (final ColumnType type : EXCESS_ORDER) {
				int count = 0;
				double typeSum = 0;
				for (int i = 0; i < n; ++i) {
					if (types[i] == type) {
						++count;
						typeSum += sizes[i];
					}
				}
				if (count == 0) {
					continue;
				}
				for (int i = 0; i < n; ++i) {
					if (types[i] != type) {
						continue;
					}
					sizes[i] += typeSum > 0 ? rem * sizes[i] / typeSum : rem / count;
				}
				break;
			}
		}
		return sizes;
	}
}
