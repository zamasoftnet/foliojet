package net.zamasoft.foliojet.layout.sizing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 自動レイアウト(table-layout: auto)の列幅解決です。 SPEC CSS 2.1 17.5.2.2
 * FixedColumnWidths と対を成す。colgroup 指定とセルの実測
 * (最小幅/推奨幅/指定幅)を列型ラダー(推奨 &lt; 絶対 &lt; パーセント)で
 * 蓄積し、列結合(colspan)の指定幅・最小幅・パーセント幅を3パスで
 * 分配して、列配列と表の最小・最大行寸法を確定する。 ボックスに触れない
 * 純粋な蓄積器で、finish() で結果配列の所有権を呼び出し側へ返す
 * (P2-4: §5.2b 表ビルダー統一)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class AutoColumnWidths {
	/** 列型: 推奨幅(内容由来)。 */
	public static final byte COLUMN_TYPE_DES = 0;
	/** 列型: 絶対指定。 */
	public static final byte COLUMN_TYPE_FIX = 1;
	/** 列型: パーセント。 */
	public static final byte COLUMN_TYPE_PCT = 2;

	/**
	 * 解決結果です。配列の所有権は呼び出し側に移る(レイアウト段で
	 * 基準寸法により変換・消費される)。
	 *
	 * @param mins        列の最小幅
	 * @param specs       列の指定幅(型に応じ絶対値または比率)
	 * @param desired     列の推奨幅
	 * @param types       列型(COLUMN_TYPE_*)
	 * @param minLineSize 表の最小行寸法(枠込み)
	 * @param maxLineSize 表の最大行寸法(枠込み)
	 */
	public record Result(double[] mins, double[] specs, double[] desired, byte[] types, double minLineSize,
			double maxLineSize) {
		/**
		 * 表の使用行寸法と列幅を確定します(CSS 2.1 17.5.2.2
		 * [Column widths influence the final table width as follows])。
		 * 指定がなければ最大行寸法を基準に%列による拡張を試み、最小・最大
		 * 表幅にクランプした上で、%指定を絶対値へ変換(specs を破壊的に
		 * 更新)して ColumnDistribution で列幅を分配する。
		 *
		 * @param specifiedLineSize 指定の表寸法(なければ LayoutUtils.NONE)
		 * @param maxTableSize      利用可能な最大表寸法
		 * @param tableFrame        表の枠
		 * @param lineBorderSpacing 行方向の境界間隔
		 * @param separateBorders   分離境界であれば true
		 * @return 表寸法と列幅
		 */
		public Sized resolve(final double specifiedLineSize, final double maxTableSize, final double tableFrame,
				final double lineBorderSpacing, final boolean separateBorders) {
			final int columnCount = this.mins.length;
			double tableSize = specifiedLineSize;
			double[] columnSizes = new double[columnCount];
			if (columnCount == 0) {
				return new Sized(LayoutUtils.isNone(tableSize) ? 0 : tableSize, columnSizes);
			}
			if (LayoutUtils.isNone(tableSize)) {
				tableSize = this.maxLineSize;
				if (tableSize < maxTableSize && columnCount > 1) {
					// パーセント幅によるテーブルの拡張
					int pctCount = 0, effColumnCount = 0;
					double pctSum = 0, noPctDesiredSum = 0;
					double w = tableSize - tableFrame;
					for (int i = 0; i < columnCount; ++i) {
						double des = this.desired[i];
						if (this.types[i] != COLUMN_TYPE_PCT && des == 0) {
							continue;
						}
						++effColumnCount;
						if (this.types[i] != COLUMN_TYPE_PCT) {
							noPctDesiredSum += des;
							continue;
						}
						++pctCount;
						double pct = this.specs[i];
						pctSum += pct;
						if (pct != 1 && pct != 0) {
							w = Math.max(w, des / pct);
						} else {
							w = maxTableSize - tableFrame;
						}
						if (w >= maxTableSize - tableFrame) {
							break;
						}
					}
					if (pctCount != 0 && pctCount != effColumnCount) {
						if (pctSum != 1 && pctSum != 0) {
							w = Math.max(w, noPctDesiredSum / (1 - pctSum));
						} else if (noPctDesiredSum > 0) {
							w = maxTableSize - tableFrame;
						}
					}
					tableSize = w + tableFrame;
				}
			}
			// minLineSize には tableFrame が含まれていることに注意
			if (tableSize < this.minLineSize) {
				tableSize = this.minLineSize;
			}
			if (tableSize > maxTableSize) {
				tableSize = maxTableSize;
			}
			final double innerSize = tableSize - tableFrame;
			// ％幅の計算
			final double refSize = separateBorders ? innerSize - columnCount * lineBorderSpacing : innerSize;
			for (int i = 0; i < columnCount; ++i) {
				if (this.types[i] != COLUMN_TYPE_PCT) {
					continue;
				}
				this.specs[i] *= refSize;
				if (separateBorders) {
					// 分離境界
					this.specs[i] += lineBorderSpacing;
				}
			}

			// 列幅の分配 (css-tables-3)
			double[] startSizes = this.mins;
			double minSum = 0;
			for (int i = 0; i < columnCount; ++i) {
				minSum += this.mins[i];
			}
			if (minSum > maxTableSize) {
				// 最小幅の合計が最大表幅を超える場合は比例縮小する
				startSizes = new double[columnCount];
				for (int i = 0; i < columnCount; ++i) {
					startSizes[i] = this.mins[i] * (maxTableSize - tableFrame) / minSum;
				}
			}
			final ColumnDistribution.ColumnType[] distTypes = new ColumnDistribution.ColumnType[columnCount];
			for (int i = 0; i < columnCount; ++i) {
				distTypes[i] = switch (this.types[i]) {
				case COLUMN_TYPE_FIX -> ColumnDistribution.ColumnType.CONSTRAINED;
				case COLUMN_TYPE_PCT -> ColumnDistribution.ColumnType.PERCENT;
				default -> ColumnDistribution.ColumnType.AUTO;
				};
			}
			columnSizes = ColumnDistribution.distribute(startSizes, this.specs, distTypes, innerSize);
			return new Sized(tableSize, columnSizes);
		}
	}

	/**
	 * 表寸法の解決結果です。
	 *
	 * @param tableSize   表の行寸法(枠込み)
	 * @param columnSizes 列幅
	 */
	public record Sized(double tableSize, double[] columnSizes) {
	}

	/** 列結合の蓄積です。 */
	private static final class Colspan {
		final int col, span;
		double min = 0;
		double pct = LayoutUtils.NONE;
		double fix = LayoutUtils.NONE;
		double des = 0;

		Colspan(int col, int span) {
			assert span >= 2;
			this.col = col;
			this.span = span;
		}

		public boolean equals(Object o) {
			Colspan colspan = (Colspan) o;
			return this.col == colspan.col && this.span == colspan.span;
		}

		public int hashCode() {
			return 31 * this.col + this.span;
		}

		static final Comparator<Colspan> SPAN_COMPARATOR = (span1, span2) -> Integer.compare(span1.span, span2.span);
	}

	private final double[] mins, specs, desired;
	private final byte[] types;
	private final Map<Colspan, Colspan> colspans = new HashMap<>();
	private final List<Colspan> colspanList = new ArrayList<>();

	public AutoColumnWidths(final int columnCount) {
		this.mins = new double[columnCount];
		this.specs = new double[columnCount];
		this.desired = new double[columnCount];
		this.types = new byte[columnCount];
	}

	/**
	 * colgroup 由来の絶対指定幅を適用します(span の各列へ)。
	 */
	public void specFixed(final int col, final int span, final double fix) {
		for (int s = 0; s < span; ++s) {
			final int k = col + s;
			if (this.types[k] <= COLUMN_TYPE_FIX) {
				if (this.types[k] != COLUMN_TYPE_FIX) {
					this.types[k] = COLUMN_TYPE_FIX;
					this.specs[k] = 0;
				}
				this.specs[k] = Math.max(this.specs[k], fix);
			}
			this.desired[k] = Math.max(this.desired[k], fix);
		}
	}

	/**
	 * colgroup 由来のパーセント指定幅を適用します(span の各列へ)。
	 */
	public void specPercent(final int col, final int span, final double pct) {
		for (int s = 0; s < span; ++s) {
			final int k = col + s;
			if (this.types[k] <= COLUMN_TYPE_PCT) {
				if (this.types[k] != COLUMN_TYPE_PCT) {
					this.types[k] = COLUMN_TYPE_PCT;
					this.specs[k] = 0;
				}
				if (pct > this.specs[k]) {
					double pctDiff = pct - this.specs[k];
					this.specs[k] += pctDiff;
					this.desired[k] = 1; // PCT指定には一応なんらかの内容があると判断させるため
				}
			}
		}
	}

	/**
	 * colgroup 由来の min-size を先頭列に適用します。
	 */
	public void colMin(final int col, final double minSize) {
		this.mins[col] = Math.max(minSize, this.mins[col]);
		this.desired[col] = Math.max(minSize, this.desired[col]);
	}

	/**
	 * colgroup 由来の max-size を先頭列に適用します。
	 */
	public void colMax(final int col, final double maxSize) {
		this.mins[col] = Math.min(maxSize, this.mins[col]);
		if (this.types[col] == COLUMN_TYPE_FIX) {
			this.specs[col] = Math.min(maxSize, this.specs[col]);
			this.desired[col] = Math.min(maxSize, this.desired[col]);
		}
	}

	/**
	 * セルの実測を蓄積します。連結なしは列型ラダーで即時適用、連結ありは
	 * Colspan バケットに積み finish() の3パスで分配する。
	 *
	 * @param col  先頭カラム(0オリジン)
	 * @param span 列結合数
	 * @param min  最小幅(枠込み)
	 * @param des  推奨幅(枠込み)
	 * @param type 指定の型(COLUMN_TYPE_*)
	 * @param spec 指定幅(型に応じ絶対値または比率。DES では des と同値)
	 */
	public void cell(final int col, final int span, final double min, double des, final byte type, final double spec) {
		if (span == 1) {
			// 連結なし
			this.mins[col] = Math.max(this.mins[col], min);
			switch (type) {
			case COLUMN_TYPE_DES:
				if (this.types[col] == COLUMN_TYPE_DES) {
					this.specs[col] = Math.max(this.specs[col], spec);
				}
				break;
			case COLUMN_TYPE_FIX:
				if (this.types[col] <= COLUMN_TYPE_FIX) {
					if (this.types[col] != COLUMN_TYPE_FIX) {
						this.types[col] = COLUMN_TYPE_FIX;
						this.specs[col] = 0;
					}
					this.specs[col] = Math.max(this.specs[col], spec);
					this.desired[col] = Math.max(this.mins[col], this.specs[col]);
				} else {
					des = Math.max(des, spec);
				}
				break;
			case COLUMN_TYPE_PCT:
				if (this.types[col] <= COLUMN_TYPE_PCT) {
					if (this.types[col] != COLUMN_TYPE_PCT) {
						this.types[col] = COLUMN_TYPE_PCT;
						this.specs[col] = 0;
					}
					if (spec > this.specs[col]) {
						double pctDiff = spec - this.specs[col];
						this.specs[col] += pctDiff;
					}
				}
				break;
			default:
				throw new IllegalStateException();
			}
			if (this.types[col] != COLUMN_TYPE_FIX) {
				this.desired[col] = Math.max(this.desired[col], des);
			}
		} else {
			// 連結あり
			Colspan key = new Colspan(col, span);
			Colspan colspan = this.colspans.get(key);
			if (colspan == null) {
				this.colspans.put(key, key);
				this.colspanList.add(key);
				colspan = key;
			}
			colspan.min = Math.max(colspan.min, min);
			colspan.des = Math.max(colspan.des, des);
			switch (type) {
			case COLUMN_TYPE_DES:
				break;
			case COLUMN_TYPE_FIX:
				if (LayoutUtils.isNone(colspan.fix)) {
					colspan.fix = spec;
				} else {
					colspan.fix = Math.max(colspan.fix, spec);
				}
				break;
			case COLUMN_TYPE_PCT:
				double pctDiff;
				if (LayoutUtils.isNone(colspan.pct)) {
					pctDiff = spec;
					colspan.pct = 0;
				} else {
					pctDiff = spec - colspan.pct;
				}
				if (pctDiff > 0) {
					colspan.pct += pctDiff;
				}
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}

	/**
	 * 蓄積された異なる(開始列, colspan)制約の個数(Uspan、最悪O(C²))を
	 * 返します。E-6増分1(2026-07-24)、spill閾値・対象選定の実測基盤の
	 * 読み取り専用アクセサ。挙動には影響しない。
	 */
	public int colspanConstraintCount() {
		return this.colspanList.size();
	}

	/**
	 * 列結合の3パス分配(指定幅/最小幅/パーセント幅)とパーセント制限を
	 * 実行し、結果を返します。
	 *
	 * @param tableFrame 表の枠(最小・最大行寸法に加算)
	 */
	public Result finish(final double tableFrame) {
		// colspanの適用
		Collections.sort(this.colspanList, Colspan.SPAN_COMPARATOR);
		for (int i = 0; i < this.colspanList.size(); ++i) {
			final Colspan colspan = this.colspanList.get(i);
			// 自動幅/固定幅を分配
			boolean fix = !LayoutUtils.isNone(colspan.fix);
			double spec = fix ? colspan.fix : colspan.des;
			double desSum = 0;
			int noFixCount = 0, effCount = 0;
			double noFixDesiredSum = 0;
			for (int s = 0; s < colspan.span; ++s) {
				int k = colspan.col + s;
				double des = this.desired[k];
				if (des == 0) {
					continue;
				}
				++effCount;
				desSum += des;
				if (this.types[k] == COLUMN_TYPE_FIX) {
					continue;
				}
				++noFixCount;
				noFixDesiredSum += des;
			}
			// 全てのカラムの幅が0なら幅0のカラムを無視しない
			if (effCount == 0) {
				noFixDesiredSum = 0;
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					double des = this.desired[k];
					++effCount;
					desSum += des;
					if (this.types[k] == COLUMN_TYPE_FIX) {
						continue;
					}
					++noFixCount;
					noFixDesiredSum += des;
				}
			}
			if (noFixCount == 0 && !fix) {
				// 元が全て固定幅の場合は、自動幅は適用しない
				continue;
			}
			if (spec > desSum) {
				if (effCount == 0) {
					effCount = colspan.span;
				}
				if (noFixCount == 0) {
					noFixDesiredSum = desSum;
				}
				double rem = spec - desSum;
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					if (effCount != colspan.span && this.desired[k] == 0) {
						continue;
					}
					if (noFixCount != 0 && this.types[k] == COLUMN_TYPE_FIX) {
						continue;
					}
					double diff;
					if (noFixDesiredSum > 0) {
						diff = rem * this.desired[k] / noFixDesiredSum;
					} else {
						diff = rem / colspan.span;
					}
					this.desired[k] += diff;
					if (this.types[k] == COLUMN_TYPE_PCT) {
						continue;
					}
					this.specs[k] = Math.max(this.mins[k], this.desired[k]);
				}
			}
		}
		for (int i = 0; i < this.colspanList.size(); ++i) {
			final Colspan colspan = this.colspanList.get(i);
			// 最小幅を分配
			double minSum = 0, desSum = 0, diffSum = 0;
			for (int s = 0; s < colspan.span; ++s) {
				int k = colspan.col + s;
				double min = this.mins[k];
				double des = this.desired[k];
				minSum += min;
				desSum += des;
				diffSum += (des - min);
			}
			if (colspan.min > minSum) {
				double rem = colspan.min - minSum;
				if (diffSum > 0) {
					double dist = Math.min(rem, diffSum);
					for (int s = 0; s < colspan.span; ++s) {
						int k = colspan.col + s;
						double min = this.mins[k];
						double des = this.desired[k];
						double diff = dist * (des - min) / diffSum;
						min = this.mins[k] += diff;
						if (this.types[k] == COLUMN_TYPE_DES) {
							this.specs[k] = Math.max(min, this.specs[k]);
						}
					}
					rem -= dist;
				}
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					double des = this.desired[k];
					double diff;
					if (desSum > 0) {
						diff = rem * des / desSum;
					} else {
						diff = rem / colspan.span;
					}
					double min = (this.mins[k] += diff);
					this.desired[k] = Math.max(min, this.desired[k]);
					if (this.types[k] != COLUMN_TYPE_DES) {
						continue;
					}
					this.specs[k] = Math.max(min, this.specs[k]);
				}
			}
		}
		for (int i = 0; i < this.colspanList.size(); ++i) {
			final Colspan colspan = this.colspanList.get(i);
			// パーセント幅をdesの比率で分配
			if (LayoutUtils.isNone(colspan.pct)) {
				continue;
			}
			double spec = colspan.pct;
			double pctSum = 0;
			int nonPctCount = 0;
			double nonPctSum = 0, desSum = 0;
			for (int s = 0; s < colspan.span; ++s) {
				int k = colspan.col + s;
				double des = this.desired[k];
				desSum += des;
				if (this.types[k] == COLUMN_TYPE_PCT) {
					pctSum += this.specs[k];
					continue;
				}
				++nonPctCount;
				nonPctSum += des;
			}
			if (spec > pctSum) {
				double rem = spec - pctSum;
				if (nonPctCount == 0) {
					nonPctCount = colspan.span;
					nonPctSum = desSum;
				}
				for (int s = 0; s < colspan.span; ++s) {
					int k = colspan.col + s;
					if (nonPctCount != colspan.span && this.types[k] == COLUMN_TYPE_PCT) {
						continue;
					}
					double diff;
					if (nonPctSum > 0) {
						diff = rem * this.desired[k] / nonPctSum;
					} else {
						diff = rem / nonPctCount;
					}
					if (this.types[k] == COLUMN_TYPE_PCT) {
						this.specs[k] += diff;
					} else {
						this.types[k] = COLUMN_TYPE_PCT;
						this.specs[k] = diff;
					}
				}
			}
		}

		// 最小幅/最大幅計算、パーセント幅制限
		double minLineSize = 0, maxLineSize = 0;
		double pctRem = 1;
		for (int i = 0; i < this.mins.length; ++i) {
			minLineSize += this.mins[i];
			if (this.types[i] == COLUMN_TYPE_PCT) {
				this.specs[i] = Math.min(pctRem, this.specs[i]);
				pctRem -= this.specs[i];
			}
			maxLineSize += Math.max(this.mins[i], this.desired[i]);
		}
		minLineSize += tableFrame;
		maxLineSize += tableFrame;
		return new Result(this.mins, this.specs, this.desired, this.types, minLineSize, maxLineSize);
	}
}
