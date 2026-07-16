package net.zamasoft.foliojet.layout.fragment;

import java.util.function.DoubleUnaryOperator;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * カラムバランス(column-fill: balance)の容量計算です(M5-B)。
 *
 * <p>
 * バランスとは「内容全体を N 個の断片(カラム)へできるだけ均等に分配する
 * 最小の断片容量を選ぶ」操作です。実際の切断は提案位置からはみ出す内容を
 * 次の断片へ送る(=直前の境界へ切り下がる)ため、旧実装の 総量/N の
 * 一回スナップでは切り下がりが段ごとに累積し、最終段だけが長くなる偏りが
 * 生じていました。ここでは切り下げオラクル(Container.getCutPointBelow、
 * ボックスを変異させない切断位置の見積り)で N-1 回の切断を窓送りで
 * シミュレートし、全内容が収まる最小容量を二分探索します。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ColumnBalancer {
	private static final int MAX_ITERATIONS = 32;

	private ColumnBalancer() {
		// pure functions
	}

	/**
	 * 内容全体を columnCount 個のカラムへ均等に分配する最小容量を探索します。
	 *
	 * @param cutPointBelow 提案位置直前の実行可能な切断位置を返す関数
	 *                      (Container.getCutPointBelow)
	 * @param totalSize     内容全体のページ方向寸法
	 * @param columnCount   カラム数
	 * @return カラムのページ方向容量
	 */
	public static double balance(final DoubleUnaryOperator cutPointBelow, final double totalSize,
			final int columnCount) {
		if (columnCount <= 1 || LayoutUtils.compare(totalSize, 0) <= 0) {
			return Math.max(totalSize, 0);
		}
		final double even = totalSize / columnCount;
		if (fits(cutPointBelow, even, totalSize, columnCount)) {
			return even;
		}
		// even では最終段があふれる: 全体が収まる最小容量を二分探索
		double lower = even;
		double upper = totalSize;
		for (int iter = 0; iter < MAX_ITERATIONS && upper - lower > 0.01; ++iter) {
			final double mid = (lower + upper) / 2;
			if (fits(cutPointBelow, mid, totalSize, columnCount)) {
				upper = mid;
			} else {
				lower = mid;
			}
		}
		return upper;
	}

	/**
	 * 容量 capacity で N-1 回切断したとき内容全体が収まるかを判定します。
	 */
	private static boolean fits(final DoubleUnaryOperator cutPointBelow, final double capacity, final double totalSize,
			final int columnCount) {
		double pos = 0;
		for (int k = 1; k < columnCount; ++k) {
			final double proposed = pos + capacity;
			if (LayoutUtils.compare(proposed, totalSize) >= 0) {
				return true;
			}
			double end = cutPointBelow.applyAsDouble(proposed);
			if (LayoutUtils.compare(end, pos) <= 0) {
				// 内部に境界がない(切断不能な)領域: 実切断も進められないため
				// 提案位置をそのまま採用して先へ進む
				end = proposed;
			}
			pos = end;
		}
		return LayoutUtils.compare(totalSize - pos, capacity) <= 0;
	}
}
