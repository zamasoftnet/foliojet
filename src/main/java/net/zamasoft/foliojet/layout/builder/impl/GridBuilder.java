package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.sizing.BasicGridTrackSizing;
import net.zamasoft.foliojet.layout.sizing.FixedGridLayout;

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
 * G1サブセット: 固定長列のみ・行はauto(行内item実高の最大)・
 * source-order row auto-placement。適格判定は
 * {@link GridBuilderLifecycle#eligible}。
 * </p>
 */
public final class GridBuilder {

	/** 録画されたitem数(空匿名破棄を除く)。bind数と一致すること。 */
	public static final AtomicLong GRID_ITEM_RECORDS = new AtomicLong();

	/** bindされたitem数。 */
	public static final AtomicLong GRID_ITEM_BINDS = new AtomicLong();

	/** 空の匿名itemを破棄した数(slot非消費)。 */
	public static final AtomicLong GRID_ITEM_EMPTY_ANON_DROPS = new AtomicLong();

	private final BlockBuilder host;

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

	GridBuilder(final BlockBuilder host, final GridBox gridBox) {
		this.host = host;
		this.gridBox = gridBox;
		final GridParams params = gridBox.getGridParams();
		this.tracks = params.templateColumns;
		this.columnGap = params.columnGap;
		this.rowGap = params.rowGap;
	}

	public GridBox getGridBox() {
		return this.gridBox;
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

	/** 次のitem(element用)を開きます。返るbuilderを積むのは呼び出し側。 */
	public TwoPassBlockBuilder startElementItem() {
		return this.startItem(false);
	}

	/** 直接テキスト用の匿名itemを開きます(開いていれば再利用)。 */
	public TwoPassBlockBuilder requireAnonymousItem() {
		if (this.openItemBuilder != null && this.openItemAnonymous) {
			return null; // 既に開いている(積み直し不要)
		}
		return this.startItem(true);
	}

	private TwoPassBlockBuilder startItem(final boolean anonymous) {
		assert this.openItemBuilder == null : "前のitemが閉じられていない";
		// 幅は暫定(auto列は未解決)。録画・計測は幅非依存で、確定幅は
		// finish()のbind直前にsetTrackWidthで入る(G3b)
		final GridItemBox itemBox = new GridItemBox(this.itemParams(), new FlowPos(), 0);
		final TwoPassBlockBuilder builder = new TwoPassBlockBuilder(this.host, itemBox);
		this.openItemBuilder = builder;
		this.openItemBox = itemBox;
		this.openItemAnonymous = anonymous;
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
		this.openItemBuilder = null;
		this.openItemBox = null;
		this.openItemAnonymous = false;
		if (anonymous && builder.hasEmptyRecordedBody() && !itemBox.paintsAnything()) {
			GRID_ITEM_EMPTY_ANON_DROPS.incrementAndGet();
			return;
		}
		GRID_ITEM_RECORDS.incrementAndGet();
		this.items.add(new GridItemContent(itemBox, builder, builder.getIntrinsicSizes(), anonymous));
	}

	/**
	 * Grid終端の組み立てです(G3a: 幅確定→bind→行高計測→配置の四段)。
	 * 全itemをトラック座標でGridコンテナへ追加し、Grid内高と親flow
	 * カーソルを同期する(独立item builderは親カーソルを進めないため、
	 * 同期しないと後続ブロックが重なる——G1答申の補正点)。
	 */
	public void finish() {
		assert this.openItemBuilder == null : "item未クローズでGrid終端に到達";
		final GridParams params = this.gridBox.getGridParams();
		// トラック幅解決(G3b): 列ごとのitem固有寸法contribution
		// (min/max-contentの最大)から、fixed=指定長・auto=base/growth
		// limit+stretchで確定する。基準幅はGridコンテナのcontent-box行幅
		final int n = this.tracks.size();
		final double[] colMin = new double[n];
		final double[] colMax = new double[n];
		for (int i = 0; i < this.items.size(); ++i) {
			final GridItemContent item = this.items.get(i);
			final int col = i % n;
			colMin[col] = Math.max(colMin[col], item.sizes.minContent());
			colMax[col] = Math.max(colMax[col], item.sizes.maxContent());
		}
		final double[] widths = BasicGridTrackSizing.resolve(this.tracks, colMin, colMax,
				this.gridBox.getLineSize(), this.columnGap);
		final FixedGridLayout layout = new FixedGridLayout(widths, this.columnGap, this.rowGap);
		// 幅確定→本文bind。PageAtomicBox契約によりGrid flowがactiveな間に
		// 全bindが完了する(ページbreakは走らない)
		final double[] extents = new double[this.items.size()];
		for (int i = 0; i < this.items.size(); ++i) {
			final GridItemContent item = this.items.get(i);
			item.bind(this.host, layout.columnWidth(layout.columnOf(i)));
			GRID_ITEM_BINDS.incrementAndGet();
			extents[i] = item.itemBox.getPageExtent(params.flow);
		}
		final FixedGridLayout.Placement placement = layout.place(extents);
		for (int i = 0; i < this.items.size(); ++i) {
			final GridItemBox itemBox = this.items.get(i).itemBox;
			itemBox.setGridLineOffset(layout.columnStart(layout.columnOf(i)));
			this.gridBox.getContainer().addFlow(itemBox, placement.rowStarts()[layout.rowOf(i)]);
		}
		this.gridBox.setPageAxis(placement.totalExtent());
		final LayoutContext.Flow active = this.host.getFlow();
		assert active.box == this.gridBox : "Grid終端でactive flowがGridではない: " + active.box;
		this.host.setPageAxis(active.pageAxis + this.gridBox.getInnerPageExtent(params.flow));
	}
}
