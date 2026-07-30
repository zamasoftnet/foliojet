package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;

import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnBox;
import net.zamasoft.foliojet.layout.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox.Cell;
import net.zamasoft.foliojet.layout.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.TableBuilder;
import net.zamasoft.foliojet.layout.builder.TableBuilderHost;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.TableCollapsedBorders;
import net.zamasoft.foliojet.layout.util.DoubleList;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * 固定レイアウトのテーブルを構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IncrementalTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
 */
public class IncrementalTableBuilder implements TableBuilder {
	/**
	 * 構築中のテーブルセルです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: IncrementalTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
	 */
	private final boolean vertical;
	private TableBox tableBox;
	private RootBuilder builder;
	private final List<AbstractInnerTableBox> innerTableStack = new ArrayList<AbstractInnerTableBox>();
	private final List<Builder> topCaptions = new ArrayList<Builder>();
	private final List<Builder> bottomCaptions = new ArrayList<Builder>();
	private TableColumnGroupBox columnGroupBox = null;
	private double pageSize;
	private double tableInnerSize;

	private List<Border[]> headerHborders = null, headerVborders = null;
	private List<Border[]> bodyHborders = null, bodyVborders = null;
	private List<Border[]> footerHborders = null, footerVborders = null;
	private DoubleList headerRowSizes = null;
	private DoubleList bodyRowSizes = null;
	private DoubleList footerRowSizes = null;

	// カラムの幅のリストです。
	private double[] columnSizes = null;
	// 構築中の行グループです。
	private TableRowGroupBox rowGroupBox = null;
	// セルボックス(TableCellBox)と元セル(TableRowBox.Cell)の対応です。
	private final Map<TableCellBox, Cell> cellToSource = new HashMap<TableCellBox, Cell>();

	// 一区切りに含まれるセルのリストのリストです。
	private final List<List<CellContent>> cellsUnit = new ArrayList<List<CellContent>>();
	private final List<TableRowBox> rowsUnit = new ArrayList<TableRowBox>();

	// 次の行で前の区切りを構築するフラグです。
	private boolean bindUnit = false;
	// 最初の行を示すフラグです。
	private boolean firstRow = true;
	// 最初の行グループを示すフラグです。
	private boolean groupFirst = true;
	// 最後に構築済みの行グループです。
	private TableRowGroupBox bindRowGroupBox = null;

	// 構築中の行です。
	private TableRowBox rowBox = null;
	// 構築中の行のセルリストです。
	private List<CellContent> cells = null;

	public IncrementalTableBuilder(TableBox tableBox) {
		this.tableBox = tableBox;
		this.vertical = tableBox.getTableParams().flow.isVertical();
	}

	public TableBox getTableBox() {
		return this.tableBox;
	}

	public void startInnerTable(AbstractInnerTableBox box) {
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
				TableColumnGroupBox parentColumnGroup = (TableColumnGroupBox) this.innerTableStack
						.get(this.innerTableStack.size() - 1);
				parentColumnGroup.addTableColumn(column);
			}
		}
			break;
		case TABLE_ROW_GROUP: {
			// 行グループ
			this.rowGroupBox = (TableRowGroupBox) box;
			if (this.bindRowGroupBox == null) {
				this.bindRowGroupBox = this.rowGroupBox;
			}
			// rowspanは行グループを越えられない(CSS 2.1 §17.5)。
			// RetainedTableBuilderは行グループ開始で upperRow = null に
			// しているが、Incremental側は繰り越しを切っておらず、
			// thead末尾のrowspanがtbody先頭列を占有していた——
			// table-layout:fixed では占有された列に落ちるセルが
			// 「列数外」と判定され**内容ごと消えて**いた
			// (2026-07-25、独立レビューで発見)
			this.rowGroupBoundary = true;
		}
			break;

		case TABLE_ROW: {
			// 行
			this.rowBox = (TableRowBox) box;
			this.cells = new ArrayList<CellContent>();
			this.complementRowspan();
		}
			break;
		default:
			throw new IllegalStateException();
		}
		this.innerTableStack.add(box);
	}

	private double getSpecificRowSize(TableRowBox rowBox) {
		// 導出は共有核(P2-5 (c))。旧実装は %指定を常に 0 としていたが、
		// rowSpec は %>0 でも 0 を返すため同値
		return RowLayoutEngine.rowSpec(rowBox.getInnerTableParams()).size();
	}

	private void firstLayout() {
		// レイアウト開始
		final TableParams tableParams = this.tableBox.getTableParams();
		final FlowBlockBox flowBox = (FlowBlockBox) this.tableBox.getBlockBox();

		// 直下はテーブルの匿名ボックスなのでその上を取る
		final AbstractContainerBox containerBox = this.builder.getFlow(this.builder.getFlowCount() - 2).box;
		// コンテナの幅をゼロとして、外周を計算
		this.tableBox.calculateFrame(containerBox.getLineSize());

		final BlockParams flowParams = flowBox.getBlockParams();
		final double lineSize = containerBox.getLineSize();
		final double tableFrame, lineBorderSpacing;
		double tableInnerSize;
		if (this.vertical) {
			tableFrame = this.tableBox.getFrame().getFrameHeight();
			lineBorderSpacing = tableParams.borderSpacingV;
			tableInnerSize = LayoutUtils.computeDimensionHeight(flowParams.size, lineSize);
			assert !LayoutUtils.isNone(tableInnerSize);
			double minWidth = LayoutUtils.computeDimensionHeight(flowParams.minSize, lineSize);
			tableInnerSize = Math.max(minWidth, tableInnerSize);
			double maxWidth = LayoutUtils.computeDimensionHeight(flowParams.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxWidth) && !LayoutUtils.isNone(tableInnerSize)) {
				tableInnerSize = Math.min(maxWidth, tableInnerSize);
			}
		} else {
			tableFrame = this.tableBox.getFrame().getFrameWidth();
			lineBorderSpacing = tableParams.borderSpacingH;
			tableInnerSize = LayoutUtils.computeDimensionWidth(flowParams.size, lineSize);
			double minWidth = LayoutUtils.computeDimensionWidth(flowParams.minSize, lineSize);
			tableInnerSize = Math.max(minWidth, tableInnerSize);
			double maxWidth = LayoutUtils.computeDimensionWidth(flowParams.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxWidth) && !LayoutUtils.isNone(tableInnerSize)) {
				tableInnerSize = Math.min(maxWidth, tableInnerSize);
			}
		}
		if (LayoutUtils.isNone(tableInnerSize)) {
			tableInnerSize = flowBox.getLineSize();
		}
		tableInnerSize -= tableFrame;

		int columnCount = 0;
		if (this.columnGroupBox != null) {
			this.tableBox.setTableColumnGroup(this.columnGroupBox);
			columnCount = TableColumnSpecs.countColumns(this.columnGroupBox);
		}
		columnCount = Math.max(this.cells == null ? 0 : this.cells.size(), columnCount);
		final FixedColumnWidths.Result result = FixedTableSizing.resolve(this.columnGroupBox, this.cells,
				columnCount, tableInnerSize, tableParams.borderCollapse == TableParams.BORDER_SEPARATE,
				lineBorderSpacing,
				(cell, refSize) -> this.fixedCellSpec(cell, refSize, containerBox, lineBorderSpacing));
		this.columnSizes = result.sizes();
		tableInnerSize = result.innerSize();

		// テーブルのレイアウト
		final double tableSize = tableInnerSize + tableFrame;
		flowBox.shrinkToFit(this.builder, new IntrinsicSizes(tableSize, tableSize, 0), true);

		// 上部キャプション
		for (int i = 0; i < this.topCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.topCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			this.builder.startFlowBlock(captionBox);
			captionBuilder.bind(this.builder);
			this.builder.endFlowBlock();
		}
		this.tableInnerSize = tableInnerSize;

		// カラム幅設定
		if (this.columnGroupBox != null) {
			this.columnGroupBox.eachColumn((column, col, span) -> {
				double size = 0;
				for (int j = 0; j < span; ++j) {
					size += this.columnSizes[col + j];
				}
				column.setLineSize(size);
			});
		}
	}

	public void endInnerTable() {
		final AbstractInnerTableBox box = (AbstractInnerTableBox) this.innerTableStack
				.remove(this.innerTableStack.size() - 1);
		switch (box.getType()) {
		case TABLE_COLUMN:
		case TABLE_COLUMN_GROUP: {
			// 列
		}
			break;
		case TABLE_ROW_GROUP: {
			// 行グループ
			this.rowGroupBox = null;
		}
			break;

		case TABLE_ROW: {
			// 行
			final boolean firstRow = (this.columnSizes == null);
			if (firstRow) {
				this.firstLayout();
			}
			InnerTableParams rowGroupParams = this.bindRowGroupBox.getInnerTableParams();
			if (rowGroupParams.size.getType() == LengthType.ABSOLUTE) {
				if (this.rowGroupBox != this.bindRowGroupBox) {
					this.bindTableRow(false);
				}
				this.bindUnit = false;
			} else {
				if (this.bindUnit) {
					this.bindTableRow(false);
				}
				this.bindUnit = true;
				for (int i = 0; i < this.cells.size(); ++i) {
					CellContent cell = (CellContent) this.cells.get(i);
					if (cell.rowspan > 1) {
						this.bindUnit = false;
						break;
					}
				}
			}
			this.cellsUnit.add(this.cells);
			this.rowsUnit.add(this.rowBox);
			// fixed ストリーミングの保持上限の観測(P2-1 保存契約)
			TableBuildStats.reportRowRetention(this.rowsUnit.size());
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	private void updateColumnHeights() {
		if (this.columnGroupBox == null) {
			return;
		}
		final double pageSize;
		if (this.vertical) {
			pageSize = this.tableBox.getInnerWidth();
		} else {
			pageSize = this.tableBox.getInnerHeight();
		}
		this.columnGroupBox.eachColumn((column, col, span) -> column.setPageSize(pageSize));
	}

	/**
	 * 行のかたまりをレイアウトします。
	 * 
	 * @param lastRow
	 */
	private void bindTableRow(boolean lastRow) {
		// System.out.println(this.cellsUnit.size());
		final TableParams tableParams = this.tableBox.getTableParams();
		final InnerTableParams rowGroupParams = this.bindRowGroupBox.getInnerTableParams();
		final TableRowGroupPos rowGroupPos = this.bindRowGroupBox.getTableRowGroupPos();
		boolean firstRow = this.firstRow;
		this.firstRow = false;
		boolean groupFirst = this.groupFirst;
		this.groupFirst = false;
		boolean groupLast = this.bindRowGroupBox != this.rowGroupBox;
		lastRow = (groupLast && rowGroupPos.rowGroupType == RowGroupType.FOOTER)
				|| (lastRow && this.tableBox.getTableFooter() == null);

		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// つぶし境界
			final List<Border[]> vborders, hborders;
			switch (rowGroupPos.rowGroupType) {
			case RowGroupType.HEADER: {
				if (this.headerVborders == null) {
					this.headerVborders = new ArrayList<Border[]>();
				}
				if (this.headerHborders == null) {
					this.headerHborders = new ArrayList<Border[]>();
				}
				vborders = this.headerVborders;
				hborders = this.headerHborders;
			}
				break;
			case RowGroupType.BODY: {
				if (this.bodyVborders == null) {
					this.bodyVborders = new ArrayList<Border[]>();
				}
				if (this.bodyHborders == null) {
					this.bodyHborders = new ArrayList<Border[]>();
				}
				vborders = this.bodyVborders;
				hborders = this.bodyHborders;
			}
				break;
			case RowGroupType.FOOTER: {
				if (this.footerVborders == null) {
					this.footerVborders = new ArrayList<Border[]>();
				}
				if (this.footerHborders == null) {
					this.footerHborders = new ArrayList<Border[]>();
				}
				vborders = this.footerVborders;
				hborders = this.footerHborders;
			}
				break;
			default:
				throw new IllegalStateException();
			}
			final BorderAxes ax = this.vertical ? BorderAxes.VERTICAL : BorderAxes.HORIZONTAL;
			for (int row = 0; row < this.cellsUnit.size(); ++row) {
				List<CellContent> cells = this.cellsUnit.get(row);
				Border[] lineBorder = new Border[this.columnSizes.length + 1];
				vborders.add(lineBorder);
				Border[] firstBorder;
				if (hborders.isEmpty()) {
					firstBorder = new Border[this.columnSizes.length];
					hborders.add(firstBorder);
				} else {
					firstBorder = (Border[]) hborders.get(hborders.size() - 1);
				}
				Border[] lastBorder = new Border[this.columnSizes.length];
				hborders.add(lastBorder);

				final boolean unitLastRow = row == this.cellsUnit.size() - 1;
				final List<CellContent> nextCells = unitLastRow ? this.cells : this.cellsUnit.get(row + 1);
				// 次行はまず単位内、単位末尾ではストリーミング側の保留行
				final TableRowBox nextRowBox = unitLastRow ? this.rowBox : this.rowsUnit.get(row + 1);
				CollapsedBorderRules.collapseRow(firstBorder, lastBorder, lineBorder, ax, tableParams,
						this.columnGroupBox, rowGroupParams, this.rowsUnit.get(row), cells, nextRowBox,
						nextCells, firstRow && row == 0, lastRow && unitLastRow, groupFirst && row == 0,
						groupLast && unitLastRow, row == 0, !groupLast || !unitLastRow, this.columnSizes.length);
			}
		}

		// セルのレイアウト
		for (int row = 0; row < this.cellsUnit.size(); ++row) {
			final List<CellContent> cells = this.cellsUnit.get(row);
			final TableRowBox rowBox = this.rowsUnit.get(row);
			for (int i = 0; i < cells.size(); ++i) {
				CellContent cell = cells.get(i);
				int span = cell.colspan;
				if (cell.isExtended()) {
					i += span - 1;
					Cell rcell = (Cell) this.cellToSource.get(cell.getCellBox());
					this.cellToSource.put(cell.getCellBox(), rowBox.addTableExtendedCell(rcell));
					continue;
				}
				final TableCellBox cellBox = cell.getCellBox();
				// セル間隔(共有核 — P2-5 (c))
				final AbsoluteInsets cellSpacing;
				if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
					cellSpacing = CollapsedBorderRules.separateSpacing(tableParams);
				} else {
					final List<Border[]> vborders, hborders;
					switch (rowGroupPos.rowGroupType) {
					case RowGroupType.HEADER:
						vborders = this.headerVborders;
						hborders = this.headerHborders;
						break;
					case RowGroupType.FOOTER:
						vborders = this.footerVborders;
						hborders = this.footerHborders;
						break;
					case RowGroupType.BODY:
						vborders = this.bodyVborders;
						hborders = this.bodyHborders;
						break;
					default:
						throw new IllegalStateException();
					}
					final int borderRow = hborders.size() - this.cellsUnit.size() + row;
					cellSpacing = CollapsedBorderRules.streamSpacing(hborders, vborders, borderRow, i, cell.rowspan,
							cell.colspan, this.columnSizes.length, this.vertical);
				}
				cellBox.prepareLayout(this.builder.getFlowBox().getLineSize(), this.tableBox, cellSpacing);

				// 直交書字方向のセルの行方向寸法は内容の実測から
				// (TwoPass と同じ規約 — 共有核 TableCellMetrics 参照)
				final double size = TableCellMetrics.spannedLineSize(this.columnSizes, i, span);
				i += span - 1;
				TableCellMetrics.applyLineAxis(cellBox, () -> cell.getBuilder().getIntrinsicSizes(), size,
						this.vertical, tableParams);
				final BlockBuilder cellBindBuilder = new BlockBuilder(this.builder, cellBox);
				cell.getBuilder().bind(cellBindBuilder);
				cellBindBuilder.close();
				Cell source = rowBox.addTableSourceCell(cellBox);
				this.cellToSource.put(cellBox, source);
			}
		}
		if (this.cellsUnit.size() == 1) {
			// rowspanによる連結がない場合の高さ計算
			final List<CellContent> cells = this.cellsUnit.get(0);
			final TableRowBox rowBox = this.rowsUnit.get(0);
			double rowSize = this.getSpecificRowSize(rowBox);
			final double rowAscent = CellContent.maxFirstAscent(cells);
			for (int i = 0; i < cells.size(); ++i) {
				final CellContent cell = cells.get(i);
				final TableCellBox cellBox = cell.getCellBox();
				final BlockParams cellParams = cellBox.getBlockParams();
				double cellSize;
				if (this.vertical) {
					cellSize = cellBox.getWidth();
					if (cellParams.size.getWidthType() == LengthType.ABSOLUTE) {
						double width = cellParams.size.getWidth();
						cellSize = Math.max(cellSize, width);
					}
				} else {
					cellSize = cellBox.getHeight();
					if (cellParams.size.getHeightType() == LengthType.ABSOLUTE) {
						double height = cellParams.size.getHeight();
						cellSize = Math.max(cellSize, height);
					}
				}
				rowSize = Math.max(rowSize, cellSize);
			}
			CellContent.applyCellExtents((List<CellContent>) (List<?>) cells, new double[] { rowSize }, 0, rowAscent,
					this.vertical);
			rowBox.setLineSize(this.tableInnerSize);
			rowBox.setPageSize(rowSize);
			this.bindRowGroupBox.addTableRow(rowBox);
			// 行1つの確定は**実際に進んだ仕事**(2026-07-27、締切の進捗信号)
			this.noteTableProgress();
			if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
				// つぶし境界
				this.addBorderRowSize(rowSize);
			}
			this.pageSize += rowSize;
		} else {
			// rowspanによる連結がある場合の高さ計算
			Map<Rowspan, Rowspan> rowspans = new HashMap<Rowspan, Rowspan>();
			List<Rowspan> rowspanList = new ArrayList<Rowspan>();
			boolean[] noAdjRows = new boolean[this.cellsUnit.size()];
			boolean[] autoRows = new boolean[this.cellsUnit.size()];

			for (int row = 0; row < this.cellsUnit.size(); ++row) {
				List<CellContent> cells = this.cellsUnit.get(row);
				TableRowBox rowBox = this.rowsUnit.get(row);
				final RowLayoutEngine.RowSpec rowSpec = RowLayoutEngine.rowSpec(rowBox.getInnerTableParams());
				double rowSize = rowSpec.size();
				// 0% 指定も自動行として扱う(共有核 rowSpec の規約に統一)
				if (rowSpec.auto()) {
					autoRows[row] = true;
				}
				final double rowAscent = CellContent.maxFirstAscent(cells);
				for (int i = 0; i < cells.size(); ++i) {
					CellContent cell = cells.get(i);
					if (cell.isExtended()) {
						i += cell.colspan - 1;
						continue;
					}
					final TableCellBox cellBox = cell.getCellBox();
					cellBox.baseline(rowAscent);
					final BlockParams cellParams = cellBox.getBlockParams();
					// 要求寸法は共有核へ(A-4)。この窓経路は従来どおり高さ軸固定
					final double cellSize = RowLayoutEngine.demandPageSize(cellBox.getHeight(), cellParams, cellBox,
							false);

					int cellRowspan = Math.min(this.cellsUnit.size() - row, cell.rowspan);
					if (cellRowspan <= 1) {
						// 連結されない行
						noAdjRows[row] = true;
						rowSize = Math.max(rowSize, cellSize);
					} else {
						// 連結された行(登録は共有核へ — A-4)
						RowLayoutEngine.addSpannedDemand(rowspans, rowspanList, row, cellRowspan, cellSize);
					}
					i += cell.colspan - 1;
				}
				rowBox.setPageSize(rowSize);
			}

			// rowspanで連結された行の高さの計算(共有エンジン — P2-2)
			Collections.sort(rowspanList, Rowspan.SPAN_COMPARATOR);
			{
				final double[] rowSizes = new double[this.rowsUnit.size()];
				final double[] rowRatios = new double[this.rowsUnit.size()];
				for (int row = 0; row < this.rowsUnit.size(); ++row) {
					final TableRowBox rowBox = this.rowsUnit.get(row);
					rowSizes[row] = rowBox.getPageSize();
					if (rowBox.getInnerTableParams().size.getType() == LengthType.RELATIVE) {
						rowRatios[row] = rowBox.getInnerTableParams().size.getLength();
					}
				}
				RowLayoutEngine.distributeSpannedRowSizes(rowSizes, rowspanList, noAdjRows, autoRows, rowRatios);
				for (int row = 0; row < this.rowsUnit.size(); ++row) {
					this.rowsUnit.get(row).setPageSize(rowSizes[row]);
				}
			}

			// 行グループ高さ(共有エンジン — P2-4)
			if (rowGroupParams.size.getType() == LengthType.ABSOLUTE) {
				final double[] rowSizes = new double[this.rowsUnit.size()];
				for (int row = 0; row < this.rowsUnit.size(); ++row) {
					rowSizes[row] = this.rowsUnit.get(row).getPageSize();
				}
				RowLayoutEngine.distributeGroupSize(rowSizes, rowGroupParams.size.getLength());
				for (int row = 0; row < this.rowsUnit.size(); ++row) {
					this.rowsUnit.get(row).setPageSize(rowSizes[row]);
				}
			}

			// 行の追加
			for (int row = 0; row < this.rowsUnit.size(); ++row) {
				TableRowBox rowBox = this.rowsUnit.get(row);
				this.bindRowGroupBox.addTableRow(rowBox);
				// 行1つの確定は**実際に進んだ仕事**(2026-07-27、締切の進捗信号)
				this.noteTableProgress();
			}

			// セルの高さ設定(共有核 — P2-5 (c)。baseline は寸法収集時に適用済み)
			{
				final double[] unitRowSizes = new double[this.rowsUnit.size()];
				for (int i = 0; i < this.rowsUnit.size(); ++i) {
					unitRowSizes[i] = this.rowsUnit.get(i).getPageSize();
				}
				for (int i = 0; i < this.rowsUnit.size(); ++i) {
					CellContent.applyCellExtents(this.cellsUnit.get(i), unitRowSizes, i, Double.NaN, this.vertical);
				}
			}
			if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
				// つぶし境界
				for (int i = 0; i < this.rowsUnit.size(); ++i) {
					TableRowBox rowBox = this.rowsUnit.get(i);
					double rowSize = rowBox.getPageSize();
					this.pageSize += rowSize;
					this.addBorderRowSize(rowSize);
				}
			} else {
				for (int i = 0; i < this.rowsUnit.size(); ++i) {
					TableRowBox rowBox = this.rowsUnit.get(i);
					double rowSize = rowBox.getPageSize();
					this.pageSize += rowSize;
				}
			}
		}

		this.cellsUnit.clear();
		this.rowsUnit.clear();

		if (this.builder.mode != BreakableBuilder.MODE_NO_BREAK
				&& this.bindRowGroupBox.getTableRowGroupPos().rowGroupType == RowGroupType.BODY) {
			while (this.checkBreak(groupLast))
				;
		}
		if (groupLast) {
			// 新しいグループの開始
			switch (this.bindRowGroupBox.getTableRowGroupPos().rowGroupType) {
			case RowGroupType.HEADER:
				this.tableBox.setTableHeader(this.bindRowGroupBox);
				break;
			case RowGroupType.FOOTER:
				this.tableBox.setTableFooter(this.bindRowGroupBox);
				break;
			case RowGroupType.BODY:
				this.tableBox.addTableBody(this.bindRowGroupBox);
				break;
			default:
				throw new IllegalStateException();
			}
			this.bindRowGroupBox = this.rowGroupBox;
			this.groupFirst = true;
		}
		if (this.builder.mode != BreakableBuilder.MODE_NO_BREAK && this.bindRowGroupBox != null
				&& this.bindRowGroupBox.getTableRowGroupPos().rowGroupType == RowGroupType.BODY) {
			// 自動改ページチェック
			for (;;) {
				this.builder.getPageContext().getPageGenerator().getUserAgent()
						.checkAbort(jp.cssj.cti2.CTISession.ABORT_FORCE);
				double pageBottom = this.builder.getPageLimit() - this.builder.getPageAxis();
				// System.err.println(this.pageAxis + "/" + pageBottom);
				if (LayoutUtils.compare(this.pageSize, pageBottom) > 0) {
					// 行グループを分割
					// System.out.println("A;" + this.pageAxis + "/"
					// + this.bindRowGroupBox.getHeight() + "/"
					// + this.bindRowGroupBox.getTableRowCount());
					double pageLimit = this.builder.getPageLimit();
					pageLimit -= this.builder.getPageAxis();
					pageLimit -= this.tableBox.getFrame().getFrameTop();
					if (this.tableBox.getTableHeader() != null) {
						pageLimit -= this.tableBox.getTableHeader().getPageSize();
					}
					if (this.tableBox.getTableFooter() != null) {
						pageLimit -= this.tableBox.getTableFooter().getPageSize();
						pageLimit -= this.tableBox.getFrame().getFrameBottom();
					}
					for (int i = 0; i < this.tableBox.getTableBodyCount(); ++i) {
						pageLimit -= this.tableBox.getTableBody(i).getPageSize();
					}
					byte flags = this.tableBox.getTableBodyCount() == 0 ? IPageBreakableBox.FLAGS_FIRST : (byte) 0;
					if (this.pageBreak(BreakMode.DEFAULT_BREAK_MODE, pageLimit, flags)) {
						continue;
					}
				}
				break;
			}
		}
	}

	private boolean checkBreak(boolean groupLast) {
		final double firstFrame = this.tableBox.getFrame().getFramePageStart(this.tableBox.getTableParams().flow);
		final double lastFrame = this.tableBox.getFrame().getFramePageEnd(this.tableBox.getTableParams().flow);
		double pageLimit = this.builder.getPageLimit();
		pageLimit -= this.builder.getPageAxis();
		pageLimit -= firstFrame;
		if (this.tableBox.getTableHeader() != null) {
			pageLimit -= this.tableBox.getTableHeader().getPageSize();
		}
		if (this.tableBox.getTableFooter() != null) {
			pageLimit -= this.tableBox.getTableFooter().getPageSize();
			pageLimit -= lastFrame;
		}
		for (int i = 0; i < this.tableBox.getTableBodyCount(); ++i) {
			TableRowGroupBox groupBox = this.tableBox.getTableBody(i);
			pageLimit -= groupBox.getPageSize();
		}
		byte flags = this.tableBox.getTableBodyCount() == 0 ? IPageBreakableBox.FLAGS_FIRST : (byte) 0;
		AbstractInnerTableBox box = null;
		int row = 0;
		double rowSplitLine = pageLimit;
		PageBreakMode breakMode = null;
		for (; row < this.bindRowGroupBox.getTableRowCount(); ++row) {
			TableRowBox rowBox = this.bindRowGroupBox.getTableRow(row);
			double rowSize = rowBox.getPageSize();
			if (LayoutUtils.compare(rowSize, rowSplitLine) > 0) {
				break;
			}
			TableRowPos pos = rowBox.getTableRowPos();
			if (row > 0) {
				breakMode = pos.pageBreakBefore;
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					// 行の直前の改ページ
					--row;
					box = this.bindRowGroupBox.getTableRow(row);
					break;
				}
			}
			if (row == this.bindRowGroupBox.getTableRowCount() - 1) {
				// 末尾の場合はループから抜ける
				break;
			}
			breakMode = pos.pageBreakAfter;
			if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
				// 行の直後の改ページ
				box = rowBox;
				break;
			}
			rowSplitLine -= rowSize;
		}
		if (box != null) {
			TableForceBreakMode mode = new TableForceBreakMode(box, breakMode, 0, row);
			return this.pageBreak(mode, pageLimit, (byte) 0);
		}

		// 自動改ページチェック
		// System.err.println("OPT A: "+ pageLimit);
		if (LayoutUtils.compare(pageLimit, 0) > 0) {
			// 行グループを分割
			// System.out.println("OPT A:" + pageLimit + "/"
			// + this.bindRowGroupBox.getHeight() + "/"
			// + this.bindRowGroupBox.getTableRowCount());
			if (this.pageBreak(BreakMode.DEFAULT_BREAK_MODE, pageLimit, flags)) {
				return true;
			}
		}

		// 強制改ページチェック
		if (groupLast && this.bindRowGroupBox != null && this.rowGroupBox != null) {
			boolean forceBreak = true;
			for (;;) {
				this.builder.getPageContext().getPageGenerator().getUserAgent()
						.checkAbort(jp.cssj.cti2.CTISession.ABORT_FORCE);
				breakMode = this.bindRowGroupBox.getTableRowGroupPos().pageBreakAfter;
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					break;
				}
				if (this.bindRowGroupBox.getTableRowCount() > 0) {
					breakMode = this.bindRowGroupBox.getTableRow(this.bindRowGroupBox.getTableRowCount() - 1)
							.getTableRowPos().pageBreakAfter;
					if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
						break;
					}
				}
				breakMode = this.rowGroupBox.getTableRowGroupPos().pageBreakBefore;
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					break;
				}
				breakMode = this.rowBox.getTableRowPos().pageBreakBefore;
				if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
					break;
				}
				forceBreak = false;
				break;
			}
			if (forceBreak) {
				// 行グループの直前の改ページ
				// 行グループの直後の改ページ
				TableForceBreakMode mode = new TableForceBreakMode(this.bindRowGroupBox, breakMode, 0, -1);
				this.pageBreak(mode, pageLimit, (byte) 0);
			}
		}
		return false;
	}

	private boolean pageBreak(final BreakMode mode, double pageLimit, byte flags) {
		if (LayoutUtils.compare(this.builder.getPageAxis(), 0) > 0) {
			flags &= ~IPageBreakableBox.FLAGS_FIRST;
		}
		TableRowGroupBox rowGroupBox = this.bindRowGroupBox;
		int rowCount = rowGroupBox.getTableRowCount();
		// System.err.println("Page Break." + mode + "/" + pageLimit + "/"
		// + this.tableBox.getHeight());
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0
				&& this.tableBox.getTableParams().pageBreakInside == PageBreakMode.AVOID) {
			// テーブルの改ページ禁止
			return false;
		}
		if (!(rowGroupBox.split(pageLimit, mode, flags) instanceof SplitResult.Split(final IPageBreakableBox groupRemainder))) {
			// 分割不可能
			return false;
		}
		TableRowGroupBox nextRowGroupBox = (TableRowGroupBox) groupRemainder;
		// System.out.println("B;" + +rowGroupBox.getHeight() + "/"
		// + rowGroupBox.getTableRowCount());
		this.tableBox.addTableBody(rowGroupBox);

		TableParams tableParams = this.tableBox.getTableParams();
		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// つぶし境界の処理
			int nextRowCount = nextRowGroupBox.getTableRowCount();
			List<Border[]> nextBodyHborders = new ArrayList<Border[]>();
			List<Border[]> nextBodyVborders = new ArrayList<Border[]>();
			DoubleList nextBodyRowSizes = new DoubleList();
			for (int i = 0; i < nextRowCount; ++i) {
				nextBodyHborders.add(0, this.bodyHborders.remove(this.bodyHborders.size() - 1));
				nextBodyVborders.add(0, this.bodyVborders.remove(this.bodyVborders.size() - 1));
				nextBodyRowSizes.add(0, this.bodyRowSizes.remove(this.bodyRowSizes.size() - 1));
			}
			nextBodyHborders.add(0, this.bodyHborders.get(this.bodyHborders.size() - 1));
			if (nextRowCount + rowGroupBox.getTableRowCount() > rowCount) {
				// 途中で切断されている場合
				this.bodyVborders.add(nextBodyVborders.get(0));
				Border[] hborders = new Border[((Border[]) nextBodyHborders.get(0)).length];
				this.bodyHborders.add(hborders);
				double nextSize = nextRowGroupBox.getTableRow(0).getPageSize();
				this.bodyRowSizes.add(nextBodyRowSizes.get(0) - nextSize);
				nextBodyRowSizes.set(0, nextSize);
			}
			this.makeBorder();
			this.bodyHborders = nextBodyHborders;
			this.bodyVborders = nextBodyVborders;
			this.bodyRowSizes = nextBodyRowSizes;
		}

		this.builder.addBound(this.tableBox);
		this.updateColumnHeights();
		this.tableBox = this.tableBox.splitTableBox();
		PageBreakMode breakMode = PageBreakMode.COLUMN;
		if (mode instanceof BreakMode.ForceBreakMode) {
			if (((ForceBreakMode) mode).breakType != PageBreakMode.COLUMN) {
				breakMode = PageBreakMode.PAGE;
			}
		}
		this.builder.forceBreak(breakMode);
		if (this.columnGroupBox != null) {
			this.columnGroupBox = (TableColumnGroupBox) this.columnGroupBox.splitPageAxis(0, 0);
			this.tableBox.setTableColumnGroup(this.columnGroupBox);
		}

		if (this.rowGroupBox == this.bindRowGroupBox) {
			this.rowGroupBox = this.bindRowGroupBox = nextRowGroupBox;
		} else {
			this.bindRowGroupBox = nextRowGroupBox;
		}
		this.groupFirst = true;
		if (this.vertical) {
			this.pageSize = this.tableBox.getWidth();
		} else {
			this.pageSize = this.tableBox.getHeight();
		}
		this.pageSize += this.bindRowGroupBox.getPageSize();
		return true;
	}

	private void addBorderRowSize(double size) {
		switch (this.bindRowGroupBox.getTableRowGroupPos().rowGroupType) {
		case RowGroupType.HEADER:
			if (this.headerRowSizes == null) {
				this.headerRowSizes = new DoubleList();
			}
			this.headerRowSizes.add(size);
			break;

		case RowGroupType.FOOTER:
			if (this.footerRowSizes == null) {
				this.footerRowSizes = new DoubleList();
			}
			this.footerRowSizes.add(size);
			break;

		case RowGroupType.BODY:
			if (this.bodyRowSizes == null) {
				this.bodyRowSizes = new DoubleList();
			}
			this.bodyRowSizes.add(size);
			break;
		default:
			throw new IllegalStateException();
		}
	}

	/** 直前が行グループ境界か(境界ではrowspanを繰り越さない)。 */
	private boolean rowGroupBoundary = false;

	private void complementRowspan() {
		if (this.rowGroupBoundary) {
			this.rowGroupBoundary = false;
			return;
		}
		if (!this.cellsUnit.isEmpty()) {
			// rowspanで連結されたセルの補完(共有核 — P2-2)
			CellContent.complementRowspan(this.cells, this.cellsUnit.get(this.cellsUnit.size() - 1));
		}
	}

	public Builder newContext(AbstractContainerBox box) {
		Builder builder;
		switch (box.getType()) {
		case BLOCK: {
			// キャプション
			FlowBlockBox caption = (FlowBlockBox) box;
			builder = new TwoPassBlockBuilder(this.builder, caption);
			switch (((TableCaptionPos) caption.getPos()).captionSide) {
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
			TableCellBox cellBox = (TableCellBox) box;
			builder = new TwoPassBlockBuilder(this.builder, cellBox);
			int colspan = cellBox.getTableCellPos().colspan;
			if (this.columnSizes != null) {
				int remainder = this.columnSizes.length - this.cells.size();
				if (remainder <= 0) {
					break;
				}
				colspan = Math.min(colspan, remainder);
			}
			CellContent cell = new CellContent((TwoPassBlockBuilder) builder, colspan);
			this.cells.add(cell);
			for (int i = cell.colspan; i > 1; --i) {
				this.cells.add(new CellContent(cell.getCellBox(), cell.rowspan, i));
			}
			this.complementRowspan();
		}
			break;

		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	public void startLayout(RootBuilder builder) {
		assert this.tableBox.getBlockBox().getPos().getType() == PosType.FLOW;
		FlowBlockBox flowBox = (FlowBlockBox) this.tableBox.getBlockBox();
		this.builder = builder;
		this.builder.startFlowBlock(flowBox);
		this.pageSize = 0;
	}

	/**
	 * ストリーミング中に行単位で蓄積した境界(行=リスト、列=配列)を、
	 * TableCollapsedBorders の列優先配列へ転置した行グループ分です。
	 */
	private void makeBorder() {
		// つぶし境界
		final int columnCount = this.columnSizes == null ? 0 : this.columnSizes.length;
		final CollapsedBorderRules.GroupBorders header = CollapsedBorderRules.GroupBorders.of(this.headerRowSizes == null ? null : this.headerRowSizes.toArray(), this.headerHborders, this.headerVborders,
				columnCount);
		final CollapsedBorderRules.GroupBorders body = CollapsedBorderRules.GroupBorders.of(this.bodyRowSizes == null ? null : this.bodyRowSizes.toArray(), this.bodyHborders, this.bodyVborders,
				columnCount);
		final CollapsedBorderRules.GroupBorders footer = CollapsedBorderRules.GroupBorders.of(this.footerRowSizes == null ? null : this.footerRowSizes.toArray(), this.footerHborders, this.footerVborders,
				columnCount);
		this.tableBox.setCollapsedBorders(new TableCollapsedBorders(this.columnSizes, header.rowSizes(),
				header.vborders(), header.hborders(), body.rowSizes(), body.vborders(), body.hborders(),
				footer.rowSizes(), footer.vborders(), footer.hborders()));
	}

	public void endLayout() {
		if (!this.rowsUnit.isEmpty()) {
			this.bindTableRow(true);
		}

		this.updateColumnHeights();
		TableParams tableParams = this.tableBox.getTableParams();
		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			this.makeBorder();
		}
		if (this.columnSizes == null) {
			this.firstLayout();
			this.tableBox.setSize(this.tableInnerSize, 0);
		}
		this.builder.addBound(this.tableBox);

		// 下部キャプション
		for (int i = 0; i < this.bottomCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.bottomCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			this.builder.startFlowBlock(captionBox);
			captionBuilder.bind(this.builder);
			this.builder.endFlowBlock();
		}

		assert this.tableBox.getBlockBox().getPos().getType() == PosType.FLOW;
		this.builder.endFlowBlock();
	}

	public void finish(final net.zamasoft.foliojet.layout.builder.Builder host) {
		// Incrementalは行単位で既にコミット済み。残余の確定だけを行う
		this.endLayout();
	}

	/**
	 * Incrementalは外側のDocumentBuilderと同じストリーミング機構で
	 * セルを構築するため、newContext呼び出し前にインライン文脈を
	 * 閉じ直す必要がある(段組span境界のstartColumnSpan/endColumnSpanと
	 * 同型の「フロー境界跨ぎ」ブラケット、C4-C深化・2026-07-19)。
	 */
	@Override
	public void prepareEnterCell(final TableBuilderHost host) {
		host.closeInlines(this.getTableBox().getParams());
		host.endContainer();
		host.startContainer();
	}

	@Override
	public void prepareEnterTrack(final TableBuilderHost host) {
		host.closeInlines(this.getTableBox().getParams());
		host.endContainer();
	}

	@Override
	public void afterEnterTrack(final TableBuilderHost host) {
		host.startContainer();
	}

	/**
	 * 固定レイアウトでの先頭行セル由来の列指定を返します(AUTOはnull)。
	 * 指定はセルの colspan で均等割りされます。指定がある場合は
	 * パディングの%を無視してセルの外周を先に計算します(prepareLayout)。
	 *
	 * @param cell              セル
	 * @param refSize           %指定の基準寸法
	 * @param containerBox      包含ブロック
	 * @param lineBorderSpacing 行方向の境界間隔
	 * @return 列指定
	 */
	private FixedColumnWidths.Spec fixedCellSpec(final CellContent cell, final double refSize,
			final AbstractContainerBox containerBox, final double lineBorderSpacing) {
		final TableCellBox cellBox = cell.getCellBox();
		final BlockParams cellParams = cellBox.getBlockParams();
		final TableParams tableParams = this.tableBox.getTableParams();
		final WritingMode tableFlow = tableParams.flow;
		if (cellParams.size.getLineType(tableFlow) != LengthType.AUTO) {
			// パディングの%を無視してセルの外周を計算
			final double space;
			if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
				space = lineBorderSpacing / 2.0;
			} else {
				space = 0;
			}
			final AbsoluteInsets cellSpacing = tableFlow.isVertical() ? new AbsoluteInsets(space, 0, space, 0)
					: new AbsoluteInsets(0, space, 0, space);
			cellBox.prepareLayout(containerBox.getLineSize(), this.tableBox, cellSpacing);
		}
		// 指定の導出は FixedColumnWidths に統合(P2-2)
		return FixedColumnWidths.cellSpec(cellBox, cell.colspan, tableFlow, refSize);
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
		this.builder.getPageContext().getPageGenerator().getUserAgent().noteProgress();
	}

}

/**
 * 結合された行です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IncrementalTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
 */
