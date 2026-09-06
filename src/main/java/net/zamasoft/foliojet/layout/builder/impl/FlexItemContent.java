package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/**
 * Flexのitem 1件分の保持です(Flex F1d、2026-08-02——
 * {@code GridItemContent}と同型)。本文(TwoPass録画)・close時点の
	 * 固有寸法スナップショット・item boxを所有する。要素・匿名項目の範囲リースは
	 * closeからFlex終端bindまで保持する。不適格は変換を失敗させる。
 */
final class FlexItemContent {

	final FlexItemBox itemBox;

	final RangeHandle body;

	/** 空本文・独立再生も確定本文だけを持ち、実測builderは手放す。 */
	private final TwoPassBlockBuilder.DeferredBind content;

	private final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator;
	private final ContinuationStats.TwoPassCensusTag censusTag;
	private final java.util.Set<Long> ownedAbsoluteAnchors;

	/** close時点の固有寸法(F1dでは未使用。auto/content basis=F1eで使用)。 */
	final IntrinsicSizes sizes;

	final boolean anonymous;

	/** 伸縮指定(authored childのFlowPos.flexItemからのスナップショット)。 */
	final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec;

	FlexItemContent(final FlexItemBox itemBox, final TwoPassBlockBuilder body, final IntrinsicSizes sizes,
			final boolean anonymous, final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec) {
		this.itemBox = itemBox;
		this.content = body.detachDeferredBind();
		this.body = this.content.handle();
		this.pageGenerator = this.body == null ? null : body.getPageContext().getPageGenerator();
		this.censusTag = body.itemCensusTag();
		this.ownedAbsoluteAnchors = body.rangeOwnedAbsoluteAnchors();
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
		if (this.body == null) {
			this.content.bind(target);
		} else {
			if (ReplayIntent.current() == ReplayIntent.MEASURE) {
				this.body.measure(target, this.pageGenerator);
			} else {
				this.body.bind(target, this.pageGenerator);
			}
			if (this.censusTag != null) {
				this.censusTag.record(ReplayIntent.current() == ReplayIntent.MEASURE
						? ContinuationStats.TwoPassCensusEvent.MEASURE_RANGE : ContinuationStats.TwoPassCensusEvent.BIND);
			}
		}
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

	/** 検証だけを行い、親リース取得後に終端する一覧へ列挙します。 */
	boolean collectAbsorbable(final net.zamasoft.foliojet.layout.fragment.LayoutSource log,
			final long fromId, final long toId, final java.util.List<TwoPassBlockBuilder> out,
			final java.util.List<RetainedTableBuilder> outTables, final java.util.List<RangeHandle> outRanges,
			final java.util.Set<Long> anchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		if (this.body == null) {
			return this.content.collectAbsorbableInto(log, fromId, toId, anchors);
		}
		if (this.body.state() != RangeHandle.State.OPEN || this.body.source() != log
				|| this.body.fromId() < fromId || this.body.toId() > toId) {
			return false;
		}
		if (!outRanges.contains(this.body)) {
			for (final long anchor : this.ownedAbsoluteAnchors) {
				if (!anchors.add(anchor)) {
					return false;
				}
			}
			outRanges.add(this.body);
		}
		return true;
	}
}
