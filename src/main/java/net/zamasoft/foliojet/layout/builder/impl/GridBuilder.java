package net.zamasoft.foliojet.layout.builder.impl;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Columns;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.builder.LayoutContext;
import net.zamasoft.foliojet.layout.segment.BlockParamsTemplate;
import net.zamasoft.foliojet.layout.sizing.FixedGridLayout;

/**
 * Gridの構築coordinatorです(Grid G1b、2026-07-31——
 * consult-codex-2026-07-31-grid-g1.txt §1)。{@code TableBuilder}と同じく
 * {@code DocumentBuilder.builderStack}に積まれるが{@code Builder}ではない。
 * 直接子ごとに固定幅の{@link GridItemBox}+独立{@code BlockBuilder}を
 * 開き、Grid終端で{@link FixedGridLayout}の結果に従って配置する。
 *
 * <p>
 * G1サブセット: 固定長列のみ・行はauto(行内item実高の最大)・
 * source-order row auto-placement。適格判定は
 * {@link GridBuilderLifecycle#eligible}。
 * </p>
 */
public final class GridBuilder {

	private final BlockBuilder host;

	private final GridBox gridBox;

	private final FixedGridLayout layout;

	private final List<GridItemBox> items = new ArrayList<>();

	/** 開いているitemのbuilder(elementまたは匿名)。閉じているときnull。 */
	private BlockBuilder openItemBuilder;

	private GridItemBox openItemBox;

	/** 開いているitemが匿名(直接テキスト)か。 */
	private boolean openItemAnonymous;

	GridBuilder(final BlockBuilder host, final GridBox gridBox) {
		this.host = host;
		this.gridBox = gridBox;
		final GridParams params = gridBox.getGridParams();
		final double[] widths = new double[params.templateColumns.size()];
		for (int i = 0; i < widths.length; ++i) {
			widths[i] = ((GridTrackListValue.Fixed) params.templateColumns.get(i)).length();
		}
		this.layout = new FixedGridLayout(widths, params.columnGap, params.rowGap);
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

	/** 合成itemのparams(Gridの文字属性を継承し、frame等は中立へ戻す)。 */
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
		return params;
	}

	/** 次のitem(element用)を開きます。返るbuilderを積むのは呼び出し側。 */
	public BlockBuilder startElementItem() {
		return this.startItem(false);
	}

	/** 直接テキスト用の匿名itemを開きます(開いていれば再利用)。 */
	public BlockBuilder requireAnonymousItem() {
		if (this.openItemBuilder != null && this.openItemAnonymous) {
			return null; // 既に開いている(積み直し不要)
		}
		return this.startItem(true);
	}

	private BlockBuilder startItem(final boolean anonymous) {
		assert this.openItemBuilder == null : "前のitemが閉じられていない";
		final int index = this.items.size();
		final GridItemBox itemBox = new GridItemBox(this.itemParams(), new FlowPos(),
				this.layout.columnWidth(this.layout.columnOf(index)));
		final BlockBuilder builder = new BlockBuilder(this.host, itemBox);
		this.openItemBuilder = builder;
		this.openItemBox = itemBox;
		this.openItemAnonymous = anonymous;
		return builder;
	}

	/**
	 * 開いているitemを確定します(builderのclose後に呼ぶ)。空の匿名item
	 * (空白のみ等)はslotを消費させず破棄する。
	 */
	public void itemClosed() {
		final GridItemBox itemBox = this.openItemBox;
		final boolean anonymous = this.openItemAnonymous;
		this.openItemBuilder = null;
		this.openItemBox = null;
		this.openItemAnonymous = false;
		if (anonymous && itemBox.getContainer().getContentSize() == 0 && !itemBox.paintsAnything()) {
			return;
		}
		this.items.add(itemBox);
	}

	/**
	 * Grid終端の配置です。全itemをトラック座標でGridコンテナへ追加し、
	 * Grid内高と親flowカーソルを同期する(独立item builderは親カーソルを
	 * 進めないため、同期しないと後続ブロックが重なる——答申の補正点)。
	 */
	public void finish() {
		assert this.openItemBuilder == null : "item未クローズでGrid終端に到達";
		final GridParams params = this.gridBox.getGridParams();
		final double[] extents = new double[this.items.size()];
		for (int i = 0; i < extents.length; ++i) {
			extents[i] = this.items.get(i).getPageExtent(params.flow);
		}
		final FixedGridLayout.Placement placement = this.layout.place(extents);
		for (int i = 0; i < this.items.size(); ++i) {
			final GridItemBox item = this.items.get(i);
			item.setGridLineOffset(this.layout.columnStart(this.layout.columnOf(i)));
			this.gridBox.getContainer().addFlow(item, placement.rowStarts()[this.layout.rowOf(i)]);
		}
		this.gridBox.setPageAxis(placement.totalExtent());
		final LayoutContext.Flow active = this.host.getFlow();
		assert active.box == this.gridBox : "Grid終端でactive flowがGridではない: " + active.box;
		this.host.setPageAxis(active.pageAxis + this.gridBox.getInnerPageExtent(params.flow));
	}
}
