package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.sizing.AutoColumnWidths;
import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

import net.zamasoft.foliojet.layout.sizing.ColumnDistribution;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;

import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox.Cell;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.builder.TwoPass;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.TableCollapsedBorders;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * 表を実行計画Retained(全体を保持してからコミットする方式)で構築します
 * (2026-07-19訂正: table-layout:autoに限らず、非FLOW配置・ページ軸寸法
 * 指定・行軸auto寸法等{@link TableRetentionReason}の理由でもこちらへ
 * ルーティングされる。固定列幅も{@code this.fixed}フィールドで扱う——
 * 「自動レイアウト専用」ではない。詳細はTableLayoutのjavadoc・
 * docs/PLAN.md「C4」参照)。
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: RetainedTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class RetainedTableBuilder implements net.zamasoft.foliojet.layout.builder.RetainedTable {
	/**
	 * 構築中のテーブルセルです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: RetainedTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
	 */

	private final boolean vertical, fixed;
	private final LayoutStack layoutStack;
	private final TableBox tableBox;
	private final List<AbstractInnerTableBox> innerTableStack = new ArrayList<AbstractInnerTableBox>();
	private final List<Builder> topCaptions = new ArrayList<Builder>();
	private final List<Builder> bottomCaptions = new ArrayList<Builder>();
	private TableRowGroupBox headerGroup = null;
	private TableRowGroupBox footerGroup = null;
	private TableRowBox firstRowBox = null;
	private final List<TableRowGroupBox> bodyGroups = new ArrayList<TableRowGroupBox>();
	private final Map<TableRowGroupBox, ArrayList<TableRowBox>> rowGroupToRows = new HashMap<TableRowGroupBox, ArrayList<TableRowBox>>();
	private final Map<TableRowBox, ArrayList<CellContent>> rowToCells = new HashMap<TableRowBox, ArrayList<CellContent>>();
	private final Map<TableCellBox, Cell> cellToSource = new HashMap<TableCellBox, Cell>();
	private final List<TableRowGroupBox> rowGroups = new ArrayList<TableRowGroupBox>();
	private TableColumnGroupBox columnGroupBox = null;
	private TableRowBox upperRow = null;
	private TableCollapsedBorders borders = null;

	/**
	 * 現在開いているセルのCellContentです(E-6増分5a、2026-07-24)。
	 * newContext(TABLE_CELL)で設定し、セルclose時の
	 * {@link #sealCellContext}が消費する。セルは行内で逐次(同時に
	 * 1つしか開かない)、ネストした表は自分のRetainedTableBuilderを
	 * 持つため、単一フィールドで足りる。
	 */
	private CellContent pendingSealCell = null;

	/**
	 * 右の境界の中央から左の中央までを基準としたカラムの最小幅、指定幅、推奨幅です。
	 */
	private AutoColumnWidths.Result columnWidths;

	/**
	 * 表Pass B(行計測)のshadow検証フックです(E-6増分5b-1、2026-07-24、
	 * テスト専用——3b-2のLayoutSourceTestHooks流儀)。productionでは
	 * nullのままで挙動不変。shadowテストがセルbindの直前・直後を観測し、
	 * {@link CellPassBMeasurer}の独立計測とbind実寸の一致を検証する。
	 * 設定したテストはfinallyで必ず解除すること(static共有のため)。
	 */
	interface CellBindShadow {
		/** セルbind({@code cell.bind})の直前(列幅適用済み)。 */
		void beforeCellBind(CellContent cell, TableCellBox cellBox, LayoutStack layoutStack, boolean vertical);

		/** セルbind+builder closeの直後。 */
		void afterCellBind(CellContent cell, TableCellBox cellBox, boolean vertical);
	}

	/** テスト専用shadow観測フック(production=null)。 */
	static CellBindShadow cellBindShadow = null;

	private static final byte PARAM_COUNT = 3;

	public RetainedTableBuilder(LayoutStack layoutStack, TableBox tableBox) {
		this.layoutStack = layoutStack;
		this.tableBox = tableBox;
		TableParams tableParams = tableBox.getTableParams();
		this.vertical = tableParams.flow.isVertical();
		this.fixed = tableParams.layout == TableParams.LAYOUT_FIXED
				&& ((this.vertical ? tableParams.size.getHeightType()
						: tableParams.size.getWidthType()) != LengthType.AUTO);
	}

	public IntrinsicSizes getIntrinsicSizes() {
		final TableParams tableParams = this.tableBox.getTableParams();
		double min = this.columnWidths == null ? 0 : this.columnWidths.minLineSize();
		double max = this.columnWidths == null ? 0 : this.columnWidths.maxLineSize();
		// 表自体の指定寸法は固有寸法の下限になる
		if (this.vertical) {
			if (tableParams.size.getHeightType() == LengthType.ABSOLUTE) {
				min = Math.max(min, tableParams.size.getHeight());
				max = Math.max(max, tableParams.size.getHeight());
			}
		} else {
			if (tableParams.size.getWidthType() == LengthType.ABSOLUTE) {
				min = Math.max(min, tableParams.size.getWidth());
				max = Math.max(max, tableParams.size.getWidth());
			}
		}
		return new IntrinsicSizes(min, max, 0);
	}

	public final TableBox getTableBox() {
		return this.tableBox;
	}

	public final void startInnerTable(final AbstractInnerTableBox box) {
		// System.out.println(box.getClass());

		box.setTableParams(this.tableBox.getTableParams());
		switch (box.getType()) {
		case TABLE_COLUMN:
		case TABLE_COLUMN_GROUP: {
			// 列
			final TableColumnBox column = (TableColumnBox) box;
			if (this.innerTableStack.isEmpty()) {
				if (this.columnGroupBox == null) {
					this.columnGroupBox = new TableColumnGroupBox(new InnerTableParams(), new TableColumnPos());
					this.columnGroupBox.setTableParams(this.tableBox.getTableParams());
				}
				this.columnGroupBox.addTableColumn(column);
			} else {
				final TableColumnGroupBox parentColumnGroup = (TableColumnGroupBox) this.innerTableStack
						.get(this.innerTableStack.size() - 1);
				parentColumnGroup.addTableColumn(column);
			}
		}
			break;
		case TABLE_ROW_GROUP: {
			// 行グループ
			final TableRowGroupBox rowGroup = (TableRowGroupBox) box;
			this.rowGroupToRows.put(rowGroup, new ArrayList<TableRowBox>());
			switch (rowGroup.getTableRowGroupPos().rowGroupType) {
			case RowGroupType.HEADER:
				this.headerGroup = rowGroup;
				break;
			case RowGroupType.FOOTER:
				this.footerGroup = rowGroup;
				break;
			case RowGroupType.BODY:
				this.bodyGroups.add(rowGroup);
				break;
			default:
				throw new IllegalStateException();
			}
		}
			break;

		case TABLE_ROW: {
			// 行
			final TableRowGroupBox rowGroup = (TableRowGroupBox) this.innerTableStack
					.get(this.innerTableStack.size() - 1);
			final TableRowBox row = (TableRowBox) box;
			final List<TableRowBox> rows = this.rowGroupToRows.get(rowGroup);
			rows.add(row);
			// 行1つの収集は**実際に進んだ仕事**。保持型の表は全行を読み終える
			// まで1ページも出さないので、ここが進捗の唯一の信号になる
			// (2026-07-27、40万行=37.5秒の無出力区間の正体)
			this.noteTableProgress();
			this.rowToCells.put(row, new ArrayList<CellContent>());
		}
			break;
		default:
			throw new IllegalStateException();
		}
		this.innerTableStack.add(box);
	}

	public final void endInnerTable() {
		final AbstractInnerTableBox box = (AbstractInnerTableBox) this.innerTableStack
				.remove(this.innerTableStack.size() - 1);
		// System.out.println("/"+box.getClass());

		switch (box.getType()) {
		case TABLE_COLUMN:
		case TABLE_COLUMN_GROUP: {
			// 列
		}
			break;
		case TABLE_ROW_GROUP: {
			// 行グループ
			this.upperRow = null;
		}
			break;

		case TABLE_ROW: {
			// 行
			final TableRowBox rowBox = (TableRowBox) box;
			this.complementRowspan(rowBox);
			this.upperRow = rowBox;
			if (this.firstRowBox == null) {
				this.firstRowBox = rowBox;
			}
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	private void complementRowspan(TableRowBox row) {
		if (this.upperRow != null) {
			// rowspanで連結されたセルの補完(共有核 — P2-2)
			CellContent.complementRowspan(this.rowToCells.get(row), this.rowToCells.get(this.upperRow));
		}
	}

	public final Builder newContext(AbstractContainerBox box) {
		final Builder builder = new TwoPassBlockBuilder(this.layoutStack, box);
		((TwoPassBlockBuilder) builder).tagLegacyBindOrigin(box.getType() == BoxType.TABLE_CELL
				? net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin.RETAINED_CELL
				: net.zamasoft.foliojet.layout.fragment.ContinuationStats.LegacyBindOrigin.RETAINED_CAPTION);
		switch (box.getType()) {
		case BLOCK: {
			// キャプション
			switch (((TableCaptionPos) box.getPos()).captionSide) {
			case CaptionSideMode.BEFORE:
				this.topCaptions.add(builder);
				break;

			case CaptionSideMode.AFTER:
				this.bottomCaptions.add(builder);
				break;

			default:
				throw new IllegalStateException();
			}
		}
			break;

		case TABLE_CELL: {
			// セル
			// TODO よこテーブルに縦がある場合は、BlockBuilderで行幅を制限してやらないといけない
			final TableRowBox rowBox = (TableRowBox) this.innerTableStack.get(this.innerTableStack.size() - 1);
			List<CellContent> cells = (ArrayList<CellContent>) this.rowToCells.get(rowBox);
			this.complementRowspan(rowBox);
			CellContent cell = new CellContent((TwoPassBlockBuilder) builder);
			cells.add(cell);
			for (int colspan = cell.colspan; colspan > 1; --colspan) {
				cells.add(new CellContent(cell.getCellBox(), cell.rowspan, colspan));
			}
			// E-6増分5a: セルclose時sealの対象として記憶する
			this.pendingSealCell = cell;
		}
			break;
		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	/**
	 * 親のrange化に吸収済みかです(表吸収=codex増分5、2026-07-30)。
	 * trueのとき{@link #prepareLayout}/{@link #bind}は契約違反——親の
	 * 範囲再生がソースから表全体を再構築するため、この計画が使われる
	 * ことはない。
	 */
	private boolean abandoned;

	/**
	 * 表吸収の検証相です(codex増分5、2026-07-30。<b>副作用なし</b>)。
	 * この記録済みRetained計画が親のrange化に吸収可能かを判定し、
	 * 未sealセルビルダー(とその孫)を{@code out}へ列挙します。
	 * 吸収可能条件(fail closed):
	 * <ul>
	 * <li>キャプションなし(キャプション付き表の親範囲はキャプションの
	 * Opaque記録によりOPAQUE_RANGEで先にrejectされるため構造的に
	 * 到達しないはずだが、二重防壁)</li>
	 * <li>seal済みセルのリースが同一LayoutSource上かつ親範囲に包含</li>
	 * <li>未sealセルの孫records・キャプション等が全て吸収可能</li>
	 * </ul>
	 */
	boolean collectAbsorbableInto(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId,
			final long toId, final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		if (this.abandoned) {
			return false;
		}
		// caption recipe化C4(2026-08-01): キャプションも親range化の吸収対象
		// (C3のclose時sealでSourceRangeBody保持——検証相で親範囲内リースで
		// あることを確かめ、コミット相は親のsubsumeが処理する。吸収された
		// 表計画はabandonForParentRangeでbindRowsごと破棄されるため、
		// subsumed後のcaption bindは発生しない)
		for (int c = 0; c < this.topCaptions.size(); ++c) {
			if (!(this.topCaptions.get(c) instanceof TwoPassBlockBuilder caption)
					|| !caption.collectAbsorbableSelf(log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		for (int c = 0; c < this.bottomCaptions.size(); ++c) {
			if (!(this.bottomCaptions.get(c) instanceof TwoPassBlockBuilder caption)
					|| !caption.collectAbsorbableSelf(log, fromId, toId, out, outTables, ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			final List<TableRowBox> rows = this.rowGroupToRows.get(this.rowGroups.get(i));
			for (int j = 0; j < rows.size(); ++j) {
				final List<CellContent> cells = this.rowToCells.get(rows.get(j));
				for (int k = 0; k < cells.size(); ++k) {
					final CellContent cell = cells.get(k);
					if (cell.isExtended()) {
						continue;
					}
					final TwoPassBlockBuilder.DeferredBind sealed = cell.sealedBodyOrNull();
					if (sealed != null) {
						if (!sealed.within(log, fromId, toId)) {
							return false;
						}
						continue;
					}
					final TwoPassBlockBuilder unsealed = cell.unsealedBuilderOrNull();
					if (unsealed == null || !unsealed.collectAbsorbableSelf(log, fromId, toId, out, outTables,
							ownedAbsoluteAnchors, seen)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * 親のrange化への吸収です(表吸収=codex増分5のコミット相)。
	 * seal済みセルのリースを解放し、以後のprepareLayout/bindを契約違反へ。
	 * 未sealセルビルダーは検証相が親の吸収一覧へ列挙済みで、親側の
	 * コミットがsubsumeするためここでは触れない。
	 */
	void abandonForParentRange() {
		this.abandoned = true;
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			final List<TableRowBox> rows = this.rowGroupToRows.get(this.rowGroups.get(i));
			for (int j = 0; j < rows.size(); ++j) {
				final List<CellContent> cells = this.rowToCells.get(rows.get(j));
				for (int k = 0; k < cells.size(); ++k) {
					cells.get(k).abandonForParentRange();
				}
			}
		}
	}

	/**
	 * セルclose(録画完了点)時のrange sealです(E-6増分5a、2026-07-24——
	 * codex設計§4.2/§4.3)。直前にnewContextで開いたセルのCellContentを、
	 * 適格なら「IntrinsicSizes数値+SourceRange(+lease)」保持へ切り替え、
	 * records(TextImpl glyph列・liveボックス)を手放す。適格判定は
	 * {@code TwoPassBlockBuilder.sealBodyForRangeBind}と同一のfail
	 * closed(セル内の表・float等のネストビルダーはNESTED_BUILDERで
	 * 不適格)。列幅計算({@link #prepareLayout})へはseal時に確定した
	 * 模倣計測(IntrinsicMeasurer)の数値がそのまま渡る——tape再読で
	 * 列幅を出すことはしない。キャプションはOpaque記録のため対象外
	 * (ビルダー保持を継続。pendingセルなしで呼ばれるためここでは無視)。
	 */
	@Override
	public void sealCellContext(final Builder cellBuilder) {
		final CellContent cell = this.pendingSealCell;
		if (cell == null) {
			// キャプション等、seal対象のセルが開いていないコンテキスト
			return;
		}
		this.pendingSealCell = null;
		if (cell.isExtended() || cell.getBuilder() != cellBuilder) {
			// 構造的には起きない(セルは逐次)が、fail closedで無視する
			return;
		}
		cell.sealForRangeBind();
	}

	/**
	 * つぶし境界を生成します。適用規則は CollapsedBorderRules.collapseRow
	 * (OnePass のストリーミング蓄積と同一)で、ここでは全行を一括で
	 * ループするだけ。行・列寸法は assemble で後から設定される。
	 */
	private TableCollapsedBorders createBorders(int columnCount, int headerRowCount, int bodyRowCount,
			int footerRowCount, List<List<TableRowBox>> rowLists, List<List<CellContent>> cellLists) {
		final TableParams params = this.tableBox.getTableParams();
		final BorderAxes ax = this.vertical ? BorderAxes.VERTICAL : BorderAxes.HORIZONTAL;
		final List<Border[]> headerH = new ArrayList<>(), headerV = new ArrayList<>();
		final List<Border[]> bodyH = new ArrayList<>(), bodyV = new ArrayList<>();
		final List<Border[]> footerH = new ArrayList<>(), footerV = new ArrayList<>();
		final int totalRows = headerRowCount + bodyRowCount + footerRowCount;
		int globalRow = 0;
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			final TableRowGroupBox rowGroup = this.rowGroups.get(i);
			final InnerTableParams rowGroupParams = rowGroup.getInnerTableParams();
			final List<TableRowBox> rows = rowLists.get(i);
			final List<Border[]> hborders, vborders;
			switch (rowGroup.getTableRowGroupPos().rowGroupType) {
			case RowGroupType.HEADER:
				hborders = headerH;
				vborders = headerV;
				break;
			case RowGroupType.BODY:
				hborders = bodyH;
				vborders = bodyV;
				break;
			case RowGroupType.FOOTER:
				hborders = footerH;
				vborders = footerV;
				break;
			default:
				throw new IllegalStateException();
			}
			for (int j = 0; j < rows.size(); ++j) {
				final Border[] lineBorder = new Border[columnCount + 1];
				vborders.add(lineBorder);
				final Border[] firstBorder;
				if (hborders.isEmpty()) {
					firstBorder = new Border[columnCount];
					hborders.add(firstBorder);
				} else {
					firstBorder = hborders.get(hborders.size() - 1);
				}
				final Border[] lastBorder = new Border[columnCount];
				hborders.add(lastBorder);

				final boolean groupLastRow = j == rows.size() - 1;
				final List<CellContent> cells = cellLists.get(globalRow);
				final TableRowBox nextRowBox = groupLastRow ? null : rows.get(j + 1);
				final List<CellContent> nextCells = groupLastRow ? null : cellLists.get(globalRow + 1);
				CollapsedBorderRules.collapseRow(firstBorder, lastBorder, lineBorder, ax, params,
						this.columnGroupBox, rowGroupParams, rows.get(j), cells, nextRowBox, nextCells,
						globalRow == 0, globalRow == totalRows - 1, j == 0, groupLastRow, j == 0, !groupLastRow,
						columnCount);
				++globalRow;
			}
		}
		final CollapsedBorderRules.GroupBorders header = CollapsedBorderRules.GroupBorders
				.of(new double[headerRowCount], headerH, headerV, columnCount);
		final CollapsedBorderRules.GroupBorders body = CollapsedBorderRules.GroupBorders.of(new double[bodyRowCount],
				bodyH, bodyV, columnCount);
		final CollapsedBorderRules.GroupBorders footer = CollapsedBorderRules.GroupBorders
				.of(new double[footerRowCount], footerH, footerV, columnCount);
		return new TableCollapsedBorders(new double[columnCount], new double[headerRowCount], header.vborders(),
				header.hborders(), new double[bodyRowCount], body.vborders(), body.hborders(),
				new double[footerRowCount], footer.vborders(), footer.hborders());
	}

	/**
	 * テーブルと各カラムの最大幅、最小幅を確定します。 内側のテーブルから順に実行します。
	 */
	public void prepareLayout() {
		if (this.abandoned) {
			// 表吸収(codex増分5): 親の範囲再生が表を再構築するため到達しない
			throw new IllegalStateException("親のrange化に吸収済みの表計画へのprepareLayout");
		}
		TableParams tableParams = this.tableBox.getTableParams();

		// 行の順番をならす
		if (this.headerGroup != null) {
			this.rowGroups.add(this.headerGroup);
		}
		for (int i = 0; i < this.bodyGroups.size(); ++i) {
			this.rowGroups.add(this.bodyGroups.get(i));
		}
		if (this.footerGroup != null) {
			this.rowGroups.add(this.footerGroup);
		}

		// テーブルの自動レイアウト SPEC CSS 2.1 17.5.2.2
		// カラム数と行数のカウント(葉のカラム位置+スパンの最大)
		int columnCount = 0;
		if (this.columnGroupBox != null) {
			this.tableBox.setTableColumnGroup(this.columnGroupBox);
			final int[] count = { 0 };
			this.columnGroupBox.eachColumn((column, col, span) -> count[0] = Math.max(count[0], col + span));
			columnCount = count[0];
		}

		int headerRowCount = 0, bodyRowCount = 0, footerRowCount = 0;
		List<List<TableRowBox>> rowLists = new ArrayList<List<TableRowBox>>();
		List<List<CellContent>> cellLists = new ArrayList<List<CellContent>>();
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			TableRowGroupBox rowGroup = this.rowGroups.get(i);
			List<TableRowBox> rows = this.rowGroupToRows.get(rowGroup);
			rowLists.add(rows);
			for (int j = 0; j < rows.size(); ++j) {
				TableRowBox row = rows.get(j);
				List<CellContent> cells = this.rowToCells.get(row);
				cellLists.add(cells);
				columnCount = Math.max(columnCount, cells.size());
			}
			switch (rowGroup.getTableRowGroupPos().rowGroupType) {
			case RowGroupType.HEADER:
				headerRowCount += rows.size();
				break;
			case RowGroupType.BODY:
				bodyRowCount += rows.size();
				break;
			case RowGroupType.FOOTER:
				footerRowCount += rows.size();
				break;
			default:
				throw new IllegalStateException();
			}
		}
		int rowCount = headerRowCount + bodyRowCount + footerRowCount;

		// 境界線
		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// つぶし境界
			this.borders = this.createBorders(columnCount, headerRowCount, bodyRowCount, footerRowCount, rowLists,
					cellLists);
			this.tableBox.setCollapsedBorders(this.borders);
		}
		this.tableBox.calculateFrame(this.layoutStack.getFlowBox().getLineSize());

		final double tableFrame, lineBorderSpacing;
		if (this.vertical) {
			tableFrame = this.tableBox.getFrame().getFrameHeight();
			lineBorderSpacing = tableParams.borderSpacingV;
		} else {
			tableFrame = this.tableBox.getFrame().getFrameWidth();
			lineBorderSpacing = tableParams.borderSpacingH;
		}

		// CSS 2.1 17.5.2.2 [Column widths are determined as follows] #1,#2
		final AutoColumnWidths widths = new AutoColumnWidths(columnCount);
		// カラムグループの幅計算
		if (this.columnGroupBox != null) {
			// 指定幅
			this.columnGroupBox.eachColumn((column, col, span) -> {
				final InnerTableParams colParams = column.getInnerTableParams();
				switch (colParams.size.getType()) {
				case ABSOLUTE:
					widths.specFixed(col, span, colParams.size.getLength() + lineBorderSpacing);
					break;
				case RELATIVE:
					widths.specPercent(col, span, colParams.size.getLength());
					break;
				case MIXED:
					// calc()による絶対長さ+割合混在の列幅は、このAUTO-layout列幅
					// 指定APIが前提とする「絶対 or 割合の二択」に収まらないため
					// 未対応。AUTO(指定なし)として扱い安全側に倒す(docs/PLAN.md参照)。
				case AUTO:
					// ignore
					break;
				default:
					throw new IllegalStateException();
				}
				if (colParams.minSize.getType() == LengthType.ABSOLUTE) {
					widths.colMin(col, colParams.minSize.getLength());
				}
				if (colParams.maxSize.getType() == LengthType.ABSOLUTE) {
					widths.colMax(col, colParams.maxSize.getLength());
				}
			});
		}

		// セルの幅計算
		int row = 0;
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			List<TableRowBox> rows = this.rowGroupToRows.get(this.rowGroups.get(i));
			for (int j = 0; j < rows.size(); ++j) {
				List<CellContent> cells = this.rowToCells.get(rows.get(j));
				// 指定幅
				for (int col = 0; col < cells.size(); ++col) {
					final CellContent cell = cells.get(col);
					if (cell.isExtended()) {
						continue;
					}
					final int span = cell.colspan;
					final TableCellBox cellBox = cell.getCellBox();
					final BlockParams cellParams = cellBox.getBlockParams();
					final TableCellPos cellPos = cellBox.getTableCellPos();
					// セル間隔(共有核 — P2-5 (c))
					final AbsoluteInsets cellSpacing = tableParams.borderCollapse == TableParams.BORDER_SEPARATE
							? CollapsedBorderRules.separateSpacing(tableParams)
							: CollapsedBorderRules.gridSpacing(this.borders, row, col, cellPos.rowspan,
									cellPos.colspan, rowCount, columnCount, this.vertical);
					cellBox.prepareLayout(this.layoutStack.getFlowBox().getLineSize(), this.tableBox, cellSpacing);

					final double cellFrame;
					if (this.vertical) {
						cellFrame = cellBox.getFrame().getFrameHeight();
					} else {
						cellFrame = cellBox.getFrame().getFrameWidth();
					}
					// E-6増分5a: seal済みセルはclose時に確定した模倣計測の
					// スナップショットを読む(従来のビルダー経由読みと同値)
					final IntrinsicSizes cellSizes = cell.getIntrinsicSizes();
					double min, des;
					if (cellParams.flow.isVertical() != this.vertical) {
						min = des = cellSizes.minPage();
					} else {
						min = cellSizes.minContent();
						des = cellSizes.maxContent();
					}
					min += cellFrame;
					des += cellFrame;
					double spec = 0;
					byte type = AutoColumnWidths.COLUMN_TYPE_DES;

					if (this.vertical) {
						switch (cellParams.size.getHeightType()) {
						case ABSOLUTE:
							type = AutoColumnWidths.COLUMN_TYPE_FIX;
							spec = cellParams.size.getHeight() + cellFrame;
							break;
						case RELATIVE:
							type = AutoColumnWidths.COLUMN_TYPE_PCT;
							spec = cellParams.size.getHeight();
							break;
						case MIXED:
							// calc()混在の表セル高さはAUTO-layoutの列高さ交渉アルゴリズムが
							// 前提とする「絶対 or 割合の二択」に収まらないため未対応。
							// AUTOと同じ扱いにして安全側に倒す(docs/PLAN.md参照)。
						case AUTO:
							spec = des;
							break;
						default:
							throw new IllegalStateException();
						}
						if (cellParams.minSize.getHeightType() == LengthType.ABSOLUTE) {
							double minSize = cellParams.minSize.getHeight() + cellFrame;
							min = Math.max(minSize, min);
							des = Math.max(minSize, des);
						}
						if (cellParams.maxSize.getHeightType() == LengthType.ABSOLUTE) {
							double maxSize = cellParams.maxSize.getHeight() + cellFrame;
							min = Math.min(maxSize, min);
							des = Math.min(maxSize, des);
							if (type == AutoColumnWidths.COLUMN_TYPE_FIX) {
								spec = Math.min(maxSize, spec);
							}
						}
					} else {
						switch (cellParams.size.getWidthType()) {
						case ABSOLUTE:
							type = AutoColumnWidths.COLUMN_TYPE_FIX;
							spec = cellParams.size.getWidth() + cellFrame;
							break;
						case RELATIVE:
							type = AutoColumnWidths.COLUMN_TYPE_PCT;
							spec = cellParams.size.getWidth();
							break;
						case MIXED:
							// calc()混在の表セル幅はAUTO-layoutの列幅交渉アルゴリズムが
							// 前提とする「絶対 or 割合の二択」に収まらないため未対応。
							// AUTOと同じ扱いにして安全側に倒す(docs/PLAN.md参照)。
						case AUTO:
							spec = des;
							break;
						default:
							throw new IllegalStateException();
						}
						if (cellParams.minSize.getWidthType() == LengthType.ABSOLUTE) {
							double minSize = cellParams.minSize.getWidth() + cellFrame;
							min = Math.max(minSize, min);
							des = Math.max(minSize, des);
						}
						if (cellParams.maxSize.getWidthType() == LengthType.ABSOLUTE) {
							double maxSize = cellParams.maxSize.getWidth() + cellFrame;
							min = Math.min(maxSize, min);
							des = Math.min(maxSize, des);
							if (type == AutoColumnWidths.COLUMN_TYPE_FIX) {
								spec = Math.min(maxSize, spec);
							}
						}
					}
					if (cellParams.boxSizing == BoxSizingMode.BORDER_BOX && type == AutoColumnWidths.COLUMN_TYPE_FIX) {
						spec -= cellFrame;
					}

					widths.cell(col, span, min, des, type, spec);
				}
				++row;
			}
		}

		this.columnWidths = widths.finish(tableFrame);

		// E-6増分1(2026-07-24): 保持形状のhigh-water観測。spill閾値・
		// 対象選定の実測基盤(読み取り・max更新のみ、挙動には影響しない)
		int realCellCount = 0;
		long retainedCellGlyphs = 0;
		for (int i = 0; i < cellLists.size(); ++i) {
			final List<CellContent> cells = cellLists.get(i);
			for (int j = 0; j < cells.size(); ++j) {
				final CellContent cell = cells.get(j);
				if (!cell.isExtended()) {
					++realCellCount;
					// E-6増分5a: 表終端時点でrecordsが保持しているglyph数の
					// 1表合計(seal済みセルは0)。セルrange化の効果の実測
					retainedCellGlyphs += cell.retainedGlyphs();
				}
			}
		}
		TableBuildStats.reportRetainedTableShape(rowCount, realCellCount, (long) rowCount * columnCount, headerRowCount,
				footerRowCount, widths.colspanConstraintCount());
		TableBuildStats.reportRetainedCellGlyphRetention(retainedCellGlyphs);
	}

	/**
	 * テーブルを構築します。 外側のテーブルから順に実行します。
	 * 
	 * @param builder
	 */
	/** 表の形(寸法・列幅・匿名ブロック)です(bind の段間受け渡し)。 */
	private record TableShape(BlockBuilder anonBuilder, AbstractBlockBox blockBox, double tableSize,
			double[] columnSizes, double specifiedPageSize, double tableInnerSize) {
	}

	public void bind(final net.zamasoft.foliojet.layout.builder.Builder host) {
		if (this.abandoned) {
			// 表吸収(codex増分5): 親の範囲再生が表を再構築するため到達しない
			throw new IllegalStateException("親のrange化に吸収済みの表計画へのbind");
		}
		// bindは実測済み内容を実レイアウトへ再駆動する操作で、hostは常に
		// BlockBuilder(直接のaddTableでも、TwoPassBlockBuilderのTableEvent
		// 再生でも、渡ってくるのは実ビルダー)。ここで一度だけ絞り込む。
		// 完全な型伝播(bind連鎖とSourceReplayer.bindTwoPassRangeの
		// Builder化)はA-2bとしてPLAN.md §1.5に記録済み
		final BlockBuilder builder = (BlockBuilder) host;
		final TableShape shape = this.resolveShape(builder);
		final int rowCount = this.bindRows(shape);
		this.assemble(builder, shape, rowCount);
	}

	/**
	 * 表と列の寸法を解決し、匿名ブロックを開きます(P2-5 (a): bind 第1段)。
	 */
	private TableShape resolveShape(final BlockBuilder builder) {
		final TableParams tableParams = this.tableBox.getTableParams();
		final AbstractContainerBox containerBox = this.layoutStack.getFlowBox();
		final double lineSize = containerBox.getBlockParams().flow.isVertical() == tableParams.flow.isVertical() ? containerBox.getLineSize()
						: (this.vertical ? this.layoutStack.getFixedHeight() : this.layoutStack.getFixedWidth());
		// テーブル幅
		double tableSize;
		final double tableFrame, lineBorderSpacing;
		if (this.vertical) {
			// 縦書き
			tableSize = LayoutUtils.computeDimensionHeight(tableParams.size, lineSize);
			double minSize = LayoutUtils.computeDimensionHeight(tableParams.minSize, lineSize);
			tableSize = Math.max(minSize, tableSize);
			double maxSize = LayoutUtils.computeDimensionHeight(tableParams.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxSize) && !LayoutUtils.isNone(tableSize)) {
				tableSize = Math.min(maxSize, tableSize);
			}
			if (tableParams.size.getHeightType() != LengthType.AUTO) {
				tableSize += this.tableBox.getFrame().margin.getFrameHeight();
			}
			tableFrame = this.tableBox.getFrame().getFrameHeight();
			lineBorderSpacing = tableParams.borderSpacingV;
		} else {
			// 横書き
			tableSize = LayoutUtils.computeDimensionWidth(tableParams.size, lineSize);
			double minSize = LayoutUtils.computeDimensionWidth(tableParams.minSize, lineSize);
			tableSize = Math.max(minSize, tableSize);
			double maxSize = LayoutUtils.computeDimensionWidth(tableParams.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxSize) && !LayoutUtils.isNone(tableSize)) {
				tableSize = Math.min(maxSize, tableSize);
			}
			if (tableParams.size.getWidthType() != LengthType.AUTO) {
				tableSize += this.tableBox.getFrame().margin.getFrameWidth();
			}
			tableFrame = this.tableBox.getFrame().getFrameWidth();
			lineBorderSpacing = tableParams.borderSpacingH;
		}

		// 匿名ブロック開始
		final AbstractBlockBox blockBox = this.tableBox.getBlockBox();
		BlockBuilder anonBuilder = null;
		switch (blockBox.getPos().getType()) {
		case FLOW: {
			FlowBlockBox flowBox = (FlowBlockBox) blockBox;
			builder.startFlowBlock(flowBox);
			anonBuilder = builder;
		}
			break;
		case INLINE: {
			InlineBlockBox inlineBox = (InlineBlockBox) blockBox;
			anonBuilder = new BlockBuilder(this.layoutStack, inlineBox);
			inlineBox.shrinkToFit(builder, new IntrinsicSizes(lineSize, lineSize, 0), false);
		}
			break;
		case FLOAT: {
			FloatBlockBox floatingBox = (FloatBlockBox) blockBox;
			anonBuilder = new BlockBuilder(this.layoutStack, floatingBox);
			floatingBox.shrinkToFit(builder, new IntrinsicSizes(lineSize, lineSize, 0), false);
		}
			break;
		case ABSOLUTE: {
			AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) blockBox;
			anonBuilder = new BlockBuilder(this.layoutStack, absoluteBox);
			final AbstractContainerBox cBox;
			if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				cBox = builder.getPageContext().getRootBox();
			} else {
				cBox = builder.getContextBox();
			}
			absoluteBox.shrinkToFit(cBox, new IntrinsicSizes(lineSize, lineSize, 0));
		}
			break;
		default:
			new IllegalStateException();
		}

		final int columnCount = this.columnWidths.mins().length;
		double[] columnSizes;
		if (this.fixed) {
			// 固定レイアウト
			if (LayoutUtils.isNone(tableSize)) {
				tableSize = lineSize;
			}
			tableSize -= tableFrame;
			if (this.columnGroupBox != null) {
				this.tableBox.setTableColumnGroup(this.columnGroupBox);
			}
			// 行方向の境界間隔は論理軸で採る(旧実装は縦書きでも
			// borderSpacingH を加算していた — OnePass と同じ論理軸へ正規化。
			// 0390-writing-mode/vert-fixed-colgroup-spacing.html で固定)
			final FixedColumnWidths.Result result = FixedTableSizing.resolve(this.columnGroupBox,
					this.rowToCells.get(this.firstRowBox), columnCount, tableSize,
					tableParams.borderCollapse == TableParams.BORDER_SEPARATE, lineBorderSpacing,
					this::fixedCellSpec);
			columnSizes = result.sizes();
			tableSize = result.innerSize() + tableFrame;
		} else {
			// 自動レイアウト(共有核 — P2-4)
			final AutoColumnWidths.Sized sized = this.columnWidths.resolve(tableSize, blockBox.getLineSize(),
					tableFrame, lineBorderSpacing, tableParams.borderCollapse == TableParams.BORDER_SEPARATE);
			tableSize = sized.tableSize();
			columnSizes = sized.columnSizes();
		}

		final double specifiedPageSize;
		if (this.vertical) {
			// 縦書き
			switch (tableParams.size.getWidthType()) {
			case ABSOLUTE:
				specifiedPageSize = tableParams.size.getWidth() - this.tableBox.getFrame().getFrameWidth();
				break;
			case RELATIVE:
			case MIXED:
				specifiedPageSize = LayoutUtils.computeDimensionWidth(tableParams.size,
						this.layoutStack.getFixedWidth());
				break;
			case AUTO:
				specifiedPageSize = 0;
				break;
			default:
				throw new IllegalStateException();
			}
		} else {
			// 横書き
			switch (tableParams.size.getHeightType()) {
			case ABSOLUTE:
				specifiedPageSize = tableParams.size.getHeight() - this.tableBox.getFrame().getFrameHeight();
				break;
			case RELATIVE:
			case MIXED:
				specifiedPageSize = LayoutUtils.computeDimensionHeight(tableParams.size,
						this.layoutStack.getFixedHeight());
				break;
			case AUTO:
				specifiedPageSize = 0;
				break;
			default:
				throw new IllegalStateException();
			}
		}
		final double tableInnerSize = tableSize - tableFrame;

		assert !LayoutUtils.isNone(tableSize);
		switch (blockBox.getPos().getType()) {
		case FLOW: {
			FlowBlockBox flowBox = (FlowBlockBox) blockBox;
			flowBox.shrinkToFit(builder, new IntrinsicSizes(tableSize, tableSize, 0), true);
			break;
		}
		case INLINE: {
			InlineBlockBox inlineBox = (InlineBlockBox) blockBox;
			inlineBox.shrinkToFit(builder, new IntrinsicSizes(tableSize, tableSize, 0), true);
		}
			break;
		case FLOAT: {
			FloatBlockBox floatingBox = (FloatBlockBox) blockBox;
			floatingBox.shrinkToFit(builder, new IntrinsicSizes(tableSize, tableSize, 0), true);
		}
			break;
		case ABSOLUTE: {
			AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) blockBox;
			final AbstractContainerBox cBox;
			if (absoluteBox.getAbsolutePos().fiducial != Fiducial.CONTEXT) {
				cBox = builder.getPageContext().getRootBox();
			} else {
				cBox = builder.getContextBox();
			}
			absoluteBox.shrinkToFit(cBox, new IntrinsicSizes(tableSize, tableSize, 0));
		}
			break;

		default:
			throw new IllegalStateException();
		}
		return new TableShape(anonBuilder, blockBox, tableSize, columnSizes, specifiedPageSize, tableInnerSize);
	}

	/**
	 * キャプションと行群をバインドし、行高を確定します(bind 第2段)。
	 *
	 * @return 行数
	 */
	private int bindRows(final TableShape shape) {
		final TableParams tableParams = this.tableBox.getTableParams();
		final BlockBuilder anonBuilder = shape.anonBuilder();
		final double[] columnSizes = shape.columnSizes();
		final double specifiedPageSize = shape.specifiedPageSize();
		final double tableInnerSize = shape.tableInnerSize();
		// 上部キャプション
		for (int i = 0; i < this.topCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.topCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			anonBuilder.startFlowBlock(captionBox);
			captionBuilder.bind(anonBuilder);
			anonBuilder.endFlowBlock();
		}

		// ヘッダ・内容・フッタ
		int rowCount = 0; // 行数
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			List<TableRowBox> rows = this.rowGroupToRows.get(rowGroups.get(i));
			rowCount += rows.size();
		}

		// E-6増分5b-2(2026-07-24): 表Pass C(行単位逐次bind)の適格判定
		// (表単位、fail closed——codex設計§4.4)。適格なら以降の行高計算は
		// bindせずPass Bのscratch計測値だけを読み、bindは行高確定後の
		// 「セル高さ確定」ループで行ごとに行う(Pass C)。不適格なら従来の
		// 「行高計算前の全セル一括bind」のまま(計算コードは両経路共有——
		// 差し替わるのは入力源とbind時点だけ)
		final boolean rowSequentialBind = this.isRowSequentialBindEligible();
		if (rowSequentialBind) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTablePassC();
		} else {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTableLegacyBindRows();
		}
		// Pass B計測値(実セルboxごとの使用ページ方向寸法)。従来経路ではnull
		final Map<TableCellBox, Double> measuredPageAxis = rowSequentialBind
				? new IdentityHashMap<TableCellBox, Double>()
				: null;

		// 行高さの計算
		double[] rowRatios = new double[rowCount]; // パーセント高さ
		double rowSizeSum = 0; // 行高さの合計
		{
			int rowIndex = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
				List<TableRowBox> rows = this.rowGroupToRows.get(rowGroupBox);

				// 連結された行
				Map<Rowspan, Rowspan> rowspans = new HashMap<Rowspan, Rowspan>();
				List<Rowspan> rowspanList = new ArrayList<Rowspan>();
				boolean[] noAdjRows = new boolean[rows.size()];
				boolean[] autoRows = new boolean[rows.size()];

				// 行高さ/セルのレイアウト
				for (int j = 0; j < rows.size(); ++j) {
					TableRowBox rowBox = rows.get(j);
					double rowSize;

					// 指定された行高さの計算(共有核 — P2-5 (c))
					final RowLayoutEngine.RowSpec rowSpec = RowLayoutEngine.rowSpec(rowBox.getInnerTableParams());
					rowSize = rowSpec.size();
					rowRatios[rowIndex] = rowSpec.ratio();
					if (rowSpec.auto()) {
						autoRows[j] = true;
					}

					// セル内のレイアウト
					List<CellContent> cells = this.rowToCells.get(rowBox);
					for (int k = 0; k < cells.size(); ++k) {
						CellContent cell = cells.get(k);
						int span = cell.colspan;
						TableCellBox cellBox = cell.getCellBox();
						if (cell.isExtended()) {
							k += span - 1;
							Cell rcell = (Cell) this.cellToSource.get(cellBox);
							// System.err.println(j+"/"+k+"/"+rcell.getSource());
							this.cellToSource.put(cellBox, rowBox.addTableExtendedCell(rcell));
							continue;
						}
						final BlockParams cellParams = cellBox.getBlockParams();
						if (this.vertical) {
							if (cellParams.size.getWidthType() == LengthType.RELATIVE) {
								int rowspan = Math.min(rows.size() - j, cellBox.getTableCellPos().rowspan);
								for (int l = 0; l < rowspan; ++l) {
									rowRatios[rowIndex + l] = Math.max(rowRatios[rowIndex + l],
											cellParams.size.getWidth() / rowspan);
								}
							}
						} else {
							if (cellParams.size.getHeightType() == LengthType.RELATIVE) {
								int rowspan = Math.min(rows.size() - j, cellBox.getTableCellPos().rowspan);
								for (int l = 0; l < rowspan; ++l) {
									rowRatios[rowIndex + l] = Math.max(rowRatios[rowIndex + l],
											cellParams.size.getHeight() / rowspan);
								}
							}
						}

						// セルの中身を再構築(軸寸法は共有核 TableCellMetrics)
						final double size = TableCellMetrics.spannedLineSize(columnSizes, k, span);
						k += span - 1;
						TableCellMetrics.applyLineAxis(cellBox, cell::getIntrinsicSizes, size, this.vertical,
								tableParams);
						if (measuredPageAxis != null) {
							// E-6増分5b-2 Pass B: bindせずscratch計測(複製box上に
							// 作った木は値の採取後に破棄——この時点でbind済みセル
							// 本文木は1つも存在しない)。行高計算はこの計測値だけを
							// 読む。bind実寸とのbit一致は5b-1の
							// RetainedCellPassBShadowTestで実証済み
							final CellPassBMeasurer.Result measured = CellPassBMeasurer.measure(cell, this.layoutStack,
									this.vertical);
							if (measured == null) {
								// isRowSequentialBindEligibleで適格判定済みのため起きない
								throw new IllegalStateException("Pass C適格表のセルがPass B計測できません");
							}
							measuredPageAxis.put(cellBox, measured.pageAxisSize());
							net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTablePassBCellMeasure();
						} else {
							// 従来経路: 行高計算前の一括bind
							// E-6増分5a: seal済みセルはSegmentExecutor範囲駆動、
							// 不適格セルは従来のrecords再演(CellContent.bindが分岐)
							this.bindCell(cell, cellBox);
						}

						this.cellToSource.put(cellBox, rowBox.addTableSourceCell(cellBox));
						int cellRowspan = Math.min(rows.size() - j, cell.rowspan);
						if (cellRowspan <= 1) {
							// 連結されない行
							noAdjRows[j] = true;
						} else {
							// 連結された行(連結では％高さはautoとする)
							// 要求寸法・登録とも共有核へ(A-4)
							final double cellSize = RowLayoutEngine.demandPageSize(
									this.boundPageAxisSize(measuredPageAxis, cellBox), cellParams, cellBox,
									this.vertical);
							RowLayoutEngine.addSpannedDemand(rowspans, rowspanList, j, cellRowspan, cellSize);
						}
					}

					// ベースラインをそろえる
					for (int k = 0; k < cells.size(); ++k) {
						final CellContent cell = cells.get(k);
						if (cell.isExtended()) {
							continue;
						}
						final TableCellBox cellBox = cell.getCellBox();
						// System.err.println(rowIndex+"/"+rowAscent);
						int cellRowspan = Math.min(rows.size() - j, cell.rowspan);
						if (cellRowspan <= 1) {
							final BlockParams cellParams = cellBox.getBlockParams();
							double cellSize;
							if (this.vertical) {
								cellSize = this.boundPageAxisSize(measuredPageAxis, cellBox);
								if (cellParams.size.getWidthType() == LengthType.ABSOLUTE) {
									double width = cellParams.size.getWidth();
									cellSize = Math.max(cellSize, width);
								}
							} else {
								cellSize = this.boundPageAxisSize(measuredPageAxis, cellBox);
								if (cellParams.size.getHeightType() == LengthType.ABSOLUTE) {
									double height = cellParams.size.getHeight();
									cellSize = Math.max(cellSize, height);
								}
							}
							rowSize = Math.max(rowSize, cellSize);
						}
					}

					rowBox.setPageSize(rowSize);
					++rowIndex;
				}

				// rowspanで連結された行の高さの計算(共有エンジン — P2-2)。
				// rowRatios はグローバル添字で書かれるため、当グループの
				// スライスを渡す(旧実装は 0 起点=先頭グループの比率を
				// 読んでおり、2つ目以降のグループの %行に分配されなかった。
				// 0242-table-height/percent-rowspan-groups.html で是正)
				Collections.sort(rowspanList, Rowspan.SPAN_COMPARATOR);
				{
					final int groupStart = rowIndex - rows.size();
					final double[] rowSizes = new double[rows.size()];
					for (int j = 0; j < rows.size(); ++j) {
						rowSizes[j] = rows.get(j).getPageSize();
					}
					RowLayoutEngine.distributeSpannedRowSizes(rowSizes, rowspanList, noAdjRows, autoRows,
							java.util.Arrays.copyOfRange(rowRatios, groupStart, rowIndex));
					for (int j = 0; j < rows.size(); ++j) {
						rows.get(j).setPageSize(rowSizes[j]);
					}
				}
				// 内容の高さ計算
				for (int j = 0; j < rows.size(); ++j) {
					TableRowBox rowBox = rows.get(j);
					rowSizeSum += rowBox.getPageSize();
				}
			}
		}

		// 行のパーセント高さ計算(共有エンジン — P2-4)
		{
			final double[] rowSizes = new double[rowCount];
			int rowIndex = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				List<TableRowBox> rows = this.rowGroupToRows.get(rowGroups.get(i));
				for (int j = 0; j < rows.size(); ++j) {
					rowSizes[rowIndex++] = rows.get(j).getPageSize();
				}
			}
			rowSizeSum += RowLayoutEngine.distributePercentRowSizes(rowSizes, rowRatios, specifiedPageSize,
					specifiedPageSize - rowSizeSum);
			rowIndex = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				List<TableRowBox> rows = this.rowGroupToRows.get(rowGroups.get(i));
				for (int j = 0; j < rows.size(); ++j) {
					rows.get(j).setPageSize(rowSizes[rowIndex++]);
				}
			}
		}

		// 行グループ高さを適用(共有エンジン — P2-4)
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
			InnerTableParams params = rowGroupBox.getInnerTableParams();
			if (params.size.getType() != LengthType.ABSOLUTE) {
				continue;
			}
			List<TableRowBox> rows = this.rowGroupToRows.get(rowGroupBox);
			final double[] rowSizes = new double[rows.size()];
			for (int j = 0; j < rows.size(); ++j) {
				rowSizes[j] = rows.get(j).getPageSize();
			}
			// 戻り値(増分)は以降どこにも読まれないため破棄する(P2、外部設計レビュー2026-07-19で発見:
			// 直後の「テーブル高さを適用」ブロックはrowBox.getPageSize()から都度読み直すため
			// rowSizeSumのこれ以降の値は死んでいた)
			RowLayoutEngine.distributeGroupSize(rowSizes, params.size.getLength());
			for (int j = 0; j < rows.size(); ++j) {
				rows.get(j).setPageSize(rowSizes[j]);
			}
		}

		// テーブル高さを適用(共有エンジン — P2-4)。自動行の判定は
		// 指定型の直判定(%0 指定行を自動行に数えた旧 autoRowCount とは
		// 分岐条件が異なり得るが、分配対象の選別とは元々不整合だった —
		// 一貫した直判定へ正規化)
		{
			final double[] rowSizes = new double[rowCount];
			final boolean[] autoRows = new boolean[rowCount];
			int rowIndex = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				List<TableRowBox> rows = this.rowGroupToRows.get(rowGroups.get(i));
				for (int j = 0; j < rows.size(); ++j) {
					final TableRowBox rowBox = rows.get(j);
					rowSizes[rowIndex] = rowBox.getPageSize();
					autoRows[rowIndex] = rowBox.getInnerTableParams().size.getType() == LengthType.AUTO;
					++rowIndex;
				}
			}
			RowLayoutEngine.distributeTableSize(rowSizes, autoRows, specifiedPageSize);
			rowIndex = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				List<TableRowBox> rows = this.rowGroupToRows.get(rowGroups.get(i));
				for (int j = 0; j < rows.size(); ++j) {
					rows.get(j).setPageSize(rowSizes[rowIndex++]);
				}
			}
		}

		// セル高さ確定(共有核 — P2-5 (c))
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			TableRowGroupBox rowGroup = this.rowGroups.get(i);
			List<TableRowBox> rows = this.rowGroupToRows.get(rowGroup);
			final double[] groupRowSizes = new double[rows.size()];
			for (int j = 0; j < rows.size(); ++j) {
				groupRowSizes[j] = rows.get(j).getPageSize();
			}
			for (int j = 0; j < rows.size(); ++j) {
				TableRowBox rowBox = rows.get(j);
				rowBox.setLineSize(tableInnerSize);
				rowGroup.addTableRow(rowBox);
				// 行1つの確定は**実際に進んだ仕事**(2026-07-27、締切の進捗信号)
				this.noteTableProgress();
				final List<CellContent> cells = this.rowToCells.get(rowBox);
				if (measuredPageAxis != null) {
					// E-6増分5b-2 Pass C: 行単位の逐次bind。確定行高の適用
					// (applyCellExtents)・baseline整列(maxFirstAscent)の直前に
					// 当行の実セルをbindする——bind後のセル実寸・firstAscentは
					// Pass B計測値とbit一致のため、以降が読む値は従来経路と
					// 同一。bind順(行順・行内セル順)も従来と同一
					this.bindRowCells(cells);
				}
				CellContent.applyCellExtents(cells, groupRowSizes, j, CellContent.maxFirstAscent(cells),
						this.vertical);
			}
		}
		return rowCount;
	}

	/**
	 * 表Pass C(行単位逐次bind)の表単位適格判定です(E-6増分5b-2、
	 * 2026-07-24——codex設計§4.4のPass B/C。fail closed)。適格条件:
	 * <ul>
	 * <li>全実セルがPass B計測可能({@link CellContent#isPassBMeasurable}:
	 * seal済みrange、またはrecords空の空セル。ネストビルダー含みセル等の
	 * seal不適格セル・段組セルが1つでもあれば表全体を従来経路へ)</li>
	 * </ul>
	 *
	 * <p>
	 * 2026-07-30(DP増分5): 旧「キャプションなし」条件は撤去した。
	 * キャプションのbindは行処理の完全に外側(上部=行高計算前・下部=
	 * addBound後)にあり、Pass C切替の影響を受けない——
	 * {@code RetainedCellPassBShadowTest}のキャプション付き表fixtureで
	 * Pass B計測値とlegacy一括bind実寸のbit一致(maxDiff=0.0)を証明の上で
	 * 解禁した。キャプション自身のbind(records再演)はこの判定の対象外の
	 * ままである。
	 * </p>
	 */
	private boolean isRowSequentialBindEligible() {
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			final List<TableRowBox> rows = this.rowGroupToRows.get(this.rowGroups.get(i));
			for (int j = 0; j < rows.size(); ++j) {
				final List<CellContent> cells = this.rowToCells.get(rows.get(j));
				for (int k = 0; k < cells.size(); ++k) {
					final CellContent cell = cells.get(k);
					if (cell.isExtended()) {
						continue;
					}
					if (!cell.isPassBMeasurable()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * 行高計算が読むセルの使用ページ方向寸法です(E-6増分5b-2)。Pass C表
	 * ({@code measured != null})ではPass Bのscratch計測値(bind実寸との
	 * bit一致は5b-1で実証済み)、従来経路ではbind済みセルboxの実寸。
	 */
	private double boundPageAxisSize(final Map<TableCellBox, Double> measured, final TableCellBox cellBox) {
		if (measured != null) {
			return measured.get(cellBox);
		}
		return this.vertical ? cellBox.getWidth() : cellBox.getHeight();
	}

	/**
	 * セル1つをbindします(従来経路の一括bindとPass Cの行順bindの共有核。
	 * E-6増分5a: seal済みセルはSegmentExecutor範囲駆動、不適格セルは
	 * records再演——{@code CellContent.bind}が分岐。E-6増分5b-1:
	 * shadow検証フック(テスト専用、production=null)はbindの直前・直後を
	 * 観測する)。
	 */
	private void bindCell(final CellContent cell, final TableCellBox cellBox) {
		final CellBindShadow shadow = cellBindShadow;
		if (shadow != null) {
			shadow.beforeCellBind(cell, cellBox, this.layoutStack, this.vertical);
		}
		final BlockBuilder cellBindBuilder = new BlockBuilder(this.layoutStack, cellBox);
		cell.bind(cellBindBuilder);
		cellBindBuilder.close();
		if (shadow != null) {
			shadow.afterCellBind(cell, cellBox, this.vertical);
		}
	}

	/**
	 * 行の実セルを行内セル順にbindします(E-6増分5b-2 Pass C)。extended
	 * (rowspan/colspan継続slot)は持ち主の行・列でbind済み/される。
	 */
	private void bindRowCells(final List<CellContent> cells) {
		for (int k = 0; k < cells.size(); ++k) {
			final CellContent cell = cells.get(k);
			if (cell.isExtended()) {
				continue;
			}
			this.bindCell(cell, cell.getCellBox());
		}
	}

	/**
	 * 行グループを表に組み付け、列・境界寸法を適用して閉じます(bind 第3段)。
	 */
	private void assemble(final BlockBuilder builder, final TableShape shape, final int rowCount) {
		final TableParams tableParams = this.tableBox.getTableParams();
		final BlockBuilder anonBuilder = shape.anonBuilder();
		final AbstractBlockBox blockBox = shape.blockBox();
		final double[] columnSizes = shape.columnSizes();
		final double specifiedPageSize = shape.specifiedPageSize();
		final double tableSize = shape.tableSize();
		final int columnCount = this.columnWidths.mins().length;
		if (this.headerGroup != null) {
			this.tableBox.setTableHeader(this.headerGroup);
		}
		for (int i = 0; i < this.bodyGroups.size(); ++i) {
			this.tableBox.addTableBody(this.bodyGroups.get(i));
		}
		if (this.footerGroup != null) {
			this.tableBox.setTableFooter(this.footerGroup);
		}
		if (rowCount == 0 || columnCount == 0) {
			if (this.vertical) {
				this.tableBox.setSize(specifiedPageSize, tableSize - this.tableBox.getFrame().getFrameHeight());
			} else {
				this.tableBox.setSize(tableSize - this.tableBox.getFrame().getFrameWidth(), specifiedPageSize);
			}
		}

		// カラム
		if (this.columnGroupBox != null) {
			final double pageSize = this.vertical ? this.tableBox.getInnerWidth() : this.tableBox.getInnerHeight();
			this.tableBox.setTableColumnGroup(this.columnGroupBox);
			final double[] sizes = columnSizes;
			this.columnGroupBox.eachColumn((column, col, span) -> {
				double size = 0;
				for (int j = 0; j < span; ++j) {
					size += sizes[col + j];
				}
				column.setLineSize(size);
				column.setPageSize(pageSize);
			});
		}

		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// つぶし境界
			for (int i = 0; i < columnSizes.length; ++i) {
				assert !LayoutUtils.isNone(columnSizes[i]);
				this.borders.setColumnSize(i, columnSizes[i]);
			}
			int row = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				List<TableRowBox> rows = this.rowGroupToRows.get(this.rowGroups.get(i));
				for (int j = 0; j < rows.size(); ++j) {
					double rowHeight = rows.get(j).getPageSize();
					this.borders.setRowSize(row++, rowHeight);
				}
			}
		}

		anonBuilder.addBound(this.tableBox);

		// 下部キャプション
		for (int i = 0; i < this.bottomCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.bottomCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			anonBuilder.startFlowBlock(captionBox);
			captionBuilder.bind(anonBuilder);
			anonBuilder.endFlowBlock();
		}

		switch (blockBox.getPos().getType()) {
		case FLOW:
			builder.endFlowBlock();
			break;
		case INLINE:
			anonBuilder.close();
			// DocumentBuilderで追加
			break;
		case FLOAT:
			anonBuilder.close();
			builder.addBound(blockBox);
			break;
		case ABSOLUTE:
			anonBuilder.close();
			final AbsoluteBlockBox absoluteBox = (AbsoluteBlockBox) blockBox;
			switch (absoluteBox.getAbsolutePos().autoPosition) {
			case AutoPosition.BLOCK:
				builder.addBound(absoluteBox);
				break;
			case AutoPosition.INLINE:
				// DocumentBuilderで追加
				break;
			default:
				throw new IllegalStateException();
			}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public void finish(final net.zamasoft.foliojet.layout.builder.Builder host) {
		// Retainedは全行を読み終えて初めてコミットできる(A-2)
		host.addTable(this);
	}

	/**
	 * 固定レイアウトでの先頭行セル由来の列指定を返します(AUTOはnull)。
	 * 指定はセルの colspan で均等割りされます。
	 *
	 * @param cell    セル
	 * @param refSize %指定の基準寸法
	 * @return 列指定
	 */
	private FixedColumnWidths.Spec fixedCellSpec(final CellContent cell, final double refSize) {
		// 指定の導出は FixedColumnWidths に統合(P2-2)
		return FixedColumnWidths.cellSpec(cell.getCellBox(), cell.colspan,
				this.tableBox.getTableParams().flow, refSize);
	}

	/**
	 * 表の行を1つ確定したことを記録します(2026-07-27新設)。
	 *
	 * <p>
	 * 締切({@code AbstractUserAgent}の「進捗が止まったら中断する」)は
	 * ページの出力を進捗とみなすが、<b>巨大な自動表の測定パスでは
	 * ページが出ないまま長く走る</b>。実測で40万行=37.5秒、外挿すると
	 * 100万行で約94秒に達し、既定の120秒に迫っていた(2026-07-27)。
	 * </p>
	 *
	 * <p>
	 * <b>「コードが動いた」ではなく「仕事が終わった」を数えること。</b>
	 * 行の確定は各行1回きりの単調な仕事なので、空回りするループが
	 * 進捗を偽装できない。
	 * </p>
	 */
	private void noteTableProgress() {
		this.layoutStack.getPageContext().getPageGenerator().getUserAgent().noteProgress();
	}

}

/**
 * 結合された列です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: RetainedTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
