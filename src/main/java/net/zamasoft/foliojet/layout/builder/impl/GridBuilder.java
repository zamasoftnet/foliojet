package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.box.impl.RowContributionSink;
import net.zamasoft.foliojet.layout.box.impl.RowGeometryFinalizer;
import net.zamasoft.foliojet.layout.box.impl.RowSubgridLink;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.LengthType;
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
import net.zamasoft.foliojet.layout.sizing.GridRowSizing;
import net.zamasoft.foliojet.layout.sizing.Sizing;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

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
 * サブセット: 列はfixed/auto/fr/minmax等・行はauto(行内item実高の最大)
 * または固定高・source-order row auto-placement。適格判定は
 * {@link GridBuilderLifecycle#eligible}。
 * </p>
 *
 * <p>
 * <b>subgrid(css-grid-2、2026-08-29/09-03)</b>: {@code grid-template-columns:
 * subgrid}のgridは、bind時に自分が直下にあるitem({@link GridItemBox})から
 * 親gridの跨ぐ列の解決済み幅・gap・線名を受け取り({@link #resolveSubgrid})、
 * それを固定トラックとして使う。行軸は子孫itemの寄与を親へ逆流させ、
 * 親の行解決後に子の配置・寸法・行台帳を最終化する。子自身は親itemで
 * 常にstretchし、指定高とalign-self/align-contentは無視する。自分の
 * border/padding/marginは端の寄与と使用可能な行高へ反映する。
 * <b>単一autoトラックの近似へ落ちる場合</b>: (a)子gridの固有寸法計測
 * ({@link #getIntrinsicSizes}——録画時、親のトラック解決前。親への
 * contributionだけがこの近似で、最終配置は本物のトラックで行う)、
 * (b)子gridがitemの直下でない(divで包まれている、itemが匿名=直接テキスト、
 * inline化されている等: bind時のhost builderのcontext flowがGridItemBoxで
 * ないか、間に別のflowがある)、(c)親gridがトラック配置を走らせていない
 * (縦書きのG0退行)、(d)親側の列解決が無い経路(親が{@code display:grid}で
 * ないのに{@code subgrid}を書いた場合——仕様でも{@code none}相当)。
 * 縦書きは適格判定でG0へ退行するため対象外。row subgrid内の並列注はbind
 * 時点のページ位置から後で動かせないため、2823で検出して元の位置へ残す。
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

	/**
	 * 列トラック(fixed/auto/fr/%/min-content/max-content)。
	 * {@link #placementPlan}で確定する——auto-repeatの展開・暗黙列の
	 * 補完・auto-fitの末尾潰しを含む(2026-08-29)。それまでは
	 * テンプレートそのまま(%は{@link #sizingTracks}が解決する)。
	 */
	private List<GridTrackListValue.TrackSize> tracks;

	/** 列軸の線名表(zero-based線index→名前。areasの暗黙名込み。2026-08-29)。 */
	private List<List<String>> columnLines = List.of();

	/** 行軸の線名表(zero-based線index→名前。areasの暗黙名・暗黙行込み)。 */
	private List<List<String>> rowLines = List.of();

	/** 明示行数(grid-template-rowsとgrid-template-areasの大きいほう。2026-08-29)。 */
	private int explicitRows;

	/** 展開後の明示列数(grid-auto-columnsの周期の基準。2026-08-29)。 */
	private int explicitColumns;

	/** {@link #placementPlan}を確定したときのコンテナ行幅(auto-repeatの再展開判定用)。 */
	private double planAvailable = Double.NaN;

	private double columnGap, rowGap;

	/**
	 * subgridで親から継いだ列トラック(bind時に{@link #resolveSubgrid}が
	 * 設定。nullなら通常のテンプレート)。
	 */
	private List<GridTrackListValue.TrackSize> subgridColumns;

	/** {@link #subgridColumns}の線名(列数+1要素。親の線名+自分の{@code subgrid [a]}名)。 */
	private List<List<String>> subgridColumnLines;

	/** subgridで親から継いだ行トラック。nullなら通常の行テンプレート。 */
	private List<GridTrackListValue.TrackSize> subgridRows;

	/** {@link #subgridRows}の線名(行数+1要素)。 */
	private List<List<String>> subgridRowLines;

	/** 親row subgridとの一回限りの接続。nullなら通常grid。 */
	private RowSubgridLink rowSubgridLink;

	/** {@link #rowSubgridLink}を所有する合成item。最終寸法もここへ書く。 */
	private GridItemBox rowSubgridOwner;

	/** 親gapとの差の半分。子itemの内側辺へ寄与・配置ともに適用する。 */
	private double rowSubgridGapShim;

	/** 構築時にこのGridの部分木でpage-margin-noteを見つけたか。 */
	private boolean containsPageMarginNote;

	/** 子row subgridの最終化登録です(座標はこのGridのローカル行)。 */
	private record PendingRowFinalizer(int rowStart, int span, RowGeometryFinalizer finalizer) {
	}

	private final List<GridItemContent> items = new ArrayList<>();

	/** 開いているitemのbuilder(elementまたは匿名)。閉じているときnull。 */
	private TwoPassBlockBuilder openItemBuilder;

	private GridItemBox openItemBox;

	private double openItemMinCap = -1;

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
		// コンテナのalign-contentを引き継がない(2026-08-29)。itemの箱は
		// コンテナのparamsから作るので、そのままではコンテナの
		// align-content: centerがitem自身の内容整列として効いてしまう。
		// itemが行の高さまで伸びるようになって表面化した(Chromeでは
		// itemは30ptへ伸びるが中身は上端のまま)
		params.blockAlignContent = BoxAlignment.NORMAL;
		params.size = Dimension.AUTO_DIMENSION;
		params.minSize = Dimension.ZERO_DIMENSION;
		params.maxSize = Dimension.AUTO_DIMENSION;
		return params;
	}

	/** takeover元のauthored box(endBoxの対応付け用。中立/匿名itemではnull)。 */
	private net.zamasoft.foliojet.layout.box.impl.FlowBlockBox openItemSource;

	/**
	 * {@code box}を元とするtakeover element itemが開いているかを返します
	 * (2026-08-29、G7——FlexBuilder.isElementItemSourceと同型)。
	 */
	public boolean isElementItemSource(final net.zamasoft.foliojet.layout.box.IBox box) {
		return this.openItemSource != null && this.openItemSource == box;
	}

	/**
	 * <b>takeover</b>でelement itemを開きます(2026-08-29、G7)。
	 *
	 * <p>
	 * authoredなブロックのparams/posを{@link GridItemBox}へ引き継ぎ、
	 * <b>元の外箱は構築しない</b>——itemがauthoredな箱そのものになるので、
	 * 行高までのstretchに背景・枠が追随する。包み箱のままではstretchが
	 * 中の子へ届かず、{@code grid-template-rows}や{@code grid-row: span}が
	 * 「効かない」ように見えていた(利用者報告A-7)。Flexが同じ形で先に
	 * 解いている({@link FlexBuilder#startElementItem})。
	 * </p>
	 */
	public TwoPassBlockBuilder startElementItem(final net.zamasoft.foliojet.layout.box.impl.FlowBlockBox source,
			final GridItemSpec spec, final double minContributionCap) {
		final GridItemBox itemBox = new GridItemBox(source.getBlockParams(), (FlowPos) source.getPos(), 0);
		itemBox.setTakeover(true);
		final TwoPassBlockBuilder builder = this.startItem(itemBox, false, spec, minContributionCap);
		this.openItemSource = source;
		return builder;
	}

	/** 次のitem(element用)を開きます。返るbuilderを積むのは呼び出し側。 */
	public TwoPassBlockBuilder startElementItem(final GridItemSpec spec) {
		return this.startItem(false, spec, -1);
	}

	/**
	 * min-content寄与の上限つきでelement itemを開きます
	 * ({@link GridItemContent#minContributionCap}参照。負=無制限)。
	 */
	public TwoPassBlockBuilder startElementItem(final GridItemSpec spec, final double minContributionCap) {
		return this.startItem(false, spec, minContributionCap);
	}

	/** 直接テキスト用の匿名itemを開きます(開いていれば再利用)。 */
	public TwoPassBlockBuilder requireAnonymousItem() {
		if (this.openItemBuilder != null && this.openItemAnonymous) {
			return null; // 既に開いている(積み直し不要)
		}
		return this.startItem(true, GridItemSpec.AUTO, -1);
	}

	private TwoPassBlockBuilder startItem(final boolean anonymous, final GridItemSpec spec,
			final double minContributionCap) {
		// 幅は暫定(auto列は未解決)。録画・計測は幅非依存で、確定幅は
		// finish()のbind直前にsetTrackWidthで入る(G3b)
		return this.startItem(new GridItemBox(this.itemParams(), new FlowPos(), 0), anonymous, spec,
				minContributionCap);
	}

	private TwoPassBlockBuilder startItem(final GridItemBox itemBox, final boolean anonymous,
			final GridItemSpec spec, final double minContributionCap) {
		assert this.openItemBuilder == null : "前のitemが閉じられていない";
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this.hostStack, itemBox);
		// census origin分離(2026-08-01): grid item由来のrecords bindを
		// TOPLEVELから分ける(診断の健全化。records bind運用自体は
		// 恒久的に正当な終着形——LegacyBindOrigin.GRID_ITEMのjavadoc参照)
		builder.tagLegacyBindOrigin(
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin.GRID_ITEM);
		this.openItemBuilder = builder;
		this.openItemBox = itemBox;
		this.openItemMinCap = minContributionCap;
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
		final boolean takeover = this.openItemSource != null;
		this.openItemBuilder = null;
		this.openItemBox = null;
		this.openItemAnonymous = false;
		this.openItemSpec = GridItemSpec.AUTO;
		this.openItemSource = null;
		if (anonymous && builder.hasEmptyRecordedBody() && !itemBox.paintsAnything()) {
			GRID_ITEM_EMPTY_ANON_DROPS.incrementAndGet();
			return;
		}
		GRID_ITEM_RECORDS.incrementAndGet();
		this.items.add(new GridItemContent(itemBox, builder, builder.getIntrinsicSizes(), anonymous, spec,
				this.openItemMinCap, takeover));
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
		final GridParams params = this.gridBox.getGridParams();
		final double available = Math.max(0, this.gridBox.getLineSize());
		this.planAvailable = available;
		// (1) 明示列の展開(2026-08-29): auto-repeatは「収まるだけ」の回数、
		// 線名はトラックと並走させる。テンプレート無しは暗黙の単一autoカラム
		final List<GridTrackListValue.TrackSize> cols = new ArrayList<>();
		final List<List<String>> lines = new ArrayList<>();
		lines.add(new ArrayList<>());
		boolean autoFit = false;
		if (this.subgridColumns != null) {
			// subgrid: 親から継いだ固定トラックと線名(2026-08-29)
			cols.addAll(this.subgridColumns);
			lines.clear();
			for (final List<String> names : this.subgridColumnLines) {
				lines.add(new ArrayList<>(names));
			}
			this.explicitColumns = cols.size();
		} else if (params.templateColumns.isEmpty()) {
			// 明示列0本: 最初の1列も暗黙列(grid-auto-columnsの寸法。無ければ
			// 従来どおりauto)。列フローで暗黙列が増えるときに1列目だけ
			// autoで残余を取ってしまわないため
			this.explicitColumns = 0;
			this.addImplicitColumn(cols, lines);
		} else {
			autoFit = expandTracks(params.templateColumns, params.columnLineNames, available, this.columnGap, cols,
					lines);
			this.explicitColumns = cols.size();
		}
		// (2) grid-template-areasが定める列数・暗黙線名(name-start/name-end)
		final net.zamasoft.foliojet.css.value.GridTemplateAreasValue areas = params.templateAreas;
		while (cols.size() < areas.getColumnCount()) {
			this.addImplicitColumn(cols, lines);
		}
		final List<List<String>> rowLines = new ArrayList<>();
		final List<List<String>> initialRowLines = this.subgridRowLines != null ? this.subgridRowLines
				: params.rowLineNames;
		for (final List<String> names : initialRowLines) {
			rowLines.add(new ArrayList<>(names));
		}
		while (rowLines.size() < areas.getRowCount() + 1) {
			rowLines.add(new ArrayList<>());
		}
		for (final net.zamasoft.foliojet.css.value.GridTemplateAreasValue.Area area : areas.getAreas()) {
			lines.get(area.columnStart()).add(area.name() + "-start");
			lines.get(area.columnEnd()).add(area.name() + "-end");
			rowLines.get(area.rowStart()).add(area.name() + "-start");
			rowLines.get(area.rowEnd()).add(area.name() + "-end");
		}
		this.explicitRows = this.subgridRows != null ? this.subgridRows.size()
				: Math.max(params.templateRows.size(), areas.getRowCount());
		// (3) 線名の数値化
		final List<GridItemSpec> specs = new ArrayList<>(this.items.size());
		for (final GridItemContent item : this.items) {
			specs.add(net.zamasoft.foliojet.layout.sizing.GridLineNameResolver.resolve(item.spec, lines, rowLines));
		}
		// (4) 行フローで明示列の外を指す線・spanには暗黙列を足す
		// (grid-auto-columnsの寸法。従来はfail closedでsource-order配置へ
		// 戻していた——1件だけauto化しない原則はそのまま、列を増やして
		// 解決可能にする)
		if (!params.autoFlowColumn) {
			final int needed = Math.min(GridPlacementResolver.LIMIT, requiredColumns(specs));
			while (cols.size() < needed) {
				this.addImplicitColumn(cols, lines);
			}
		}
		GridPlacementResolver.Plan plan = null;
		final GridPlacementResolver.Result placement = this.rowSubgridLink == null
				? GridPlacementResolver.resolve(specs, cols.size(), this.explicitRows, params.autoFlowColumn,
						params.autoFlowDense)
				: GridPlacementResolver.resolve(specs, cols.size(), this.explicitRows, params.autoFlowColumn,
						params.autoFlowDense, this.rowSubgridLink.span());
		if (placement instanceof GridPlacementResolver.Result.Resolved resolved) {
			plan = resolved.plan(); // rowSpanはGridRowSizingの不足分配で対応(G4d)
			// 列フローが作った暗黙列
			while (cols.size() < plan.columnCount()) {
				this.addImplicitColumn(cols, lines);
			}
		}
		if (plan == null) {
			GRID_PLACEMENT_FALLBACKS.incrementAndGet();
			final int n = cols.size();
			final GridPlacementResolver.GridArea[] fallback = new GridPlacementResolver.GridArea[this.items.size()];
			for (int i = 0; i < fallback.length; ++i) {
				final int row = this.rowSubgridLink == null ? i / n
						: Math.min(this.rowSubgridLink.span() - 1, i / n);
				fallback[i] = new GridPlacementResolver.GridArea(i % n, row, 1, 1);
			}
			plan = new GridPlacementResolver.Plan(List.of(fallback), n, this.rowSubgridLink == null
					? Math.max((fallback.length + n - 1) / n, this.explicitRows)
					: this.rowSubgridLink.span());
		}
		// (5) auto-fit: itemの無い末尾トラックを(gapごと)潰す
		if (autoFit) {
			int used = 1;
			for (final GridPlacementResolver.GridArea area : plan.areas()) {
				used = Math.max(used, area.column() + area.columnSpan());
			}
			if (used < cols.size()) {
				cols.subList(used, cols.size()).clear();
				lines.subList(used + 1, lines.size()).clear();
				plan = new GridPlacementResolver.Plan(plan.areas(), used, plan.rowCount());
			}
		}
		this.tracks = List.copyOf(cols);
		this.columnLines = lines;
		while (rowLines.size() < plan.rowCount() + 1) {
			rowLines.add(new ArrayList<>());
		}
		this.rowLines = rowLines;
		this.placementPlan = plan;
		return plan;
	}

	/**
	 * 明示列テンプレートを展開します(2026-08-29)。auto-repeatは
	 * 「他の固定幅トラックとgapを引いた残りに収まるだけ」の回数(最低1回。
	 * 基準幅が未確定なら1回=仕様の固有寸法計測時の扱い)。
	 *
	 * @return auto-fitを含むか
	 */
	private static boolean expandTracks(final List<GridTrackListValue.TrackSize> template,
			final List<List<String>> templateLines, final double available, final double gap,
			final List<GridTrackListValue.TrackSize> cols, final List<List<String>> lines) {
		boolean autoFit = false;
		// auto-repeat以外の固定幅の合計(回数判定の残り幅)
		double fixedSum = 0;
		int fixedCount = 0;
		for (final GridTrackListValue.TrackSize t : template) {
			if (t instanceof GridTrackListValue.AutoRepeat) {
				continue;
			}
			// 仕様(§7.2.3.2): 回数判定では各トラックをmax側が確定ならその値、
			// そうでなければmin側で数える(内容依存はgapだけ)
			fixedSum += definiteExtent(t, available);
			++fixedCount;
		}
		for (int i = 0; i < template.size(); ++i) {
			final GridTrackListValue.TrackSize t = template.get(i);
			if (i < templateLines.size()) {
				lines.get(lines.size() - 1).addAll(templateLines.get(i));
			}
			if (t instanceof GridTrackListValue.AutoRepeat repeat) {
				final int unitSize = repeat.unit().size();
				final double unitMin = repeat.unitMinLength() + repeat.unitMinRatio() * available;
				int reps = 1;
				if (available > 0 && unitMin > 0) {
					final double room = available - fixedSum - gap * fixedCount;
					reps = (int) Math.floor((room + gap) / (unitMin + gap * unitSize) + 1e-9);
					reps = Math.max(1, reps);
				}
				reps = Math.min(reps, Math.max(1, (net.zamasoft.foliojet.css.impl.property.grid.GridTemplateTracks.MAX_TRACKS
						- cols.size() - template.size()) / unitSize));
				autoFit |= repeat.fit();
				for (int r = 0; r < reps; ++r) {
					lines.get(lines.size() - 1).addAll(repeat.unitLineNames().get(0));
					for (int k = 0; k < unitSize; ++k) {
						cols.add(repeat.unit().get(k));
						lines.add(new ArrayList<>(repeat.unitLineNames().get(k + 1)));
					}
				}
				continue;
			}
			cols.add(t);
			lines.add(new ArrayList<>());
		}
		if (templateLines.size() > template.size()) {
			lines.get(lines.size() - 1).addAll(templateLines.get(template.size()));
		}
		return autoFit;
	}

	/**
	 * auto-repeatの回数判定に使うトラックの確定幅です(2026-08-29): 固定長・
	 * %はその値、minmax()はmax側が確定ならmax、そうでなければmin側。
	 * 内容依存は0。
	 */
	private static double definiteExtent(final GridTrackListValue.TrackSize t, final double available) {
		if (t instanceof GridTrackListValue.Fixed f) {
			return f.length();
		}
		if (t instanceof GridTrackListValue.Percentage p) {
			return p.ratio() * available;
		}
		if (t instanceof GridTrackListValue.MinMax m) {
			final double max = definiteExtent(m.max(), available);
			return m.max() instanceof GridTrackListValue.Fixed || m.max() instanceof GridTrackListValue.Percentage
					? max
					: definiteExtent(m.min(), available);
		}
		return 0;
	}

	/** grid-auto-columnsの周期で暗黙列を1本足します(空ならauto)。 */
	private void addImplicitColumn(final List<GridTrackListValue.TrackSize> cols, final List<List<String>> lines) {
		final List<GridTrackListValue.TrackSize> autoColumns = this.gridBox.getGridParams().autoColumns;
		final int implicitIndex = cols.size() - this.explicitColumns;
		cols.add(autoColumns.isEmpty() ? GridTrackListValue.Auto.INSTANCE
				: autoColumns.get(Math.max(0, implicitIndex) % autoColumns.size()));
		lines.add(new ArrayList<>());
	}

	/**
	 * 行フローで各itemの列指定が要求する列数です(正の線番号とspanのみ。
	 * 負番号は明示末端基準なので数えない)。
	 */
	private static int requiredColumns(final List<GridItemSpec> specs) {
		int needed = 1;
		for (final GridItemSpec spec : specs) {
			final net.zamasoft.foliojet.css.value.GridLineValue s = spec.columnStart(), e = spec.columnEnd();
			final int startLine = !s.isAuto() && !s.isSpan() && !s.isNamed() && s.getNumber() > 0 ? s.getNumber() : 0;
			final int endLine = !e.isAuto() && !e.isSpan() && !e.isNamed() && e.getNumber() > 0 ? e.getNumber() : 0;
			final int startSpan = s.isSpan() ? s.getNumber() : 0;
			final int endSpan = e.isSpan() ? e.getNumber() : 0;
			if (startLine > 0 && endLine > 0) {
				needed = Math.max(needed, Math.max(startLine, endLine) - 1);
			} else if (startLine > 0) {
				needed = Math.max(needed, startLine - 1 + Math.max(1, endSpan));
			} else if (endLine > 0) {
				needed = Math.max(needed, endLine - 1);
			} else {
				needed = Math.max(needed, Math.max(startSpan, endSpan));
			}
		}
		return needed;
	}

	/**
	 * トラック解決に渡す列です(2026-08-29): %はコンテナ行幅で絶対化する
	 * (基準幅が未確定=固有寸法計測ではautoとして扱う——
	 * {@code BasicGridTrackSizing}側)。
	 */
	private List<GridTrackListValue.TrackSize> sizingTracks(final double available) {
		if (!(available > 0)) {
			return this.tracks;
		}
		List<GridTrackListValue.TrackSize> resolved = null;
		for (int i = 0; i < this.tracks.size(); ++i) {
			final GridTrackListValue.TrackSize t = this.tracks.get(i);
			final GridTrackListValue.TrackSize r;
			if (t instanceof GridTrackListValue.Percentage p) {
				r = new GridTrackListValue.Fixed(p.ratio() * available);
			} else if (t instanceof GridTrackListValue.MinMax m
					&& (m.min() instanceof GridTrackListValue.Percentage
							|| m.max() instanceof GridTrackListValue.Percentage)) {
				// minmax()の片側の%(2026-08-29)
				r = new GridTrackListValue.MinMax(
						m.min() instanceof GridTrackListValue.Percentage p
								? new GridTrackListValue.Fixed(p.ratio() * available)
								: m.min(),
						m.max() instanceof GridTrackListValue.Percentage p
								? new GridTrackListValue.Fixed(p.ratio() * available)
								: m.max());
			} else {
				continue;
			}
			if (resolved == null) {
				resolved = new ArrayList<>(this.tracks);
			}
			resolved.set(i, r);
		}
		return resolved == null ? this.tracks : resolved;
	}

	/**
	 * subgridの列トラックとrow subgrid接続を親から継ぎます(css-grid-2、
	 * 2026-08-29/09-03。クラス
	 * javadocの「単一autoトラックの近似へ落ちる場合」参照)。
	 *
	 * @param target bind先(context flowが自分のitemのGridItemBoxで、その上に
	 *               自分のGridBoxだけが積まれているときに限り継ぐ)
	 * @return 継いだか
	 */
	private boolean resolveSubgrid(final BlockBuilder target) {
		final GridParams params = this.gridBox.getGridParams();
		if (!params.columnsSubgrid && !params.rowsSubgrid) {
			return false;
		}
		if (target.getFlowCount() != 2 || !(target.getFlow(0).box instanceof GridItemBox item)
				|| target.getFlow(1).box != this.gridBox) {
			return false;
		}
		if (item.isTakeover()) {
			// takeover itemはauthoredな要素そのもの。その中のgridは
			// **item直下ではない**ので親の列は継げない(G7、2026-08-29。
			// 包み箱のころはflow段数だけで区別できていた。
			// files/unittest/0500-grid/subgrid.htmlの#s3が回帰)
			return false;
		}
		final GridItemBox.SubgridTracks parent = item.getSubgridTracks();
		if (parent == null) {
			return false;
		}
		boolean rowsResolved = false;
		if (params.rowsSubgrid) {
			final RowSubgridLink link = parent.consumeRowSubgridLink();
			if (link != null) {
				this.rowSubgridLink = link;
				this.rowSubgridOwner = item;
				final double childGap = params.rowGapNormal ? link.parentRowGap() : this.rowGap;
				this.rowSubgridGapShim = (childGap - link.parentRowGap()) / 2;
				this.rowGap = childGap;
				final int span = link.span();
				final List<GridTrackListValue.TrackSize> rows = new ArrayList<>(span);
				for (int i = 0; i < span; ++i) {
					rows.add(GridTrackListValue.Auto.INSTANCE);
				}
				final List<List<String>> lines = new ArrayList<>(span + 1);
				for (int i = 0; i <= span; ++i) {
					final List<String> names = new ArrayList<>(link.rowLineNames().get(i));
					if (i < params.rowLineNames.size()) {
						names.addAll(params.rowLineNames.get(i));
					}
					lines.add(names);
				}
				this.subgridRows = List.copyOf(rows);
				this.subgridRowLines = lines;
				rowsResolved = true;
				if (this.containsPageMarginNote && target.getPageContext() != null) {
					final String detailKey = "2823.subgrid-rows-margin-note";
					final net.zamasoft.foliojet.ua.UserAgent ua = target.getPageContext().getPageGenerator()
							.getUserAgent();
					if (ua.getUAContext().getReportedIneffectiveCombinationDetails().add(detailKey)) {
						ua.message(net.zamasoft.foliojet.message.MessageCodes.WARN_INEFFECTIVE_CSS_COMBINATION,
								"float", net.zamasoft.foliojet.message.MessageCodeUtils.detail(detailKey));
					}
				}
			}
		}
		boolean columnsResolved = false;
		if (params.columnsSubgrid) {
			final double[] widths = parent.columnWidths();
			final int span = widths.length;
			final double line = Math.max(0, this.gridBox.getLineSize());
			final List<GridTrackListValue.TrackSize> cols = new ArrayList<>(span);
			if (span == 1) {
				cols.add(new GridTrackListValue.Fixed(line));
			} else {
				// 自分のborder/padding/marginは先頭・末尾トラックに食い込む。
				// 末尾側は「親のarea幅−先頭側の縁−自分のcontent幅」で求める
				// (justify-self:stretch以外でitem幅がarea幅より狭いときは末尾
				// トラックがその分だけ狭くなる=内側の線は親と揃ったまま)
				final double startInset = this.gridBox.getFrame().getFrameLineStart(params.flow);
				double sum = parent.columnGap() * (span - 1);
				for (final double w : widths) {
					sum += w;
				}
				final double endInset = sum - startInset - line;
				for (int i = 0; i < span; ++i) {
					double w = widths[i];
					if (i == 0) {
						w -= startInset;
					}
					if (i == span - 1) {
						w -= endInset;
					}
					cols.add(new GridTrackListValue.Fixed(Math.max(0, w)));
				}
			}
			final List<List<String>> lines = new ArrayList<>(span + 1);
			for (int i = 0; i <= span; ++i) {
				final List<String> names = new ArrayList<>(parent.columnLineNames().get(i));
				if (i < params.columnLineNames.size()) {
					names.addAll(params.columnLineNames.get(i));
				}
				lines.add(names);
			}
			this.subgridColumns = cols;
			this.subgridColumnLines = lines;
			this.columnGap = parent.columnGap();
			this.tracks = List.copyOf(cols);
			columnsResolved = true;
		}
		return columnsResolved || rowsResolved;
	}

	/** row subgrid化した場合に2823を出すため、並列注の存在を記録します。 */
	public void notePageMarginNote() {
		this.containsPageMarginNote = true;
	}

	/**
	 * 行rのテンプレート寸法です(明示行は{@code grid-template-rows}、暗黙行は
	 * {@code grid-auto-rows}の周期。無ければnull=内容高。2026-08-29)。
	 */
	private GridTrackListValue.TrackSize rowTrack(final int r) {
		final GridParams params = this.gridBox.getGridParams();
		if (this.subgridRows != null && r < this.subgridRows.size()) {
			return this.subgridRows.get(r);
		}
		if (this.subgridRows != null) {
			return null;
		}
		if (r < params.templateRows.size()) {
			return params.templateRows.get(r);
		}
		if (params.autoRows.isEmpty()) {
			return null;
		}
		return params.autoRows.get((r - params.templateRows.size()) % params.autoRows.size());
	}

	private static boolean hasAutoRepeat(final List<GridTrackListValue.TrackSize> template) {
		for (final GridTrackListValue.TrackSize t : template) {
			if (t instanceof GridTrackListValue.AutoRepeat) {
				return true;
			}
		}
		return false;
	}

	/**
	 * itemのauthoredな行方向寸法(content-box)です。auto・未指定はNaN
	 * (G7、2026-08-29)。{@code box-sizing: border-box}は枠を引いて内寸へ直す。
	 */
	private static double authoredLineSize(final GridItemBox itemBox,
			final net.zamasoft.foliojet.layout.box.params.WritingMode flow,
			final double areaWidth) {
		final BlockParams p = itemBox.getBlockParams();
		final double value;
		switch (p.size.getLineType(flow)) {
		case ABSOLUTE:
			value = p.size.getLineLength(flow);
			break;
		case RELATIVE:
			value = p.size.getLineLength(flow) * areaWidth;
			break;
		case MIXED:
			value = p.size.getLineLength(flow) + p.size.getLineRatio(flow) * areaWidth;
			break;
		default:
			return Double.NaN;
		}
		final double borderBoxAdjust = p.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
				? itemBox.getFrame().getBorderLineExtent(flow)
				: 0;
		return Math.max(0, value - borderBoxAdjust);
	}

	/** 行rの固定高(fixedまたは基準確定の%。それ以外=内容高ならNONE)。 */
	private double fixedRowHeight(final int r) {
		final GridTrackListValue.TrackSize track = this.rowTrack(r);
		if (track instanceof GridTrackListValue.Fixed f) {
			return f.length();
		}
		if (track instanceof GridTrackListValue.Percentage p && this.gridBox.isSpecifiedPageSize()) {
			return p.ratio() * this.gridBox.getInnerPageExtent(this.gridBox.getGridParams().flow);
		}
		return net.zamasoft.foliojet.layout.util.LayoutUtils.NONE;
	}

	/**
	 * item bind前に確定している行高だけを控えます。未確定行はNONEのままにし、
	 * bind後の{@code GridRowSizing.resolve}へseedとして渡さない。
	 */
	private double[] preResolvedRowHeights(final GridPlacementResolver.Plan plan) {
		final double[] heights = new double[Math.max(1, plan.rowCount())];
		java.util.Arrays.fill(heights, net.zamasoft.foliojet.layout.util.LayoutUtils.NONE);
		for (int r = 0; r < heights.length; ++r) {
			heights[r] = this.fixedRowHeight(r);
		}
		return heights;
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
		return this.columnContributions(plan, -1);
	}

	/**
	 * @param inflatedCap 段数倍で膨らんだitem min-content
	 *                    ({@code columnInflated})の上限(負=キャップなし)。
	 *                    トラック解決時はGridコンテナのcontent-box行幅を
	 *                    渡す——段は狭くできるため膨張分の床は守らなくて
	 *                    よい(AbstractStaticBlockBoxのclampと同じ理由。
	 *                    2026-08-22、掃過seed 1879802: grid内の段組表の
	 *                    min-contentがトラックを紙面の2.7倍へ押し広げ、
	 *                    第2段が紙面外に描かれた)。固有寸法計測
	 *                    (intrinsics)側はキャップせずflagを上へ運ぶ
	 */
	private List<BasicGridTrackSizing.ItemContribution> columnContributions(
			final GridPlacementResolver.Plan plan, final double inflatedCap) {
		final List<BasicGridTrackSizing.ItemContribution> contributions = new ArrayList<>(this.items.size());
		for (int i = 0; i < this.items.size(); ++i) {
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			final GridItemContent item = this.items.get(i);
			// 自動最小サイズの上書き(GridItemContent.minContributionCap参照)
			double itemMin = item.minContributionCap >= 0
					? Math.min(item.sizes.minContent(), item.minContributionCap)
					: item.sizes.minContent();
			if (inflatedCap >= 0 && item.sizes.columnInflated() && itemMin > inflatedCap) {
				itemMin = inflatedCap;
			}
			double itemMax = item.sizes.maxContent();
			if (item.takeover) {
				// takeoverではauthored rootの枠と宣言幅が録画本文の外にある
				// (G7、2026-08-29)。足し直さないと、幅を持つitemのトラックが
				// 内容だけの細さで解決される(place-shorthandのDで実測)
				final net.zamasoft.foliojet.layout.box.params.WritingMode flow = this.gridBox.getGridParams().flow;
				final BlockParams ip = item.itemBox.getBlockParams();
				final double declared = ip.size.getLineType(flow) == LengthType.ABSOLUTE
						? ip.size.getLineLength(flow)
						: Double.NaN;
				final double extras = item.itemBox.getFrame().getBorderLineExtent(flow);
				if (!Double.isNaN(declared)) {
					final double used = ip.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
							? Math.max(declared, extras)
							: declared + extras;
					itemMin = used;
					itemMax = used;
				} else {
					itemMin += extras;
					itemMax += extras;
				}
			}
			contributions.add(new BasicGridTrackSizing.ItemContribution(area.column(), area.columnSpan(),
					itemMin, itemMax));
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
		final BasicGridTrackSizing.Intrinsics line = BasicGridTrackSizing.intrinsics(
				this.sizingTracks(this.gridBox.getLineSize()), this.columnContributions(plan), this.columnGap);
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
			// 固定高の明示・暗黙行はその高さ(2026-08-29)
			final double fixed = this.fixedRowHeight(r);
			minPage += net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(fixed) ? rowMinPage[r] : fixed;
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

	/** frame調整済みstartsを持つrow subgrid内の行群寸法です。 */
	private static double rowAreaExtent(final GridPlacementResolver.GridArea area, final double[] rowHeights,
			final double[] rowStarts) {
		final int last = area.row() + area.rowSpan() - 1;
		return rowStarts[last] + rowHeights[last] - rowStarts[area.row()];
	}

	/**
	 * 子finalizerへ、このGridのローカル行sliceを渡します。row subgrid内から
	 * 呼ぶ場合は、そのitemの内側辺へ現在のgap shimも一度だけ適用します。
	 */
	private static void runRowFinalizers(final List<PendingRowFinalizer> finalizers, final double[] rowHeights,
			final double[] rowStarts, final double rowGap, final double gapShim) {
		for (final PendingRowFinalizer pending : finalizers) {
			final double[] heights = new double[pending.span()];
			final double[] starts = new double[pending.span()];
			final double baseShim = pending.rowStart() == 0 ? 0 : gapShim;
			final double base = rowStarts[pending.rowStart()] + baseShim;
			for (int i = 0; i < starts.length; ++i) {
				final int row = pending.rowStart() + i;
				final double startShim = row == 0 ? 0 : gapShim;
				final double endShim = row + 1 == rowHeights.length ? 0 : gapShim;
				heights[i] = Math.max(0, rowHeights[row] - startShim - endShim);
				starts[i] = i == 0 ? 0 : rowStarts[row] + startShim - base;
			}
			pending.finalizer().finalizeRows(heights, starts, rowGap);
		}
	}

	/**
	 * このrow subgridの子孫寄与を一段上へ渡します。frame/gapはこの境界で
	 * 一度だけ足し、空の端行には最寄り占有行の寄与をspan拡張して複製します。
	 */
	private void forwardRowSubgridContributions(final List<GridRowSizing.Contribution> local) {
		final RowSubgridLink link = this.rowSubgridLink;
		final int rowCount = link.span();
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = this.gridBox.getFrame();
		final double startFrame = frame.getFramePageStart(this.gridBox.getGridParams().flow);
		final double endFrame = frame.getFramePageEnd(this.gridBox.getGridParams().flow);
		if (local.isEmpty()) {
			// 完全に空ならframeだけ。子gapやhypothetical itemは作らない。
			link.sink().contribute(0, rowCount, Math.max(0, startFrame + endFrame));
			return;
		}

		final List<GridRowSizing.Contribution> expanded = new ArrayList<>(local);
		int firstOccupied = rowCount, lastOccupied = -1;
		for (final GridRowSizing.Contribution contribution : local) {
			firstOccupied = Math.min(firstOccupied, contribution.row());
			lastOccupied = Math.max(lastOccupied, contribution.row() + contribution.span() - 1);
		}
		if (firstOccupied > 0) {
			for (final GridRowSizing.Contribution contribution : local) {
				if (contribution.row() <= firstOccupied
						&& contribution.row() + contribution.span() > firstOccupied) {
					expanded.add(new GridRowSizing.Contribution(0,
							contribution.row() + contribution.span(), contribution.extent()));
				}
			}
		}
		if (lastOccupied < rowCount - 1) {
			for (final GridRowSizing.Contribution contribution : local) {
				if (contribution.row() <= lastOccupied
						&& contribution.row() + contribution.span() > lastOccupied) {
					expanded.add(new GridRowSizing.Contribution(contribution.row(),
							rowCount - contribution.row(), contribution.extent()));
				}
			}
		}
		for (final GridRowSizing.Contribution contribution : expanded) {
			double extent = contribution.extent();
			if (contribution.row() == 0) {
				extent += startFrame;
			} else {
				extent += this.rowSubgridGapShim;
			}
			if (contribution.row() + contribution.span() == rowCount) {
				extent += endFrame;
			} else {
				extent += this.rowSubgridGapShim;
			}
			link.sink().contribute(contribution.row(), contribution.span(), Math.max(0, extent));
		}
	}

	/** 親行確定後にrow subgridの直接item、孫、行台帳の順で最終化します。 */
	private void finalizeRowSubgrid(final GridPlacementResolver.Plan plan, final FixedGridLayout layout,
			final double contentX, final double[] itemXOffsets, final BoxAlignment[] aligns,
			final double[] boundExtents, final boolean[] rowSubgridItems,
			final List<PendingRowFinalizer> childFinalizers, final double[] parentHeights,
			final double[] parentStarts, final double parentRowGap) {
		final GridParams params = this.gridBox.getGridParams();
		assert Double.doubleToLongBits(parentRowGap) == Double
				.doubleToLongBits(this.rowSubgridLink.parentRowGap()) : "row subgridの親gapが不一致";
		final int count = this.items.size();
		final double startFrame = this.gridBox.getFrame().getFramePageStart(params.flow);
		final double endFrame = this.gridBox.getFrame().getFramePageEnd(params.flow);
		final double[] rowHeights = parentHeights.clone();
		if (rowHeights.length == 1) {
			rowHeights[0] = Math.max(0, rowHeights[0] - startFrame - endFrame);
		} else {
			rowHeights[0] = Math.max(0, rowHeights[0] - startFrame);
			rowHeights[rowHeights.length - 1] = Math.max(0,
					rowHeights[rowHeights.length - 1] - endFrame);
		}
		final double[] rowStarts = new double[rowHeights.length];
		for (int r = 0; r < rowHeights.length; ++r) {
			rowStarts[r] = r == 0 ? 0 : parentStarts[r] - startFrame;
		}
		final int lastRow = rowHeights.length - 1;
		final double ownerOuter = parentStarts[lastRow] + parentHeights[lastRow];
		final double cursor = Math.max(0, ownerOuter - startFrame - endFrame);
		this.gridBox.setExactUsedPageSize(cursor);
		final double ownerFrame = this.rowSubgridOwner.getFrame().getFramePageExtent(params.flow);
		this.rowSubgridOwner.setExactUsedPageSize(Math.max(0, ownerOuter - ownerFrame));

		final boolean ledgerEligible = count > 0 && !params.flow.isVertical() && allSingleRowSpan(plan, count);
		final Integer[] order = new Integer[count];
		for (int i = 0; i < count; ++i) {
			order[i] = i;
		}
		boolean rowMajor = ledgerEligible && isRowMajor(plan, count);
		if (ledgerEligible && !rowMajor && !hasOverlap(plan, count)) {
			java.util.Arrays.sort(order, java.util.Comparator.comparingInt(i -> plan.areas().get(i).row()));
			rowMajor = true;
		}
		final double[] yOffsets = new double[count];
		for (int idx = 0; idx < count; ++idx) {
			final int i = order[idx];
			final GridItemBox itemBox = this.items.get(i).itemBox;
			final GridPlacementResolver.GridArea area = plan.areas().get(i);
			final double logicalLine = contentX + layout.columnStart(area.column()) + itemXOffsets[i];
			itemBox.setGridLineOffset(LayoutUtils.inlineToPhysical(params, this.gridBox.getLineSize(), logicalLine,
					logicalLine + itemBox.getLineExtent(params.flow)));
			final double startShim = area.row() == 0 ? 0 : this.rowSubgridGapShim;
			final double endShim = area.row() + area.rowSpan() == rowHeights.length ? 0
					: this.rowSubgridGapShim;
			final double areaHeight = Math.max(0,
					rowAreaExtent(area, rowHeights, rowStarts) - startShim - endShim);
			if (!rowSubgridItems[i] && aligns[i] == BoxAlignment.STRETCH
					&& areaHeight > itemBox.getPageExtent(params.flow)
					&& itemBox.getBlockParams().size.getPageType(params.flow) == LengthType.AUTO) {
				final double deficit = areaHeight - itemBox.getPageExtent(params.flow);
				itemBox.setPageAxis(itemBox.getInnerPageExtent(params.flow) + deficit);
			}
			final double free = Math.max(0, areaHeight - itemBox.getPageExtent(params.flow));
			final double yOffset = startShim + (aligns[i] == BoxAlignment.CENTER ? free / 2
					: aligns[i] == BoxAlignment.END ? free : 0);
			yOffsets[i] = yOffset;
			this.gridBox.getContainer().addFlow(itemBox, rowStarts[area.row()] + yOffset);
		}

		// 直接itemを最終位置へ登録してから、孫の正確な寸法・幾何を確定する。
		runRowFinalizers(childFinalizers, rowHeights, rowStarts, this.rowGap, this.rowSubgridGapShim);
		for (int i = 0; i < count; ++i) {
			boundExtents[i] = this.items.get(i).itemBox.getPageExtent(params.flow);
		}
		if (rowMajor) {
			final double[] ledgerRowStarts = new double[rowStarts.length];
			final double[] ledgerRowHeights = new double[rowHeights.length];
			for (int r = 0; r < rowStarts.length; ++r) {
				final double startShim = r == 0 ? 0 : this.rowSubgridGapShim;
				final double endShim = r + 1 == rowStarts.length ? 0 : this.rowSubgridGapShim;
				ledgerRowStarts[r] = rowStarts[r] + startShim;
				ledgerRowHeights[r] = Math.max(0, rowHeights[r] - startShim - endShim);
			}
			final List<GridBox.Row> gridRows = new ArrayList<>();
			final List<GridItemBox> gridRowItems = new ArrayList<>(count);
			int rowStartFlow = 0;
			double itemsEnd = 0;
			int currentRow = plan.areas().get(order[0]).row();
			for (int idx = 0; idx < count; ++idx) {
				final int i = order[idx];
				final int r = plan.areas().get(i).row();
				if (r != currentRow) {
					gridRows.add(new GridBox.Row(rowStartFlow, idx - rowStartFlow,
							ledgerRowStarts[currentRow], ledgerRowHeights[currentRow], itemsEnd));
					rowStartFlow = idx;
					itemsEnd = 0;
					currentRow = r;
				}
				final double startShim = r == 0 ? 0 : this.rowSubgridGapShim;
				itemsEnd = Math.max(itemsEnd, yOffsets[i] - startShim
						+ this.items.get(i).itemBox.paintedPageExtent(params.flow));
				gridRowItems.add(this.items.get(i).itemBox);
			}
			gridRows.add(new GridBox.Row(rowStartFlow, count - rowStartFlow, ledgerRowStarts[currentRow],
					ledgerRowHeights[currentRow], itemsEnd));
			this.gridBox.setGridRows(gridRows, gridRowItems);
		}
		this.rowSubgridLink = null;
		this.rowSubgridOwner = null;
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
		if (this.resolveSubgrid(target)) {
			// 固有寸法計測で作った単一autoのplanは捨て、親の線で配置し直す
			// (2026-08-29。plan共有の原則G4bは同じトラック集合の中でのみ)
			this.placementPlan = null;
		}
		if (this.placementPlan != null && this.planAvailable != Math.max(0, this.gridBox.getLineSize())
				&& hasAutoRepeat(params.templateColumns)) {
			// auto-repeatの回数はコンテナ幅で決まる(2026-08-29)。固有寸法
			// 計測(幅未確定=1回)で作ったplanは、幅確定後のbindで作り直す
			// ——仕様でも不確定幅の計測時は1回、確定幅では収まるだけ、と
			// 別の値になる。plan共有の原則(G4b)は同一幅の中でのみ成り立つ
			this.placementPlan = null;
		}
		final GridPlacementResolver.Plan plan = this.placementPlan();
		final double[] preResolvedRowHeights = this.preResolvedRowHeights(plan);
		// トラック幅解決(G3b/c): planに基づく列contribution(G4b)から、
		// fixed=指定長・auto=base/growth limit+stretch・fr=find-frで
		// 確定する。基準幅はGridコンテナのcontent-box行幅
		// (TwoPass経由ではshrink-to-fit確定後の幅)
		// G5c: justify-contentのused value。positional(start/center/end)の
		// ときauto列の残余stretchを止め、残余をトラック群のoffsetへ回す
		final BoxAlignment justifyContent = BoxAlignment.resolve(BoxAlignment.AUTO, params.justifyContent);
		final double[] widths = BasicGridTrackSizing.resolve(this.sizingTracks(this.gridBox.getLineSize()),
				this.columnContributions(plan, Math.max(0, this.gridBox.getLineSize())),
				this.gridBox.getLineSize(), this.columnGap, justifyContent == BoxAlignment.STRETCH);
		final FixedGridLayout layout = new FixedGridLayout(widths, this.columnGap, this.rowGap);
		// 解決済み列の公開(subgridの子・診断用、2026-08-29)
		this.gridBox.setResolvedColumnTracks(widths, this.columnGap);
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
			// takeover item(G7、2026-08-29)は**自分の枠**を持つ。areaWidthは
			// マージン箱の幅、setTrackWidthが受け取るのは内寸なので、枠の分を
			// 引いてから渡す。枠の%・emもここで実寸へ直す(基準はグリッド領域)
			double lineExtras = 0;
			if (item.takeover) {
				final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = item.itemBox.getFrame();
				net.zamasoft.foliojet.layout.util.LayoutUtils.computePaddings(frame.padding, frame.frame.padding,
						areaWidth);
				net.zamasoft.foliojet.layout.util.LayoutUtils.computeMarginsAutoToZero(frame.margin,
						frame.frame.margin, areaWidth);
				lineExtras = frame.getFrameLineExtent(params.flow);
			}
			final double innerArea = Math.max(0, areaWidth - lineExtras);
			// 明示幅はstretchにも優先する(css-grid §6.6)——包み箱のころは
			// 中の子が自分で適用していたので、ここで見なければ落ちる
			final double authoredLine = authoredLineSize(item.itemBox, params.flow, innerArea);
			if (!Double.isNaN(authoredLine)) {
				itemWidths[i] = authoredLine;
			} else if (justify == BoxAlignment.STRETCH) {
				itemWidths[i] = innerArea;
			} else {
				itemWidths[i] = Sizing.fitContent(item.sizes.minContent(), item.sizes.maxContent(), innerArea);
			}
			final double freeLineInArea = Math.max(0, areaWidth - (itemWidths[i] + lineExtras));
			itemXOffsets[i] = justify == BoxAlignment.CENTER ? freeLineInArea / 2
					: justify == BoxAlignment.END ? freeLineInArea : 0;
			assert itemWidths[i] >= 0 && !Double.isNaN(itemWidths[i]) : "不正なitem幅: " + itemWidths[i];
		}
		// 幅確定→本文bind。PageAtomicBox契約によりGrid flowがactiveな間に
		// 全bindが完了する(ページbreakは走らない)
		final double[] extents = new double[count];
		final boolean[] rowSubgridItems = new boolean[count];
		final List<GridRowSizing.Contribution> rowContributions = new ArrayList<>();
		final List<PendingRowFinalizer> rowFinalizers = new ArrayList<>();
		final RootBuilder pageContext = target.getPageContext();
		if (this.rowSubgridLink != null && pageContext != null) {
			pageContext.beginRowSubgridBind();
		}
		try {
			for (int i = 0; i < count; ++i) {
				final int itemIndex = i;
				final GridItemContent item = this.items.get(i);
				// 列トラックと、行寄与を親座標へ一度だけ変換する一時linkを渡す。
				final GridPlacementResolver.GridArea area = plan.areas().get(i);
				final RowContributionSink sink = new RowContributionSink() {
					@Override
					public void contribute(final int row, final int span, final double extent) {
						if (row < 0 || span <= 0 || row + span > area.rowSpan()) {
							throw new IllegalArgumentException("row subgrid contribution: " + row + "/" + span);
						}
						rowContributions.add(new GridRowSizing.Contribution(area.row() + row, span, extent));
					}

					@Override
					public void whenRowsResolved(final RowGeometryFinalizer finalizer) {
						if (rowSubgridItems[itemIndex]) {
							throw new IllegalStateException("row subgrid finalizerの二重登録");
						}
						rowSubgridItems[itemIndex] = true;
						aligns[itemIndex] = BoxAlignment.STRETCH;
						rowFinalizers.add(new PendingRowFinalizer(area.row(), area.rowSpan(), finalizer));
					}
				};
				final RowSubgridLink link = new RowSubgridLink(area.row(), area.rowSpan(), this.rowGap,
						this.rowLines.subList(area.row(), area.row() + area.rowSpan() + 1), sink);
				item.itemBox.setSubgridTracks(new GridItemBox.SubgridTracks(
						java.util.Arrays.copyOfRange(widths, area.column(), area.column() + area.columnSpan()),
						this.columnGap,
						this.columnLines.subList(area.column(), area.column() + area.columnSpan() + 1),
						this.rowGap, this.rowLines.subList(area.row(), area.row() + area.rowSpan() + 1), link));
				item.bind(target, itemWidths[i]);
				GRID_ITEM_BINDS.incrementAndGet();
				extents[i] = item.itemBox.getPageExtent(params.flow);
				// row subgridでなかったitemのclosureも永続boxへ残さない。
				item.itemBox.getSubgridTracks().consumeRowSubgridLink();
			}
		} finally {
			if (this.rowSubgridLink != null && pageContext != null) {
				pageContext.endRowSubgridBind();
			}
		}

		if (this.rowSubgridLink != null) {
			for (int i = 0; i < count; ++i) {
				if (!rowSubgridItems[i]) {
					final GridPlacementResolver.GridArea area = plan.areas().get(i);
					rowContributions.add(new GridRowSizing.Contribution(area.row(), area.rowSpan(), extents[i]));
				}
			}
			this.forwardRowSubgridContributions(rowContributions);
			final boolean[] finalized = new boolean[1];
			this.rowSubgridLink.sink().whenRowsResolved((rowHeights, rowStarts, inheritedGap) -> {
				if (finalized[0]) {
					throw new IllegalStateException("row subgrid finalizerの二重実行");
				}
				finalized[0] = true;
				this.finalizeRowSubgrid(plan, layout, contentX, itemXOffsets, aligns, extents,
						rowSubgridItems, rowFinalizers, rowHeights, rowStarts, inheritedGap);
			});
			final LayoutContext.Flow active = target.getFlow();
			assert active.box == this.gridBox : "Grid bindでactive flowがGridではない: " + active.box;
			return;
		}
		// 行高解決(G4d: rowSpanの不足分配込み——GridRowSizing。
		// 空行は高さ0だが隣接rowGapは残る=仕様のgutter挙動)
		List<GridPlacementResolver.GridArea> sizingAreas = plan.areas();
		double[] sizingExtents = extents;
		int ordinaryCount = 0;
		for (final boolean rowSubgrid : rowSubgridItems) {
			if (!rowSubgrid) {
				++ordinaryCount;
			}
		}
		if (ordinaryCount != count) {
			final List<GridPlacementResolver.GridArea> ordinaryAreas = new ArrayList<>(ordinaryCount);
			final double[] ordinaryExtents = new double[ordinaryCount];
			int ordinary = 0;
			for (int i = 0; i < count; ++i) {
				if (!rowSubgridItems[i]) {
					ordinaryAreas.add(plan.areas().get(i));
					ordinaryExtents[ordinary++] = extents[i];
				}
			}
			sizingAreas = ordinaryAreas;
			sizingExtents = ordinaryExtents;
		}
		final double[] rowHeights = GridRowSizing.resolve(sizingAreas, sizingExtents, plan.rowCount(), this.rowGap,
				rowContributions);
		// 固定高の行(grid-template-rows/grid-auto-rowsの絶対長・基準確定の%)
		// はその高さに固定する(2026-08-29。内容が高ければitemがはみ出す=
		// 仕様どおり。auto/fr/min-content等は内容高のまま——高さautoの
		// Gridではfr行もautoに等しい)
		final boolean[] fixedRow = new boolean[rowHeights.length];
		for (int r = 0; r < rowHeights.length; ++r) {
			final double fixed = preResolvedRowHeights[r];
			if (!net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(fixed)) {
				rowHeights[r] = fixed;
				fixedRow[r] = true;
			}
		}
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
				int stretchable = 0;
				for (final boolean fixed : fixedRow) {
					if (!fixed) {
						++stretchable;
					}
				}
				if (alignContent == BoxAlignment.STRETCH && stretchable > 0) {
					// auto行へ均等分配(空行も対象。固定高の行は伸ばさない——2026-08-29)
					final double share = freePage / stretchable;
					for (int r = 0; r < rowHeights.length; ++r) {
						if (!fixedRow[r]) {
							rowHeights[r] += share;
						}
					}
				} else if (alignContent != BoxAlignment.STRETCH) {
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
		// 親の行が確定してから、直接itemの配置より先に子subgridを最終化する。
		runRowFinalizers(rowFinalizers, rowHeights, rowStarts, this.rowGap, 0);
		for (int i = 0; i < count; ++i) {
			extents[i] = this.items.get(i).itemBox.getPageExtent(params.flow);
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
			final double logicalLine = contentX + layout.columnStart(area.column()) + itemXOffsets[i];
			itemBox.setGridLineOffset(LayoutUtils.inlineToPhysical(params, this.gridBox.getLineSize(), logicalLine,
					logicalLine + itemBox.getLineExtent(params.flow)));
			double areaHeight = this.rowGap * (area.rowSpan() - 1);
			for (int r = area.row(); r < area.row() + area.rowSpan(); ++r) {
				areaHeight += rowHeights[r];
			}
			// 既定のstretch: itemを行(spanするときは跨ぐ範囲)の高さまで伸ばす
			// (G7、2026-08-29の利用者報告A-7)。takeoverでitemはauthoredな箱
			// そのものなので、背景・枠がここで伸びる。列側のjustify: stretchが
			// itemWidths=areaWidthとするのと対称
			if (aligns[i] == BoxAlignment.STRETCH && areaHeight > extents[i]
					&& itemBox.getBlockParams().size.getPageType(params.flow) == LengthType.AUTO) {
				// 不足分を**内寸**へ足す(codexの指摘)。areaHeightをそのまま
				// 内容高にすると、枠のあるitemでpadding/borderが上積みされる
				final double deficit = areaHeight - itemBox.getPageExtent(params.flow);
				itemBox.setPageAxis(itemBox.getInnerPageExtent(params.flow) + deficit);
			}
			final double free = Math.max(0, areaHeight - extents[i]);
			final double yOffset = aligns[i] == BoxAlignment.CENTER ? free / 2
					: aligns[i] == BoxAlignment.END ? free : 0;
			this.gridBox.getContainer().addFlow(itemBox, rowStarts[area.row()] + yOffset);
			// 行分割(G6)のslack判定用: 行開始からのitemが**実際に描く**端
			// (2026-08-29、G7)。背景・枠が無ければ内容末端、あれば伸ばした
			// 箱全体——後者を空白扱いで切ると背景の継続が消える
			itemPageEnds[i] = yOffset + itemBox.paintedPageExtent(params.flow);
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
