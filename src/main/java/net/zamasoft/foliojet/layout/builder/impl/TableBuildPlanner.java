package net.zamasoft.foliojet.layout.builder.impl;

import java.util.EnumSet;

import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * 表の実行計画(Incremental/Retained)を単一の判定点で決定します(C4-B、
 * 2026-07-19)。旧{@code LayoutUtils.needsIntrinsicSizing(TableBox)}の
 * boolean一本化(auto列幅・非FLOW配置・ページ軸寸法指定・行軸auto寸法の
 * 4条件を1つのbooleanへ潰していた)を、理由ごとに追跡できる型へ置き換える。
 * 判定条件そのものは旧実装から変更していない(挙動不変)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class TableBuildPlanner {
	private TableBuildPlanner() {
	}

	/**
	 * @param builder  表を構築するコンテキストのビルダー
	 * @param tableBox 対象の表ボックス
	 */
	public static TableBuildPlan plan(final Builder builder, final TableBox tableBox) {
		final TableParams params = tableBox.getTableParams();
		final EnumSet<TableRetentionReason> reasons = EnumSet.noneOf(TableRetentionReason.class);
		if (!builder.isMain()) {
			reasons.add(TableRetentionReason.NESTED_LAYOUT);
		}
		if (params.layout == TableParams.LAYOUT_AUTO) {
			reasons.add(TableRetentionReason.AUTO_COLUMNS);
		}
		final boolean isFlow = tableBox.getBlockBox().getPos().getType() == PosType.FLOW;
		if (!isFlow) {
			reasons.add(TableRetentionReason.OUT_OF_FLOW);
		}
		if (params.size.getPageType(params.flow) != LengthType.AUTO) {
			reasons.add(TableRetentionReason.SPECIFIED_PAGE_SIZE);
		}
		if (params.size.getLineType(params.flow) == LengthType.AUTO) {
			reasons.add(TableRetentionReason.AUTO_LINE_SIZE);
		}
		if (reasons.isEmpty()) {
			return new TableBuildPlan(TableBuildPlan.Mode.INCREMENTAL, reasons);
		}
		return new TableBuildPlan(TableBuildPlan.Mode.RETAINED, reasons);
	}
}
