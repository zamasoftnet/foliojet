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

	/**
	 * このitemが跨ぐ親gridのトラック(subgrid用、css-grid-2、2026-08-29)。
	 * 親の{@code GridBuilder.bind}がitem本文のbind直前に設定し、item直下の
	 * {@code grid-template-columns: subgrid}なgridが自分のトラックとして継ぐ。
	 *
	 * @param columnWidths    跨ぐ列の解決済み幅(ソース順、span本)
	 * @param columnGap       親の列gap
	 * @param columnLineNames 跨ぐ列線の名前(span+1要素。親の明示名+areasの暗黙名)
	 * @param rowGap          親の行gap
	 */
	public record SubgridTracks(double[] columnWidths, double columnGap, java.util.List<java.util.List<String>> columnLineNames,
			double rowGap) {
	}

	private SubgridTracks subgridTracks;

	public GridItemBox(final BlockParams params, final FlowPos pos, final double trackWidth) {
		super(params, pos);
		this.width = trackWidth;
	}

	/** 跨ぐ親トラックを設定します(親の{@code GridBuilder.bind}、2026-08-29)。 */
	public void setSubgridTracks(final SubgridTracks tracks) {
		this.subgridTracks = tracks;
	}

	/** 跨ぐ親トラックです(親のトラック配置前・G0退行の親ではnull)。 */
	public SubgridTracks getSubgridTracks() {
		return this.subgridTracks;
	}

	/**
	 * 行方向のトラック開始位置(Gridコンテナ内辺原点)を設定します。
	 *
	 * <p>
	 * {@code baseOffsetX}にも同じ値を退避する(2026-08-06)。
	 * {@code AbstractContainerBox.resolveRelativeOffset}が
	 * {@code position:relative}のずらし量をこの上へ加算するための基準値
	 * ——退避しないと、そちらが{@code offsetX}を代入で上書きしてGridの
	 * 配置が消える(FlexItemBox.setFlexLineOffsetと同じ理由)。
	 * </p>
	 */
	public void setGridLineOffset(final double lineOffset) {
		this.baseOffsetX = lineOffset;
		this.offsetX = lineOffset;
	}

	/**
	 * {@link #setGridLineOffset}で設定した行方向位置を読みます
	 * (2026-08-10、grid行分割用)。
	 *
	 * <p>
	 * 行を跨いで強制分割した残余{@link GridItemBox}は{@code fragmentRecipe}が
	 * 新規生成するため行方向位置を引き継がない——分割後に呼び出し側が
	 * これで読んだ元の値を残余へ{@link #setGridLineOffset}し直す必要がある
	 * ({@code FlexItemBox.getFlexLineOffset}と同じ理由)。
	 * </p>
	 */
	public double getGridLineOffset() {
		return this.baseOffsetX;
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
		// 行方向は指定寸法でなく**トラック解決後の使用寸法**を継続断片へ
		// 運ぶ(2026-08-10、G6行分割)。widthはauto(トラック幅は
		// setTrackWidthの注入)なので、そのまま運ぶと継続断片のrestyle
		// 再構築(startFlowBlock.calculateSize)が包含幅=グリッド全幅へ
		// 再解決し、断片の背景がグリッド全幅の帯になる(row-split-carryの
		// page2で実測——FlexItemBox.fragmentRecipeの2026-08-08の修正と
		// 同じ機序)。レシピはthisを保持しない規約のため、値でキャプチャする
		final boolean vertical = params.flow.isVertical();
		final double usedTrack = (vertical ? this.height : this.width)
				+ (params.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? this.frame.getBorderLineExtent(params.flow)
						: 0);
		return (state, container) -> {
			final net.zamasoft.foliojet.layout.box.params.Dimension ns = state.nextSize();
			final net.zamasoft.foliojet.layout.box.params.Dimension sized = vertical
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(ns.getWidth(), ns.getWidthRatio(),
							usedTrack, 0, ns.getWidthType(),
							net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: net.zamasoft.foliojet.layout.box.params.Dimension.create(usedTrack, 0, ns.getHeight(),
							ns.getHeightRatio(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE,
							ns.getHeightType());
			return new GridItemBox(params, pos, sized, state.nextMinSize(), state.nextFrame(), container);
		};
	}
}
