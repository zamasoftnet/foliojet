package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/**
 * 構築中のセル内容です(P2-2: §5.2b 表ビルダー統一の共通 model 第一片。
 * OnePass/TwoPass 両ビルダーの同名内部クラスの統合 — 差は colspan を
 * 引数で受けるか pos から読むかのコンストラクタだけだった)。
 *
 * <p>
 * 実体は次の4態のどれか(E-6増分5a、2026-07-24で第3態を追加——
 * {@code docs/consultations/consult-e6b-remaining-increments-codex.md}
 * §4.2/§4.3)。
 * </p>
 * <ol>
 * <li>実測ビルダー({@link TwoPassBlockBuilder}、未確定内容)</li>
 * <li>連結の続き(rowspan の2行目以降 = extended、確定済みセルボックス)</li>
 * <li>seal済み本文({@link TwoPassBlockBuilder.DeferredBind}: IntrinsicSizes数値+
 * SourceRange(+lease)。Retained表のセルclose時に計測器を手放した形)</li>
 * <li>MAIN後の固有寸法({@link IntrinsicSizes}。本文の所有は終了済み)</li>
 * </ol>
 *
 * <p>セルcloseで確定本文を持ち出す。範囲の所有はMAIN bind・親への吸収・
 * 文書終了時の破棄で終端する。空セルはリースを持たないEmptyとしてsealする。
 * CELL_RANGE_SEALSと各終端カウンタの収支はRangeOnlyInvariantTestで検査する。</p>
 */
class CellContent {
	/** 根箱は全状態で同じ。状態ごとの根箱+本文のラッパーを割り当てない。 */
	private final TableCellBox cellBox;
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
		this.cellBox = (TableCellBox) cellBuilder.getRootBox();
		this.cell = cellBuilder;
		this.rowspan = this.cellBox.getTableCellPos().rowspan;
		this.colspan = colspan;
	}

	/**
	 * 連結の続き(確定済みセルボックス)から。
	 */
	public CellContent(TableCellBox cell, int rowspan, int colspan) {
		assert rowspan >= 1;
		assert colspan >= 1;
		this.cellBox = cell;
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
	static void complementRowspan(final java.util.List<CellContent> cells, final java.util.List<CellContent> upperCells) {
		while (upperCells.size() > cells.size()) {
			final CellContent upperCell = upperCells.get(cells.size());
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
	 * 列計測中のIncremental表のみ)。
	 */
	public TwoPassBlockBuilder getBuilder() {
		return (TwoPassBlockBuilder) this.cell;
	}

	/** セルcloseで確定本文を持ち出し、実測builderを手放す。 */
	void sealForRangeBind() {
		this.sealForRangeBind(false);
	}

	void sealForRangeBind(final boolean sliceText) {
		if (!(this.cell instanceof TwoPassBlockBuilder builder)) {
			return;
		}
		builder.sealCellBodyForRangeBind(sliceText);
		final TwoPassBlockBuilder.DeferredBind body = builder.detachDeferredBind();
		this.cell = body;
		if (body.handle() != null) body.handle().markCell();
	}

	/**
	 * 列幅計算・直交セル寸法が読む固有寸法です(既存IntrinsicMeasurerの
	 * 模倣値。計測器はclose後不変なので、seal時の数値は従来のbind時読みと同値)。
	 */
	public net.zamasoft.foliojet.layout.sizing.IntrinsicSizes getIntrinsicSizes() {
		if (this.cell instanceof IntrinsicSizes sizes) return sizes;
		if (this.cell instanceof TwoPassBlockBuilder.DeferredBind body) {
			return body.sizes();
		}
		return this.getBuilder().getIntrinsicSizes();
	}

	/** 確定本文を配置する。未確定のままの配置は契約違反。 */
	public void bind(final BlockBuilder cellBindBuilder) {
		if (this.cell instanceof IntrinsicSizes) throw new IllegalStateException("消費済みセル本文のbind");
		if (!(this.cell instanceof TwoPassBlockBuilder.DeferredBind body)) {
			throw this.getBuilder().invariant("未sealセルのbind");
		}
		body.bind(cellBindBuilder);
		if (net.zamasoft.foliojet.layout.fragment.ReplayIntent.current()
				== net.zamasoft.foliojet.layout.fragment.ReplayIntent.MAIN) {
			this.cell = body.sizes();
		}
	}

	/**
	 * seal済み本文のDeferredBindを返します(E-6増分5b-1、表Pass B計測
	 * プリミティブ{@link CellPassBMeasurer}用)。未seal(計測中)・
	 * extendedはnull(Pass B対象外)。
	 */
	TwoPassBlockBuilder.DeferredBind rangeBody() {
		return this.cell instanceof TwoPassBlockBuilder.DeferredBind body && body.handle() != null ? body : null;
	}

	/**
	 * 親range化への吸収です(表吸収=codex増分5のコミット相、2026-07-30)。
	 * seal済みのセルだけを処理する——DeferredBindのリースを
	 * 解放し、セル側のSUBSUMED収支を計上した上で、実セル参照だけの保持
	 * (extended相当。bind経路は{@code isExtended}スキップで到達しない)へ
	 * 落とす。未seal(計測中)のセルビルダーは検証相
	 * ({@code TwoPassBlockBuilder.collectAbsorbableSelf})が吸収一覧へ
	 * 直接列挙し、コミット相が{@code subsumeIntoParentRange}するため
	 * ここではno-op。extendedも実セル側が処理するためno-op。
	 */
	void abandonForParentRange() {
		if (this.cell instanceof TwoPassBlockBuilder.DeferredBind body) {
			body.abandonForParentRange();
			this.cell = this.cellBox;
		}
	}

	/**
	 * 表吸収の検証相(副作用なし)がseal済みセルの範囲包含を検査するための
	 * 読み取りです(codex増分5)。seal済み本文でなければnull。
	 */
	TwoPassBlockBuilder.DeferredBind sealedBodyOrNull() {
		return this.cell instanceof TwoPassBlockBuilder.DeferredBind body ? body : null;
	}

	/**
	 * 未seal(計測中)のセルビルダーを返します(表吸収の検証相用。
	 * seal済み・extendedはnull)。
	 */
	TwoPassBlockBuilder unsealedBuilderOrNull() {
		return this.cell instanceof TwoPassBlockBuilder builder ? builder : null;
	}

	/** 範囲本文・空本文だけをscratch計測できる。 */
	boolean isPassBMeasurable() {
		return this.cell instanceof TwoPassBlockBuilder.DeferredBind body
				&& (body.handle() != null || body.isEmpty())
				&& this.getCellBox().canMeasureReplica();
	}

	public TableCellBox getCellBox() {
		return this.cellBox;
	}
}
