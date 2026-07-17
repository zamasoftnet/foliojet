package net.zamasoft.foliojet.layout.builder.impl;

import java.util.List;

/**
 * 行・セル配置の共有核です(P2-2: §5.2b 表ビルダー統一)。
 *
 * <p>
 * OnePass/TwoPass 両ビルダーが鏡像で持っていた行高さのアルゴリズムを、
 * ボックス木から切り離した配列演算として統合します。呼び出し側は
 * 行ボックスから配列を組み、結果を書き戻す(データの出所だけが
 * ビルダーごとに異なる — CellContent.complementRowspan と同じ分担)。
 * </p>
 */
final class RowLayoutEngine {
	private RowLayoutEngine() {
		// engine
	}

	/**
	 * rowspan で連結された行の高さを分配します(両ビルダーの同一
	 * アルゴリズムの統合)。各連結について、連結範囲の行高合計が連結
	 * セルの要求(min)に足りなければ、不足分を (1) %指定行に比率適用 →
	 * (2) 連結によってのみ拡張された自動行 → (3) 自動行 → (4) 全行、の
	 * 優先順で分配する。
	 *
	 * @param rowSizes    各行の高さ(入出力)
	 * @param rowspanList 連結(row=開始行、span=連結数、min=要求高さ)。
	 *                    Rowspan.SPAN_COMPARATOR でソート済みであること
	 * @param noAdjRows   連結されないセルを含む行
	 * @param autoRows    自動高さの行
	 * @param rowRatios   %指定行の比率(なければ 0)
	 */
	/**
	 * 行グループの指定高さを行へ分配します(両ビルダーの同一アルゴリズムの
	 * 統合)。行高合計が指定に満たなければ比例拡大し、合計0なら均等分配
	 * する(均等分配の分母はグループ自身の行数 — 旧 TwoPass は表全体の
	 * 行数で割っており合計が指定高にならなかったが、この分岐は通常文書
	 * では到達し難く fixture では発火確認できていない。正規化して統合)。
	 *
	 * @param rowSizes  各行の高さ(入出力)
	 * @param groupSize 行グループの指定高さ
	 * @return 行高合計の増分
	 */
	static double distributeGroupSize(final double[] rowSizes, final double groupSize) {
		double sum = 0;
		for (final double s : rowSizes) {
			sum += s;
		}
		if (groupSize <= sum) {
			return 0;
		}
		double added = 0;
		for (int i = 0; i < rowSizes.length; ++i) {
			final double size = sum == 0 ? groupSize / rowSizes.length : rowSizes[i] * groupSize / sum;
			added += size - rowSizes[i];
			rowSizes[i] = size;
		}
		return added;
	}

	static void distributeSpannedRowSizes(final double[] rowSizes, final List<Rowspan> rowspanList,
			final boolean[] noAdjRows, final boolean[] autoRows, final double[] rowRatios) {
		for (int j = 0; j < rowspanList.size(); ++j) {
			final Rowspan rowspan = (Rowspan) rowspanList.get(j);
			double minSum = rowSizes[rowspan.row];
			for (int k = 1; k < rowspan.span; ++k) {
				final int kk = rowspan.row + k;
				if (kk < rowSizes.length) {
					minSum += rowSizes[kk];
				}
			}
			double minRem = rowspan.min - minSum;
			if (minRem > 0) {
				// minを分配
				double adjCount = 0, autoCount = 0;
				for (int k = 0; k < rowspan.span; ++k) {
					final int kk = rowspan.row + k;
					if (kk >= rowSizes.length) {
						break;
					}
					if (!noAdjRows[kk] && autoRows[kk]) {
						++adjCount;
					}
					if (autoRows[kk]) {
						++autoCount;
					}
					// %の適用
					if (rowRatios[kk] > 0) {
						final double diff = minRem * rowRatios[kk];
						minRem -= diff;
						rowSizes[kk] += diff;
					}
				}
				if (adjCount > 0 && adjCount < rowspan.span) {
					// 連結により拡張したセルのだけの行に分配
					minRem /= adjCount;
					for (int k = 0; k < rowspan.span; ++k) {
						final int kk = rowspan.row + k;
						if (kk >= rowSizes.length) {
							break;
						}
						if (!noAdjRows[kk] && autoRows[kk]) {
							rowSizes[kk] += minRem;
						}
					}
				} else if (autoCount > 0 && autoCount < rowspan.span) {
					// 自動高さの行に分配
					minRem /= autoCount;
					for (int k = 0; k < rowspan.span; ++k) {
						final int kk = rowspan.row + k;
						if (kk >= rowSizes.length) {
							break;
						}
						if (autoRows[kk]) {
							rowSizes[kk] += minRem;
						}
					}
				} else {
					// 高さの分配
					minRem /= rowspan.span;
					for (int k = 0; k < rowspan.span; ++k) {
						final int kk = rowspan.row + k;
						if (kk >= rowSizes.length) {
							break;
						}
						rowSizes[kk] += minRem;
					}
				}
			}
		}
	}
}
