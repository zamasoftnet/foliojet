package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * Flexアイテムのボックスです(Flex F1d、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt Q2)。
 *
 * <p>
 * {@code GridItemBox}(中立合成wrapper)と違い、plainなブロック直下子では
 * **authored childのBlockParams/FlowPosを引き継いで生成**し、元の外箱は
 * 構築しない——将来のstretch(F3c)でauthoredの背景・枠がitemサイズへ
 * 追随するため(答申の最重要プロトタイプ条件)。匿名テキスト・置換要素・
 * 非plain子(表・入れ子コンテナ等)のみ中立paramsのwrapperになる。
 * 合成経路でもauthored経路でもsource protocolへは露出させない
 * (記録・再生時は子イベントから決定的に再合成される)。
 * </p>
 */
public class FlexItemBox extends FlowBlockBox {

	public FlexItemBox(final BlockParams params, final FlowPos pos) {
		super(params, pos);
	}

	/**
	 * restyle再構築で潰れた確定寸法を復元します(2026-08-08、
	 * {@code FlexRowContainer.restyle}専用)。itemの寸法は
	 * {@code FlexBuilder}(item coordinator)が所有するが、ページ跨ぎ
	 * 移動後の汎用再構築は{@code startFlowBlock.calculateSize}が
	 * width:autoを包含幅へ、{@code endFlowBlock}がheight:autoを内容高
	 * (絶対配置子だけなら0)へ再解決してしまう——yahoo.co.jpの
	 * ランキング順位バッジが行全幅の色帯になった実バグ。
	 */
	public void restoreExtents(final double width, final double height) {
		this.width = width;
		this.height = height;
	}

	/**
	 * 線方向のitem開始位置(Flexコンテナ内辺原点、自然位置からの相対)を
	 * 設定します(F6: 縦書きでは物理Y)。
	 *
	 * <p>
	 * {@code baseOffsetX}/{@code baseOffsetY}にも同じ値を退避する
	 * (2026-08-06)。{@code AbstractContainerBox.resolveRelativeOffset}が
	 * {@code position:relative}のずらし量をこの上へ加算するための基準値
	 * ——退避しないと、そちらが{@code offsetX}を代入で上書きしてFlexの
	 * 配置が消える(検索ボタンの左右逆転・アイコンの原点集約はこれが原因)。
	 * </p>
	 */
	public void setFlexLineOffset(final double lineOffset, final boolean vertical) {
		if (vertical) {
			this.baseOffsetY = lineOffset;
			this.offsetY = lineOffset;
		} else {
			this.baseOffsetX = lineOffset;
			this.offsetX = lineOffset;
		}
	}

	/**
	 * {@link #setFlexLineOffset}で設定した線方向位置を読みます
	 * (2026-08-07、Flex行分割用)。
	 *
	 * <p>
	 * 行を跨いで強制分割した残余{@link FlexItemBox}は{@code fragmentRecipe}が
	 * 新規生成するため線方向位置を引き継がない——分割後に呼び出し側が
	 * これで読んだ元の値を残余へ{@link #setFlexLineOffset}し直す必要がある
	 * (cross軸の位置はitemではなくコンテナのFlow側が持つので、こちらは
	 * 触らなくてよい)。
	 * </p>
	 */
	public double getFlexLineOffset(final boolean vertical) {
		return vertical ? this.baseOffsetY : this.baseOffsetX;
	}

	/** 確定した線方向内寸(content-box)を設定します(bind直前に呼ぶ。縦書き=高さ)。 */
	public void setFlexMainSize(final double mainSize, final boolean vertical) {
		if (vertical) {
			this.height = mainSize;
		} else {
			this.width = mainSize;
		}
	}

	protected FlexItemBox(final BlockParams params, final FlowPos pos,
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
		return (state, container) -> new FlexItemBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
