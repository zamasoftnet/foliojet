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

	/** column主軸のbasis/height未確定によるコンテナ単位fallback数(F4b)。 */
	public static final AtomicLong FLEX_COLUMN_FALLBACKS = new AtomicLong();

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
		if (!this.flexBox.getFlexParams().flexDirection.isRow()) {
			this.bindColumn(target);
			return;
		}
		final double containerInner = this.flexBox.getLineSize();
		final int[] seq = this.visualOrder();
		final java.util.List<FlexItemMetrics> metrics = new ArrayList<>(this.items.size());
		for (int k = 0; k < seq.length; ++k) {
			final FlexItemContent item = this.items.get(seq[k]);
			final BlockParams p = item.itemBox.getBlockParams();
			final RectFrame frame = p.frame;
			final double mainFrame = insetsLine(frame.padding, containerInner)
					+ frame.border.getLeft().width + frame.border.getRight().width;
			metrics.add(FlexItemMetricsResolver.resolve(new FlexItemMetricsResolver.Input(seq[k],
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
		final java.util.List<net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line> lines;
		if (this.flexBox.getFlexParams().flexWrap.isWrap()) {
			// F2b: 行分割はouter hypothetical main size基準(§9.3 step 5)
			lines = net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.breakLines(metrics, containerInner,
					this.flexBox.getFlexParams().columnGap);
		} else if (this.items.isEmpty()) {
			lines = java.util.List.of();
		} else {
			lines = java.util.List
					.of(new net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line(0, this.items.size()));
		}
		this.bindLines(target, seq, metrics, lines, containerInner);
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
		final boolean wrap = this.flexBox.getFlexParams().flexWrap.isWrap();
		for (final FlexItemContent item : this.items) {
			final RectFrame frame = item.itemBox.getBlockParams().frame;
			final double extra = insetsLine(frame.margin, 0) + insetsLine(frame.padding, 0)
					+ frame.border.getLeft().width + frame.border.getRight().width;
			// wrap時のminは「最大item」(行ごとに折り返せる)、nowrapは総和
			min = wrap ? Math.max(min, item.sizes.minContent() + extra) : min + item.sizes.minContent() + extra;
			max += item.sizes.maxContent() + extra;
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
	 * columnの組み立てです(F4b——主軸=page軸。§9.7はdefinite basis/height
	 * のみで解決し、未確定itemが1つでもあればコンテナ単位で単一列縮退
	 * (item単位の縮退は禁止——答申の段階的fallback規則)。crossは
	 * align-items(stretch=内寸いっぱい、start/center/end=fit-content幅)、
	 * 主軸配置はjustify-content。auto marginはcolumnサブセット外=0扱い。
	 */
	private void bindColumn(final BlockBuilder target) {
		assert !this.bound : "Flexの二重bind";
		this.bound = true;
		final FlexParams params = this.flexBox.getFlexParams();
		final double innerLine = this.flexBox.getLineSize();
		// 主軸(page)の内寸: 指定高(eligibleで絶対長を保証)は
		// getInnerPageExtentが返す(G5eの手筋)
		final double innerMain = this.flexBox.getInnerPageExtent(params.flow);
		final int count = this.items.size();
		final int[] seq = this.visualOrder();
		final java.util.List<FlexItemMetrics> metrics = new ArrayList<>(count);
		for (int k = 0; k < count; ++k) {
			final FlexItemContent item = this.items.get(seq[k]);
			final BlockParams p = item.itemBox.getBlockParams();
			if ((item.spec.basis().isAuto() || item.spec.basis().isContent())
					&& p.size.getHeightType() != net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				// basis auto→heightプロパティ。それもautoなら内容高が要る
				// (F4cのprobe)——コンテナ単位fallback
				FLEX_COLUMN_FALLBACKS.incrementAndGet();
				this.bindFallback(target);
				return;
			}
			final RectFrame frame = p.frame;
			// 縦方向margin/paddingの%基準はインライン寸法(コンテナ行内寸)
			final double mainFrame = insetsPage(frame.padding, innerLine)
					+ frame.border.getTop().width + frame.border.getBottom().width;
			metrics.add(FlexItemMetricsResolver.resolve(new FlexItemMetricsResolver.Input(seq[k],
					item.spec.grow(), item.spec.shrink(), item.spec.basis(),
					pageValue(p.size, innerMain),
					item.spec.minHeightAuto() ? Double.NaN
							: Math.max(0, zeroIfNaN(pageValue(p.minSize, innerMain))),
					maxPageValue(p.maxSize, innerMain), mainFrame, insetsPage(frame.margin, innerLine),
					p.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX,
					p.overflow != net.zamasoft.foliojet.layout.box.params.OverflowMode.VISIBLE,
					item.sizes.minPage(), item.sizes.minPage(), innerMain)));
		}
		// columnの主軸gap=row-gap(block軸間隔)。justifyは§9.5と同じ算術
		final double[] mainSizes = FlexLengthResolver.resolve(metrics, innerMain, params.rowGap);
		double used = count > 0 ? params.rowGap * (count - 1) : 0;
		for (int i = 0; i < count; ++i) {
			used += mainSizes[i] + metrics.get(i).outerMainExtra();
		}
		final double free = innerMain - used;
		double mainCursor = params.justifyContent.leadingOffset(free, count);
		final double between = params.rowGap + params.justifyContent.betweenOffset(free, count);
		// F5a: bindはソース順(cross幅は視覚順に依存しないため先に確定できる)
		final double[] crossWidthByOriginal = new double[count];
		final double[] mainSizeByOriginal = new double[count];
		for (int k = 0; k < count; ++k) {
			final FlexItemContent item = this.items.get(seq[k]);
			final BlockParams p = item.itemBox.getBlockParams();
			final RectFrame frame = p.frame;
			final double lineExtras = insetsLine(frame.margin, innerLine) + insetsLine(frame.padding, innerLine)
					+ frame.border.getLeft().width + frame.border.getRight().width;
			final net.zamasoft.foliojet.layout.box.params.BoxAlignment align = net.zamasoft.foliojet.layout.box.params.BoxAlignment
					.resolve(item.spec.alignSelf(), params.alignItems);
			final double crossWidth;
			if (p.size.getWidthType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
				// 明示幅(border-boxは枠を引いて内寸へ。marginは含まない)
				final double borderBoxAdjust = p.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
						? lineExtras - insetsLine(frame.margin, innerLine)
						: 0;
				crossWidth = Math.max(0, lineValue(p.size, innerLine) - borderBoxAdjust);
			} else if (align == net.zamasoft.foliojet.layout.box.params.BoxAlignment.STRETCH) {
				crossWidth = Math.max(0, innerLine - lineExtras);
			} else {
				crossWidth = net.zamasoft.foliojet.layout.sizing.Sizing.fitContent(item.sizes.minContent(),
						item.sizes.maxContent(), Math.max(0, innerLine - lineExtras));
			}
			crossWidthByOriginal[seq[k]] = crossWidth;
			mainSizeByOriginal[seq[k]] = mainSizes[k];
		}
		for (int i = 0; i < count; ++i) {
			this.items.get(i).bind(target, crossWidthByOriginal[i]);
			FLEX_ITEM_BINDS.incrementAndGet();
		}
		for (int k = 0; k < count; ++k) {
			final FlexItemContent item = this.items.get(seq[k]);
			final BlockParams p = item.itemBox.getBlockParams();
			final net.zamasoft.foliojet.layout.box.params.BoxAlignment align = net.zamasoft.foliojet.layout.box.params.BoxAlignment
					.resolve(item.spec.alignSelf(), params.alignItems);
			// 主軸(page)寸法を確定(§9.7の結果——指定高より優先)
			item.itemBox.setPageAxis(mainSizeByOriginal[seq[k]]);
			// cross整列(line軸): start/stretch=0、center/end=残余
			final double freeCross = Math.max(0, innerLine - item.itemBox.getLineExtent(params.flow));
			final double crossOffset = align == net.zamasoft.foliojet.layout.box.params.BoxAlignment.CENTER
					? freeCross / 2
					: align == net.zamasoft.foliojet.layout.box.params.BoxAlignment.END ? freeCross : 0;
			item.itemBox.setFlexLineOffset(crossOffset);
			this.flexBox.getContainer().addFlow(item.itemBox, mainCursor);
			mainCursor += mainSizeByOriginal[seq[k]] + metrics.get(k).outerMainExtra()
					+ (k < count - 1 ? between : 0);
		}
		this.flexBox.setPageAxis(count == 0 ? 0 : Math.max(innerMain, mainCursor));
		this.syncHostCursor(target, params);
	}

	/**
	 * 単一列縮退です(F4b——columnのbasis/height未確定時。itemはコンテナ
	 * 内寸いっぱいのブロックとして縦積み。マージン相殺は行わない)。
	 */
	private void bindFallback(final BlockBuilder target) {
		final FlexParams params = this.flexBox.getFlexParams();
		final double innerLine = this.flexBox.getLineSize();
		double pageCursor = 0;
		for (final FlexItemContent item : this.items) {
			final RectFrame frame = item.itemBox.getBlockParams().frame;
			final double lineExtras = insetsLine(frame.margin, innerLine) + insetsLine(frame.padding, innerLine)
					+ frame.border.getLeft().width + frame.border.getRight().width;
			item.bind(target, Math.max(0, innerLine - lineExtras));
			FLEX_ITEM_BINDS.incrementAndGet();
			this.flexBox.getContainer().addFlow(item.itemBox, pageCursor);
			pageCursor += item.itemBox.getPageExtent(params.flow);
		}
		this.flexBox.setPageAxis(pageCursor);
		this.syncHostCursor(target, params);
	}

	/** Dimensionのpage方向値(auto=NaN。%は主軸内寸基準)。横書き=height。 */
	private static double pageValue(final Dimension size, final double base) {
		if (size.getHeightType() == net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			return Double.NaN;
		}
		return size.getHeight() + size.getHeightRatio() * base;
	}

	/** max-heightのpage方向値(なし=+∞)。 */
	private static double maxPageValue(final Dimension size, final double base) {
		final double value = pageValue(size, base);
		return Double.isNaN(value) ? Double.POSITIVE_INFINITY : Math.max(0, value);
	}

	/** page方向のInsets合計(絶対部+比率×基準。autoは0)。 */
	private static double insetsPage(final net.zamasoft.foliojet.layout.box.params.Insets insets,
			final double base) {
		double sum = 0;
		if (insets.getTopType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			sum += insets.getTop() + insets.getTopRatio() * base;
		}
		if (insets.getBottomType() != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
			sum += insets.getBottom() + insets.getBottomRatio() * base;
		}
		return sum;
	}

	/**
	 * 行群の組み立てです(行ごとに§9.7解決→幅確定→bind→cross実測→
	 * 配置、行はcross方向へ積む——GridBuilder.bindと同じ骨格。F2bで
	 * 複数行化)。PageAtomicBox契約によりFlex flowがactiveな間に全bindが
	 * 完了する。
	 */
	private void bindLines(final BlockBuilder target, final int[] seq,
			final java.util.List<FlexItemMetrics> metrics,
			final java.util.List<net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line> lines,
			final double containerInner) {
		assert !this.bound : "Flexの二重bind";
		this.bound = true;
		final FlexParams params = this.flexBox.getFlexParams();
		// 相0(F5a): §9.7の使用寸法を全行ぶん先に確定し、bindは
		// ソース順で行う(Tagged PDFの読み順・構造をソース順に保つ)。
		// 行分割・配置は視覚順seq
		final double[] mainSizeByOriginal = new double[this.items.size()];
		for (final net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line line : lines) {
			final double[] sizes = FlexLengthResolver.resolve(metrics.subList(line.from(), line.to()),
					containerInner, params.columnGap);
			for (int k = line.from(); k < line.to(); ++k) {
				mainSizeByOriginal[seq[k]] = sizes[k - line.from()];
			}
		}
		for (int i = 0; i < this.items.size(); ++i) {
			this.items.get(i).bind(target, mainSizeByOriginal[i]);
			FLEX_ITEM_BINDS.incrementAndGet();
		}
		// 相1: 行ごとに主軸配置(setFlexLineOffset)。cross配置は相3
		// (align-contentの行分配が先——G5eと同じ順序)
		final double[] lineExtents = new double[lines.size()];
		for (int li = 0; li < lines.size(); ++li) {
			final net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line line = lines.get(li);
			double lineUsed = params.columnGap * (line.count() - 1);
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				lineUsed += item.itemBox.getLineExtent(params.flow);
				lineExtents[li] = Math.max(lineExtents[li], item.itemBox.getPageExtent(params.flow));
			}
			// 主軸auto marginはjustify-contentより先に余白を消費する(§8.1、
			// F3e——1つでもあればjustifyは働かない)
			final double free = containerInner - lineUsed;
			int autoMargins = 0;
			for (int k = line.from(); k < line.to(); ++k) {
				autoMargins += (mainMarginAuto(this.items.get(seq[k]), false) ? 1 : 0)
						+ (mainMarginAuto(this.items.get(seq[k]), true) ? 1 : 0);
			}
			final double share = autoMargins > 0 && free > 0 ? free / autoMargins : 0;
			// 主軸の余白分配(§9.5——負余白はsafe start=0。stretchは
			// justify-contentではflex-start扱い)
			double lineCursor = autoMargins > 0 ? 0 : params.justifyContent.leadingOffset(free, line.count());
			final double between = params.columnGap
					+ (autoMargins > 0 ? 0 : params.justifyContent.betweenOffset(free, line.count()));
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				if (mainMarginAuto(item, false)) {
					lineCursor += share;
				}
				// 自然位置は自margin込みのため、offsetは先行分の累積
				item.itemBox.setFlexLineOffset(lineCursor);
				lineCursor += item.itemBox.getLineExtent(params.flow)
						+ (mainMarginAuto(item, true) ? share : 0) + (k < line.to() - 1 ? between : 0);
			}
		}
		// 相2: cross軸の行分配(F3d)。内容cross合計を仮確定した上で
		// definite crossとの差=freeを得る(getInnerPageExtentは指定高が
		// あればそれを返す——G5eと同じ手筋)
		double content = lines.size() > 1 ? params.rowGap * (lines.size() - 1) : 0;
		for (final double extent : lineExtents) {
			content += extent;
		}
		this.flexBox.setPageAxis(content);
		final double innerCross = this.flexBox.getInnerPageExtent(params.flow);
		double leadingCross = 0;
		double betweenCross = lines.size() > 1 ? params.rowGap : 0;
		if (params.flexWrap.isWrap()) {
			final double freeCross = Math.max(0, innerCross - content);
			if (freeCross > 0 && !lines.isEmpty()) {
				if (params.alignContent == net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.STRETCH
						|| params.alignContent == net.zamasoft.foliojet.layout.box.params.FlexContentAlignment.NORMAL) {
					// §9.6: align-contentの既定(normal/stretch)は行へ均等加算
					final double share = freeCross / lines.size();
					for (int li = 0; li < lineExtents.length; ++li) {
						lineExtents[li] += share;
					}
				} else {
					leadingCross = params.alignContent.leadingOffset(freeCross, lines.size());
					betweenCross += params.alignContent.betweenOffset(freeCross, lines.size());
				}
			}
		} else if (lines.size() == 1 && innerCross > lineExtents[0]) {
			// §9.4: 単一行(nowrap)+definite crossの行高=コンテナ内cross
			lineExtents[0] = innerCross;
		}
		// 相3: cross整列(F3c——align-contentで確定した行高に対して行う)
		double crossCursor = leadingCross;
		for (int li = 0; li < lines.size(); ++li) {
			final net.zamasoft.foliojet.layout.sizing.FlexLineBreaker.Line line = lines.get(li);
			final double lineExtent = lineExtents[li];
			for (int k = line.from(); k < line.to(); ++k) {
				final FlexItemContent item = this.items.get(seq[k]);
				// align-self:auto→align-items合成(§9.6。baselineはパーサ
				// 段階で不受理=宣言無効)
				final net.zamasoft.foliojet.layout.box.params.BoxAlignment align = net.zamasoft.foliojet.layout.box.params.BoxAlignment
						.resolve(item.spec.alignSelf(), params.alignItems);
				// cross軸auto marginはalign-self/stretchより先(§8.1、F3e):
				// start側autoで終端寄せ、両側autoで中央
				final boolean crossStartAuto = crossMarginAuto(item, false);
				final boolean crossEndAuto = crossMarginAuto(item, true);
				if (crossStartAuto || crossEndAuto) {
					final double freeCross = Math.max(0,
							lineExtent - item.itemBox.getPageExtent(params.flow));
					final double autoOffset = crossStartAuto && crossEndAuto ? freeCross / 2
							: crossStartAuto ? freeCross : 0;
					this.flexBox.getContainer().addFlow(item.itemBox, crossCursor + autoOffset);
					continue;
				}
				double crossOffset = 0;
				if (align == net.zamasoft.foliojet.layout.box.params.BoxAlignment.STRETCH) {
					// cross autoのitemだけ行高まで伸長——takeover設計により
					// authoredの背景・枠がそのまま追随する(F1dの狙い)
					final BlockParams itemParams = item.itemBox.getBlockParams();
					final boolean vertical = itemParams.flow.isVertical();
					if ((vertical ? itemParams.size.getWidthType() : itemParams.size.getHeightType()) == net.zamasoft.foliojet.layout.box.params.LengthType.AUTO) {
						final double deficit = lineExtent - item.itemBox.getPageExtent(params.flow);
						if (deficit > 0) {
							item.itemBox.setPageAxis(
									item.itemBox.getInnerPageExtent(params.flow) + deficit);
						}
					}
				} else {
					final double freeCross = lineExtent - item.itemBox.getPageExtent(params.flow);
					crossOffset = align == net.zamasoft.foliojet.layout.box.params.BoxAlignment.CENTER
							? Math.max(0, freeCross / 2)
							: align == net.zamasoft.foliojet.layout.box.params.BoxAlignment.END
									? Math.max(0, freeCross)
									: 0;
				}
				this.flexBox.getContainer().addFlow(item.itemBox, crossCursor + crossOffset);
			}
			crossCursor += lineExtent + (li < lines.size() - 1 ? betweenCross : 0);
		}
		this.flexBox.setPageAxis(this.items.isEmpty() ? 0 : Math.max(content, crossCursor));
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

	/** 主軸(行方向)marginがautoかを返します(F3e。end=進行方向の後側)。 */
	private static boolean mainMarginAuto(final FlexItemContent item, final boolean end) {
		final net.zamasoft.foliojet.layout.box.params.Insets margin = item.itemBox.getBlockParams().frame.margin;
		// F1系は横書きrowのみ(eligible)——主軸=left/right
		return (end ? margin.getRightType() : margin.getLeftType()) == net.zamasoft.foliojet.layout.box.params.LengthType.AUTO;
	}

	/** cross軸marginがautoかを返します(F3e)。 */
	private static boolean crossMarginAuto(final FlexItemContent item, final boolean end) {
		final net.zamasoft.foliojet.layout.box.params.Insets margin = item.itemBox.getBlockParams().frame.margin;
		return (end ? margin.getBottomType() : margin.getTopType()) == net.zamasoft.foliojet.layout.box.params.LengthType.AUTO;
	}

	/**
	 * 視覚順(order昇順、同値は録画順の安定ソート——§5.4)のindex列です
	 * (F5a)。行分割・§9.7・配置は視覚順、bindはソース順(Tagged PDFの
	 * 読み順・構造をソース順に保つ——答申F5a)。
	 */
	private int[] visualOrder() {
		final Integer[] seq = new Integer[this.items.size()];
		for (int i = 0; i < seq.length; ++i) {
			seq[i] = i;
		}
		java.util.Arrays.sort(seq, (x, y) -> Integer.compare(this.items.get(x).spec.order(),
				this.items.get(y).spec.order()));
		final int[] result = new int[seq.length];
		final boolean reversed = this.flexBox.getFlexParams().flexDirection.isReverse();
		for (int i = 0; i < seq.length; ++i) {
			// F5b: reverse主軸は視覚並びを反転(行分割§9.3も主軸順で
			// 収集される。justify側の反転はmapperのtoFlexJustify)
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
