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
	 * @param rowLineNames    跨ぐ行線の名前(span+1要素)
	 * @param link            row subgridの一時接続。使わないときはnull
	 */
	public static final class SubgridTracks {
		private final double[] columnWidths;
		private final double columnGap;
		private final java.util.List<java.util.List<String>> columnLineNames;
		private final double rowGap;
		private final java.util.List<java.util.List<String>> rowLineNames;
		private RowSubgridLink link;

		/** 列軸だけを渡していた従来の呼び出しとの互換構築子。 */
		public SubgridTracks(final double[] columnWidths, final double columnGap,
				final java.util.List<java.util.List<String>> columnLineNames, final double rowGap) {
			this(columnWidths, columnGap, columnLineNames, rowGap, java.util.List.of(), null);
		}

		public SubgridTracks(final double[] columnWidths, final double columnGap,
				final java.util.List<java.util.List<String>> columnLineNames, final double rowGap,
				final java.util.List<java.util.List<String>> rowLineNames,
				final RowSubgridLink link) {
			this.columnWidths = columnWidths;
			this.columnGap = columnGap;
			this.columnLineNames = columnLineNames;
			this.rowGap = rowGap;
			this.rowLineNames = rowLineNames;
			this.link = link;
		}

		public double[] columnWidths() {
			return this.columnWidths;
		}

		public double columnGap() {
			return this.columnGap;
		}

		public java.util.List<java.util.List<String>> columnLineNames() {
			return this.columnLineNames;
		}

		public double rowGap() {
			return this.rowGap;
		}

		public java.util.List<java.util.List<String>> rowLineNames() {
			return this.rowLineNames;
		}

		/** 未消費の一時接続です。消費後はnull。 */
		public synchronized RowSubgridLink link() {
			return this.link;
		}

		/**
		 * 一時接続を一度だけ取り出し、永続boxからsink closureを切ります。
		 * 2回目以降はnullを返します。
		 */
		public synchronized RowSubgridLink consumeRowSubgridLink() {
			final RowSubgridLink consumed = this.link;
			this.link = null;
			return consumed;
		}
	}

	private SubgridTracks subgridTracks;

	public GridItemBox(final BlockParams params, final FlowPos pos, final double trackWidth) {
		super(params, pos);
		if (params.flow.isVertical()) {
			this.height = trackWidth;
		} else {
			this.width = trackWidth;
		}
		this.markSpecifiedPageAxisFromSize();
	}

	/**
	 * grid itemは寸法を{@code GridBuilder}が注入するため、
	 * {@code specifiedPageAxis}を立てる{@code calculateSize}の分岐を
	 * 通らない(G7、2026-08-29——{@code FlexItemBox}の同名メソッドと同じ理由)。
	 * 立てないと、ページ跨ぎの残量計算({@code FragmentState.of})が
	 * 「指定寸法なし」と誤認して継続断片が指定高をフルに再解決する
	 * (row-split-carryの2ページ目が56ptでなく91ptになった)。
	 * <b>継続断片の構築子でも立てる</b>——3ページ以上の再分割で再発するため。
	 */
	private void markSpecifiedPageAxisFromSize() {
		this.specifiedPageAxis = this.size
				.getPageType(this.getBlockParams().flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
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
	 * row subgridが確定したトラック寸法を、作者指定のheight/min/max-height・
	 * aspect-ratioに拘束されず正確に設定します(2026-09-03)。
	 */
	public final void setExactUsedPageSize(final double pageSize) {
		this.restoreContentExtent(Math.max(0, pageSize));
		this.minPageAxis = 0;
		this.maxPageAxis = Double.MAX_VALUE;
		this.specifiedPageAxis = false;
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
		if (this.getBlockParams().flow.isVertical()) {
			this.baseOffsetY = lineOffset;
			this.offsetY = lineOffset;
		} else {
			this.baseOffsetX = lineOffset;
			this.offsetX = lineOffset;
		}
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
		return this.getBlockParams().flow.isVertical() ? this.baseOffsetY : this.baseOffsetX;
	}

	/**
	 * 確定したトラック幅を設定します(Grid G3a: bind直前に呼ぶ。
	 * 固定列では構築時の値と同じ。auto/fr列=G3b/cで解決値が入る)。
	 */
	public void setTrackWidth(final double trackWidth) {
		if (this.getBlockParams().flow.isVertical()) {
			this.height = trackWidth;
		} else {
			this.width = trackWidth;
		}
	}

	/**
	 * takeover item(authoredな箱をitem箱そのものにした)かどうか
	 * (G7、2026-08-29)。subgridの「itemの直下か」判定に要る——takeoverでは
	 * この箱がauthoredな要素そのものなので、その中のgridは<b>item直下では
	 * ない</b>(包み箱時代はflow段数だけで区別できていた)。
	 */
	private boolean takeover;

	public void setTakeover(final boolean takeover) {
		this.takeover = takeover;
	}

	public boolean isTakeover() {
		return this.takeover;
	}

	protected GridItemBox(final BlockParams params, final FlowPos pos,
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
