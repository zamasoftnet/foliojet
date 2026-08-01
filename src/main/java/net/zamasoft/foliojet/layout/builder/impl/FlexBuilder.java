package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlexItemSpec;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.sizing.FlexItemMetrics;
import net.zamasoft.foliojet.layout.sizing.FlexItemMetricsResolver;
import net.zamasoft.foliojet.layout.sizing.FlexLengthResolver;

/**
 * Flexの構築coordinatorです(Flex F1d、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt。{@code GridBuilder}と同型で
 * {@code DocumentBuilder.builderStack}に積まれるが{@code Builder}ではない)。
 * 直接子ごとに{@link FlexItemBox}+item builder(TwoPass録画)を開き、
 * Flex終端で単一行rowへ配置する。
 *
 * <p>
 * F1e: 各itemを{@code FlexItemMetricsResolver}(basis auto/content=
 * TwoPass録画の内在サイズ、min-size:auto=§4.5)で数値化し、
 * {@code FlexLengthResolver}(§9.7)でgrow/shrinkを解決してrow配置する。
 * 匿名テキストitemもcontent由来で常に配置可能なため、F1dのコンテナ単位
 * fallbackは撤去(将来の子で判る不適格が再び現れるF4cで、コンテナ単位
 * bindFallback+理由別カウンタの形をこのクラスへ戻す——答申の段階的
 * fallback規則。item 1件だけの縮退は禁止)。TwoPass宿主(RetainedFlex)は
 * F1f。
 * </p>
 */
public final class FlexBuilder implements net.zamasoft.foliojet.layout.builder.RetainedFlex {

	/** 録画されたitem数(空匿名破棄を除く)。bind数と一致すること。 */
	public static final AtomicLong FLEX_ITEM_RECORDS = new AtomicLong();

	/** bindされたitem数(fallback経路も数える)。 */
	public static final AtomicLong FLEX_ITEM_BINDS = new AtomicLong();

	/** 空の匿名itemを破棄した数。 */
	public static final AtomicLong FLEX_ITEM_EMPTY_ANON_DROPS = new AtomicLong();

	private final Builder host;

	/** item builderの親LayoutStack({@code host}と同一インスタンス)。 */
	private final LayoutStack hostStack;

	private final FlexBox flexBox;

	private final List<FlexItemContent> items = new ArrayList<>();

	/** 開いているitemのbuilder(elementまたは匿名)。閉じているときnull。 */
	private TwoPassBlockBuilder openItemBuilder;

	private FlexItemBox openItemBox;

	private boolean openItemAnonymous;

	private FlexItemSpec openItemSpec = FlexItemSpec.DEFAULT;

	/** takeover元のauthored box(endBoxの対応付け用。中立/匿名itemではnull)。 */
	private FlowBlockBox openItemSource;

	FlexBuilder(final Builder host, final FlexBox flexBox) {
		this.host = host;
		this.hostStack = (LayoutStack) host;
		this.flexBox = flexBox;
	}

	@Override
	public FlexBox getFlexBox() {
		return this.flexBox;
	}

	public boolean hasOpenElementItem() {
		return this.openItemBuilder != null && !this.openItemAnonymous;
	}

	public boolean hasOpenItem() {
		return this.openItemBuilder != null;
	}

	/** {@code box}が開いているtakeover element itemの元boxかを返します。 */
	public boolean isElementItemSource(final Object box) {
		return this.openItemSource != null && this.openItemSource == box;
	}

	/** 中立itemのparams(GridBuilder.itemParams()と同型の中立化)。 */
	private BlockParams itemParams() {
		final BlockParams params = BlockParamsTemplate.freeze(this.flexBox.getFlexParams()).materialize();
		params.frame = RectFrame.NULL_FRAME;
		params.element = null;
		params.footnoteId = -1;
		params.opacity = 1f;
		params.zIndexType = Params.Z_INDEX_AUTO;
		params.zIndexValue = 0;
		params.transform = new AffineTransform();
		params.columns = Columns.NONE_COLUMNS;
		params.size = Dimension.AUTO_DIMENSION;
		params.minSize = Dimension.ZERO_DIMENSION;
		params.maxSize = Dimension.AUTO_DIMENSION;
		return params;
	}

	/**
	 * plainなブロック直下子のelement itemを開きます(takeover——authored
	 * childのparams/posをitem box自身へ引き継ぎ、元の外箱は構築しない。
	 * 答申の最重要プロトタイプ条件)。返るbuilderを積むのは呼び出し側。
	 */
	public TwoPassBlockBuilder startElementItem(final FlowBlockBox source, final FlexItemSpec spec) {
		final TwoPassBlockBuilder builder = this.startItem(
				new FlexItemBox(source.getBlockParams(), (FlowPos) source.getPos()), false, spec);
		this.openItemSource = source;
		return builder;
	}

	/** 非plain子(表・入れ子コンテナ等)用の中立wrapper element itemを開きます。 */
	public TwoPassBlockBuilder startNeutralElementItem(final FlexItemSpec spec) {
		return this.startItem(new FlexItemBox(this.itemParams(), new FlowPos()), false, spec);
	}

	/** 直接テキスト用の匿名itemを開きます(開いていれば再利用)。 */
	public TwoPassBlockBuilder requireAnonymousItem() {
		if (this.openItemBuilder != null && this.openItemAnonymous) {
			return null; // 既に開いている(積み直し不要)
		}
		return this.startItem(new FlexItemBox(this.itemParams(), new FlowPos()), true, FlexItemSpec.DEFAULT);
	}

	private TwoPassBlockBuilder startItem(final FlexItemBox itemBox, final boolean anonymous,
			final FlexItemSpec spec) {
		assert this.openItemBuilder == null : "前のitemが閉じられていない";
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this.hostStack, itemBox);
		builder.tagLegacyBindOrigin(
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin.GRID_ITEM);
		this.openItemBuilder = builder;
		this.openItemBox = itemBox;
		this.openItemAnonymous = anonymous;
		this.openItemSpec = spec;
		this.openItemSource = null;
		return builder;
	}

	/** 開いているitemを確定します(録画完了点)。空の匿名itemは破棄。 */
	public void itemClosed() {
		final TwoPassBlockBuilder builder = this.openItemBuilder;
		final FlexItemBox itemBox = this.openItemBox;
		final boolean anonymous = this.openItemAnonymous;
		final FlexItemSpec spec = this.openItemSpec;
		this.openItemBuilder = null;
		this.openItemBox = null;
		this.openItemAnonymous = false;
		this.openItemSpec = FlexItemSpec.DEFAULT;
		this.openItemSource = null;
		if (anonymous && builder.hasEmptyRecordedBody() && !itemBox.paintsAnything()) {
			FLEX_ITEM_EMPTY_ANON_DROPS.incrementAndGet();
			return;
		}
		FLEX_ITEM_RECORDS.incrementAndGet();
		this.items.add(new FlexItemContent(itemBox, builder, builder.getIntrinsicSizes(), anonymous, spec));
	}

	/**
	 * Flex終端です(F1f): 実行計画としてホストへ渡す。BlockBuilderは
	 * 即時{@link #bind}、TwoPassは録画のFlexEventに保持して幅確定後に
	 * bindする。
	 */
	public void finish() {
		assert this.openItemBuilder == null : "item未クローズでFlex終端に到達";
		this.host.addFlex(this);
	}

	/**
	 * Flexの組み立てです(F1e: 全itemをFlexItemMetricsResolverで数値化し、
	 * §9.7=FlexLengthResolverで主軸内寸を解決してrow配置)。ホストの
	 * active flowが当のFlexBoxである間に呼ぶこと(liveはDocumentBuilderの
	 * FLOW終端、records bindはStartFlow(FlexBox)とEndFlowの間)。
	 */
	@Override
	public void bind(final net.zamasoft.foliojet.layout.builder.Builder hostBuilder) {
		final BlockBuilder target = (BlockBuilder) hostBuilder;
		final double containerInner = this.flexBox.getLineSize();
		final java.util.List<FlexItemMetrics> metrics = new ArrayList<>(this.items.size());
		for (int i = 0; i < this.items.size(); ++i) {
			final FlexItemContent item = this.items.get(i);
			final BlockParams p = item.itemBox.getBlockParams();
			final RectFrame frame = p.frame;
			final double mainFrame = insetsLine(frame.padding, containerInner)
					+ frame.border.getLeft().width + frame.border.getRight().width;
			metrics.add(FlexItemMetricsResolver.resolve(new FlexItemMetricsResolver.Input(i,
					item.spec.grow(), item.spec.shrink(), item.spec.basis(),
					lineValue(p.size, containerInner),
					item.spec.minWidthAuto() ? Double.NaN
							: Math.max(0, zeroIfNaN(lineValue(p.minSize, containerInner))),
					maxLineValue(p.maxSize, containerInner), mainFrame,
					insetsLine(frame.margin, containerInner),
					p.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX,
					p.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE,
					item.sizes.minContent(), item.sizes.maxContent(), containerInner)));
		}
		this.bindRow(target, FlexLengthResolver.resolve(metrics, containerInner, 0));
	}

	/** Dimensionの線方向値(auto=NaN。%はコンテナ主軸内寸基準で解決)。 */
	private static double lineValue(final Dimension size, final double base) {
		// F1は横書きのみ(eligible)——線方向=width
		if (size.getWidthType() == net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			return Double.NaN;
		}
		return size.getWidth() + size.getWidthRatio() * base;
	}

	private static double zeroIfNaN(final double value) {
		return Double.isNaN(value) ? 0 : value;
	}

	/** max-widthの線方向値(なし=+∞)。 */
	private static double maxLineValue(final Dimension size, final double base) {
		final double value = lineValue(size, base);
		return Double.isNaN(value) ? Double.POSITIVE_INFINITY : Math.max(0, value);
	}

	/** bindは一度きり。 */
	private boolean bound;

	/**
	 * Flex全体のcontent-box固有寸法contributionです(F1f——§9.9の
	 * 単一行row近似)。行方向: min=Σ(item min-content+枠の絶対部)、
	 * max=Σ(item max-content+同)。%枠は基準未確定のため絶対部のみ
	 * (控えめな近似——確定幅はbind時に正確に解決される)。ページ方向
	 * min=item minPageの最大(単一行)。frameは含めない(計測器の
	 * 通常経路が一度だけ加算する)。
	 */
	@Override
	public net.zamasoft.foliojet.layout.sizing.IntrinsicSizes getIntrinsicSizes() {
		double min = 0, max = 0, minPage = 0;
		boolean columnInflated = false;
		for (final FlexItemContent item : this.items) {
			final RectFrame frame = item.itemBox.getBlockParams().frame;
			final double extra = insetsLine(frame.margin, 0) + insetsLine(frame.padding, 0)
					+ frame.border.getLeft().width + frame.border.getRight().width;
			min += item.sizes.minContent() + extra;
			max += item.sizes.maxContent() + extra;
			minPage = Math.max(minPage, item.sizes.minPage());
			columnInflated |= item.sizes.columnInflated();
		}
		return new net.zamasoft.foliojet.layout.sizing.IntrinsicSizes(min, max, minPage, columnInflated);
	}

	@Override
	public void abandonForParentRange() {
		// 親rangeの範囲再生がFlex全体を再構築する(GridBuilderと同型)。
		// 合成itemはLayoutSource非記録のためリースを持たない
		this.items.clear();
	}

	/**
	 * 親range化の検証相です(F1f——GridBuilder.collectAbsorbableItemsと
	 * 同型、副作用なし)。全itemの本文を通常のネストビルダーとして
	 * 検証・列挙する。bind済みは吸収不可(fail closed)。
	 */
	boolean collectAbsorbableItems(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId,
			final long toId, final java.util.List<TwoPassBlockBuilder> out,
			final java.util.List<RetainedTableBuilder> outTables, final java.util.Set<Long> ownedAbsoluteAnchors,
			final java.util.Set<TwoPassBlockBuilder> seen) {
		if (this.bound) {
			return false;
		}
		for (final FlexItemContent item : this.items) {
			if (!item.body.collectAbsorbableSelf(log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 単一行rowの組み立てです(幅確定→bind→cross実測→配置→親カーソル
	 * 同期——GridBuilder.bindと同じ骨格)。PageAtomicBox契約により
	 * Flex flowがactiveな間に全bindが完了する。
	 */
	private void bindRow(final BlockBuilder target, final double[] mainSizes) {
		assert !this.bound : "Flexの二重bind";
		this.bound = true;
		final FlexParams params = this.flexBox.getFlexParams();
		final int count = this.items.size();
		double lineCursor = 0;
		double maxExtent = 0;
		for (int i = 0; i < count; ++i) {
			final FlexItemContent item = this.items.get(i);
			item.bind(target, mainSizes[i]);
			FLEX_ITEM_BINDS.incrementAndGet();
			// 自然位置は自margin込みのため、offsetは先行itemのouter合計
			item.itemBox.setFlexLineOffset(lineCursor);
			lineCursor += item.itemBox.getLineExtent(params.flow);
			maxExtent = Math.max(maxExtent, item.itemBox.getPageExtent(params.flow));
			this.flexBox.getContainer().addFlow(item.itemBox, 0);
		}
		this.flexBox.setPageAxis(this.items.isEmpty() ? 0 : maxExtent);
		this.syncHostCursor(target, params);
	}


	/** 線方向のInsets合計(絶対部+比率×基準。autoは0)。 */
	private static double insetsLine(final net.zamasoft.foliojet.layout.box.params.Insets insets,
			final double base) {
		double sum = 0;
		if (insets.getLeftType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			sum += insets.getLeft() + insets.getLeftRatio() * base;
		}
		if (insets.getRightType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			sum += insets.getRight() + insets.getRightRatio() * base;
		}
		return sum;
	}

	/** ホストflowカーソルの同期(GridBuilder.bind末尾と同型)。 */
	private void syncHostCursor(final BlockBuilder target, final FlexParams params) {
		final LayoutContext.Flow active = target.getFlow();
		assert active.box == this.flexBox : "Flex bindでactive flowがFlexではない: " + active.box;
		target.setPageAxis(active.pageAxis + this.flexBox.getInnerPageExtent(params.flow));
	}
}
