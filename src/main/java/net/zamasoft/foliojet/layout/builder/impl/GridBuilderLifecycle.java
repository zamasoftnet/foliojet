package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * Grid構築ライフサイクルの入口です(Grid G1b——TableBuilderLifecycleと
 * 同じ薄い形。実行計画の選択ではなく、G1適格判定と開始/終了の対称性
 * だけを持つ)。
 */
public final class GridBuilderLifecycle {
	private GridBuilderLifecycle() {
		// 静的ユーティリティ
	}

	/**
	 * トラック配置を適用できるGridかを判定します
	 * (consult-codex-2026-07-31-grid-g1.txt §1.1)。不適格(明示行
	 * トラック・縦書き)はG0の単一列フローへ落とす。列種は
	 * fixed(G1)/auto(G3b)/fr(G3c)の全部、宿主はBlockBuilderに加えて
	 * TwoPass(G3d1——実行計画をGridEventとして録画し幅確定後にbind)も
	 * 適格(consult-codex-2026-07-31-grid-g3.txt Q3)。
	 */
	public static boolean eligible(final GridBox gridBox, final Builder builder) {
		final GridParams params = gridBox.getGridParams();
		// grid-template-columns無し(=暗黙の単一autoカラム)も適格(2026-08-09)。
		// 従来はG0の単一列フローへ落としており、place-items等のitem整列が
		// 一切効かなかった(NHKニュースのボタンのアイコン中央寄せ)。
		// GridBoxは不適格でもPageAtomicのため、適格化で改ページ特性は
		// 変わらない。暗黙トラックの補完はGridBuilderのコンストラクタが行う
		// grid-template-rows有りも適格(2026-08-29)。従来はG0の単一列へ
		// 落としていたが、grid-template-areasを使う実物のページはほぼ必ず
		// 行テンプレートも書く。固定長の行はその高さ、auto/fr/%の行は内容高
		// (GridBuilder.bind参照)
		if (params.flow.isVertical()) {
			if (params.writingModeVariant == WritingModeVariant.NORMAL
					|| TypesettingMode.inlineProgression(params.flow, params.writingModeVariant,
							params.direction) != TypesettingMode.InlineProgression.BOTTOM_TO_TOP) {
				return false;
			}
		}
		return builder instanceof BlockBuilder || builder instanceof TwoPassBlockBuilder;
	}

	/** GridBuilderを開始します(適格判定済みであること)。 */
	public static GridBuilder start(final Builder builder, final GridBox gridBox) {
		return new GridBuilder(builder, gridBox);
	}
}
