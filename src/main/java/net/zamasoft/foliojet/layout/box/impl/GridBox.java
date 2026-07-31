package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.PageAtomicBox;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridParams;

/**
 * Gridコンテナです(Grid G0、2026-07-31——
 * consult-codex-2026-07-31-grid.txt §3)。
 *
 * <p>
 * ページング上は正規のblock({@code BoxType.BLOCK}/{@code PosType.FLOW})の
 * まま——rescue・描画・フレーム処理を{@link FlowBlockBox}から継承し、
 * {@link PageAtomicBox}でページ軸の構造分割だけを型付きで禁じる
 * (入らなければ丸ごと送り→visual rescue)。
 * </p>
 *
 * <p>
 * G0時点の内容配置は単一列の通常フロー(=FlowBlockBoxの挙動そのまま。
 * template=noneの意味論)。トラック解決とitem配置はG1以降で
 * {@code GridBuilder}が担う。
 * </p>
 */
public class GridBox extends FlowBlockBox implements PageAtomicBox {

	public GridBox(final GridParams params, final FlowPos pos) {
		super(params, pos);
	}

	public final GridParams getGridParams() {
		return (GridParams) this.params;
	}
}
