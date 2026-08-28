package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/**
 * Flexのitem 1件分の保持です(Flex F1d、2026-08-02——
 * {@code GridItemContent}と同型)。本文(TwoPass録画)・close時点の
 * 固有寸法スナップショット・item boxを所有する。itemは
 * LayoutSourceに記録されない(sourceAnchor=-1)ためrange seal・
 * リースは持たない——本文はFlex終端のbindまでLegacyRecordsのまま。
 */
final class FlexItemContent {

	final FlexItemBox itemBox;

	final TwoPassBlockBuilder body;

	/** close時点の固有寸法(F1dでは未使用。auto/content basis=F1eで使用)。 */
	final IntrinsicSizes sizes;

	final boolean anonymous;

	/** 伸縮指定(authored childのFlowPos.flexItemからのスナップショット)。 */
	final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec;

	FlexItemContent(final FlexItemBox itemBox, final TwoPassBlockBuilder body, final IntrinsicSizes sizes,
			final boolean anonymous, final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec) {
		this.itemBox = itemBox;
		this.body = body;
		this.sizes = sizes;
		this.anonymous = anonymous;
		this.spec = spec;
	}

	/**
	 * 確定した主軸内寸で本文を一度だけbindします(録画→計測→bindの
	 * TwoPassライフサイクル。{@code GridItemContent.bind}と同型)。
	 */
	void bind(final BlockBuilder host, final double mainSize, final double insetBase) {
		// **枠の実寸解決**(2026-08-04)。通常のフローの箱は
		// firstPassLayout / calculateSize が padding・margin の相対値(%・em)を
		// 実寸(AbsoluteInsets)へ直すが、**flexアイテムの箱はそのどちらも
		// 通らない**ため実寸が0のままだった。結果、行方向のflexアイテムは
		// パディングもマージンも丸ごと消えていた——Bootstrapのグリッドは
		// `.row > * { padding-inline: … }` で組まれているので、実在の
		// ページでは列の内容が枠に貼りついていた(実地コーパス第6波の
		// checkout-form でラベルの1文字目が切れて発覚)。
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = this.itemBox.getFrame();
		net.zamasoft.foliojet.layout.util.LayoutUtils.computePaddings(frame.padding, frame.frame.padding,
				insetBase);
		net.zamasoft.foliojet.layout.util.LayoutUtils.computeMarginsAutoToZero(frame.margin, frame.frame.margin,
				insetBase);
		this.itemBox.setFlexMainSize(mainSize, this.itemBox.getBlockParams().flow.isVertical());
		// aspect-ratio(2026-08-29): flex itemはcalculateSizeを通らないので、
		// 行方向寸法が入ったここでページ方向を比率で決める(内容が高ければ
		// overflow:visibleに限り伸びる——FlowBlockBox.calculateSizeと同じ規則)
		this.itemBox.applyAspectRatio(mainSize);
		final BlockBuilder target = new BlockBuilder(host, this.itemBox);
		this.body.bind(target);
		target.close();
		// takeover item(authored paramsを引き継いだ根box)は指定高を
		// 自己適用する——通常flowでは親のstartFlowBlockが適用するが、
		// bind builderの根には適用者がいない(F1d: 絶対長のみ。
		// %・min/max・border-boxの正規化はF1eでcross実測へ一本化)
		final net.zamasoft.foliojet.layout.box.params.BlockParams params = this.itemBox.getBlockParams();
		final boolean vertical = params.flow.isVertical();
		final net.zamasoft.foliojet.layout.box.params.LengthType pageType = vertical
				? params.size.getWidthType()
				: params.size.getHeightType();
		if (pageType == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			this.itemBox.setPageAxis(Math.max(0, vertical ? params.size.getWidth() : params.size.getHeight()));
		}
	}
}
