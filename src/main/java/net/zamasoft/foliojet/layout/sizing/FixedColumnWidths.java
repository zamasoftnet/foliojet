package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * 固定レイアウト(table-layout: fixed)の列幅分配です。 SPEC CSS2.1 17.5.2.1
 * colgroup 指定と先頭行セル指定(colgroup 優先)をマージし、
 * 表幅を超えた%指定を削り、残余を AUTO 列に均等分配
 * (AUTO 列が無ければ全列に均等加算、余らなければ内容幅に切り詰め)します。
 * ボックスに触れない純関数です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class FixedColumnWidths {
	/**
	 * 列幅の指定です。指定なし(AUTO)は配列要素 null で表します。
	 *
	 * @param length     指定幅(解決済み)
	 * @param percentage %指定由来であればtrue(超過時の削り対象)
	 */
	public record Spec(double length, boolean percentage) {
	}

	/**
	 * 分配結果です。
	 *
	 * @param sizes     列幅
	 * @param innerSize 内容に合わせて調整された表の内側寸法
	 */
	public record Result(double[] sizes, double innerSize) {
	}

	private FixedColumnWidths() {
		// utility
	}

	/**
	 * セルの行方向指定から列幅指定を導出します(P2-2: OnePass/TwoPass の
	 * 同一実装の統合)。AUTO は null(指定なし)。
	 *
	 * @param cellBox   セル
	 * @param colspan   列結合数(指定幅は colspan で均等割り)
	 * @param tableFlow 表の書字方向
	 * @param refSize   %指定の基準寸法
	 */
	public static Spec cellSpec(final net.zamasoft.foliojet.layout.box.impl.TableCellBox cellBox, final int colspan,
			final net.zamasoft.foliojet.layout.box.params.WritingMode tableFlow, final double refSize) {
		final net.zamasoft.foliojet.layout.box.params.BlockParams cellParams = cellBox.getBlockParams();
		switch (cellParams.size.getLineType(tableFlow)) {
		case AUTO:
			return null;
		case ABSOLUTE: {
			double fix = cellParams.size.getLineLength(tableFlow);
			if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
				fix += cellBox.getFrame().getFrameLineExtent(tableFlow);
			}
			return new Spec(fix / colspan, false);
		}
		case RELATIVE: {
			double fix = refSize * cellParams.size.getLineLength(tableFlow);
			if (cellParams.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.CONTENT_BOX) {
				fix += cellBox.getFrame().getFrameLineExtent(tableFlow);
			}
			return new Spec(fix / colspan, true);
		}
		default:
			throw new IllegalStateException();
		}
	}

	/**
	 * 列幅を分配します。
	 *
	 * @param colgroupSpecs colgroup 由来の列指定(優先。なしは null)
	 * @param cellSpecs     先頭行セル由来の列指定(colspan 展開済み。なしは null)
	 * @param innerSize     表の内側寸法
	 * @return 列幅と調整後の内側寸法
	 */
	public static Result distribute(Spec[] colgroupSpecs, Spec[] cellSpecs, double innerSize) {
		final int n = colgroupSpecs.length;
		assert cellSpecs.length == n;
		final double[] sizes = new double[n];
		final Spec[] merged = new Spec[n];
		int autoCount = 0;
		double sizeSum = 0, percentSizeSum = 0;
		for (int i = 0; i < n; ++i) {
			final Spec spec = colgroupSpecs[i] != null ? colgroupSpecs[i] : cellSpecs[i];
			merged[i] = spec;
			if (spec == null) {
				++autoCount;
				sizes[i] = LayoutUtils.NONE;
			} else {
				sizes[i] = spec.length();
				sizeSum += spec.length();
				if (spec.percentage()) {
					percentSizeSum += spec.length();
				}
			}
		}

		// テーブル幅を超えたパーセント幅は削る
		if (percentSizeSum > 0 && sizeSum > innerSize) {
			final double removeSize = Math.min(percentSizeSum, sizeSum - innerSize);
			for (int i = 0; i < n; ++i) {
				final Spec spec = merged[i];
				if (spec != null && spec.percentage()) {
					final double sizeDiff = removeSize * spec.length() / percentSizeSum;
					sizes[i] -= sizeDiff;
					sizeSum -= sizeDiff;
				}
			}
		}

		if (autoCount > 0) {
			// 残余を AUTO 列に均等分配
			final double each;
			if (innerSize > sizeSum) {
				each = (innerSize - sizeSum) / autoCount;
			} else {
				innerSize = sizeSum;
				each = 0;
			}
			for (int i = 0; i < n; ++i) {
				if (LayoutUtils.isNone(sizes[i])) {
					sizes[i] = each;
				}
			}
		} else if (innerSize > sizeSum) {
			// AUTO 列が無ければ全列に均等加算
			final double each = (innerSize - sizeSum) / n;
			for (int i = 0; i < n; ++i) {
				sizes[i] += each;
			}
		} else {
			// 余らない場合は内容幅に切り詰め
			innerSize = 0;
			for (int i = 0; i < n; ++i) {
				innerSize += sizes[i];
			}
		}
		return new Result(sizes, innerSize);
	}
}
