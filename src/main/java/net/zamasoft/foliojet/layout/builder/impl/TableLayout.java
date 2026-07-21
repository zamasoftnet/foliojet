package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.TableBuilder;

/**
 * OnePassTableBuilder/TwoPassTableBuilderの選択・開始・終了を一箇所へ集約する
 * ライフサイクルアダプタです(C4準備、2026-07-19。C4-Bでルーティング判定を
 * {@link TableBuildPlanner}へ委譲)。
 *
 * <p>
 * 計算・箱操作は一切変更していません。DocumentBuilderに分散していた
 * ビルダー選択条件と、終了時のisIncremental()判定+OnePassTableBuilderへの
 * castをここへ移しただけです。
 * </p>
 *
 * <p>
 * <b>C4の完成形について(2026-07-19、ChatGPT Pro外部設計レビューで確定、
 * 詳細はdocs/PLAN.md「C4」参照)</b>: 表ビルダーの統一は「単一の行アルゴリズム
 * への融合」ではない。fixed/autoという列幅方針の違いとは別に、
 * 「早期コミット可能(Incremental)」か「表(またはrow-group)全体を保持
 * してからコミットする(Retained)」かという実行計画の違いが本質であり、
 * この保持寿命・コミット時点は統一すべきではない(数学的に、表全体依存の
 * 行高分配を互換規則どおり正確に行う・ページを完成次第不変な形で出力する・
 * 有界メモリで入力を一度だけ消費する、の3条件は同時に満たせないため)。
 * 統一してよいのは列幅計算・セル実測・rowspan分配・境界解決・断片化部品
 * という計算核であり、既にRowLayoutEngine/CellContent/CollapsedBorderRules
 * 等として共有済み。このクラスは「2つの実行計画を選ぶ薄い層」として
 * 確定させる方向で発展させる(C4-C: 2026-07-19、isOnePass()を
 * isIncremental()へ改名済み(挙動不変)。DocumentBuilder側の
 * 分岐そのものをTableBuildSession相当のtell-don't-ask構造へ
 * 置き換える本体作業は、DocumentBuilderの非公開インライン文脈操作
 * (closeInlines/endContainer/startContainer)を公開する設計判断を
 * 要するため、別途の設計サイクルへ回す)。
 * </p>
 */
public final class TableLayout {
	private TableLayout() {
	}

	/**
	 * 表の開始時に、{@link TableBuildPlanner}が決めた実行計画に従って
	 * ビルダーを選び、必要なら{@link OnePassTableBuilder#startLayout}まで
	 * 実行して返します。
	 */
	public static TableBuilder start(Builder builder, TableBox tableBox) {
		final TableBuildPlan plan = TableBuildPlanner.plan(builder, tableBox);
		if (plan.mode() == TableBuildPlan.Mode.RETAINED) {
			TableBuildStats.TWO_PASS_BUILDS.incrementAndGet();
			return new TwoPassTableBuilder(builder, tableBox);
		}
		// Incremental(table-layout:fixed相当)
		TableBuildStats.ONE_PASS_BUILDS.incrementAndGet();
		final OnePassTableBuilder fixedTableBuilder = new OnePassTableBuilder(tableBox);
		fixedTableBuilder.startLayout((RootBuilder) builder);
		return fixedTableBuilder;
	}

	/**
	 * 表の終了時に、開始側のルーティング結果と一致させるため、条件を
	 * 再計算せずtableBuilder自身に問うて終了処理を行います。
	 */
	public static void finish(TableBuilder tableBuilder, Builder builder) {
		if (!tableBuilder.isIncremental()) {
			// Retained
			builder.addTable(tableBuilder);
		} else {
			// Incremental(table-layout:fixed相当)
			((OnePassTableBuilder) tableBuilder).endLayout();
		}
	}
}
