package net.zamasoft.foliojet.layout.builder.impl;

import java.util.function.Supplier;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * セルの軸寸法の共通計算です(A-3、2026-07-30)。Incremental/Retained
 * 両ビルダーにあった同型ブロックの統合。演算順は旧実装のまま。
 */
final class TableCellMetrics {
	private TableCellMetrics() {
	}

	/**
	 * colspanの窓の列幅を合算します。加算順は旧実装どおり左から。
	 */
	static double spannedLineSize(final double[] columnSizes, final int column, final int span) {
		double size = columnSizes[column];
		for (int j = 1; j < span; ++j) {
			assert !LayoutUtils.isNone(columnSizes[column + j]);
			size += columnSizes[column + j];
		}
		return size;
	}

	/**
	 * 行軸寸法を適用し、直交書字方向のセルには直交軸へ内容の実測を
	 * 適用します(両ビルダー共通の規約。旧実装はfontSize*10の仮寸法——
	 * 0390-writing-mode/orthogonal-cell-fixedで是正済み)。
	 *
	 * @param intrinsics 直交セルのときだけ評価される(遅延——
	 *                   非直交セルでは計測ビルダーを参照しない)
	 */
	static void applyLineAxis(final TableCellBox cellBox, final Supplier<IntrinsicSizes> intrinsics,
			final double lineSize, final boolean vertical, final TableParams tableParams) {
		if (vertical) {
			cellBox.setHeight(lineSize);
			if (!cellBox.getBlockParams().flow.isVertical()) {
				cellBox.setWidth(intrinsics.get().maxContent() + cellBox.getFrame().getFrameWidth()
						+ tableParams.borderSpacingH);
			}
		} else {
			cellBox.setWidth(lineSize);
			if (cellBox.getBlockParams().flow.isVertical()) {
				cellBox.setHeight(intrinsics.get().maxContent() + cellBox.getFrame().getFrameHeight()
						+ tableParams.borderSpacingV);
			}
		}
	}
}
