package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;

/**
 * 構築中のセル内容です(P2-2: §5.2b 表ビルダー統一の共通 model 第一片。
 * OnePass/TwoPass 両ビルダーの同名内部クラスの統合 — 差は colspan を
 * 引数で受けるか pos から読むかのコンストラクタだけだった)。
 *
 * <p>
 * 実体は次の3態のどれか(E-6増分5a、2026-07-24で第3態を追加——
 * {@code docs/consultations/consult-e6b-remaining-increments-codex.md}
 * §4.2/§4.3)。
 * </p>
 * <ol>
 * <li>実測ビルダー({@link TwoPassBlockBuilder}、未確定内容)</li>
 * <li>連結の続き(rowspan の2行目以降 = extended、確定済みセルボックス)</li>
 * <li>seal済み本文({@link RangeContent}: IntrinsicSizes数値+
 * SourceRange(+lease)。Retained表のセルclose時にrecordsを解放した形)</li>
 * </ol>
 *
 * <p>
 * <b>seal済みセルのリース寿命(E-6増分5a)</b>: セルは
 * 「seal(セルclose)→bind(表終端の列幅確定後)」の窓が表全体の入力期間に
 * わたる。リースはbindのfinally({@code TwoPassBlockBuilder.DeferredBind
 * .bind})で解放される。正常系でbindされない実セルは存在しない——
 * {@code RetainedTableBuilder}のnewContext(TABLE_CELL)は必ず行の
 * セル列へCellContentを追加し、bindRows()は全行・全非extendedセルを
 * 一度ずつbindする(空セルはseal適格判定のEMPTY_RANGEで最初から
 * sealされない。Incremental側の「cols制約でCellContentを作らない」
 * 早期returnはIncremental専用でRetainedには存在しない)。変換中断・
 * 例外時はformatter finallyの{@code LayoutSource.close()}がリース
 * マップごと破棄する。1:1はCELL_RANGE_SEALS == CELL_RANGE_BINDS
 * (DisplayListGoldenTest)が監視する。
 * </p>
 */
class CellContent {
	/**
	 * seal済みセル本文です(E-6増分5a)。sizesスナップショットと
	 * range+leaseは{@link TwoPassBlockBuilder.DeferredBind}が運ぶ
	 * (列幅計算が読む固有寸法は既存IntrinsicMeasurerの模倣値の
	 * スナップショット——「列幅のためのtape再読はしない」codex裁定
	 * §4.3。計測器は録画完了後不変のためclose時スナップショットは
	 * 従来のbind時読みと同値)。
	 */
	private record RangeContent(TableCellBox cellBox, TwoPassBlockBuilder.DeferredBind body) {
	}

	private Object cell;

	public final int rowspan, colspan;

	/**
	 * 実測ビルダーから(colspan は pos から読む)。
	 */
	public CellContent(TwoPassBlockBuilder cellBuilder) {
		this(cellBuilder, ((TableCellBox) cellBuilder.getRootBox()).getTableCellPos().colspan);
	}

	/**
	 * 実測ビルダーから(colspan を明示。固定レイアウトの列切り詰め用)。
	 */
	public CellContent(TwoPassBlockBuilder cellBuilder, int colspan) {
		this.cell = cellBuilder;
		this.rowspan = ((TableCellBox) cellBuilder.getRootBox()).getTableCellPos().rowspan;
		this.colspan = colspan;
	}

	/**
	 * 連結の続き(確定済みセルボックス)から。
	 */
	public CellContent(TableCellBox cell, int rowspan, int colspan) {
		assert rowspan >= 1;
		assert colspan >= 1;
		this.cell = cell;
		this.rowspan = rowspan;
		this.colspan = colspan;
	}

	/**
	 * rowspan で連結されたセルの補完です(P2-2 共有核。両ビルダーの
	 * 同一アルゴリズムの統合 — 差はリストの出所だけだった)。
	 * 前行のセル列を参照し、rowspan が続くセルの継続 CellContent を
	 * 当行へ追加する。
	 *
	 * @param cells      当行のセル列(追記される)
	 * @param upperCells 前行のセル列
	 */
	static void complementRowspan(final java.util.List<CellContent> cells, final java.util.List<?> upperCells) {
		while (upperCells.size() > cells.size()) {
			final CellContent upperCell = (CellContent) upperCells.get(cells.size());
			if (upperCell.rowspan > 1) {
				for (int colspan = upperCell.colspan; colspan >= 1; --colspan) {
					cells.add(new CellContent(upperCell.getCellBox(), upperCell.rowspan - 1, colspan));
				}
			} else {
				break;
			}
		}
	}

	/**
	 * 行のベースライン(先頭アセントの最大)を求めます(P2-4 共有核。
	 * 3箇所の同一ループの統合 — 連結の続きは持ち主の行で数える)。
	 */
	static double maxFirstAscent(final java.util.List<CellContent> cells) {
		double rowAscent = 0;
		for (int i = 0; i < cells.size(); ++i) {
			final CellContent cell = cells.get(i);
			if (cell.isExtended()) {
				continue;
			}
			final double firstAscent = cell.getCellBox().getFirstAscent();
			if (!net.zamasoft.foliojet.layout.util.LayoutUtils.isNone(firstAscent) && firstAscent > rowAscent) {
				rowAscent = firstAscent;
			}
		}
		return rowAscent;
	}

	/**
	 * 行高をセルへ適用します(P2-5 (c) 共有核。3箇所の同一処理の統合)。
	 * 非連結セルごとに連結範囲の行高合計をページ方向寸法として設定し、
	 * 縦位置合わせを行う。
	 *
	 * @param cells     行のセル列
	 * @param rowSizes  行高(単位または行グループの窓)
	 * @param rowIndex  当行の窓内位置
	 * @param rowAscent 行のベースライン(NaN なら適用済みとして省略)
	 * @param vertical  縦書きであれば true
	 */
	static void applyCellExtents(final java.util.List<CellContent> cells, final double[] rowSizes, final int rowIndex,
			final double rowAscent, final boolean vertical) {
		for (int k = 0; k < cells.size(); ++k) {
			final CellContent cell = cells.get(k);
			if (cell.isExtended()) {
				continue;
			}
			final TableCellBox cellBox = cell.getCellBox();
			double size = 0;
			final int rowspan = Math.min(rowSizes.length - rowIndex, cellBox.getTableCellPos().rowspan);
			for (int l = 0; l < rowspan; ++l) {
				size += rowSizes[rowIndex + l];
			}
			if (!Double.isNaN(rowAscent)) {
				cellBox.baseline(rowAscent);
			}
			if (vertical) {
				cellBox.setWidth(size);
			} else {
				cellBox.setHeight(size);
			}
			cellBox.verticalAlign();
		}
	}

	public boolean isExtended() {
		return this.cell instanceof TableCellBox;
	}

	/**
	 * 実測ビルダーを返します。E-6増分5a以降、Retained表のセルはclose時に
	 * seal({@link #sealForRangeBind()})されビルダーを手放しうるため、
	 * Retained側はこれを使わず{@link #getIntrinsicSizes()}/
	 * {@link #bind(BlockBuilder)}を使うこと(現在の呼び出しは
	 * sealが起きないIncremental表のみ)。
	 */
	public TwoPassBlockBuilder getBuilder() {
		return (TwoPassBlockBuilder) this.cell;
	}

	/**
	 * セルclose(録画完了)時のrange sealです(E-6増分5a)。適格
	 * (fail closed判定は{@code TwoPassBlockBuilder.sealBodyForRangeBind}
	 * ——Opaque/絶対配置/multicol/mixed-flow/ネストビルダー(セル内の表は
	 * ここに入る)/範囲穴あき等は不適格)なら、IntrinsicSizes数値を確定して
	 * recordsを解放し、本文をSourceRange(+lease)参照へ切り替える。
	 * 不適格ならビルダー保持を継続する(従来と同一)。
	 */
	void sealForRangeBind() {
		if (!(this.cell instanceof TwoPassBlockBuilder builder)) {
			return;
		}
		builder.sealBodyForRangeBind();
		final TwoPassBlockBuilder.DeferredBind body = builder.detachDeferredBind();
		if (body == null) {
			return; // 不適格: ビルダー保持を継続(fail closed)
		}
		this.cell = new RangeContent((TableCellBox) builder.getRootBox(), body);
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordCellRangeSeal();
	}

	/**
	 * 列幅計算・直交セル寸法が読む固有寸法です(既存IntrinsicMeasurerの
	 * 模倣値。seal済みセルはclose時のスナップショット——同値性は
	 * {@link RangeContent}のjavadoc)。
	 */
	public net.zamasoft.foliojet.layout.sizing.IntrinsicSizes getIntrinsicSizes() {
		if (this.cell instanceof RangeContent range) {
			return range.body().sizes();
		}
		return this.getBuilder().getIntrinsicSizes();
	}

	/**
	 * セル本文を{@code cellBindBuilder}(セルボックス上のBlockBuilder)へ
	 * 構築します(E-6増分5a)。seal済みセルはSegmentExecutor範囲駆動
	 * (4bのbindTwoPassRangeと同経路)、不適格セルは従来のrecords再演。
	 * bindのタイミング・順序は呼び出し側(表終端の列幅確定後の一括bind)の
	 * まま——変わるのは再生元だけ。
	 */
	public void bind(final BlockBuilder cellBindBuilder) {
		if (this.cell instanceof RangeContent range) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordCellRangeBind();
			range.body().bind(cellBindBuilder);
		} else {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordCellLegacyBind();
			this.getBuilder().bind(cellBindBuilder);
		}
	}

	/**
	 * recordsが現に保持しているglyph数の概算です(E-6増分5a、保持量観測
	 * 専用——seal済み・extendedは0)。挙動には影響しない。
	 */
	long retainedGlyphs() {
		return this.cell instanceof TwoPassBlockBuilder builder ? builder.retainedGlyphs() : 0;
	}

	public TableCellBox getCellBox() {
		if (this.isExtended()) {
			return (TableCellBox) this.cell;
		}
		if (this.cell instanceof RangeContent range) {
			return range.cellBox();
		}
		return (TableCellBox) this.getBuilder().getRootBox();
	}
}
