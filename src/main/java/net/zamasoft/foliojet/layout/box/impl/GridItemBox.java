package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * Gridアイテムの合成ラッパーです(Grid G1b、2026-07-31——
 * consult-codex-2026-07-31-grid-g1.txt §2)。
 *
 * <p>
 * 幅はトラック幅で固定(構築前に確定)。行方向のトラック位置は継承済みの
 * {@code offsetX}({@link #setGridLineOffset})で与える——背景/枠・通常
 * 内容・テキストclipの三描画経路すべてに効く。合成ボックスなので
 * source protocolへは露出させない(記録・再生の対象外。再生時は
 * 同じ子イベントから決定的に再合成される)。
 * </p>
 */
public class GridItemBox extends FlowBlockBox {

	public GridItemBox(final BlockParams params, final FlowPos pos, final double trackWidth) {
		super(params, pos);
		this.width = trackWidth;
	}

	/** 行方向のトラック開始位置(Gridコンテナ内辺原点)を設定します。 */
	public void setGridLineOffset(final double lineOffset) {
		this.offsetX = lineOffset;
	}

	/**
	 * 確定したトラック幅を設定します(Grid G3a: bind直前に呼ぶ。
	 * 固定列では構築時の値と同じ。auto/fr列=G3b/cで解決値が入る)。
	 */
	public void setTrackWidth(final double trackWidth) {
		this.width = trackWidth;
	}
}
