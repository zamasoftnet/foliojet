package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

/**
 * colgroup 構造から固定レイアウトの列指定を組み立てるヘルパです。
 * IncrementalTableBuilder / RetainedTableBuilder で共有します。
 *
 * @author MIYABE Tatsuhiko
 */
final class TableColumnSpecs {
	private TableColumnSpecs() {
		// utility
	}

	/**
	 * colgroup 配下の列数(span 込み)を数えます。
	 *
	 * @param root 列グループのルート
	 * @return 列数
	 */
	static int countColumns(TableColumnGroupBox root) {
		final int[] count = { 0 };
		root.forEachColumn(column -> count[0] += column.getTableColumnPos().span);
		return count[0];
	}

	/**
	 * colgroup 由来の列指定を組み立てます。
	 *
	 * @param root            列グループのルート
	 * @param columnCount     列数
	 * @param refSize         %指定の基準寸法
	 * @param separateSpacing 分離境界モデルの場合に各指定へ加算する境界間隔
	 *                        (それ以外は0)
	 * @return 列指定(AUTO は null)
	 */
	static FixedColumnWidths.Spec[] colgroupSpecs(TableColumnGroupBox root, int columnCount, double refSize,
			double separateSpacing) {
		final FixedColumnWidths.Spec[] specs = new FixedColumnWidths.Spec[columnCount];
		final int[] k = { 0 };
		root.forEachColumn(column -> {
			final FixedColumnWidths.Spec spec = switch (column.getInnerTableParams().size.getType()) {
			case AUTO -> null;
			case ABSOLUTE -> new FixedColumnWidths.Spec(column.getInnerTableParams().size.getLength() + separateSpacing,
					false);
			case RELATIVE -> new FixedColumnWidths.Spec(
					refSize * column.getInnerTableParams().size.getLength() + separateSpacing, true);
			// calc()による絶対長さと割合の混在(例: calc(50% + 10px))。refSizeは
			// この時点で既に確定しているため、ABSOLUTEと同様に確定値として扱う。
			case MIXED -> new FixedColumnWidths.Spec(column.getInnerTableParams().size.getLength()
					+ refSize * column.getInnerTableParams().size.getRatio() + separateSpacing, false);
			};
			final int span = column.getTableColumnPos().span;
			for (int j = 0; j < span; ++j) {
				specs[k[0]++] = spec;
			}
		});
		return specs;
	}
}
