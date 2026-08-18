package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;
import net.zamasoft.foliojet.layout.sizing.BasicGridTrackSizing;
import net.zamasoft.foliojet.layout.sizing.FixedGridLayout;
import net.zamasoft.foliojet.layout.sizing.GridPlacementResolver;
import net.zamasoft.foliojet.layout.sizing.Sizing;

/**
 * Gridの構築coordinatorです(Grid G1b、2026-07-31——
 * consult-codex-2026-07-31-grid-g1.txt §1)。{@code TableBuilder}と同じく
 * {@code DocumentBuilder.builderStack}に積まれるが{@code Builder}ではない。
 * 直接子ごとに固定幅の{@link GridItemBox}+item builderを開き、
 * Grid終端で{@link FixedGridLayout}の結果に従って配置する。
 *
 * <p>
 * G3a(consult-codex-2026-07-31-grid-g3.txt): itemの本文は
 * {@link TwoPassBlockBuilder}で録画し、Grid終端に「幅確定→bind→
 * 行高計測→配置」の順で組む。固定列では幅は構築時から不変のため
 * 結果はG1(直接構築)と同一——固有寸法スナップショットは
 * auto/fr列(G3b/c)の下準備。副作用: TwoPass内のGridは不活性の
 * ため、item内へネストしたGridはG0(単一列)へ退行する
 * (G3d1のGridEventで回復予定)。
 * </p>
 *
 * <p>
 * G3d1(RetainedGrid化): 宿主がBlockBuilderなら{@code finish()}→
 * {@code addGrid}が即時{@link #bind}を呼ぶ。TwoPass宿主(幅なし
 * float等)では録画の{@code GridEvent}に保持され、幅確定後の
 * records bindで同じ{@link #bind}を通る——両経路の幾何は単一実装。
 * 固有寸法contribution({@link #getIntrinsicSizes})はG3d2。
 * </p>
 *
 * <p>
 * サブセット: 列はfixed/auto/fr・行はauto(行内item実高の最大)・
 * source-order row auto-placement。適格判定は
 * {@link GridBuilderLifecycle#eligible}。
 * </p>
 */
public final class GridBuilder
		implements net.zamasoft.foliojet.layout.builder.RetainedGrid, net.zamasoft.foliojet.layout.builder.ItemCoordinator {

	/** 録画されたitem数(空匿名破棄を除く)。bind数と一致すること。 */
	public static final AtomicLong GRID_ITEM_RECORDS = new AtomicLong();

	/** bindされたitem数。 */
	public static final AtomicLong GRID_ITEM_BINDS = new AtomicLong();

	/** 空の匿名itemを破棄した数(slot非消費)。 */
	public static final AtomicLong GRID_ITEM_EMPTY_ANON_DROPS = new AtomicLong();

	/**
	 * 明示配置が未対応でcontainer単位のsource-order配置へ戻した数
	 * (G4b——silent capの禁止。1件だけauto化しない=答申Q5)。
	 */
	public static final AtomicLong GRID_PLACEMENT_FALLBACKS = new AtomicLong();

	private final Builder host;

	/** item builderの親LayoutStack({@code host}と同一インスタンス)。 */
	private final LayoutStack hostStack;

	private final GridBox gridBox;

	/** 列テンプレート(fixed/auto。frは適格判定で除外——G3c)。 */
	private final List<GridTrackListValue.TrackSize> tracks;

	private final double columnGap, rowGap;

	private final List<GridItemContent> items = new ArrayList<>();

	/** 開いているitemのbuilder(elementまたは匿名)。閉じているときnull。 */
	private TwoPassBlockBuilder openItemBuilder;

	private GridItemBox openItemBox;

	/** 開いているitemが匿名(直接テキスト)か。 */
	private boolean openItemAnonymous;

	/** 開いているitemの明示配置指定(G4a)。 */
	private GridItemSpec openItemSpec = GridItemSpec.AUTO;

	GridBuilder(final Builder host, final GridBox gridBox) {
		this.host = host;
		this.hostStack = (LayoutStack) host;
		this.gridBox = gridBox;
		final GridParams params = gridBox.getGridParams();
		// template無しは暗黙の単一autoカラム(2026-08-09、
		// GridBuilderLifecycle.eligible参照)
		this.tracks = params.templateColumns.isEmpty()
				? List.of(net.zamasoft.foliojet.css.value.GridTrackListValue.Auto.INSTANCE)
				: params.templateColumns;
		this.columnGap = params.columnGap;
		this.rowGap = params.rowGap;
	}

	public boolean hasOpenElementItem() {
		return this.openItemBuilder != null && !this.openItemAnonymous;
	}

	public boolean hasOpenItem() {
		return this.openItemBuilder != null;
	}

	/**
	 * 合成itemのparams(Gridの文字属性を継承し、frame等は中立へ戻す)。
	 * G3a追補(答申Q1): Grid本体のwidth/min/max-widthがitemの固有寸法へ
	 * 混入しないようsize系も中立化する。
	 */
	private BlockParams itemParams() {
		final BlockParams params = BlockParamsTemplate.freeze(this.gridBox.getGridParams()).materialize();
		params.frame = RectFrame.NULL_FRAME;
		params.element = null;
		params.footnoteId = -1;
		// **コンテナの実効opacityを引き継ぐ**(2026-08-18)。以前は1fへ
		// 戻していたが、visibility:hiddenはopacity 0へ写像される
		// (BoxStyleMapper.setupParams)ため、hiddenなコンテナの匿名・
		// 中立itemだけが描かれてしまう——e-Statのドロップダウンメニューが
		// 本文に重なって出た実欠陥(重なり1,462対)。authored itemは
		// 自分のstyleからvisibilityを継承するので元から正しい。
		params.opacity = this.gridBox.getGridParams().opacity;
		params.zIndexType = Params.Z_INDEX_AUTO;
		params.zIndexValue = 0;
		params.transform = new AffineTransform();
		params.columns = Columns.NONE_COLUMNS;
		params.size = Dimension.AUTO_DIMENSION;
		params.minSize = Dimension.ZERO_DIMENSION;
		params.maxSize = Dimension.AUTO_DIMENSION;
		return params;
	}

	/** 次のitem(element用)を開きます。返るbuilderを積むのは呼び出し側。 */
	public TwoPassBlockBuilder startElementItem(final GridItemSpec spec) {
		return this.startItem(false, spec);
	}

	/** 直接テキスト用の匿名itemを開きます(開いていれば再利用)。 */
	public TwoPassBlockBuilder requireAnonymousItem() {
		if (this.openItemBuilder != null && this.openItemAnonymous) {
			return null; // 既に開いている(積み直し不要)
		}
		return this.startItem(true, GridItemSpec.AUTO);
	}

	private TwoPassBlockBuilder startItem(final boolean anonymous, final GridItemSpec spec) {
		assert this.openItemBuilder == null : "前のitemが閉じられていない";
		// 幅は暫定(auto列は未解決)。録画・計測は幅非依存で、確定幅は
		// finish()のbind直前にsetTrackWidthで入る(G3b)
		final GridItemBox itemBox = new GridItemBox(this.itemParams(), new FlowPos(), 0);
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this.hostStack, itemBox);
		// census origin分離(2026-08-01): grid item由来のrecords bindを
		// TOPLEVELから分ける(診断の健全化。records bind運用自体は
		// 恒久的に正当な終着形——LegacyBindOrigin.GRID_ITEMのjavadoc参照)
		builder.tagLegacyBindOrigin(
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin.GRID_ITEM);
		this.openItemBuilder = builder;
		this.openItemBox = itemBox;
		this.openItemAnonymous = anonymous;
		this.openItemSpec = spec;
		return builder;
	}

	/**
	 * 開いているitemを確定します(録画完了点)。空の匿名item(空白のみ等)は
	 * slotを消費させず破棄する。合成itemはLayoutSource非記録
	 * (sourceAnchor=-1)のためrange sealは行わない——本文はGrid終端の
	 * bindまでLegacyRecordsのまま(答申Q1)。
	 */
	public void itemClosed() {
		final TwoPassBlockBuilder builder = this.openItemBuilder;
		final GridItemBox itemBox = this.openItemBox;
		final boolean anonymous = this.openItemAnonymous;
		final GridItemSpec spec = this.openItemSpec;
		this.openItemBuilder = null;
		this.openItemBox = null;
		this.openItemAnonymous = false;
		this.openItemSpec = GridItemSpec.AUTO;
		if (anonymous && builder.hasEmptyRecordedBody() && !itemBox.paintsAnything()) {
			GRID_ITEM_EMPTY_ANON_DROPS.incrementAndGet();
			return;
		}
		GRID_ITEM_RECORDS.incrementAndGet();
		this.items.add(new GridItemContent(itemBox, builder, builder.getIntrinsicSizes(), anonymous, spec));
	}

	/**
	 * Grid終端です(G3d1): 実行計画としてホストへ渡す。BlockBuilderは
	 * 即時{@link #bind}、TwoPassは録画のGridEventに保持して幅確定後に
	 * bindする。
	 */
	public void finish() {
		assert this.openItemBuilder == null : "item未クローズでGrid終端に到達";
		this.host.addGrid(this);
	}

	@Override
	public net.zamasoft.foliojet.layout.box.IBox getItemHostBox() {
		return this.gridBox;
	}

	@Override
	public GridBox getGridBox() {
		return this.gridBox;
	}

	/** 確定済み配置plan(G4b——getIntrinsicSizesとbindが必ず共有する)。 */
	private GridPlacementResolver.Plan placementPlan;

	/**
	 * 配置planを一度だけ確定します(G4b、答申Q3)。明示配置が未対応
	 * (implicit column・負行・上限超過)またはrowSpan&gt;1(G4d予定)の
	 * ときはcontainer単位でG3のsource-order配置(col=i%n、row=i/n)へ
	 * 戻す——1件だけauto化するとoccupancy/cursor経由で後続全itemが
	 * ずれるため(答申Q5の最重要則)。
	 */
	private GridPlacementResolver.Plan placementPlan() {
		if (this.placementPlan != null) {
			return this.placementPlan;
		}
		final int n = this.tracks.size();
		final List<net.zamasoft.foliojet.layout.box.params.GridItemSpec> specs = new ArrayList<>(this.items.size());
		for (final GridItemContent item : this.items) {
			specs.add(item.spec);
		}
		GridPlacementResolver.Plan plan = null;
		if (GridPlacementResolver.resolve(specs, n) instanceof GridPlacementResolver.Result.Resolved resolved) {
			plan = resolved.plan(); // rowSpanはGridRowSizingの不足分配で対応(G4d)
		}
		if (plan == null) {
			GRID_PLACEMENT_FALLBACKS.incrementAndGet();
			final GridPlacementResolver.GridArea[] areas = new GridPlacementResolver.GridArea[this.items.size()];
			for (int i = 0; i < areas.length; ++i) {
				areas[i] = new GridPlacementResolver.GridArea(i % n, i / n, 1, 1);
			}
			plan = new GridPlacementResolver.Plan(List.of(areas), n, (areas.length + n - 1) / n);
		}
		this.placementPlan = plan;
		return plan;
	}

	/** 全itemがrowSpan=1か(G6行分割の適格条件)。 */
	private static boolean allSingleRowSpan(final GridPlacementResolver.Plan plan, final int count) {
		for (int i = 0; i < count; ++i) {
			if (plan.areas().get(i).rowSpan() != 1) {
				return false;
			}
		}
		return true;
	}

	/** ソース順のままで行番号が非減少か(G6)。 */
	private static boolean isRowMajor(final GridPlacementResolver.Plan plan, final int count) {
		int prevRow = -1;
		for (int i = 0; i < count; ++i) {
			final int r = plan.areas().get(i).row();
			if (r < prevRow) {
				return false;
			}
			prevRow = r;
		}
		return true;
	}

	/** いずれかのitem同士がグリッド領域で重なるか(G6——重なりがあれば
	 * flow登録順の並べ替えが描画順=文書順の仕様を壊すため対象外)。 */
	private static boolean hasOverlap(final GridPlacementResolver.Plan plan, final int count) {
		for (int a = 0; a < count; ++a) {
			final GridPlacementResolver.GridArea x = plan.areas().get(a);
			for (int b = a + 1; b < count; ++b) {
				final GridPlacementResolver.GridArea y = plan.areas().get(b);
				final boolean rowsMeet = x.row() < y.row() + y.rowSpan() && y.row() < x.row() + x.rowSpan();
				final boolean colsMeet = x.column() < y.column() + y.columnSpan()
						&& y.column() < x.column() + x.columnSpan();
				if (rowsMeet && colsMeet) {
					return true;
				}
			}
		}
		return false;
	}

	/** planに基づく各itemの列contributionです(G4d——span込み)。 */
	private List<BasicGridTrackSizing.ItemContribution> columnContributions(
			final GridPlacementResolver.Plan plan) {
		final List<BasicGridTrackSizing.ItemContribution> contributions = new ArrayList<>(this.items.size());
		for (int i = 0; i < this.items.size(); ++i) {
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			final GridItemContent item = this.items.get(i);
			contributions.add(new BasicGridTrackSizing.ItemContribution(area.column(), area.columnSpan(),
					item.sizes.minContent(), item.sizes.maxContent()));
		}
		return contributions;
	}

	/**
	 * Grid全体のcontent-box固有寸法contributionです(G3d2、答申Q2/G3d2)。
	 * 行方向: min=gap+Σ(fixed長|列内item min-contentの最大)、
	 * max=gap+Σ(fixed長|列内item max-contentの最大)(auto/frとも——
	 * frのmax-content contributionは内容由来)。ページ方向minは
	 * 行ごとのitem minPage最大の合計+rowGap。frameは含めない
	 * (計測器の通常経路が一度だけ加算する)。
	 */
	@Override
	public IntrinsicSizes getIntrinsicSizes() {
		final GridPlacementResolver.Plan plan = this.placementPlan();
		final BasicGridTrackSizing.Intrinsics line = BasicGridTrackSizing.intrinsics(this.tracks,
				this.columnContributions(plan), this.columnGap);
		boolean columnInflated = false;
		final double[] rowMinPage = new double[Math.max(1, plan.rowCount())];
		for (int i = 0; i < this.items.size(); ++i) {
			final GridItemContent item = this.items.get(i);
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			// rowSpanは各行へ均等の近似(不足分配の粗い相当——bind後の
			// 実高解決はGridRowSizingが正確に行う)
			final double perRow = item.sizes.minPage() / area.rowSpan();
			for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
				rowMinPage[r] = Math.max(rowMinPage[r], perRow);
			}
			columnInflated |= item.sizes.columnInflated();
		}
		double minPage = plan.rowCount() > 1 ? this.rowGap * (plan.rowCount() - 1) : 0;
		for (int r = 0; r < rowMinPage.length; ++r) {
			minPage += rowMinPage[r];
		}
		return new IntrinsicSizes(line.min(), line.max(), minPage, columnInflated);
	}

	@Override
	public void abandonForParentRange() {
		// 親rangeの範囲再生がGrid全体を再構築する(G3d3)。item録画への
		// 参照を手放すだけでよい——合成itemはLayoutSource非記録のため
		// リースを持たない(item本文内のseal済み子リースは検証相で
		// 列挙され、親のコミット相がsubsumeで解放する)
		this.items.clear();
	}

	/**
	 * 親range化の検証相です(Grid G3d3——consult-codex-2026-07-31-grid-g3.txt
	 * Q3のG3d3、副作用なし)。全itemの本文を通常のネストビルダーとして
	 * 検証・列挙する。itemの本文がseal済み子(float等)のリースを含む
	 * 場合も、この再帰で親範囲への包含が証明される。bind済みのGridは
	 * 吸収不可(構造的に到達しないがfail closed)。
	 */
	boolean collectAbsorbableItems(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId,
			final long toId, final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		if (this.bound) {
			return false;
		}
		for (final GridItemContent item : this.items) {
			if (!item.body.collectAbsorbableSelf(log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		return true;
	}

	/** bindは一度きり(二重bindはLegacyRecordsのlive box変異——答申Q5)。 */
	private boolean bound;

	/**
	 * Gridの組み立てです(G3a: 幅確定→bind→行高計測→配置の四段)。
	 * 全itemをトラック座標でGridコンテナへ追加し、Grid内高とホストflow
	 * カーソルを同期する(独立item builderは親カーソルを進めないため、
	 * 同期しないと後続ブロックが重なる——G1答申の補正点)。ホストの
	 * active flowが当のGridBoxである間に呼ぶこと(liveはDocumentBuilder
	 * のFLOW終端、records bindはStartFlow(GridBox)とEndFlowの間)。
	 */
	@Override
	public void bind(final Builder hostBuilder) {
		assert !this.bound : "Gridの二重bind";
		this.bound = true;
		// 原子契約の有効化(G6): トラック配置が走らないG0退行gridは
		// 原子扱いしない(PageAtomicBox.isPageAtomicNow参照)
		this.gridBox.markTrackLayout();
		final BlockBuilder target = (BlockBuilder) hostBuilder;
		final GridParams params = this.gridBox.getGridParams();
		final GridPlacementResolver.Plan plan = this.placementPlan();
		// トラック幅解決(G3b/c): planに基づく列contribution(G4b)から、
		// fixed=指定長・auto=base/growth limit+stretch・fr=find-frで
		// 確定する。基準幅はGridコンテナのcontent-box行幅
		// (TwoPass経由ではshrink-to-fit確定後の幅)
		// G5c: justify-contentのused value。positional(start/center/end)の
		// ときauto列の残余stretchを止め、残余をトラック群のoffsetへ回す
		final BoxAlignment justifyContent = BoxAlignment.resolve(BoxAlignment.AUTO, params.justifyContent);
		final double[] widths = BasicGridTrackSizing.resolve(this.tracks, this.columnContributions(plan),
				this.gridBox.getLineSize(), this.columnGap, justifyContent == BoxAlignment.STRETCH);
		final FixedGridLayout layout = new FixedGridLayout(widths, this.columnGap, this.rowGap);
		double trackLineExtent = this.columnGap * (this.tracks.size() - 1);
		for (final double w : widths) {
			trackLineExtent += w;
		}
		final double freeLine = Math.max(0, this.gridBox.getLineSize() - trackLineExtent);
		final double contentX = justifyContent == BoxAlignment.CENTER ? freeLine / 2
				: justifyContent == BoxAlignment.END ? freeLine : 0;
		// G5b: itemごとのjustify used value・bind幅・行方向オフセットを
		// bind前に全件確定する(途中bind後のフォールバックは不可能——
		// 答申Q3)。stretch=area幅(現行)、start/center/end=fit-content幅
		// (min-content床——max(min, min(area, max)))+余白×{0,0.5,1}。
		// 負余白は0へ丸める(印刷向けsafe: start側overflow)
		final int count = this.items.size();
		final double[] itemWidths = new double[count];
		final double[] itemXOffsets = new double[count];
		final BoxAlignment[] aligns = new BoxAlignment[count];
		for (int i = 0; i < count; ++i) {
			final GridItemContent item = this.items.get(i);
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			double areaWidth = this.columnGap * (area.columnSpan() - 1);
			for (int c = area.column(); c < area.column() + area.columnSpan(); ++c) {
				areaWidth += widths[c];
			}
			final BoxAlignment justify = BoxAlignment.resolve(item.spec.justifySelf(), params.justifyItems);
			aligns[i] = BoxAlignment.resolve(item.spec.alignSelf(), params.alignItems);
			if (justify == BoxAlignment.STRETCH) {
				itemWidths[i] = areaWidth;
			} else {
				itemWidths[i] = Sizing.fitContent(item.sizes.minContent(), item.sizes.maxContent(), areaWidth);
				final double free = Math.max(0, areaWidth - itemWidths[i]);
				itemXOffsets[i] = justify == BoxAlignment.CENTER ? free / 2
						: justify == BoxAlignment.END ? free : 0;
			}
			assert itemWidths[i] >= 0 && !Double.isNaN(itemWidths[i]) : "不正なitem幅: " + itemWidths[i];
		}
		// 幅確定→本文bind。PageAtomicBox契約によりGrid flowがactiveな間に
		// 全bindが完了する(ページbreakは走らない)
		final double[] extents = new double[count];
		for (int i = 0; i < count; ++i) {
			final GridItemContent item = this.items.get(i);
			item.bind(target, itemWidths[i]);
			GRID_ITEM_BINDS.incrementAndGet();
			extents[i] = item.itemBox.getPageExtent(params.flow);
		}
		// 行高解決(G4d: rowSpanの不足分配込み——GridRowSizing。
		// 空行は高さ0だが隣接rowGapは残る=仕様のgutter挙動)
		final double[] rowHeights = net.zamasoft.foliojet.layout.sizing.GridRowSizing.resolve(plan.areas(),
				extents, plan.rowCount(), this.rowGap);
		// G5e: align-content——明示高Gridの余白。content distributionが先、
		// item self alignmentは後(調整後の行高を参照する)
		double trackPageExtent = plan.rowCount() > 1 ? this.rowGap * (plan.rowCount() - 1) : 0;
		for (final double h : rowHeights) {
			trackPageExtent += h;
		}
		double contentY = 0;
		if (!this.items.isEmpty()) {
			this.gridBox.setPageAxis(trackPageExtent);
			final double freePage = Math.max(0,
					this.gridBox.getInnerPageExtent(params.flow) - trackPageExtent);
			if (freePage > 0) {
				final BoxAlignment alignContent = BoxAlignment.resolve(BoxAlignment.AUTO, params.alignContent);
				if (alignContent == BoxAlignment.STRETCH) {
					// 現サブセットの行は全implicit auto——均等分配(空行も対象)
					final double share = freePage / rowHeights.length;
					for (int r = 0; r < rowHeights.length; ++r) {
						rowHeights[r] += share;
					}
				} else {
					contentY = alignContent == BoxAlignment.CENTER ? freePage / 2
							: alignContent == BoxAlignment.END ? freePage : 0;
				}
			}
		}
		final double[] rowStarts = new double[rowHeights.length];
		double cursor = contentY;
		for (int r = 0; r < rowHeights.length; ++r) {
			rowStarts[r] = cursor;
			cursor += rowHeights[r];
			if (r < rowHeights.length - 1) {
				cursor += this.rowGap;
			}
		}
		// G5d: align used valueによるページ方向オフセット(areaは
		// span行群+内側gap。stretchは現行互換の上詰め近似——真の
		// used-height stretchは後続。負余白は0へ丸める)
		// 行分割(G6)の適格判定と、必要なら行優先へのflow登録順の並べ替え。
		// 帳簿(GridBox.Row)はflow一覧の連続範囲で行を表すため、行優先順で
		// 登録されている必要がある。ソース順が行優先でない明示配置
		// (gigazine.netの.content——grid-rowで先頭へピン留めされたitemが
		// DOM後方に来る)は、**itemの重なりが無い場合に限り**行優先へ
		// 並べ替える。行内はソース順を保つ安定ソート——重なるitemの描画順は
		// CSS仕様で文書順のため、重なりのあるgridは並べ替えず従来どおり
		// atomicへ落とす(explicit-overlapの回帰を守る)
		final boolean ledgerEligible = !this.items.isEmpty() && contentY == 0 && !params.flow.isVertical()
				&& allSingleRowSpan(plan, count);
		Integer[] order = new Integer[count];
		for (int i = 0; i < count; ++i) {
			order[i] = i;
		}
		boolean rowMajor = ledgerEligible && isRowMajor(plan, count);
		if (ledgerEligible && !rowMajor && !hasOverlap(plan, count)) {
			java.util.Arrays.sort(order,
					java.util.Comparator.comparingInt(i -> plan.areas().get(i).row()));
			rowMajor = true;
		}
		final double[] itemPageEnds = new double[count];
		for (int idx = 0; idx < count; ++idx) {
			final int i = order[idx];
			final GridItemBox itemBox = this.items.get(i).itemBox;
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			itemBox.setGridLineOffset(contentX + layout.columnStart(area.column()) + itemXOffsets[i]);
			double areaHeight = this.rowGap * (area.rowSpan() - 1);
			for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
				areaHeight += rowHeights[r];
			}
			final double free = Math.max(0, areaHeight - extents[i]);
			final double yOffset = aligns[i] == BoxAlignment.CENTER ? free / 2
					: aligns[i] == BoxAlignment.END ? free : 0;
			this.gridBox.getContainer().addFlow(itemBox, rowStarts[area.row()] + yOffset);
			// 行分割(G6)のslack判定用: 行開始からのitem実端
			itemPageEnds[i] = yOffset + extents[i];
		}
		this.gridBox.setPageAxis(this.items.isEmpty() ? 0 : cursor);
		// **改ページ用の行境界の記録**(2026-08-10、G6行分割——
		// FlexBuilder.placeRowと同型)。対象はflow順(=ソース順)が行優先で
		// 連続し、全itemがrowSpan=1、書字が水平、align-contentの先頭余白が
		// 無い構成だけ。帳簿を付けなければGridBox.splitは呼ばれず、従来
		// どおりPageAtomicBoxのatomic経路(丸ごと送り/visual rescue)に
		// 落ちるので、ゲートに引っかかっても今より悪くならない
		if (rowMajor) {
			final java.util.List<GridBox.Row> gridRows = new ArrayList<>();
			final java.util.List<GridItemBox> gridRowItems = new ArrayList<>(count);
			int rowStartFlow = 0;
			double itemsEnd = 0;
			int currentRow = plan.areas().get(order[0]).row();
			for (int idx = 0; idx < count; ++idx) {
				final int i = order[idx];
				final int r = plan.areas().get(i).row();
				if (r != currentRow) {
					gridRows.add(new GridBox.Row(rowStartFlow, idx - rowStartFlow, rowStarts[currentRow],
							rowHeights[currentRow], itemsEnd));
					rowStartFlow = idx;
					itemsEnd = 0;
					currentRow = r;
				}
				itemsEnd = Math.max(itemsEnd, itemPageEnds[i]);
				gridRowItems.add(this.items.get(i).itemBox);
			}
			gridRows.add(new GridBox.Row(rowStartFlow, count - rowStartFlow, rowStarts[currentRow],
					rowHeights[currentRow], itemsEnd));
			this.gridBox.setGridRows(gridRows, gridRowItems);
		}
		final LayoutContext.Flow active = target.getFlow();
		assert active.box == this.gridBox : "Grid bindでactive flowがGridではない: " + active.box;
		target.setPageAxis(active.pageAxis + this.gridBox.getInnerPageExtent(params.flow));
	}
}
