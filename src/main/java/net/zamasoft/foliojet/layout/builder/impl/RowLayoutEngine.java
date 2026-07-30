package net.zamasoft.foliojet.layout.builder.impl;

import java.util.List;

/**
 * 行・セル配置の共有核です(P2-2: §5.2b 表ビルダー統一)。
 *
 * <p>
 * 「両ビルダーの行高さアルゴリズムを統合した」という説明は過大だった
 * (2026-07-19訂正、C4-D・外部設計レビュー)。実際に共有されているのは
 * 局所/全体それぞれの行高方針をボックス木から切り離した純粋な配列演算
 * であり、いつ・どの範囲へ・どの順序で適用するかはIncremental
 * (IncrementalTableBuilder)/Retained(RetainedTableBuilder)で異なる。
 * 呼び出し側は行ボックスから配列を組み、結果を書き戻す(データの出所
 * だけがビルダーごとに異なる — CellContent.complementRowspan と同じ分担)。
 * </p>
 * <p>
 * 概念上2種類に分かれる(クラス自体は分離していない、2026-07-19時点):
 * <ul>
 * <li><b>局所(窓内で完結、Incremental/Retained共用)</b>: {@link #rowSpec}、
 * {@link #distributeSpannedRowSizes}(開いているrowspanの範囲だけで完結)、
 * {@link #distributeGroupSize}(1つのrow-group内で完結——絶対高さ
 * row-groupはIncrementalでもこの単位を丸ごと保持する、P0-2参照)。</li>
 * <li><b>表全体(Retained専用。specifiedPageSize=0=表heightがautoの
 * 場合は恒等的に無効になるため、Incrementalは実質呼ばない)</b>:
 * {@link #distributePercentRowSizes}、{@link #distributeTableSize}。</li>
 * </ul>
 * </p>
 */
public final class RowLayoutEngine {
	private RowLayoutEngine() {
		// engine
	}

	/**
	 * 指定行高の導出結果です。
	 *
	 * @param size  指定・min/max から確定した行高(自動・%は 0)
	 * @param ratio %指定の比率(なければ 0)
	 * @param auto  自動高さ(0% 指定も自動として扱う)
	 */
	public record RowSpec(double size, double ratio, boolean auto) {
	}

	/**
	 * 行の指定高さを導出します(両ビルダーの同一 switch の統合)。
	 * ABSOLUTE は指定値、%は比率へ、0% と AUTO は自動行。min/max の
	 * ABSOLUTE 指定でクランプする。
	 */
	public static RowSpec rowSpec(final net.zamasoft.foliojet.layout.box.params.InnerTableParams rowParams) {
		double rowSize;
		double ratio = 0;
		boolean auto = false;
		switch (rowParams.size.getType()) {
		case ABSOLUTE:
			rowSize = rowParams.size.getLength();
			break;
		case RELATIVE:
			ratio = rowParams.size.getLength();
			if (ratio > 0) {
				rowSize = 0;
				break;
			}
		case AUTO:
			auto = true;
			rowSize = 0;
			break;
		case MIXED:
			// calc()による絶対長さ+割合混在(例: calc(50% + 10pt))の行高は、
			// このRowSpecが前提とする「絶対値 or 比率の二択」に収まらない
			// (行高分配アルゴリズムのratio利用箇所は比率単独を前提にしている)。
			// RELATIVEと同じ扱いにして安全側に倒す(絶対成分は無視、比率成分の
			// みratioへ渡す。docs/PLAN.md参照)。
			ratio = rowParams.size.getRatio();
			if (ratio > 0) {
				rowSize = 0;
				break;
			}
			auto = true;
			rowSize = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		switch (rowParams.minSize.getType()) {
		case ABSOLUTE:
			rowSize = Math.max(rowParams.minSize.getLength(), rowSize);
			break;
		case RELATIVE:
		case MIXED:
		case AUTO:
			break;
		default:
			throw new IllegalStateException();
		}
		switch (rowParams.maxSize.getType()) {
		case ABSOLUTE:
			rowSize = Math.min(rowParams.maxSize.getLength(), rowSize);
			break;
		case RELATIVE:
		case MIXED:
		case AUTO:
			break;
		default:
			throw new IllegalStateException();
		}
		return new RowSpec(rowSize, ratio, auto);
	}

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
	public static double distributeGroupSize(final double[] rowSizes, final double groupSize) {
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

	/**
	 * %指定行の高さを表の指定高さへ向けて拡大します(文書順に残余を
	 * 消費)。
	 *
	 * @param rowSizes          各行の高さ(入出力)
	 * @param rowRatios         %指定行の比率(なければ 0)
	 * @param specifiedPageSize 表の指定高さ
	 * @param remainder         分配できる残余
	 * @return 行高合計の増分
	 */
	public static double distributePercentRowSizes(final double[] rowSizes, final double[] rowRatios,
			final double specifiedPageSize, double remainder) {
		double added = 0;
		for (int i = 0; i < rowSizes.length && remainder > 0; ++i) {
			if (rowRatios[i] > 0) {
				final double diff = Math.min(remainder, specifiedPageSize * rowRatios[i] - rowSizes[i]);
				if (diff > 0) {
					remainder -= diff;
					rowSizes[i] += diff;
					added += diff;
				}
			}
		}
		return added;
	}

	/**
	 * 表の指定高さへの不足分を行へ分配します。自動行と固定行が混在すれば
	 * 自動行へ現在高さの比で分配(自動行合計が0なら均等)、そうでなければ
	 * 全行を指定高さへ比例スケール(合計0なら均等)する。
	 *
	 * @param rowSizes          各行の高さ(入出力)
	 * @param autoRows          高さ指定が auto の行(%0 指定は含まない —
	 *                          rowspan 分配の autoRows とは判定が異なる)
	 * @param specifiedPageSize 表の指定高さ
	 */
	public static void distributeTableSize(final double[] rowSizes, final boolean[] autoRows,
			final double specifiedPageSize) {
		double rowSizeSum = 0;
		int autoRowCount = 0;
		for (int i = 0; i < rowSizes.length; ++i) {
			rowSizeSum += rowSizes[i];
			if (autoRows[i]) {
				++autoRowCount;
			}
		}
		if (rowSizeSum >= specifiedPageSize) {
			return;
		}
		if (autoRowCount > 0 && autoRowCount < rowSizes.length) {
			// 固定高さの行がある場合
			final double remainder = specifiedPageSize - rowSizeSum;
			double autoSum = 0;
			for (int i = 0; i < rowSizes.length; ++i) {
				if (autoRows[i]) {
					autoSum += rowSizes[i];
				}
			}
			for (int i = 0; i < rowSizes.length; ++i) {
				if (autoRows[i]) {
					rowSizes[i] += autoSum <= 0 ? remainder / autoRowCount : remainder * rowSizes[i] / autoSum;
				}
			}
		} else {
			for (int i = 0; i < rowSizes.length; ++i) {
				rowSizes[i] = rowSizeSum <= 0 ? specifiedPageSize / rowSizes.length
						: specifiedPageSize * rowSizes[i] / rowSizeSum;
			}
		}
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
	 * セルのページ軸要求寸法です(A-4、2026-07-30。両ビルダーの同型計算の
	 * 統合): 実測値と、ABSOLUTE指定(content-boxなら枠を加算)の大きい方。
	 * 演算順は旧実装のまま。
	 */
	public static double demandPageSize(final double measured,
			final net.zamasoft.foliojet.layout.box.params.BlockParams cellParams,
			final net.zamasoft.foliojet.layout.box.impl.TableCellBox cellBox, final boolean vertical) {
		double cellSize = measured;
		if (vertical) {
			if (cellParams.size.getWidthType() == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				double width = cellParams.size.getWidth();
				if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
					width += cellBox.getFrame().getFrameWidth();
				}
				cellSize = Math.max(cellSize, width);
			}
		} else {
			if (cellParams.size.getHeightType() == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				double height = cellParams.size.getHeight();
				if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
					height += cellBox.getFrame().getFrameHeight();
				}
				cellSize = Math.max(cellSize, height);
			}
		}
		return cellSize;
	}

	/**
	 * rowspanの分配要求を登録します(A-4。同一(row,span)は1つにまとめ、
	 * 要求値は最大を採る——両ビルダーの同型登録の統合)。
	 */
	public static void addSpannedDemand(final java.util.Map<Rowspan, Rowspan> rowspans,
			final java.util.List<Rowspan> rowspanList, final int row, final int span, final double size) {
		final Rowspan key = new Rowspan(row, span);
		Rowspan rowspan = rowspans.get(key);
		if (rowspan == null) {
			rowspan = key;
			rowspans.put(key, rowspan);
			rowspanList.add(rowspan);
		}
		rowspan.min = Math.max(rowspan.min, size);
	}

	public static void distributeSpannedRowSizes(final double[] rowSizes, final List<Rowspan> rowspanList,
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
