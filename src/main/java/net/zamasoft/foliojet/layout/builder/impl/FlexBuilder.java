package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
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

/**
 * Flexの構築coordinatorです(Flex F1d、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt。{@code GridBuilder}と同型で
 * {@code DocumentBuilder.builderStack}に積まれるが{@code Builder}ではない)。
 * 直接子ごとに{@link FlexItemBox}+item builder(TwoPass録画)を開き、
 * Flex終端で単一行rowへ配置する。
 *
 * <p>
 * F1dのサイズ決定はdefinite basis(絶対長さ・%)のみ:
 * 全element itemがdefinite basisならrow配置({@link #bind})、
 * 匿名itemやbasis auto/contentが混ざればコンテナ単位で
 * {@link #bindFallback}(単一列縮退)。item 1件だけの縮退は禁止
 * (行分割・free space・orderが全て変わる——答申の段階的fallback規則)。
 * §9.7(FlexLengthResolver)とauto/content basisの配線はF1e、
 * TwoPass宿主(RetainedFlex)はF1f。
 * </p>
 */
public final class FlexBuilder {

	/** 録画されたitem数(空匿名破棄を除く)。bind数と一致すること。 */
	public static final AtomicLong FLEX_ITEM_RECORDS = new AtomicLong();

	/** bindされたitem数(fallback経路も数える)。 */
	public static final AtomicLong FLEX_ITEM_BINDS = new AtomicLong();

	/** 空の匿名itemを破棄した数。 */
	public static final AtomicLong FLEX_ITEM_EMPTY_ANON_DROPS = new AtomicLong();

	/** コンテナ単位fallbackの理由(silent fallbackの禁止——答申Q1)。 */
	public enum FallbackReason {
		/** 匿名item(直接テキスト)が混ざった(basisを持てない)。 */
		ANONYMOUS_ITEM,
		/** basisがauto/content/未解決(F1eで解禁)。 */
		INDEFINITE_BASIS;

		final AtomicLong count = new AtomicLong();
	}

	/** 理由別fallback観測です。 */
	public static long fallbacksByReason(final FallbackReason reason) {
		return reason.count.get();
	}

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
	 * Flex終端です。F1dは宿主がBlockBuilderのみ(適格判定)なので
	 * 即時bind——row配置できる条件が揃わなければコンテナ単位fallback。
	 */
	public void finish() {
		assert this.openItemBuilder == null : "item未クローズでFlex終端に到達";
		final BlockBuilder target = (BlockBuilder) this.host;
		final double[] mainSizes = new double[this.items.size()];
		FallbackReason reason = null;
		for (int i = 0; i < this.items.size(); ++i) {
			final FlexItemContent item = this.items.get(i);
			if (item.anonymous) {
				reason = FallbackReason.ANONYMOUS_ITEM;
				break;
			}
			final Double size = this.definiteBasis(item.spec.basis());
			if (size == null) {
				reason = FallbackReason.INDEFINITE_BASIS;
				break;
			}
			mainSizes[i] = size;
		}
		if (reason != null) {
			reason.count.incrementAndGet();
			this.bindFallback(target);
		} else {
			this.bind(target, mainSizes);
		}
	}

	/**
	 * definite basisの内寸解決(F1d: 絶対長さ・コンテナ主軸内寸に対する%。
	 * box-sizingの正規化とwidth由来basisはF1eでFlexItemMetricsResolverへ
	 * 一本化する)。解決不能はnull。
	 */
	private Double definiteBasis(final FlexBasisValue basis) {
		if (basis.isAuto() || basis.isContent()) {
			return null;
		}
		if (basis.getSize() instanceof AbsoluteLengthValue length) {
			return Math.max(0, length.getLength());
		}
		if (basis.getSize() instanceof PercentageValue percent) {
			return Math.max(0, this.flexBox.getLineSize() * percent.getRatio());
		}
		return null;
	}

	/** bindは一度きり。 */
	private boolean bound;

	/**
	 * 単一行rowの組み立てです(幅確定→bind→cross実測→配置→親カーソル
	 * 同期——GridBuilder.bindと同じ骨格)。PageAtomicBox契約により
	 * Flex flowがactiveな間に全bindが完了する。
	 */
	private void bind(final BlockBuilder target, final double[] mainSizes) {
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

	/**
	 * 単一列縮退です(F0相当の見た目へ寄せる: itemはコンテナ内寸いっぱいの
	 * ブロックとして縦積み)。マージン相殺は行わない——F0のプレーンflowとの
	 * 既知の差(fixtureはmargin 0で固定)。
	 */
	private void bindFallback(final BlockBuilder target) {
		assert !this.bound : "Flexの二重bind";
		this.bound = true;
		final FlexParams params = this.flexBox.getFlexParams();
		double pageCursor = 0;
		for (final FlexItemContent item : this.items) {
			// 中立itemは内寸いっぱい(NULL_FRAME)。takeover itemは枠ぶんを
			// 引いたcontent-box幅(通常flowのauto幅相当。auto marginは0)
			item.bind(target, this.fallbackContentWidth(item));
			FLEX_ITEM_BINDS.incrementAndGet();
			this.flexBox.getContainer().addFlow(item.itemBox, pageCursor);
			pageCursor += item.itemBox.getPageExtent(params.flow);
		}
		this.flexBox.setPageAxis(pageCursor);
		this.syncHostCursor(target, params);
	}

	/** fallback時のitem content-box幅(コンテナ内寸−margin/border/padding線和)。 */
	private double fallbackContentWidth(final FlexItemContent item) {
		final RectFrame frame = item.itemBox.getBlockParams().frame;
		final double base = this.flexBox.getLineSize();
		final double line = insetsLine(frame.margin, base) + insetsLine(frame.padding, base)
				+ frame.border.getLeft().width + frame.border.getRight().width;
		return Math.max(0, base - line);
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
