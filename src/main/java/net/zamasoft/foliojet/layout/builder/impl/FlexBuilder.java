package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlexContentAlignment;
import net.zamasoft.foliojet.layout.box.params.FlexItemSpec;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Insets;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.RetainedFlex;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.sizing.FlexItemMetrics;
import net.zamasoft.foliojet.layout.sizing.FlexItemMetricsResolver;
import net.zamasoft.foliojet.layout.sizing.FlexLengthResolver;
import net.zamasoft.foliojet.layout.sizing.FlexLineBreaker;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.sizing.Sizing;

/**
 * Flexの構築coordinatorです(Flex F1d〜F6、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt。{@code GridBuilder}と同型で
 * {@code DocumentBuilder.builderStack}に積まれるが{@code Builder}ではない)。
 * 直接子ごとに{@link FlexItemBox}+item builder(TwoPass録画)を開き、
 * Flex終端で§9.7を解決して配置する。
 *
 * <p>
 * bindの骨格はrow/column共通で、主軸の違いは{@link MainAxis}
 * (Dimension/Insets/枠の論理アクセサ束)が一点で吸収する(2026-08-02の
 * 一本化——旧bindLines/bindColumnの同型二重実装を、共有の
 * 計測({@link #buildMetrics})・行分割({@link #breakMainLines})・
 * §9.7適用({@link #resolveMainSizes})・cross分配
 * ({@link #distributeCross})・主軸分配({@link MainDistribution})へ
 * 統合)。残る差は配置の物理写像(rowは主軸=線offset・cross=addFlow、
 * columnは逆)と、rowだけが持つ実測cross(bind後のstretch・§9.4・
 * auto margin)で、それぞれ{@link #placeRow}/{@link #placeColumn}が担う。
 * </p>
 *
 * <p>
 * columnの内容依存basis(auto高item)は恒久サブセット外
 * (F4c裁定=consult-codex-2026-08-02-flexbox-f4c.txt)——classifierが
 * コンテナ単位で単一列縮退させる(item単位の縮退は禁止)。
 * </p>
 */
public final class FlexBuilder implements RetainedFlex, net.zamasoft.foliojet.layout.builder.ItemCoordinator {

	/** 録画されたitem数(空匿名破棄を除く)。bind数と一致すること。 */
	public static final AtomicLong FLEX_ITEM_RECORDS = new AtomicLong();

	/** bindされたitem数(fallback経路も数える)。 */
	public static final AtomicLong FLEX_ITEM_BINDS = new AtomicLong();

	/** 空の匿名itemを破棄した数。 */
	public static final AtomicLong FLEX_ITEM_EMPTY_ANON_DROPS = new AtomicLong();

	/** columnのbasis:contentによるコンテナ単位fallback数(F4c——恒久サブセット外)。 */
	public static final AtomicLong FLEX_COLUMN_FALLBACKS_CONTENT_BASIS = new AtomicLong();

	/** columnのbasis:auto+主軸auto(内容高要求)によるfallback数(F4c)。 */
	public static final AtomicLong FLEX_COLUMN_FALLBACKS_AUTO_MAIN = new AtomicLong();

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

	/** bindは一度きり。 */
	private boolean bound;

	FlexBuilder(final Builder host, final FlexBox flexBox) {
		this.host = host;
		this.hostStack = (LayoutStack) host;
		this.flexBox = flexBox;
	}

	@Override
	public net.zamasoft.foliojet.layout.box.IBox getItemHostBox() {
		return this.flexBox;
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

	/**
	 * 非plain子(表・入れ子コンテナ等)用の中立wrapper element itemを開きます。
	 *
	 * <p>
	 * {@code authored}(非null時)はauthored childのparamsで、<b>行方向の
	 * 寸法指定(size/min/max)をwrapperへ引き取る</b>(2026-08-08)。
	 * 旧実装はwrapperが常にsize:autoで、入れ子flexコンテナの
	 * {@code width:50%}等が丸ごと落ちてshrink-to-fitへ潰れていた
	 * (asahi.comトップの高校野球ストリップが1文字幅の縦積みになった
	 * 実バグ)。子側の二重解決は{@link FlexItemBox#markNeutralLineFill}の
	 * フラグ経由で充填(auto)扱いにして防ぐ——子paramsの直接変異は
	 * item本体の再生(再具現化)で失われるため使えない。page方向は
	 * wrapperをautoのままにして子の指定を生かす(wrapperだけが伸びると
	 * 背景・枠が子から乖離する)。
	 * </p>
	 */
	public TwoPassBlockBuilder startNeutralElementItem(final FlexItemSpec spec, final BlockParams authored) {
		final BlockParams wrapper = this.itemParams();
		final boolean transfer = authored != null;
		if (transfer) {
			final boolean vertical = this.flow().isVertical();
			wrapper.size = lineOnly(authored.size, vertical);
			wrapper.minSize = lineOnly(authored.minSize, vertical);
			wrapper.maxSize = lineOnly(authored.maxSize, vertical);
			wrapper.boxSizing = authored.boxSizing;
			// autoマージンはitem(wrapper)レベルの自由空間を吸収する(§8.1)
			// ため、autoの辺だけwrapperへ引き取る(2026-08-09——Bootstrapの
			// navbar .ml-auto、入れ子コンテナが右端へ寄らない実バグ)。
			// 内側に残るautoはwrapper内の自由空間が0のため無害(二重シフト
			// しない)。非autoのマージンは内側で視覚上等価のため移さない
			final Insets margin = authored.frame.margin;
			if (margin.getTopType() == LengthType.AUTO || margin.getRightType() == LengthType.AUTO
					|| margin.getBottomType() == LengthType.AUTO || margin.getLeftType() == LengthType.AUTO) {
				wrapper.frame = RectFrame.create(
						Insets.create(0, 0, 0, 0,
								margin.getTopType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE,
								margin.getRightType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE,
								margin.getBottomType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE,
								margin.getLeftType() == LengthType.AUTO ? LengthType.AUTO : LengthType.ABSOLUTE),
						null, null, null);
			}
		}
		final FlexItemBox itemBox = new FlexItemBox(wrapper, new FlowPos());
		if (transfer) {
			itemBox.markNeutralLineFill();
		}
		return this.startItem(itemBox, false, spec);
	}

	/** 行方向成分だけ残したDimension(page方向はauto。縦書きの行方向=高さ)。 */
	private static Dimension lineOnly(final Dimension d, final boolean vertical) {
		return vertical
				? Dimension.create(0, 0, d.getHeight(), d.getHeightRatio(), LengthType.AUTO, d.getHeightType())
				: Dimension.create(d.getWidth(), d.getWidthRatio(), 0, 0, d.getWidthType(), LengthType.AUTO);
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
		builder.tagLegacyBindOrigin(ContinuationStats.LegacyBindOrigin.GRID_ITEM);
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

	// ------------------------------------------------------------------
	// 論理軸

	/** コンテナの書字方向(論理軸写像の基準——F6)。 */
	private WritingMode flow() {
		return this.flexBox.getFlexParams().flow;
	}

	/**
	 * 主軸の論理アクセサ束です(2026-08-02の一本化)。rowの主軸=線軸、
	 * columnの主軸=page軸——Dimension/Insets/border/最小auto判定の
	 * 軸選択をここへ一点集約し、bind骨格を共通化する。
	 * {@code marginBase}はInsets%の解決基準で、CSSの規定により
	 * 縦横どちらのmargin/paddingもインライン寸法(コンテナ行内寸)基準。
	 */
	private final class MainAxis {
		final boolean mainIsLine;

		/** 主軸寸法(width/height系)の%基準。 */
		final double mainBase;

		/** margin/padding%の基準(常にコンテナ行内寸)。 */
		final double marginBase;

		MainAxis(final boolean mainIsLine, final double mainBase, final double marginBase) {
			this.mainIsLine = mainIsLine;
			this.mainBase = mainBase;
			this.marginBase = marginBase;
		}

		double mainGap() {
			final FlexParams params = FlexBuilder.this.flexBox.getFlexParams();
			return this.mainIsLine ? params.columnGap : params.rowGap;
		}

		double crossGap() {
			final FlexParams params = FlexBuilder.this.flexBox.getFlexParams();
			return this.mainIsLine ? params.rowGap : params.columnGap;
		}

		/** 主軸の寸法値(auto=NaN、%は{@code mainBase}で解決)。 */
		double mainValue(final Dimension size) {
			return this.mainIsLine ? lineValue(size, this.mainBase) : pageValue(size, this.mainBase);
		}

		/** 主軸のmax寸法(なし=+∞)。 */
		double mainMaxValue(final Dimension size) {
			final double value = this.mainValue(size);
			return Double.isNaN(value) ? Double.POSITIVE_INFINITY : Math.max(0, value);
		}

		/** 主軸のborder+padding合計。 */
		double mainFrame(final RectFrame frame) {
			return this.mainIsLine ? insetsLine(frame.padding, this.marginBase) + borderLine(frame)
					: insetsPage(frame.padding, this.marginBase) + borderPage(frame);
		}

		/** 主軸のmargin合計(autoは0)。 */
		double mainMargin(final RectFrame frame) {
			return this.mainIsLine ? insetsLine(frame.margin, this.marginBase)
					: insetsPage(frame.margin, this.marginBase);
		}

		/** 主軸のmin-size:auto判定(§4.5——FlexItemSpecが宣言有無を運ぶ)。 */
		boolean minMainAuto(final FlexItemSpec spec) {
			final boolean vertical = FlexBuilder.this.flow().isVertical();
			return this.mainIsLine == !vertical ? spec.minWidthAuto() : spec.minHeightAuto();
		}
	}

	// ------------------------------------------------------------------
	// bind骨格(row/column共通)

	/**
	 * Flexの組み立てです(全itemをFlexItemMetricsResolverで数値化し、
	 * §9.7=FlexLengthResolverで主軸寸法を解決して配置)。ホストの
	 * active flowが当のFlexBoxである間に呼ぶこと(liveはDocumentBuilderの
	 * FLOW終端、records bindはStartFlow(FlexBox)とEndFlowの間)。
	 */
	@Override
	public void bind(final Builder hostBuilder) {
		assert !this.bound : "Flexの二重bind";
		this.bound = true;
		final BlockBuilder target = (BlockBuilder) hostBuilder;
		final FlexParams params = this.flexBox.getFlexParams();
		final boolean mainIsLine = params.flexDirection.isRow();
		final double innerLine = this.flexBox.getLineSize();
		if (!mainIsLine && !this.columnMainResolvable(target)) {
			return; // コンテナ単位fallback済み(F4c)
		}
		// columnの主軸内寸=指定高(eligibleで絶対長を保証)は
		// getInnerPageExtentが返す(G5eの手筋)
		final MainAxis axis = new MainAxis(mainIsLine,
				mainIsLine ? innerLine : this.flexBox.getInnerPageExtent(params.flow), innerLine);
		final int[] seq = this.visualOrder();
		final List<FlexItemMetrics> metrics = this.buildMetrics(seq, axis);
		final List<FlexLineBreaker.Line> lines = this.breakMainLines(metrics, axis);
		final double[] mainSizeByOriginal = this.resolveMainSizes(seq, metrics, lines, axis);
		if (mainIsLine) {
			this.placeRow(target, axis, seq, metrics, lines, mainSizeByOriginal);
		} else {
			this.placeColumn(target, axis, seq, metrics, lines, mainSizeByOriginal);
		}
		this.syncHostCursor(target, params);
	}

	/**
	 * columnの主軸寸法が全item数値化できるかの事前走査です(F4c——
	 * 内容依存basisは恒久サブセット外。一件でも不適格なら本文を一度も
	 * bindせずコンテナ単位fallback。判定は論理page軸=縦書きcolumnの
	 * 主軸は物理width)。
	 */
	private boolean columnMainResolvable(final BlockBuilder target) {
		for (final FlexItemContent item : this.items) {
			if (item.spec.basis().isContent()) {
				// basis:contentは主軸指定に関係なく内容高を要求する
				FLEX_COLUMN_FALLBACKS_CONTENT_BASIS.incrementAndGet();
				this.bindFallback(target);
				return false;
			}
			if (item.spec.basis().isAuto() && item.itemBox.getBlockParams().size
					.getPageType(this.flow()) == LengthType.AUTO) {
				FLEX_COLUMN_FALLBACKS_AUTO_MAIN.incrementAndGet();
				this.bindFallback(target);
				return false;
			}
		}
		return true;
	}

	/** 視覚順の全itemを§9.7の入力(主軸計測値)へ数値化します。 */
	private List<FlexItemMetrics> buildMetrics(final int[] seq, final MainAxis axis) {
		final List<FlexItemMetrics> metrics = new ArrayList<>(seq.length);
		for (final int oi : seq) {
			final FlexItemContent item = this.items.get(oi);
			final BlockParams p = item.itemBox.getBlockParams();
			// columnの主軸内在サイズはminPageのみ(min-main:autoのminPage
			// 利用はF4bの既知近似——正確なcontent minimumは幅依存、
			// F4c答申に記録)
			final double minContent = axis.mainIsLine ? item.sizes.minContent() : item.sizes.minPage();
			final double maxContent = axis.mainIsLine ? item.sizes.maxContent() : item.sizes.minPage();
			metrics.add(FlexItemMetricsResolver.resolve(new FlexItemMetricsResolver.Input(oi,
					item.spec.grow(), item.spec.shrink(), item.spec.basis(), axis.mainValue(p.size),
					axis.minMainAuto(item.spec) ? Double.NaN
							: Math.max(0, zeroIfNaN(axis.mainValue(p.minSize))),
					axis.mainMaxValue(p.maxSize), axis.mainFrame(p.frame), axis.mainMargin(p.frame),
					p.boxSizing == BoxSizingMode.BORDER_BOX, p.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE,
					minContent, maxContent, axis.mainBase)));
		}
		return metrics;
	}

	/** 主軸の行(rowの行/columnの列)分割です(§9.3。nowrapは単一行)。 */
	private List<FlexLineBreaker.Line> breakMainLines(final List<FlexItemMetrics> metrics, final MainAxis axis) {
		if (this.flexBox.getFlexParams().flexWrap.isWrap()) {
			return FlexLineBreaker.breakLines(metrics, axis.mainBase, axis.mainGap());
		}
		if (this.items.isEmpty()) {
			return List.of();
		}
		return List.of(new FlexLineBreaker.Line(0, this.items.size()));
	}

	/** 行ごとに§9.7を解決し、使用主軸寸法をソースindexで引ける形にします。 */
	private double[] resolveMainSizes(final int[] seq, final List<FlexItemMetrics> metrics,
			final List<FlexLineBreaker.Line> lines, final MainAxis axis) {
		final double[] byOriginal = new double[this.items.size()];
		for (final FlexLineBreaker.Line line : lines) {
			final double[] sizes = FlexLengthResolver.resolve(metrics.subList(line.from(), line.to()),
					axis.mainBase, axis.mainGap());
			for (int k = line.from(); k < line.to(); ++k) {
				byOriginal[seq[k]] = sizes[k - line.from()];
			}
		}
		return byOriginal;
	}

	/**
	 * 主軸の余白分配です(§9.5+§8.1)。auto marginはjustify-contentより
	 * 先に余白を消費する(1つでもあればjustifyは働かない。columnは
	 * auto marginサブセット外のため{@code autoMargins}=0で渡す)。
	 * 負余白はsafe start(0)、stretchはjustify-contentではflex-start扱い。
	 */
	private record MainDistribution(double leading, double between, double autoShare) {
	}

	private MainDistribution distributeMain(final double free, final int count, final int autoMargins,
			final double mainGap) {
		final FlexContentAlignment justify = this.flexBox.getFlexParams().justifyContent;
		if (autoMargins > 0) {
			return new MainDistribution(0, mainGap, free > 0 ? free / autoMargins : 0);
		}
		return new MainDistribution(justify.leadingOffset(free, count),
				mainGap + justify.betweenOffset(free, count), 0);
	}

	/**
	 * cross軸の行群分配です(§9.6——wrap時のみ。normal/stretchは各行へ
	 * 均等加算し、他はFlexContentAlignmentの算術でleading/betweenへ)。
	 * {@code extents}は行のcross寸法(破壊的に加算される)。
	 */
	private record CrossDistribution(double leading, double between) {
	}

	private CrossDistribution distributeCross(final double[] extents, final double innerCross,
			final double crossGap) {
		final FlexParams params = this.flexBox.getFlexParams();
		double content = extents.length > 1 ? crossGap * (extents.length - 1) : 0;
		for (final double extent : extents) {
			content += extent;
		}
		double leading = 0;
		double between = extents.length > 1 ? crossGap : 0;
		final double freeCross = Math.max(0, innerCross - content);
		if (freeCross > 0 && extents.length > 0) {
			if (params.alignContent == FlexContentAlignment.STRETCH
					|| params.alignContent == FlexContentAlignment.NORMAL) {
				final double share = freeCross / extents.length;
				for (int i = 0; i < extents.length; ++i) {
					extents[i] += share;
				}
			} else {
				leading = params.alignContent.leadingOffset(freeCross, extents.length);
				between += params.alignContent.betweenOffset(freeCross, extents.length);
			}
		}
		return new CrossDistribution(leading, between);
	}

	/**
	 * align-self:auto→align-itemsの合成used value(§9.6)。wrap-reverse
	 * (cross軸反転)ではstart/endを交換する——無印start/endも
	 * flex-start/endと同扱い(flexでの慣用が圧倒的にflex-*のため。
	 * 厳密な書字方向基準start/endはサブセット外)。
	 */
	private BoxAlignment resolveAlign(final FlexItemContent item, final boolean crossReversed) {
		final BoxAlignment align = BoxAlignment.resolve(item.spec.alignSelf(),
				this.flexBox.getFlexParams().alignItems);
		if (crossReversed) {
			return align == BoxAlignment.START ? BoxAlignment.END
					: align == BoxAlignment.END ? BoxAlignment.START : align;
		}
		return align;
	}

	// ------------------------------------------------------------------
	// 配置(物理写像がrow/columnで逆——ここだけが二枚)

	/**
	 * rowの配置です。cross寸法は実測(bind後のitem page extent)——
	 * §9.7の主軸寸法でソース順に全item bindしてから、行cross=行内最大、
	 * §9.4(単一行nowrap+definite crossの行高=コンテナ内cross)、
	 * align-content、stretch伸長(takeoverによりauthored背景が追随)、
	 * cross/主軸auto margin、wrap-reverseの行順+start/end反転を適用する。
	 */
	private void placeRow(final BlockBuilder target, final MainAxis axis, final int[] seq,
			final List<FlexItemMetrics> metrics, final List<FlexLineBreaker.Line> lines,
			final double[] mainSizeByOriginal) {
		final FlexParams params = this.flexBox.getFlexParams();
		// bindはソース順(F5a——Tagged PDFの読み順・構造をソース順に保つ)
		for (int i = 0; i < this.items.size(); ++i) {
			this.items.get(i).bind(target, mainSizeByOriginal[i], axis.marginBase);
			FLEX_ITEM_BINDS.incrementAndGet();
		}
		// 行ごとの主軸配置(線offset)。crossは行分配後
		final double[] lineExtents = new double[lines.size()];
		for (int li = 0; li < lines.size(); ++li) {
			final FlexLineBreaker.Line line = lines.get(li);
			double lineUsed = axis.mainGap() * (line.count() - 1);
			int autoMargins = 0;
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				lineUsed += item.itemBox.getLineExtent(params.flow);
				lineExtents[li] = Math.max(lineExtents[li], item.itemBox.getPageExtent(params.flow));
				autoMargins += (this.mainMarginAuto(item, false) ? 1 : 0)
						+ (this.mainMarginAuto(item, true) ? 1 : 0);
			}
			final MainDistribution dist = this.distributeMain(axis.mainBase - lineUsed, line.count(),
					autoMargins, axis.mainGap());
			double lineCursor = dist.leading();
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				if (this.mainMarginAuto(item, false)) {
					lineCursor += dist.autoShare();
				}
				// 自然位置は自margin込みのため、offsetは先行分の累積
				item.itemBox.setFlexLineOffset(lineCursor, params.flow.isVertical());
				lineCursor += item.itemBox.getLineExtent(params.flow)
						+ (this.mainMarginAuto(item, true) ? dist.autoShare() : 0)
						+ (k < line.to() - 1 ? dist.between() : 0);
			}
		}
		// cross軸の行分配(F3d)。内容cross合計を仮確定した上でdefinite
		// crossとの差=freeを得る(getInnerPageExtentは指定高があれば
		// それを返す——G5eの手筋)
		double content = lines.size() > 1 ? axis.crossGap() * (lines.size() - 1) : 0;
		for (final double extent : lineExtents) {
			content += extent;
		}
		this.flexBox.setPageAxis(content);
		final double innerCross = this.flexBox.getInnerPageExtent(params.flow);
		CrossDistribution dist = new CrossDistribution(0, lines.size() > 1 ? axis.crossGap() : 0);
		if (params.flexWrap.isWrap()) {
			dist = this.distributeCross(lineExtents, innerCross, axis.crossGap());
		} else if (lines.size() == 1 && innerCross > lineExtents[0]) {
			// §9.4: 単一行(nowrap)+definite crossの行高=コンテナ内cross
			lineExtents[0] = innerCross;
		}
		// cross整列(F3c——分配後の行高に対して行う)。wrap-reverse(F5c)は
		// 行の視覚順を反転
		final boolean crossReversed = params.flexWrap == FlexWrap.WRAP_REVERSE;
		double crossCursor = dist.leading();
		// **改ページ用の行境界の記録**(2026-08-07、Bug C)。addFlowと同じ
		// 順序でitemを積んでおくことで、FlexBox.splitが「どのitemがどの行に
		// 属すか」をコンテナ越しに探さず直接引ける(TableRowGroupBoxの
		// rows/cellsリストと同じ役割)
		final List<FlexBox.Line> flexLines = new ArrayList<>(lines.size());
		final List<FlexItemBox> flexLineItems = new ArrayList<>(this.items.size());
		for (int v = 0; v < lines.size(); ++v) {
			final int li = crossReversed ? lines.size() - 1 - v : v;
			final FlexLineBreaker.Line line = lines.get(li);
			final double lineExtent = lineExtents[li];
			final int lineStartFlow = flexLineItems.size();
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				final BoxAlignment align = this.resolveAlign(item, crossReversed);
				// cross軸auto marginはalign-self/stretchより先(§8.1、F3e):
				// start側autoで終端寄せ、両側autoで中央
				final boolean crossStartAuto = this.crossMarginAuto(item, false);
				final boolean crossEndAuto = this.crossMarginAuto(item, true);
				double crossOffset = 0;
				if (crossStartAuto || crossEndAuto) {
					final double freeCross = Math.max(0,
							lineExtent - item.itemBox.getPageExtent(params.flow));
					crossOffset = crossStartAuto && crossEndAuto ? freeCross / 2
							: crossStartAuto ? freeCross : 0;
				} else if (align == BoxAlignment.STRETCH) {
					// cross autoのitemだけ行高まで伸長——takeover設計により
					// authoredの背景・枠がそのまま追随する(F1dの狙い)
					if (item.itemBox.getBlockParams().size.getPageType(params.flow) == LengthType.AUTO) {
						final double deficit = lineExtent - item.itemBox.getPageExtent(params.flow);
						if (deficit > 0) {
							item.itemBox.setPageAxis(item.itemBox.getInnerPageExtent(params.flow) + deficit);
						}
					}
				} else {
					final double freeCross = lineExtent - item.itemBox.getPageExtent(params.flow);
					crossOffset = align == BoxAlignment.CENTER ? Math.max(0, freeCross / 2)
							: align == BoxAlignment.END ? Math.max(0, freeCross) : 0;
				}
				this.flexBox.getContainer().addFlow(item.itemBox, crossCursor + crossOffset);
				flexLineItems.add(item.itemBox);
			}
			flexLines.add(new FlexBox.Line(lineStartFlow, line.to() - line.from(), lineExtent));
			crossCursor += lineExtent + (v < lines.size() - 1 ? dist.between() : 0);
		}
		if (!this.items.isEmpty()) {
			this.flexBox.setFlexLines(flexLines, flexLineItems);
		}
		this.flexBox.setPageAxis(this.items.isEmpty() ? 0 : Math.max(content, crossCursor));
	}

	/**
	 * columnの配置です。cross寸法は事前計算(F4c答申の処理順——本文高さを
	 * 一切読まずに、列cross=列内itemの明示幅/fit-contentの最大、
	 * align-content分配、stretch itemの列幅追随まで確定してからbindする)。
	 * 主軸寸法はbind後にsetPageAxisで課す(§9.7の結果——指定高より優先)。
	 * auto marginはcolumnサブセット外。wrap-reverseは列順とitem整列の
	 * start/endをrowと対称に反転する。
	 */
	private void placeColumn(final BlockBuilder target, final MainAxis axis, final int[] seq,
			final List<FlexItemMetrics> metrics, final List<FlexLineBreaker.Line> cols,
			final double[] mainSizeByOriginal) {
		final FlexParams params = this.flexBox.getFlexParams();
		final double innerLine = axis.marginBase;
		final int count = this.items.size();
		// 列cross幅=列内itemのcross(明示幅/stretch/fit-content)最大
		final double[] crossWidthByOriginal = new double[count];
		final double[] itemCrossExtras = new double[count];
		final double[] colCross = new double[cols.size()];
		for (int ci = 0; ci < cols.size(); ++ci) {
			final FlexLineBreaker.Line col = cols.get(ci);
			for (int k = col.from(); k < col.to(); ++k) {
				final int oi = seq[k];
				final FlexItemContent item = this.items.get(oi);
				final BlockParams p = item.itemBox.getBlockParams();
				final RectFrame frame = p.frame;
				final double lineExtras = insetsLine(frame.margin, innerLine)
						+ insetsLine(frame.padding, innerLine) + borderLine(frame);
				final BoxAlignment align = this.resolveAlign(item, false);
				final double crossWidth;
				if (p.size.getLineType(params.flow) != LengthType.AUTO) {
					// 明示幅(border-boxは枠を引いて内寸へ。marginは含まない)
					final double borderBoxAdjust = p.boxSizing == BoxSizingMode.BORDER_BOX
							? lineExtras - insetsLine(frame.margin, innerLine)
							: 0;
					crossWidth = Math.max(0, lineValue(p.size, innerLine) - borderBoxAdjust);
				} else if (align == BoxAlignment.STRETCH && !params.flexWrap.isWrap()) {
					// nowrap単一列のstretchはコンテナ内寸いっぱい。
					// wrapの列stretchは列幅確定後(下)
					crossWidth = Math.max(0, innerLine - lineExtras);
				} else {
					crossWidth = Sizing.fitContent(item.sizes.minContent(), item.sizes.maxContent(),
							Math.max(0, innerLine - lineExtras));
				}
				crossWidthByOriginal[oi] = crossWidth;
				itemCrossExtras[oi] = lineExtras;
				colCross[ci] = Math.max(colCross[ci], crossWidth + lineExtras);
			}
		}
		// 列群のcross分配(wrap時のみ)+stretch itemの確定列幅への追随
		CrossDistribution dist = new CrossDistribution(0, cols.size() > 1 ? axis.crossGap() : 0);
		if (params.flexWrap.isWrap()) {
			dist = this.distributeCross(colCross, innerLine, axis.crossGap());
			for (int ci = 0; ci < cols.size(); ++ci) {
				final FlexLineBreaker.Line col = cols.get(ci);
				for (int k = col.from(); k < col.to(); ++k) {
					final int oi = seq[k];
					final FlexItemContent item = this.items.get(oi);
					if (this.resolveAlign(item, false) == BoxAlignment.STRETCH && item.itemBox.getBlockParams().size
							.getLineType(params.flow) == LengthType.AUTO) {
						crossWidthByOriginal[oi] = Math.max(crossWidthByOriginal[oi],
								colCross[ci] - itemCrossExtras[oi]);
					}
				}
			}
		}
		// bindはソース順(F5a——Tagged PDFの読み順・構造をソース順に保つ)
		for (int i = 0; i < count; ++i) {
			this.items.get(i).bind(target, crossWidthByOriginal[i], axis.marginBase);
			FLEX_ITEM_BINDS.incrementAndGet();
		}
		// 配置は視覚順。列はcross方向へ積む(wrap-reverseは列順反転)
		final boolean crossReversed = params.flexWrap == FlexWrap.WRAP_REVERSE;
		double crossCursor = dist.leading();
		double lastMainEnd = 0;
		for (int v = 0; v < cols.size(); ++v) {
			final int ci = crossReversed ? cols.size() - 1 - v : v;
			final FlexLineBreaker.Line col = cols.get(ci);
			double used = axis.mainGap() * (col.count() - 1);
			for (int k = col.from(); k < col.to(); ++k) {
				used += mainSizeByOriginal[seq[k]] + metrics.get(k).outerMainExtra();
			}
			final MainDistribution main = this.distributeMain(axis.mainBase - used, col.count(), 0,
					axis.mainGap());
			double mainCursor = main.leading();
			for (int k = col.from(); k < col.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				// 主軸(page)寸法を確定(§9.7の結果——指定高より優先)
				item.itemBox.setPageAxis(mainSizeByOriginal[seq[k]]);
				// cross整列(line軸): 列内の残余+列開始位置。wrap-reverseは
				// rowと対称にstart/endを反転(2026-08-02——一本化の照合で
				// 発見した非対称の解消)
				final double freeCross = Math.max(0, colCross[ci] - item.itemBox.getLineExtent(params.flow));
				final BoxAlignment align = this.resolveAlign(item, crossReversed);
				final double crossOffset = align == BoxAlignment.CENTER ? freeCross / 2
						: align == BoxAlignment.END ? freeCross : 0;
				item.itemBox.setFlexLineOffset(crossCursor + crossOffset, params.flow.isVertical());
				this.flexBox.getContainer().addFlow(item.itemBox, mainCursor);
				mainCursor += mainSizeByOriginal[seq[k]] + metrics.get(k).outerMainExtra()
						+ (k < col.to() - 1 ? main.between() : 0);
			}
			lastMainEnd = Math.max(lastMainEnd, mainCursor);
			crossCursor += colCross[ci] + (v < cols.size() - 1 ? dist.between() : 0);
		}
		this.flexBox.setPageAxis(count == 0 ? 0 : Math.max(axis.mainBase, lastMainEnd));
	}

	/**
	 * 単一列縮退です(F4c——columnの内容依存basis時。itemはコンテナ
	 * 内寸いっぱいのブロックとして縦積み。マージン相殺は行わない)。
	 */
	private void bindFallback(final BlockBuilder target) {
		final FlexParams params = this.flexBox.getFlexParams();
		final double innerLine = this.flexBox.getLineSize();
		double pageCursor = 0;
		for (final FlexItemContent item : this.items) {
			final RectFrame frame = item.itemBox.getBlockParams().frame;
			final double lineExtras = insetsLine(frame.margin, innerLine) + insetsLine(frame.padding, innerLine)
					+ borderLine(frame);
			item.bind(target, Math.max(0, innerLine - lineExtras), innerLine);
			FLEX_ITEM_BINDS.incrementAndGet();
			this.flexBox.getContainer().addFlow(item.itemBox, pageCursor);
			pageCursor += item.itemBox.getPageExtent(params.flow);
		}
		this.flexBox.setPageAxis(pageCursor);
		this.syncHostCursor(target, params);
	}

	// ------------------------------------------------------------------
	// RetainedFlex(TwoPass宿主・親range吸収)

	/**
	 * Flex全体のcontent-box固有寸法contributionです(F1f——§9.9の
	 * 単一行row近似)。行方向: min=Σ(item min-content+枠の絶対部)、
	 * max=Σ(item max-content+同)。%枠は基準未確定のため絶対部のみ
	 * (控えめな近似——確定幅はbind時に正確に解決される)。ページ方向
	 * min=item minPageの最大(単一行)。frameは含めない(計測器の
	 * 通常経路が一度だけ加算する)。
	 */
	@Override
	public IntrinsicSizes getIntrinsicSizes() {
		double min = 0, max = 0, minPage = 0;
		boolean columnInflated = false;
		final boolean wrap = this.flexBox.getFlexParams().flexWrap.isWrap();
		for (final FlexItemContent item : this.items) {
			final BlockParams itemParams = item.itemBox.getBlockParams();
			final RectFrame frame = itemParams.frame;
			final double extra = insetsLine(frame.margin, 0) + insetsLine(frame.padding, 0) + borderLine(frame);
			// **項目自身が宣言した寸法を数えること**(2026-08-05)。
			// これを見ずに中身の寸法だけを足していたため、`width:20pt` を
			// 持つが中身が空の項目は0と数えられ、**入れ子のフレックス容器の
			// 内在寸法が文字ぶんだけ**になっていた。すると外側から見た
			// 主軸の空きが負になり、既定の flex-shrink で中の項目が
			// 幅0に潰れる——「入れ子のフレックスで子が消える」の正体。
			// bind時(FlexItemMetricsResolver)と同じく flex-basis を優先する。
			final double declared = declaredLineBase(item);
			double itemMin = item.sizes.minContent();
			double itemMax = item.sizes.maxContent();
			if (!Double.isNaN(declared)) {
				final double outer = itemParams.boxSizing == BoxSizingMode.BORDER_BOX
						? Math.max(declared, insetsLine(frame.padding, 0) + borderLine(frame))
						: declared + insetsLine(frame.padding, 0) + borderLine(frame);
				itemMin = Math.max(itemMin, outer - insetsLine(frame.padding, 0) - borderLine(frame));
				itemMax = Math.max(itemMax, outer - insetsLine(frame.padding, 0) - borderLine(frame));
			}
			// wrap時のminは「最大item」(行ごとに折り返せる)、nowrapは総和
			min = wrap ? Math.max(min, itemMin + extra) : min + itemMin + extra;
			max += itemMax + extra;
			minPage = Math.max(minPage, item.sizes.minPage());
			columnInflated |= item.sizes.columnInflated();
		}
		if (this.items.size() > 1) {
			final double gaps = this.flexBox.getFlexParams().columnGap * (this.items.size() - 1);
			max += gaps;
			if (!wrap) {
				min += gaps;
			}
		}
		return new IntrinsicSizes(min, max, minPage, columnInflated);
	}

	/**
	 * 項目が<b>自分で宣言した</b>線方向の基準寸法です(不定ならNaN)。
	 * {@code flex-basis} を {@code width} より優先するのは
	 * {@link FlexItemMetricsResolver} と同じ順序です。
	 */
	private double declaredLineBase(final FlexItemContent item) {
		final net.zamasoft.foliojet.css.value.FlexBasisValue basis = item.spec.basis();
		if (!basis.isAuto() && !basis.isContent()
				&& basis.getSize() instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue length) {
			// 割合の基準は容器の内寸で、この段階では未確定なので数えない
			return length.getLength();
		}
		return lineValue(item.itemBox.getBlockParams().size, 0);
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
	boolean collectAbsorbableItems(final LayoutSource log, final long fromId, final long toId,
			final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final Set<Long> ownedAbsoluteAnchors, final Set<TwoPassBlockBuilder> seen) {
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

	// ------------------------------------------------------------------
	// 物理軸ヘルパ(WritingModeが向きを吸収)

	/** Dimensionの線方向値(auto=NaN。%は基準寸法で解決。縦書き=高さ)。 */
	private double lineValue(final Dimension size, final double base) {
		return axisValue(size.getLineType(this.flow()), size.getLineLength(this.flow()),
				size.getLineRatio(this.flow()), base);
	}

	/** Dimensionのpage方向値(auto=NaN。%は基準寸法で解決。縦書き=幅)。 */
	private double pageValue(final Dimension size, final double base) {
		return axisValue(size.getPageType(this.flow()), size.getPageLength(this.flow()),
				size.getPageRatio(this.flow()), base);
	}

	/**
	 * 寸法値の解決です。
	 *
	 * <p>
	 * <b>{@link LengthType#RELATIVE}(純粋な割合)は値の欄に割合が入る</b>
	 * ({@code Length.create(ratio, RELATIVE)})。割合の欄が使われるのは
	 * {@link LengthType#MIXED}(calc()で絶対長と割合が混ざった場合)だけである。
	 *
	 * <p>
	 * 2026-08-03まで、ここは型を見ずに「長さ+割合×基準」で計算していた。
	 * その結果<b>{@code width: 66.66%} のflexアイテムが「0.67pt」と読まれ、
	 * 自動最小サイズに切り上げられてmin-content幅へ潰れていた</b>。
	 * Bootstrap 5のグリッドは {@code .row > * { width: 100% }} と
	 * {@code .col-N { width: X% }} で組まれているため、<b>Bootstrapで作られた
	 * 文書は全部が1語ずつ改行される版面になっていた</b>。実物大の文書を
	 * 取り込んだ第0波の1件目で発覚(PLAN §3)。掃過2000万文書は一度も
	 * 捕まえていない——生成器がflexアイテムに%幅を書かないため。
	 * 回帰は files/unittest/3120-FLEXBOX/percentage-width.html。
	 */
	private static double axisValue(final LengthType type, final double length, final double ratio,
			final double base) {
		switch (type) {
		case AUTO:
			return Double.NaN;
		case ABSOLUTE:
			return length;
		case RELATIVE:
			return length * base;
		case MIXED:
			return length + ratio * base;
		default:
			throw new IllegalStateException(String.valueOf(type));
		}
	}

	private static double zeroIfNaN(final double value) {
		return Double.isNaN(value) ? 0 : value;
	}

	/** 線方向のInsets合計(絶対部+比率×基準。autoは0。縦書き=上下)。 */
	private double insetsLine(final Insets insets, final double base) {
		return insetsAxis(insets, base, this.flow().isVertical());
	}

	/** page方向のInsets合計(絶対部+比率×基準。autoは0。縦書き=左右)。 */
	private double insetsPage(final Insets insets, final double base) {
		return insetsAxis(insets, base, !this.flow().isVertical());
	}

	/**
	 * 指定物理軸のInsets合計(horizontal=true: 上下、false: 左右)。
	 *
	 * <p>
	 * 割合の読み方は{@link #axisValue}と同じ落とし穴がある——{@code RELATIVE}は
	 * 値の欄に割合が入る。{@code padding: 5%} のflexアイテムが0.05pt扱いに
	 * なっていた(2026-08-03に幅の件と一緒に修正)。
	 */
	private static double insetsAxis(final Insets insets, final double base, final boolean horizontal) {
		double sum = 0;
		if (horizontal) {
			sum += insetValue(insets.getTopType(), insets.getTop(), insets.getTopRatio(), base);
			sum += insetValue(insets.getBottomType(), insets.getBottom(), insets.getBottomRatio(), base);
		} else {
			sum += insetValue(insets.getLeftType(), insets.getLeft(), insets.getLeftRatio(), base);
			sum += insetValue(insets.getRightType(), insets.getRight(), insets.getRightRatio(), base);
		}
		return sum;
	}

	/** Insetsの1辺(autoは0として合計に寄与しない)。 */
	private static double insetValue(final LengthType type, final double length, final double ratio,
			final double base) {
		if (type == LengthType.AUTO) {
			return 0;
		}
		return axisValue(type, length, ratio, base);
	}

	/** 線方向のborder幅合計(縦書き=上下)。 */
	private double borderLine(final RectFrame frame) {
		return this.flow().isVertical() ? frame.border.getTop().width + frame.border.getBottom().width
				: frame.border.getLeft().width + frame.border.getRight().width;
	}

	/** page方向のborder幅合計(縦書き=左右)。 */
	private double borderPage(final RectFrame frame) {
		return this.flow().isVertical() ? frame.border.getLeft().width + frame.border.getRight().width
				: frame.border.getTop().width + frame.border.getBottom().width;
	}

	/** 主軸(行方向)marginがautoかを返します(F3e。end=進行方向の後側。縦書き=上下)。 */
	private boolean mainMarginAuto(final FlexItemContent item, final boolean end) {
		final Insets margin = item.itemBox.getBlockParams().frame.margin;
		if (this.flow().isVertical()) {
			return (end ? margin.getBottomType() : margin.getTopType()) == LengthType.AUTO;
		}
		return (end ? margin.getRightType() : margin.getLeftType()) == LengthType.AUTO;
	}

	/** cross軸marginがautoかを返します(F3e。縦書き=左右)。 */
	private boolean crossMarginAuto(final FlexItemContent item, final boolean end) {
		final Insets margin = item.itemBox.getBlockParams().frame.margin;
		if (this.flow().isVertical()) {
			return (end ? margin.getRightType() : margin.getLeftType()) == LengthType.AUTO;
		}
		return (end ? margin.getBottomType() : margin.getTopType()) == LengthType.AUTO;
	}

	/**
	 * 視覚順(order昇順、同値は録画順の安定ソート——§5.4)のindex列です
	 * (F5a)。行分割・§9.7・配置は視覚順、bindはソース順(Tagged PDFの
	 * 読み順・構造をソース順に保つ——答申F5a)。reverse主軸(F5b)は
	 * 視覚並びを反転(justify側の反転はmapperのtoFlexJustify)。
	 */
	private int[] visualOrder() {
		final Integer[] seq = new Integer[this.items.size()];
		for (int i = 0; i < seq.length; ++i) {
			seq[i] = i;
		}
		Arrays.sort(seq,
				(x, y) -> Integer.compare(this.items.get(x).spec.order(), this.items.get(y).spec.order()));
		final int[] result = new int[seq.length];
		final boolean reversed = this.flexBox.getFlexParams().flexDirection.isReverse();
		for (int i = 0; i < seq.length; ++i) {
			result[reversed ? seq.length - 1 - i : i] = seq[i];
		}
		return result;
	}

	/** ホストflowカーソルの同期(GridBuilder.bind末尾と同型)。 */
	private void syncHostCursor(final BlockBuilder target, final FlexParams params) {
		final LayoutContext.Flow active = target.getFlow();
		assert active.box == this.flexBox : "Flex bindでactive flowがFlexではない: " + active.box;
		target.setPageAxis(active.pageAxis + this.flexBox.getInnerPageExtent(params.flow));
	}
}
