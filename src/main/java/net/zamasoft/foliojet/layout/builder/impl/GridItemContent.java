package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/**
 * Gridのitem 1件分の保持です(Grid G3a、2026-07-31——
 * consult-codex-2026-07-31-grid-g3.txt Q1)。本文(TwoPass録画)・
 * close時点の固有寸法スナップショット・最終item boxを所有する。
 * 表セルの{@code CellContent}と同じ考え方だが、合成itemは
 * LayoutSourceに記録されない(sourceAnchor=-1)ためrange seal・
 * リースは持たない——本文はGrid終端までLegacyRecordsのまま。
 *
 * <p>
 * 固有寸法は{@code getIntrinsicSizes()}(IntrinsicMeasurer模倣計測)を
 * 正本とする。{@code intrinsicSizesMeasured()}のscratch実測は合成item
 * (anchorなし・%基準の再現不能)には適用できない(答申Q1)。
 * </p>
 */
final class GridItemContent {

	final GridItemBox itemBox;

	final TwoPassBlockBuilder body;

	/** close時点の固有寸法(G3aではshadow観測のみ。auto/fr列=G3b/cで使用)。 */
	final IntrinsicSizes sizes;

	final boolean anonymous;

	/** 明示配置指定(G4a——authored childのFlowPosからのスナップショット)。 */
	final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec;

	/**
	 * 行方向min-content寄与の上限です(2026-08-19。負=無制限)。
	 * css-grid §6.6のautomatic minimum size——itemに行軸のmin寸法が
	 * <b>明示宣言</b>されている(例: Tailwindの`min-w-0`)か、itemが
	 * スクロールコンテナ(overflow≠visible)のとき、自動最小サイズは
	 * 明示値(0など)になり、内容のmin-contentでトラックを押し広げない
	 * (Chrome実測: min-width:0のitemはトラックがコンテナ幅に収まり、
	 * 無指定なら内容min-contentまで膨らむ——react.devの`main.min-w-0`は
	 * 前者に依存しており、無視すると本文が紙面外x=628〜へ押し出されて
	 * 全ページ白紙になっていた)。
	 */
	final double minContributionCap;

	/**
	 * takeover item(authoredな箱をitem箱そのものにした)かどうか
	 * (G7、2026-08-29)。takeoverでは<b>authored rootの枠と宣言寸法が
	 * 録画本文の外にある</b>ため、固有寸法の寄与へ足し直す必要がある。
	 */
	final boolean takeover;

	GridItemContent(final GridItemBox itemBox, final TwoPassBlockBuilder body, final IntrinsicSizes sizes,
			final boolean anonymous, final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec,
			final double minContributionCap, final boolean takeover) {
		this.itemBox = itemBox;
		this.body = body;
		this.sizes = sizes;
		this.anonymous = anonymous;
		this.spec = spec;
		this.minContributionCap = minContributionCap;
		this.takeover = takeover;
	}

	/**
	 * 確定トラック幅で本文を一度だけbindします(録画→計測→bindの
	 * TwoPassライフサイクル。floatの
	 * {@code contentBuilder.bind(floatBuilder); floatBuilder.close()}と同型)。
	 */
	void bind(final BlockBuilder host, final double trackWidth) {
		// 枠の実寸解決はGridBuilderの幅決定と同じ場所で済ませてある
		// (基準はグリッド領域の幅。G7、2026-08-29)
		this.itemBox.setTrackWidth(trackWidth);
		if (this.takeover) {
			// aspect-ratio(G7、2026-08-29——FlexItemContent.bindと同じ理由)。
			// takeoverしたitem箱はcalculateSizeを通らないので、トラック幅が
			// 入ったここでページ方向を比率で決める。これが無いと
			// 3080-MODERN-CSS/aspect-ratioのgrid itemが内容高へ潰れる
			this.itemBox.applyAspectRatio(trackWidth);
		}
		final BlockBuilder target = new BlockBuilder(host, this.itemBox);
		this.body.bind(target);
		target.close();
		// takeover item(authored paramsを引き継いだ根box)は指定高を
		// 自己適用する(G7、2026-08-29——FlexItemContent.bindと同型)。
		// 通常フローでは親のstartFlowBlockが適用するが、bind builderの
		// 根には適用者がいない。これが無いと、takeoverしたitemの
		// height指定が丸ごと落ちる(実測: height:20mmが内容高2.7mmになった)
		final net.zamasoft.foliojet.layout.box.params.BlockParams params = this.itemBox.getBlockParams();
		final boolean vertical = params.flow.isVertical();
		final net.zamasoft.foliojet.layout.box.params.LengthType pageType = vertical
				? params.size.getWidthType()
				: params.size.getHeightType();
		if (pageType == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			this.itemBox.applySpecifiedPageAxis(
					Math.max(0, vertical ? params.size.getWidth() : params.size.getHeight()));
		}
	}
}
