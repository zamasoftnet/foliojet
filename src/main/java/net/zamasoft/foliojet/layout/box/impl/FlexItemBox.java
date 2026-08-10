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

	/**
	 * 中立wrapperがauthored childの行方向寸法指定を引き取ったか
	 * (2026-08-08、{@code FlexBuilder.startNeutralElementItem})。
	 * trueのとき、直下の子は行方向指定をauto(wrapper充填)として解決する
	 * ({@code FlowBlockBox.calculateSize})——wrapperと子の双方が%を
	 * 解決すると二重適用になるため。子のparams側で中立化しないのは、
	 * item本体の再生がchildの記録から再具現化されるため、liveの変異が
	 * 残らないから(検証済み——asahi.comの高校野球ストリップ)。
	 */
	private boolean neutralLineFill;

	public FlexItemBox(final BlockParams params, final FlowPos pos) {
		super(params, pos);
		this.markSpecifiedPageAxisFromSize();
	}

	/**
	 * flex itemは寸法をFlexBuilderが注入するため、{@code specifiedPageAxis}を
	 * 立てる唯一の場所である{@code AbstractStaticBlockBox.calculateSize}の
	 * 該当分岐を通らないことがある(2026-08-09)。その場合ページ跨ぎ分割の
	 * 残量計算({@code FragmentState.of})が「指定寸法なし」と誤認して
	 * ページ方向の指定寸法を残量へ分割せず、継続断片が指定高を<b>フルに</b>
	 * 再解決する——固定高itemを持つflex行がページを跨ぐと、継続側の行が
	 * ほぼ指定高まるごと膨らみ、後続内容を押し下げていた(flex丸ごと移動の
	 * replay経路の変形として記録されていた実バグ)。絶対長のときだけ立てる
	 * (%はflexの基準が要るため保守的に従来どおり)。
	 */
	private void markSpecifiedPageAxisFromSize() {
		this.specifiedPageAxis = this.size
				.getPageType(this.getBlockParams().flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
	}

	/** 行方向寸法の引き取りを記録します(中立wrapper専用)。 */
	public void markNeutralLineFill() {
		this.neutralLineFill = true;
	}

	/** {@link #markNeutralLineFill}参照。 */
	public boolean isNeutralLineFill() {
		return this.neutralLineFill;
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
		this.markSpecifiedPageAxisFromSize();
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
		// 線方向(主軸)は指定寸法でなく**flex解決後の使用寸法**を継続断片へ
		// 運ぶ(2026-08-08)。FragmentStateのnextSizeは行方向の指定寸法を
		// そのまま残すため、width:100%のitemがflex-shrinkで縮んでいた場合、
		// 継続側の%再解決が縮小前の幅を復元してしまい、隣のitem
		// (flex-shrink:0の固定幅サイドバー)を紙面外へ押し出す——
		// asahi.comトップの速報ニュース欄が時刻だけ残して消えた実バグ。
		// レシピはthisを保持しない規約のため、値でキャプチャする
		final boolean vertical = params.flow.isVertical();
		// Dimensionのabsolute値はbox-sizingスケール(BORDER_BOXなら解決時に
		// 枠が控除される——AbstractStaticBlockBox)。内寸this.width/heightへ
		// 枠ぶんを足し戻してから運ぶ
		final double usedMain = (vertical ? this.height : this.width)
				+ (params.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? this.frame.getBorderLineExtent(params.flow)
						: 0);
		final boolean fill = this.neutralLineFill;
		return (state, container) -> {
			final net.zamasoft.foliojet.layout.box.params.Dimension ns = state.nextSize();
			final net.zamasoft.foliojet.layout.box.params.Dimension sized = vertical
					? net.zamasoft.foliojet.layout.box.params.Dimension.create(ns.getWidth(), ns.getWidthRatio(),
							usedMain, 0, ns.getWidthType(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE)
					: net.zamasoft.foliojet.layout.box.params.Dimension.create(usedMain, 0, ns.getHeight(),
							ns.getHeightRatio(), net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE,
							ns.getHeightType());
			final FlexItemBox next = new FlexItemBox(params, pos, sized, state.nextMinSize(), state.nextFrame(),
					container);
			if (fill) {
				next.markNeutralLineFill();
			}
			return next;
		};
	}
}
