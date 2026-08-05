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

	protected GridItemBox(final BlockParams params, final FlowPos pos,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame,
			final net.zamasoft.foliojet.layout.box.content.Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	/**
	 * <b>継続断片も同じ種別で作る</b>(2026-08-05)。
	 *
	 * <p>
	 * {@link FlowBlockBox#fragmentRecipe()} は {@code new FlowBlockBox(...)} を
	 * 直に書いているので、<b>上書きしないと継続断片が素のブロックになる</b>。
	 * {@code ContinuationValidator} が種別の食い違いを検出して
	 * <b>変換全体を止める</b>——実地コーパス第23波の {@code ecma262}
	 * (ECMAScript仕様書、7.5MBの単一ページ)がこれで、出力2.9MBの途中で
	 * 落ちていた。{@code MulticolumnBlockBox} だけが上書きしていた。
	 * </p>
	 */
	@Override
	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new GridItemBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
