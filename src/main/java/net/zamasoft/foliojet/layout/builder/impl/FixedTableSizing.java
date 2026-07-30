package net.zamasoft.foliojet.layout.builder.impl;

import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

/**
 * {@code table-layout:fixed}の列幅解決です(A-3、2026-07-30)。
 * Incremental/Retained両ビルダーにあった同型ブロックの共有純関数。
 *
 * <p>
 * <b>Boxを変更しない。</b> colgroupの表への装着・{@code columnSizes}への
 * 反映・表幅の適用は呼び出し側に残す。加算・分配の演算順は旧実装の
 * ループをそのまま移した(浮動小数点の結果を変えない——golden既定)。
 * </p>
 */
final class FixedTableSizing {
	private FixedTableSizing() {
	}

	/**
	 * 先頭行のセルからSpecを導出します。Incrementalは指定寸法つきセルの
	 * {@code prepareLayout}という副作用を伴うため、導出そのものは
	 * 呼び出し側から注入する。
	 */
	interface CellSpecFactory {
		FixedColumnWidths.Spec spec(CellContent cell, double refSize);
	}

	/**
	 * colgroup・先頭行セルの指定から列幅を分配します。
	 *
	 * @param innerSize 表の内容領域の行方向寸法(フレーム控除済み)
	 */
	static FixedColumnWidths.Result resolve(final TableColumnGroupBox columnGroup,
			final List<CellContent> firstRowCells, final int columnCount, final double innerSize,
			final boolean separateBorders, final double lineBorderSpacing, final CellSpecFactory cellSpec) {
		double refSize = innerSize;
		if (separateBorders) {
			// 分離境界
			refSize -= columnCount * lineBorderSpacing;
		}
		refSize = Math.max(0, refSize);
		final FixedColumnWidths.Spec[] colgroupSpecs;
		if (columnGroup != null) {
			colgroupSpecs = TableColumnSpecs.colgroupSpecs(columnGroup, columnCount, refSize,
					separateBorders ? lineBorderSpacing : 0);
		} else {
			colgroupSpecs = new FixedColumnWidths.Spec[columnCount];
		}
		final FixedColumnWidths.Spec[] cellSpecs = new FixedColumnWidths.Spec[columnCount];
		if (firstRowCells != null) {
			for (int i = 0; i < columnCount; ++i) {
				if (i >= firstRowCells.size()) {
					continue;
				}
				final CellContent cell = firstRowCells.get(i);
				final FixedColumnWidths.Spec spec = cellSpec.spec(cell, refSize);
				cellSpecs[i] = spec;
				for (int j = 1; j < cell.colspan; ++j) {
					++i;
					if (i < columnCount) {
						cellSpecs[i] = spec;
					}
				}
			}
		}
		return FixedColumnWidths.distribute(colgroupSpecs, cellSpecs, innerSize);
	}
}
