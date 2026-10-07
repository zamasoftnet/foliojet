package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;

import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
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
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;
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

/**
 * Builds fixed-layout tables.
 *  
 * @author MIYABE Tatsuhiko
 * @version $Id: IncrementalTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
 */
public class IncrementalTableBuilder implements TableBuilder {

	/**
	 * Inset that shifts captions inward by the table's outer margin (line-axis start side).
	 *
	 * <p>
	 * CSS 2.1 §17.4: the table element's {@code margin} belongs to the wrapper box, not the
	 * table itself. The caption's containing block is the wrapper's content box, namely
	 * <b>the table's border box</b>. copper4 uses the table margin box as the wrapper content
	 * width ({@code tableInnerSize + tableFrame}, where {@code tableFrame} includes margins),
	 * so without this inset captions extend outward by the table margin (2026-08-30).
	 * </p>
	 */
	private double captionInsetStart() {
		final AbsoluteInsets margin = this.tableBox.getFrame().margin;
		return this.vertical ? margin.top : margin.left;
	}

	/** Caption inset at the line-axis end. See {@link #captionInsetStart()} for the reason. */
	private double captionInsetEnd() {
		final AbsoluteInsets margin = this.tableBox.getFrame().margin;
		return this.vertical ? margin.bottom : margin.right;
	}

	/**
	 * Table cell under construction.
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

	// List of column widths.
	private double[] columnSizes = null;
	// Row group under construction.
	private TableRowGroupBox rowGroupBox = null;
	// Mapping from cell boxes (TableCellBox) to original cells (TableRowBox.Cell).
	private final Map<TableCellBox, Cell> cellToSource = new HashMap<TableCellBox, Cell>();

	// List of cell lists in one unit.
	private final List<List<CellContent>> cellsUnit = new ArrayList<List<CellContent>>();
	private final List<TableRowBox> rowsUnit = new ArrayList<TableRowBox>();

	// Flag to build the previous unit on the next row.
	private boolean bindUnit = false;
	// Flag indicating the first row.
	private boolean firstRow = true;
	// Flag indicating the first row group.
	private boolean groupFirst = true;
	// Last completed row group.
	private TableRowGroupBox bindRowGroupBox = null;

	// Row under construction.
	private TableRowBox rowBox = null;
	// Cell list of the row under construction.
	private List<CellContent> cells = null;

	/**
	 * Cell most recently opened by {@link #newContext} (DP increment 1, 2026-07-30;
	 * same structure as E-6 increment 5a in Retained). {@link #sealCellContext} consumes it
	 * as the seal-on-close target. Cells are processed sequentially within a row
	 * (only one is open at a time), so a single field suffices.
	 */
	private CellContent pendingSealCell;

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
			// Column
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
			// Row group
			this.rowGroupBox = (TableRowGroupBox) box;
			if (this.bindRowGroupBox == null) {
				this.bindRowGroupBox = this.rowGroupBox;
			}
			// rowspan cannot cross row groups (CSS 2.1 §17.5).
			// RetainedTableBuilder sets upperRow = null at row group start,
			// but Incremental did not stop carryover, so
			// a rowspan at the end of thead occupied the first columns of tbody.
			// With table-layout:fixed, cells landing in those occupied columns
			// were considered "beyond the column count" and **disappeared with all their content**
			// (found by independent review, 2026-07-25).
			this.rowGroupBoundary = true;
		}
			break;

		case TABLE_ROW: {
			// Row
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
		// Use the shared core for derivation (P2-5 (c)). The old implementation always treated % as 0;
		// rowSpec returns 0 even for %>0, so this is equivalent.
		return RowLayoutEngine.rowSpec(rowBox.getInnerTableParams()).size();
	}

	private void firstLayout() {
		// Start layout
		final TableParams tableParams = this.tableBox.getTableParams();
		final FlowBlockBox flowBox = (FlowBlockBox) this.tableBox.getBlockBox();

		// The immediate box is the anonymous table box, so use the one above it.
		final AbstractContainerBox containerBox = this.builder.getFlow(this.builder.getFlowCount() - 2).box;
		// Calculate the frame with container width set to zero.
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

		// Table layout
		final double tableSize = tableInnerSize + tableFrame;
		flowBox.shrinkToFit(this.builder, new IntrinsicSizes(tableSize, tableSize, 0), true);

		// Top caption
		for (int i = 0; i < this.topCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.topCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			this.builder.startFlowBlock(captionBox, this.captionInsetStart(), this.captionInsetEnd());
			captionBuilder.bind(this.builder);
			this.builder.endFlowBlock();
		}
		this.tableInnerSize = tableInnerSize;

		// Set column widths
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
			// Column
		}
			break;
		case TABLE_ROW_GROUP: {
			// Row group
			this.rowGroupBox = null;
		}
			break;

		case TABLE_ROW: {
			// Row
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
				// When the row group changes, finalize the previous group’s unit even if a rowspan remains open
				// (2026-09-29). rowspan does not cross row groups (CSS 2.1 §17.5). Deferring this included new-group
				// rows in the same unit, added them to the previous group, and extended the spanning cell height through them
				// (a rowspan in the last thead row made tbody rows overlap thead rows). The finalized unit’s row count
				// caps rowspan (Math.min in bindTableRowContent). Retained builds per row group, so this cannot occur there.
				if (this.bindUnit || (!this.cellsUnit.isEmpty() && this.rowGroupBox != this.bindRowGroupBox)) {
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
			// The first row of the row group has ended (subsequent rows carry over from rows in the same group).
			this.rowGroupBoundary = false;
			// Observe the retention bound of fixed streaming (P2-1 preservation contract).
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
	 * Lays out a group of rows.
	 *  
	 * @param lastRow
	 */
	private void bindTableRow(boolean lastRow) {
		final RetainedTextLimit limit = RetainedTextLimit.get(this.builder);
		// With rowspan or a specified row group height, cellsUnit is an indivisible placement unit.
		try (var retained = limit == null || this.rowsUnit.isEmpty() ? null
				: limit.enter(RetainedTextLimit.elementName(this.rowsUnit.get(0).getParams(), "table-row"))) {
			this.bindTableRowContent(lastRow);
		}
	}

	private void bindTableRowContent(boolean lastRow) {
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
			// Collapsed borders
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
				// Use the next row within the unit first; at unit end, use the streaming path’s pending row.
				final TableRowBox nextRowBox = unitLastRow ? this.rowBox : this.rowsUnit.get(row + 1);
				CollapsedBorderRules.collapseRow(firstBorder, lastBorder, lineBorder, ax, tableParams,
						this.columnGroupBox, rowGroupParams, this.rowsUnit.get(row), cells, nextRowBox,
						nextCells, firstRow && row == 0, lastRow && unitLastRow, groupFirst && row == 0,
						groupLast && unitLastRow, row == 0, !groupLast || !unitLastRow, this.columnSizes.length);
			}
		}

		// Cell layout
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
				// Cell spacing (shared core — P2-5 (c))
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

				// Derive the line-axis size of orthogonal-writing cells from measured content
				// (the same convention as TwoPass; see the shared TableCellMetrics core).
				final double size = TableCellMetrics.spannedLineSize(this.columnSizes, i, span);
				i += span - 1;
				// DP increment 1: unify access through CellContent to also support sealed cells
				// (RangeContent), using the same entry as Retained’s shared bind core.
				// CellContent handles replay source branching and the 1:1 seal:bind counter.
				TableCellMetrics.applyLineAxis(cellBox, () -> cell.getIntrinsicSizes(), size,
						this.vertical, tableParams);
				final BlockBuilder cellBindBuilder = new BlockBuilder(this.builder, cellBox);
				cell.bind(cellBindBuilder);
				cellBindBuilder.close();
				Cell source = rowBox.addTableSourceCell(cellBox);
				this.cellToSource.put(cellBox, source);
			}
		}
		if (this.cellsUnit.size() == 1) {
			// Calculate heights without rowspan spans.
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
			// Finalizing one row is **actual work completed** (2026-07-27, progress signal for the deadline).
			this.noteTableProgress();
			if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
				// Collapsed borders
				this.addBorderRowSize(rowSize);
			}
			this.pageSize += rowSize;
		} else {
			// Calculate heights with rowspan spans.
			Map<Rowspan, Rowspan> rowspans = new HashMap<Rowspan, Rowspan>();
			List<Rowspan> rowspanList = new ArrayList<Rowspan>();
			boolean[] noAdjRows = new boolean[this.cellsUnit.size()];
			boolean[] autoRows = new boolean[this.cellsUnit.size()];

			for (int row = 0; row < this.cellsUnit.size(); ++row) {
				List<CellContent> cells = this.cellsUnit.get(row);
				TableRowBox rowBox = this.rowsUnit.get(row);
				final RowLayoutEngine.RowSpec rowSpec = RowLayoutEngine.rowSpec(rowBox.getInnerTableParams());
				double rowSize = rowSpec.size();
				// Treat 0% as an auto row too (consistent with the shared rowSpec convention).
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
					// Use the shared core for requested sizes (A-4). In vertical writing, the page axis is physical width.
					final double cellSize = RowLayoutEngine.demandPageSize(
							RowLayoutEngine.measuredRowspanPageSize(cellBox, this.vertical), cellParams, cellBox,
							this.vertical);

					int cellRowspan = Math.min(this.cellsUnit.size() - row, cell.rowspan);
					if (cellRowspan <= 1) {
						// Non-spanning rows
						noAdjRows[row] = true;
						rowSize = Math.max(rowSize, cellSize);
					} else {
						// Spanning rows (registration uses the shared core — A-4)
						RowLayoutEngine.addSpannedDemand(rowspans, rowspanList, row, cellRowspan, cellSize);
					}
					i += cell.colspan - 1;
				}
				rowBox.setPageSize(rowSize);
			}

			// Calculate heights of rows joined by rowspan (shared engine — P2-2).
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

			// Row group height (shared engine — P2-4)
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

			// Add rows
			for (int row = 0; row < this.rowsUnit.size(); ++row) {
				TableRowBox rowBox = this.rowsUnit.get(row);
				this.bindRowGroupBox.addTableRow(rowBox);
				// Finalizing one row is **actual work completed** (2026-07-27, progress signal for the deadline).
				this.noteTableProgress();
			}

			// Set cell heights (shared core — P2-5 (c); baseline already applied during size collection).
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
				// Collapsed borders
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
			// Start a new group
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
			// Check automatic page breaks
			for (;;) {
				this.builder.getPageContext().getPageGenerator().getUserAgent()
						.checkAbort(jp.cssj.cti2.CTISession.ABORT_FORCE);
				double pageBottom = this.builder.getPageLimit() - this.builder.getPageAxis();
				if (LayoutUtils.compare(this.pageSize, pageBottom) > 0) {
					// Split the row group
					double pageLimit = this.builder.getPageLimit();
					pageLimit -= this.builder.getPageAxis();
					pageLimit -= this.tableBox.getFrame().getFramePageStart(this.tableBox.getTableParams().flow);
					if (this.tableBox.getTableHeader() != null) {
						pageLimit -= this.tableBox.getTableHeader().getPageSize();
					}
					if (this.tableBox.getTableFooter() != null) {
						pageLimit -= this.tableBox.getTableFooter().getPageSize();
						pageLimit -= this.tableBox.getFrame().getFramePageEnd(this.tableBox.getTableParams().flow);
					}
					for (int i = 0; i < this.tableBox.getTableBodyCount(); ++i) {
						pageLimit -= this.tableBox.getTableBody(i).getPageSize();
					}
					byte flags = this.tableBox.getTableBodyCount() == 0 ? IPageBreakableBox.FLAGS_FIRST : (byte) 0;
					// Pass fragmentainer capacity (2026-08-27; same as the checkBreak path).
					if (this.pageBreak(BreakMode.AutoBreakMode.withCapacity(this.builder.getPageLimit()), pageLimit,
							flags)) {
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
					// Page break just before the row
					--row;
					box = this.bindRowGroupBox.getTableRow(row);
					break;
				}
			}
			if (row == this.bindRowGroupBox.getTableRowCount() - 1) {
				// Exit the loop at the end
				break;
			}
			breakMode = pos.pageBreakAfter;
			if (breakMode == PageBreakMode.PAGE || breakMode == PageBreakMode.COLUMN) {
				// Page break just after the row
				box = rowBox;
				break;
			}
			rowSplitLine -= rowSize;
		}
		if (box != null) {
			TableForceBreakMode mode = new TableForceBreakMode(box, breakMode, 0, row);
			return this.pageBreak(mode, pageLimit, (byte) 0);
		}

		// Check automatic page breaks
		if (LayoutUtils.compare(pageLimit, 0) > 0) {
			// Split the row group
			// Pass fragmentainer capacity (2026-08-27) so this path also prefers
			// splitting at row boundaries (TableCutter.rowPreDecide: if the row intersected by
			// the split line fits intact on a new page, carry over the whole row).
			if (this.pageBreak(BreakMode.AutoBreakMode.withCapacity(this.builder.getPageLimit()), pageLimit, flags)) {
				return true;
			}
		}

		// Check forced page breaks
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
				// Page break just before the row group
				// Page break just after the row group
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
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0
				&& this.tableBox.getTableParams().pageBreakInside == PageBreakMode.AVOID) {
			// Page breaks prohibited in the table
			return false;
		}
		if (!(rowGroupBox.split(pageLimit, mode, flags) instanceof SplitResult.Split(final IPageBreakableBox groupRemainder))) {
			// Cannot split
			return false;
		}
		TableRowGroupBox nextRowGroupBox = (TableRowGroupBox) groupRemainder;
		this.tableBox.addTableBody(rowGroupBox);

		TableParams tableParams = this.tableBox.getTableParams();
		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// Process collapsed borders
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
				// When split in the middle
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

	/**
	 * Whether the first row of a row group is under construction (do not carry rowspan across
	 * the boundary). Completion runs not only at row start but on each cell addition, so keep
	 * the flag until row end (2026-09-29). Previously, clearing it after the first call at row
	 * start let completion after the first cell import rowspan from the previous group's last
	 * row. In fixed layout, later cells disappeared as beyond the column count. Found by codex review.
	 */
	private boolean rowGroupBoundary = false;

	private void complementRowspan() {
		if (this.rowGroupBoundary) {
			return;
		}
		if (!this.cellsUnit.isEmpty()) {
			// Fill in cells joined by rowspan (shared core — P2-2).
			CellContent.complementRowspan(this.cells, this.cellsUnit.get(this.cellsUnit.size() - 1));
		}
	}

	/**
	 * Seals the range at cell close (recording completion point) (DP increment 1, 2026-07-30;
	 * same structure as {@code RetainedTableBuilder.sealCellContext} from E-6 increment 5a
	 * in Retained; replaces TableBuilder's default no-op). Eligibility fails closed in the same
	 * way as {@code TwoPassBlockBuilder.sealBodyForRangeBind}. Incremental cells bind exactly
	 * once through {@code cell.bind()} in row-wise flush. Header/footer repetition on pages
	 * copies already bound boxes rather than rebinding, so the seal → single-bind lease
	 * lifetime matches float/absolute (sealed at DocumentBuilder close).
	 */
	@Override
	public void sealCellContext(final Builder cellBuilder) {
		final CellContent cell = this.pendingSealCell;
		if (cell == null) {
			// Context without an open cell to seal, such as a caption
			return;
		}
		this.pendingSealCell = null;
		if (cell.isExtended() || cell.getBuilder() != cellBuilder) {
			// Structurally impossible (cells are sequential), but ignore it to fail closed.
			return;
		}
		cell.sealForRangeBind();
	}

	public Builder newContext(AbstractContainerBox box) {
		Builder builder;
		switch (box.getType()) {
		case BLOCK: {
			// Caption
			FlowBlockBox caption = (FlowBlockBox) box;
			builder = new TwoPassBlockBuilder(this.builder, caption);
			((TwoPassBlockBuilder) builder).tagRootKind(
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind.INCREMENTAL_CAPTION);
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
			// Cell
			TableCellBox cellBox = (TableCellBox) box;
			builder = new TwoPassBlockBuilder(this.builder, cellBox);
			((TwoPassBlockBuilder) builder).tagRootKind(
					net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind.INCREMENTAL_CELL);
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
			// DP increment 1: remember the seal-on-close target. Cells discarded for exceeding the
			// column count (the remainder<=0 break above) never become CellContent,
			// so they never reach here and are not sealed. Sealing a builder that will not bind
			// would leave its lease unreleased, so this asymmetry is correct.
			this.pendingSealCell = cell;
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
	 * One row group's borders, transposed from row-wise streaming accumulation
	 * (rows = list, columns = array) to TableCollapsedBorders column-major arrays.
	 */
	private void makeBorder() {
		// Collapsed borders
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

		// Bottom caption
		for (int i = 0; i < this.bottomCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.bottomCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			this.builder.startFlowBlock(captionBox, this.captionInsetStart(), this.captionInsetEnd());
			captionBuilder.bind(this.builder);
			this.builder.endFlowBlock();
		}

		assert this.tableBox.getBlockBox().getPos().getType() == PosType.FLOW;
		this.builder.endFlowBlock();
	}

	public void finish(final net.zamasoft.foliojet.layout.builder.Builder host) {
		// Incremental has already committed row by row. Finalize only the remainder.
		this.endLayout();
	}

	/**
	 * Incremental constructs cells using the same streaming mechanism as the outer
	 * DocumentBuilder, so it must close the inline context before calling newContext.
	 * This brackets "crossing a flow boundary", like startColumnSpan/endColumnSpan at
	 * multi-column span boundaries (C4-C deepening, 2026-07-19).
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
	 * Returns a column specification from a first-row cell in fixed layout (null for AUTO).
	 * Divide the specification equally by the cell's colspan. If specified, first calculate
	 * the cell frame ignoring padding percentages (prepareLayout).
	 *
	 * @param cell              cell
	 * @param refSize           reference size for percentage specifications
	 * @param containerBox      containing block
	 * @param lineBorderSpacing line-axis border spacing
	 * @return column specification
	 */
	private FixedColumnWidths.Spec fixedCellSpec(final CellContent cell, final double refSize,
			final AbstractContainerBox containerBox, final double lineBorderSpacing) {
		final TableCellBox cellBox = cell.getCellBox();
		final BlockParams cellParams = cellBox.getBlockParams();
		final TableParams tableParams = this.tableBox.getTableParams();
		final WritingMode tableFlow = tableParams.flow;
		if (cellParams.size.getLineType(tableFlow) != LengthType.AUTO) {
			// Calculate the cell frame ignoring padding percentages.
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
		// Specification derivation is unified in FixedColumnWidths (P2-2).
		return FixedColumnWidths.cellSpec(cellBox, cell.colspan, tableFlow, refSize);
	}

	/**
	 * Records that one table row has been finalized (introduced 2026-07-27).
	 *
	 * <p>
	 * The deadline ({@code AbstractUserAgent}'s "abort when progress stops") treats page
	 * output as progress, but <b>measurement passes for huge auto-layout tables run a long
	 * time without emitting pages</b>. Measurement showed 400,000 rows took 37.5 seconds;
	 * extrapolating to 1 million rows gave about 94 seconds, approaching the default
	 * 120 seconds (2026-07-27).
	 * </p>
	 *
	 * <p>
	 * <b>Count "work completed", not "code executed".</b>
	 * Row finalization is monotonic work done once per row, so an idle loop cannot
	 * fake progress.
	 * </p>
	 */
	private void noteTableProgress() {
		this.builder.getPageContext().getPageGenerator().getUserAgent().noteProgress();
	}

}

/**
 * Joined rows.
 *  
 * @author MIYABE Tatsuhiko
 * @version $Id: IncrementalTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
 */
