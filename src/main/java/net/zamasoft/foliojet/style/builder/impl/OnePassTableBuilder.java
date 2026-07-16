package net.zamasoft.foliojet.style.builder.impl;

import net.zamasoft.foliojet.style.box.params.BoxSizingMode;

import net.zamasoft.foliojet.style.box.params.RowGroupType;

import net.zamasoft.foliojet.style.box.params.CaptionSideMode;

import net.zamasoft.foliojet.style.box.params.PageBreakMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.AbstractContainerBox;
import net.zamasoft.foliojet.style.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.IPageBreakableBox;
import net.zamasoft.foliojet.style.box.content.BreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.style.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.style.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.style.box.impl.TableBox;
import net.zamasoft.foliojet.style.box.impl.TableCellBox;
import net.zamasoft.foliojet.style.box.impl.TableColumnBox;
import net.zamasoft.foliojet.style.box.impl.TableColumnGroupBox;
import net.zamasoft.foliojet.style.box.impl.TableRowBox;
import net.zamasoft.foliojet.style.box.impl.TableRowBox.Cell;
import net.zamasoft.foliojet.style.box.impl.TableRowGroupBox;
import net.zamasoft.foliojet.style.box.params.LengthType;
import net.zamasoft.foliojet.style.box.params.PosType;
import net.zamasoft.foliojet.style.box.params.BlockParams;
import net.zamasoft.foliojet.style.box.params.Border;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.style.box.params.InnerTableParams;
import net.zamasoft.foliojet.style.box.params.Length;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.TableCaptionPos;
import net.zamasoft.foliojet.style.box.params.TableCellPos;
import net.zamasoft.foliojet.style.box.params.TableColumnPos;
import net.zamasoft.foliojet.style.box.params.TableParams;
import net.zamasoft.foliojet.style.box.params.TableRowGroupPos;
import net.zamasoft.foliojet.style.box.params.TableRowPos;

import net.zamasoft.foliojet.style.builder.Builder;
import net.zamasoft.foliojet.style.builder.TableBuilder;
import net.zamasoft.foliojet.style.part.AbsoluteInsets;
import net.zamasoft.foliojet.style.part.TableCollapsedBorders;
import net.zamasoft.foliojet.style.util.DoubleList;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.pdfg2d.util.NumberUtils;

/**
 * 固定レイアウトのテーブルを構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: OnePassTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
 */
public class OnePassTableBuilder implements TableBuilder {
	/**
	 * 構築中のテーブルセルです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: OnePassTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
	 */
	protected static class CellContent {
		private final Object cell;

		public final int rowspan, colspan;

		public CellContent(TwoPassBlockBuilder cellBuilder, int colspan) {
			this.cell = cellBuilder;
			TableCellBox cellBox = (TableCellBox) cellBuilder.getRootBox();
			this.rowspan = cellBox.getTableCellPos().rowspan;
			this.colspan = colspan;
		}

		public CellContent(TableCellBox cell, int rowspan, int colspan) {
			assert rowspan >= 1;
			assert colspan >= 1;
			this.cell = cell;
			this.rowspan = rowspan;
			this.colspan = colspan;
		}

		public boolean isExtended() {
			return this.cell instanceof TableCellBox;
		}

		public TwoPassBlockBuilder getBuilder() {
			return (TwoPassBlockBuilder) this.cell;
		}

		public TableCellBox getCellBox() {
			if (this.isExtended()) {
				return (TableCellBox) this.cell;
			}
			return (TableCellBox) this.getBuilder().getRootBox();
		}
	}

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

	public OnePassTableBuilder(TableBox tableBox) {
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
		double rowSize;
		InnerTableParams rowParams = rowBox.getInnerTableParams();
		switch (rowParams.size.getType()) {
		case ABSOLUTE:
			rowSize = rowParams.size.getLength();
			break;
		case RELATIVE:
		case AUTO:
			rowSize = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		switch (rowParams.minSize.getType()) {
		case ABSOLUTE:
			rowSize = Math.max(rowParams.minSize.getLength(), rowSize);
			break;
		case RELATIVE:
		case AUTO:
			break;
		default:
			throw new IllegalStateException();
		}
		switch (rowParams.maxSize.getType()) {
		case ABSOLUTE:
			rowSize = Math.min(rowParams.maxSize.getLength(), rowSize);
			break;
		case RELATIVE:
		case AUTO:
			break;
		default:
			throw new IllegalStateException();
		}
		return rowSize;
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
			tableInnerSize = StyleUtils.computeDimensionHeight(flowParams.size, lineSize);
			assert !StyleUtils.isNone(tableInnerSize);
			double minWidth = StyleUtils.computeDimensionHeight(flowParams.minSize, lineSize);
			tableInnerSize = Math.max(minWidth, tableInnerSize);
			double maxWidth = StyleUtils.computeDimensionHeight(flowParams.maxSize, lineSize);
			if (!StyleUtils.isNone(maxWidth) && !StyleUtils.isNone(tableInnerSize)) {
				tableInnerSize = Math.min(maxWidth, tableInnerSize);
			}
		} else {
			tableFrame = this.tableBox.getFrame().getFrameWidth();
			lineBorderSpacing = tableParams.borderSpacingH;
			tableInnerSize = StyleUtils.computeDimensionWidth(flowParams.size, lineSize);
			double minWidth = StyleUtils.computeDimensionWidth(flowParams.minSize, lineSize);
			tableInnerSize = Math.max(minWidth, tableInnerSize);
			double maxWidth = StyleUtils.computeDimensionWidth(flowParams.maxSize, lineSize);
			if (!StyleUtils.isNone(maxWidth) && !StyleUtils.isNone(tableInnerSize)) {
				tableInnerSize = Math.min(maxWidth, tableInnerSize);
			}
		}
		if (StyleUtils.isNone(tableInnerSize)) {
			tableInnerSize = flowBox.getLineSize();
		}
		tableInnerSize -= tableFrame;

		int columnCount = 0;
		if (this.columnGroupBox != null) {
			List<Object> stack = new ArrayList<Object>();
			TableColumnGroupBox colgroup = this.columnGroupBox;
			this.tableBox.setTableColumnGroup(colgroup);
			int i = 0;
			RECURSE: for (;;) {
				for (; i < colgroup.getTableColumnCount(); ++i) {
					TableColumnBox column = colgroup.getTableColumn(i);
					TableColumnPos colParams = column.getTableColumnPos();
					if (column.getType() == BoxType.TABLE_COLUMN_GROUP
							&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
						stack.add(colgroup);
						stack.add(NumberUtils.intValue(i + 1));
						colgroup = (TableColumnGroupBox) column;
						i = 0;
						continue RECURSE;
					} else {
						columnCount += colParams.span;
					}
				}
				if (stack.isEmpty()) {
					break;
				}
				i = ((Integer) stack.remove(stack.size() - 1)).intValue();
				colgroup = (TableColumnGroupBox) stack.remove(stack.size() - 1);
			}
		}
		columnCount = Math.max(this.cells == null ? 0 : this.cells.size(), columnCount);
		double refSize = tableInnerSize;
		if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
			// 分離境界
			refSize -= columnCount * lineBorderSpacing;
		}
		refSize = Math.max(0, refSize);
		ColumnSize[] columnSizeList = new ColumnSize[columnCount];
		if (this.columnGroupBox != null) {
			List<Object> stack = new ArrayList<Object>();
			TableColumnGroupBox colgroup = this.columnGroupBox;
			int i = 0, k = 0;
			RECURSE: for (;;) {
				for (; i < colgroup.getTableColumnCount(); ++i) {
					TableColumnBox column = colgroup.getTableColumn(i);
					InnerTableParams colParams = column.getInnerTableParams();
					TableColumnPos colPos = column.getTableColumnPos();
					if (column.getType() == BoxType.TABLE_COLUMN_GROUP
							&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
						stack.add(colgroup);
						stack.add(NumberUtils.intValue(i + 1));
						colgroup = (TableColumnGroupBox) column;
						i = 0;
						continue RECURSE;
					} else {
						switch (colParams.size.getType()) {
						case AUTO:
							for (int j = 0; j < colPos.span; ++j) {
								columnSizeList[k++] = null;
							}
							break;
						case ABSOLUTE: {
							double fix = colParams.size.getLength();
							if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
								// 分離境界
								fix += lineBorderSpacing;
							}
							ColumnSize size = new ColumnSize(fix, false);
							for (int j = 0; j < colPos.span; ++j) {
								columnSizeList[k++] = size;
							}
						}
							break;
						case RELATIVE: {
							double fix = refSize * colParams.size.getLength();
							if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
								// 分離境界
								fix += lineBorderSpacing;
							}
							ColumnSize size = new ColumnSize(fix, true);
							for (int j = 0; j < colPos.span; ++j) {
								columnSizeList[k++] = size;
							}
						}
							break;

						default:
							throw new IllegalStateException();
						}
					}
				}
				if (stack.isEmpty()) {
					break;
				}
				i = ((Integer) stack.remove(stack.size() - 1)).intValue();
				colgroup = (TableColumnGroupBox) stack.remove(stack.size() - 1);
			}
		}

		this.columnSizes = new double[columnCount];
		int autoCount = 0;
		double sizeSum = 0, percentSizeSum = 0;
		for (int i = 0; i < columnCount; ++i) {
			ColumnSize size;
			if (this.cells == null || i >= this.cells.size()) {
				// table-columnによる幅指定しか存在しない場合
				if (columnSizeList[i] == null) {
					size = null;
					++autoCount;
				} else {
					size = columnSizeList[i];
					sizeSum += size.length;
					if (size.percentage) {
						percentSizeSum += size.length;
					}
				}
				this.columnSizes[i] = size == null ? StyleUtils.NONE : size.length;
			} else {
				// table-cellによる幅指定がある場合
				final CellContent cell = (CellContent) this.cells.get(i);
				final TableCellBox cellBox = cell.getCellBox();
				final BlockParams cellParams = cellBox.getBlockParams();
				if (this.vertical) {
					if (cellParams.size.getHeightType() != LengthType.AUTO) {
						// パディングの％を無視してセルの外周を計算
						double space;
						if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
							space = lineBorderSpacing / 2.0;
						} else {
							space = 0;
						}
						AbsoluteInsets cellSpacing = new AbsoluteInsets(space, 0, space, 0);
						cellBox.prepareLayout(containerBox.getLineSize(), this.tableBox, cellSpacing);
					}
					switch (cellParams.size.getHeightType()) {
					case AUTO:
						size = null;
						break;
					case ABSOLUTE: {
						double fix = cellParams.size.getHeight();
						if (cellParams.boxSizing == BoxSizingMode.CONTENT_BOX) {
							fix += cellBox.getFrame().getFrameHeight();
						}
						fix /= cell.colspan;
						size = new ColumnSize(fix, false);
					}
						break;
					case RELATIVE: {
						double fix = refSize * cellParams.size.getHeight();
						if (cellParams.boxSizing == BoxSizingMode.CONTENT_BOX) {
							fix += cellBox.getFrame().getFrameHeight();
						}
						fix /= cell.colspan;
						size = new ColumnSize(fix, true);
					}
						break;
					default:
						throw new IllegalStateException();
					}
				} else {
					if (cellParams.size.getWidthType() != LengthType.AUTO) {
						// パディングの％を無視してセルの外周を計算
						double space;
						if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
							space = lineBorderSpacing / 2.0;
						} else {
							space = 0;
						}
						AbsoluteInsets cellSpacing = new AbsoluteInsets(0, space, 0, space);
						cellBox.prepareLayout(containerBox.getLineSize(), this.tableBox, cellSpacing);
					}
					switch (cellParams.size.getWidthType()) {
					case AUTO:
						size = null;
						break;
					case ABSOLUTE: {
						double fix = cellParams.size.getWidth();
						if (cellParams.boxSizing == BoxSizingMode.CONTENT_BOX) {
							fix += cellBox.getFrame().getFrameWidth();
						}
						fix /= cell.colspan;
						size = new ColumnSize(fix, false);
					}
						break;
					case RELATIVE: {
						double fix = refSize * cellParams.size.getWidth();
						if (cellParams.boxSizing == BoxSizingMode.CONTENT_BOX) {
							fix += cellBox.getFrame().getFrameWidth();
						}
						fix /= cell.colspan;
						size = new ColumnSize(fix, true);
					}
						break;
					default:
						throw new IllegalStateException();
					}
				}

				if (columnSizeList[i] == null) {
					if (size == null) {
						++autoCount;
					} else {
						// セルによる幅指定だけの場合
						columnSizeList[i] = size;
						sizeSum += size.length;
						if (size.percentage) {
							percentSizeSum += size.length;
						}
					}
					this.columnSizes[i] = size == null ? StyleUtils.NONE : size.length;
				} else {
					ColumnSize columnWidth = columnSizeList[i];
					this.columnSizes[i] = columnWidth.length;
					sizeSum += this.columnSizes[i];
					if (columnWidth.percentage) {
						percentSizeSum += columnWidth.length;
					}
				}
				for (int j = 1; j < cell.colspan; ++j) {
					++i;
					if (columnSizeList[i] == null) {
						if (size == null) {
							++autoCount;
						} else {
							columnSizeList[i] = size;
							sizeSum += size.length;
							if (size.percentage) {
								percentSizeSum += size.length;
							}
						}
						this.columnSizes[i] = size == null ? StyleUtils.NONE : size.length;
					} else {
						ColumnSize columnWidth = columnSizeList[i];
						this.columnSizes[i] = columnWidth.length;
						sizeSum += this.columnSizes[i];
						if (columnWidth.percentage) {
							percentSizeSum += columnWidth.length;
						}
					}
				}
			}
		}

		// System.err.println(autoCount + "/" + tableWidth + "/" +
		// widthSum+"/"+percentWidthSum);
		if (percentSizeSum > 0 && sizeSum > tableInnerSize) {
			// テーブル幅を超えたパーセント幅は削る
			final double removeSize = Math.min(percentSizeSum, sizeSum - tableInnerSize);
			for (int i = 0; i < columnCount; ++i) {
				ColumnSize size = columnSizeList[i];
				if (size != null && size.percentage) {
					double sizeDiff = removeSize * size.length / percentSizeSum;
					this.columnSizes[i] -= sizeDiff;
					sizeSum -= sizeDiff;
				}
			}
		}
		if (autoCount > 0) {
			final double size;
			if (tableInnerSize > sizeSum) {
				size = (tableInnerSize - sizeSum) / autoCount;
			} else {
				tableInnerSize = sizeSum;
				size = 0;
			}
			for (int i = 0; i < columnCount; ++i) {
				if (StyleUtils.isNone(this.columnSizes[i])) {
					this.columnSizes[i] = size;
				}
			}
		} else if (tableInnerSize > sizeSum) {
			final double size = (tableInnerSize - sizeSum) / columnCount;
			for (int i = 0; i < columnCount; ++i) {
				this.columnSizes[i] += size;
			}
		} else {
			tableInnerSize = 0;
			for (int i = 0; i < columnCount; ++i) {
				tableInnerSize += this.columnSizes[i];
			}
		}

		// テーブルのレイアウト
		final double tableSize = tableInnerSize + tableFrame;
		flowBox.shrinkToFit(this.builder, tableSize, tableSize, true);

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
			int col = 0;
			List<Object> stack = new ArrayList<Object>();
			TableColumnGroupBox colgroup = this.columnGroupBox;
			int i = 0;
			RECURSE: for (;;) {
				for (; i < colgroup.getTableColumnCount(); ++i) {
					TableColumnBox column = colgroup.getTableColumn(i);
					TableColumnPos colPos = column.getTableColumnPos();

					int span;
					if (column.getType() == BoxType.TABLE_COLUMN_GROUP
							&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
						span = ((TableColumnGroupBox) column).getTableColumnCount();
					} else {
						span = colPos.span;
					}
					double size = 0;
					for (int j = 0; j < span; ++j) {
						size += this.columnSizes[col + j];
					}
					column.setLineSize(size);
					if (column.getType() == BoxType.TABLE_COLUMN_GROUP
							&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
						stack.add(colgroup);
						stack.add(NumberUtils.intValue(i + 1));
						colgroup = (TableColumnGroupBox) column;
						i = 0;
						continue RECURSE;
					} else {
						col += span;
					}
				}
				if (stack.isEmpty()) {
					break;
				}
				i = ((Integer) stack.remove(stack.size() - 1)).intValue();
				colgroup = (TableColumnGroupBox) stack.remove(stack.size() - 1);
			}
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
		List<Object> stack = new ArrayList<Object>();
		TableColumnGroupBox colgroup = this.columnGroupBox;
		int i = 0;
		RECURSE: for (;;) {
			for (; i < colgroup.getTableColumnCount(); ++i) {
				TableColumnBox column = colgroup.getTableColumn(i);
				column.setPageSize(pageSize);
				if (column.getType() == BoxType.TABLE_COLUMN_GROUP
						&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
					stack.add(colgroup);
					stack.add(NumberUtils.intValue(i + 1));
					colgroup = (TableColumnGroupBox) column;
					i = 0;
					continue RECURSE;
				}
			}
			if (stack.isEmpty()) {
				break;
			}
			i = ((Integer) stack.remove(stack.size() - 1)).intValue();
			colgroup = (TableColumnGroupBox) stack.remove(stack.size() - 1);
		}
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
			if (this.vertical) {
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

					// テーブル境界
					// 左
					lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0],
							tableParams.frame.border.getTop());
					// 右
					lineBorder[lineBorder.length - 1] = TableCollapsedBorders
							.collapseBorder(lineBorder[lineBorder.length - 1], tableParams.frame.border.getTop());
					// 上
					if (firstRow && row == 0) {
						for (int i = 0; i < firstBorder.length; ++i) {
							firstBorder[i] = TableCollapsedBorders.collapseBorder(firstBorder[i],
									tableParams.frame.border.getRight());
						}
					}
					// 下
					if (lastRow && row == this.cellsUnit.size() - 1) {
						for (int i = 0; i < lastBorder.length; ++i) {
							lastBorder[i] = TableCollapsedBorders.collapseBorder(lastBorder[i],
									tableParams.frame.border.getLeft());
						}
					}

					// カラムグループ境界
					// カラム境界
					if (this.columnGroupBox != null) {
						TableColumnGroupBox columnGroup = this.columnGroupBox;
						int col = 0;
						List<Object> stack = new ArrayList<Object>();
						int i = 0;
						RECURSE: for (;;) {
							for (; i < columnGroup.getTableColumnCount(); ++i) {
								TableColumnBox column = columnGroup.getTableColumn(i);
								InnerTableParams colParams = column.getInnerTableParams();
								TableColumnPos colPos = column.getTableColumnPos();
								int colspan;
								if (column.getType() == BoxType.TABLE_COLUMN_GROUP
										&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
									colspan = ((TableColumnGroupBox) column).getTableColumnCount();
								} else {
									colspan = colPos.span;
								}
								if (firstRow && row == 0) {
									for (int j = 0; j < colspan; ++j) {
										int jj = col + j;
										// 列グループ上
										firstBorder[jj] = TableCollapsedBorders.collapseBorder(firstBorder[jj],
												colParams.border.getRight());
									}
								}
								if (lastRow && row == this.cellsUnit.size() - 1) {
									for (int j = 0; j < colspan; ++j) {
										int jj = col + j;
										// 列グループ下
										lastBorder[jj] = TableCollapsedBorders.collapseBorder(lastBorder[jj],
												colParams.border.getLeft());
									}
								}
								// 行グループ左
								lineBorder[col] = TableCollapsedBorders.collapseBorder(lineBorder[col],
										colParams.border.getTop());
								// 行グループ右
								lineBorder[col + colspan] = TableCollapsedBorders
										.collapseBorder(lineBorder[col + colspan], colParams.border.getBottom());
								if (column.getType() == BoxType.TABLE_COLUMN_GROUP
										&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
									stack.add(columnGroup);
									stack.add(NumberUtils.intValue(i + 1));
									columnGroup = (TableColumnGroupBox) column;
									i = 0;
									continue RECURSE;
								} else {
									col += colspan;
								}
							}
							if (stack.isEmpty()) {
								break;
							}
							i = ((Integer) stack.remove(stack.size() - 1)).intValue();
							columnGroup = (TableColumnGroupBox) stack.remove(stack.size() - 1);
						}
					}

					// 行グループ境界
					// 左
					lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0], rowGroupParams.border.getTop());
					// 右
					lineBorder[lineBorder.length - 1] = TableCollapsedBorders
							.collapseBorder(lineBorder[lineBorder.length - 1], rowGroupParams.border.getTop());
					// 上
					if (groupFirst) {
						for (int j = 0; j < this.columnSizes.length; ++j) {
							firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
									rowGroupParams.border.getRight());
						}
					}
					// 下
					if (groupLast) {
						for (int j = 0; j < this.columnSizes.length; ++j) {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
									rowGroupParams.border.getLeft());
						}
					}

					// 行境界
					TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(row);
					InnerTableParams rowParams = rowBox.getInnerTableParams();
					// 左
					lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0], rowParams.border.getTop());
					// 右
					lineBorder[lineBorder.length - 1] = TableCollapsedBorders
							.collapseBorder(lineBorder[lineBorder.length - 1], rowParams.border.getTop());
					// 下
					for (int j = 0; j < cells.size(); ++j) {
						CellContent cell = (CellContent) cells.get(j);
						if (cell.rowspan == 1) {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
									rowParams.border.getLeft());
						}
					}
					// 次の行の上
					List<?> nextCells = (row < this.cellsUnit.size() - 1) ? (List<?>) this.cellsUnit.get(row + 1)
							: this.cells;
					if (!groupLast || row < this.cellsUnit.size() - 1) {
						InnerTableParams nextRowParams = this.rowBox.getInnerTableParams();
						for (int j = 0; j < nextCells.size(); ++j) {
							CellContent nextCell = (CellContent) nextCells.get(j);
							TableCellPos cellPos = nextCell.getCellBox().getTableCellPos();
							if (nextCell.rowspan == cellPos.rowspan) {
								lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
										nextRowParams.border.getRight());
							}
						}
					}
					if (groupFirst && row == 0) {
						// 最初の行の上
						for (int j = 0; j < cells.size(); ++j) {
							firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
									rowParams.border.getRight());
						}
					}

					// セル境界
					for (int j = 0; j < cells.size(); ++j) {
						CellContent cell = (CellContent) cells.get(j);
						BlockParams cellParams = cell.getCellBox().getBlockParams();
						// 左
						// System.out.println((this.vborders.size() -
						// 1)+"/"+j+"/"+cell.colspan);
						lineBorder[j] = TableCollapsedBorders.collapseBorder(lineBorder[j],
								cellParams.frame.border.getTop());
						j += cell.colspan - 1;
						// 右
						lineBorder[j + 1] = TableCollapsedBorders.collapseBorder(lineBorder[j + 1],
								cellParams.frame.border.getBottom());
					}

					if (groupFirst && row == 0) {
						// 最初の行の上
						for (int j = 0; j < cells.size(); ++j) {
							CellContent cell = (CellContent) cells.get(j);
							BlockParams cellParams = cell.getCellBox().getBlockParams();
							firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
									cellParams.frame.border.getRight());
							// System.out.println((this.hborders.size() - 2) +
							// "/" +
							// j
							// + "/" + topBorder[j]);
						}
					}
					for (int j = 0; j < cells.size(); ++j) {
						CellContent cell = (CellContent) cells.get(j);
						BlockParams cellParams = cell.getCellBox().getBlockParams();
						// 下
						if (cell.rowspan == 1) {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
									cellParams.frame.border.getLeft());
						} else {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j], Border.NONE_BORDER);
						}
					}
					// 次の行の上
					if (!groupLast || row < this.cellsUnit.size() - 1) {
						for (int j = 0; j < nextCells.size(); ++j) {
							CellContent cell = (CellContent) nextCells.get(j);
							BlockParams cellParams = cell.getCellBox().getBlockParams();
							if (cell.rowspan == cell.getCellBox().getTableCellPos().rowspan) {
								lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
										cellParams.frame.border.getRight());
							} else {
								lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j], Border.NONE_BORDER);
							}
						}
					}
				}
			} else {
				for (int row = 0; row < this.cellsUnit.size(); ++row) {
					List<?> cells = (List<?>) this.cellsUnit.get(row);
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

					// テーブル境界
					// 左
					lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0],
							tableParams.frame.border.getLeft());
					// 右
					lineBorder[lineBorder.length - 1] = TableCollapsedBorders
							.collapseBorder(lineBorder[lineBorder.length - 1], tableParams.frame.border.getLeft());
					// 上
					if (firstRow && row == 0) {
						for (int i = 0; i < firstBorder.length; ++i) {
							firstBorder[i] = TableCollapsedBorders.collapseBorder(firstBorder[i],
									tableParams.frame.border.getTop());
						}
					}
					// 下
					if (lastRow && row == this.cellsUnit.size() - 1) {
						for (int i = 0; i < lastBorder.length; ++i) {
							lastBorder[i] = TableCollapsedBorders.collapseBorder(lastBorder[i],
									tableParams.frame.border.getBottom());
						}
					}

					// カラムグループ境界
					// カラム境界
					if (this.columnGroupBox != null) {
						TableColumnGroupBox columnGroup = this.columnGroupBox;
						int col = 0;
						List<Object> stack = new ArrayList<Object>();
						int i = 0;
						RECURSE: for (;;) {
							for (; i < columnGroup.getTableColumnCount(); ++i) {
								TableColumnBox column = columnGroup.getTableColumn(i);
								InnerTableParams colParams = column.getInnerTableParams();
								TableColumnPos colPos = column.getTableColumnPos();
								int colspan;
								if (column.getType() == BoxType.TABLE_COLUMN_GROUP
										&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
									colspan = ((TableColumnGroupBox) column).getTableColumnCount();
								} else {
									colspan = colPos.span;
								}
								if (firstRow && row == 0) {
									for (int j = 0; j < colspan; ++j) {
										int jj = col + j;
										// 列グループ上
										firstBorder[jj] = TableCollapsedBorders.collapseBorder(firstBorder[jj],
												colParams.border.getTop());
									}
								}
								if (lastRow && row == this.cellsUnit.size() - 1) {
									for (int j = 0; j < colspan; ++j) {
										int jj = col + j;
										// 列グループ下
										lastBorder[jj] = TableCollapsedBorders.collapseBorder(lastBorder[jj],
												colParams.border.getBottom());
									}
								}
								// 行グループ左
								lineBorder[col] = TableCollapsedBorders.collapseBorder(lineBorder[col],
										colParams.border.getLeft());
								// 行グループ右
								lineBorder[col + colspan] = TableCollapsedBorders
										.collapseBorder(lineBorder[col + colspan], colParams.border.getRight());
								if (column.getType() == BoxType.TABLE_COLUMN_GROUP
										&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
									stack.add(columnGroup);
									stack.add(NumberUtils.intValue(i + 1));
									columnGroup = (TableColumnGroupBox) column;
									i = 0;
									continue RECURSE;
								} else {
									col += colspan;
								}
							}
							if (stack.isEmpty()) {
								break;
							}
							i = ((Integer) stack.remove(stack.size() - 1)).intValue();
							columnGroup = (TableColumnGroupBox) stack.remove(stack.size() - 1);
						}
					}

					// 行グループ境界
					// 左
					lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0],
							rowGroupParams.border.getLeft());
					// 右
					lineBorder[lineBorder.length - 1] = TableCollapsedBorders
							.collapseBorder(lineBorder[lineBorder.length - 1], rowGroupParams.border.getLeft());
					// 上
					if (groupFirst) {
						for (int j = 0; j < this.columnSizes.length; ++j) {
							firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
									rowGroupParams.border.getTop());
						}
					}
					// 下
					if (groupLast) {
						for (int j = 0; j < this.columnSizes.length; ++j) {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
									rowGroupParams.border.getBottom());
						}
					}

					// 行境界
					TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(row);
					InnerTableParams rowParams = rowBox.getInnerTableParams();
					// 左
					lineBorder[0] = TableCollapsedBorders.collapseBorder(lineBorder[0], rowParams.border.getLeft());
					// 右
					lineBorder[lineBorder.length - 1] = TableCollapsedBorders
							.collapseBorder(lineBorder[lineBorder.length - 1], rowParams.border.getLeft());
					// 下
					for (int j = 0; j < cells.size(); ++j) {
						CellContent cell = (CellContent) cells.get(j);
						if (cell.rowspan == 1) {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
									rowParams.border.getBottom());
						}
					}
					// 次の行の上
					List<?> nextCells = (row < this.cellsUnit.size() - 1) ? (List<?>) this.cellsUnit.get(row + 1)
							: this.cells;
					if (!groupLast || row < this.cellsUnit.size() - 1) {
						InnerTableParams nextRowParams = this.rowBox.getInnerTableParams();
						for (int j = 0; j < nextCells.size(); ++j) {
							CellContent nextCell = (CellContent) nextCells.get(j);
							TableCellPos cellPps = nextCell.getCellBox().getTableCellPos();
							if (nextCell.rowspan == cellPps.rowspan) {
								lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
										nextRowParams.border.getTop());
							}
						}
					}
					if (groupFirst && row == 0) {
						// 最初の行の上
						for (int j = 0; j < cells.size(); ++j) {
							firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
									rowParams.border.getTop());
						}
					}

					// セル境界
					for (int j = 0; j < cells.size(); ++j) {
						CellContent cell = (CellContent) cells.get(j);
						BlockParams cellParams = cell.getCellBox().getBlockParams();
						// 左
						// System.out.println((this.vborders.size() -
						// 1)+"/"+j+"/"+cell.colspan);
						lineBorder[j] = TableCollapsedBorders.collapseBorder(lineBorder[j],
								cellParams.frame.border.getLeft());
						j += cell.colspan - 1;
						// 右
						lineBorder[j + 1] = TableCollapsedBorders.collapseBorder(lineBorder[j + 1],
								cellParams.frame.border.getRight());
					}

					if (groupFirst && row == 0) {
						// 最初の行の上
						for (int j = 0; j < cells.size(); ++j) {
							CellContent cell = (CellContent) cells.get(j);
							BlockParams cellParams = cell.getCellBox().getBlockParams();
							firstBorder[j] = TableCollapsedBorders.collapseBorder(firstBorder[j],
									cellParams.frame.border.getTop());
							// System.out.println((this.hborders.size() - 2) +
							// "/" +
							// j
							// + "/" + topBorder[j]);
						}
					}
					for (int j = 0; j < cells.size(); ++j) {
						CellContent cell = (CellContent) cells.get(j);
						BlockParams cellParams = cell.getCellBox().getBlockParams();
						// 下
						if (cell.rowspan == 1) {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
									cellParams.frame.border.getBottom());
						} else {
							lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j], Border.NONE_BORDER);
						}
					}
					// 次の行の上
					if (!groupLast || row < this.cellsUnit.size() - 1) {
						for (int j = 0; j < nextCells.size(); ++j) {
							CellContent cell = (CellContent) nextCells.get(j);
							BlockParams cellParams = cell.getCellBox().getBlockParams();
							if (cell.rowspan == cell.getCellBox().getTableCellPos().rowspan) {
								lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j],
										cellParams.frame.border.getTop());
							} else {
								lastBorder[j] = TableCollapsedBorders.collapseBorder(lastBorder[j], Border.NONE_BORDER);
							}
						}
					}
				}
			}
		}

		// セルのレイアウト
		for (int row = 0; row < this.cellsUnit.size(); ++row) {
			final List<?> cells = (List<?>) this.cellsUnit.get(row);
			final TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(row);
			for (int i = 0; i < cells.size(); ++i) {
				CellContent cell = (CellContent) cells.get(i);
				int span = cell.colspan;
				if (cell.isExtended()) {
					i += span - 1;
					Cell rcell = (Cell) this.cellToSource.get(cell.getCellBox());
					this.cellToSource.put(cell.getCellBox(), rowBox.addTableExtendedCell(rcell));
					continue;
				}
				final TableCellBox cellBox = cell.getCellBox();
				if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
					// 分離境界
					double top = tableParams.borderSpacingV / 2.0;
					double right = tableParams.borderSpacingH / 2.0;
					double bottom = tableParams.borderSpacingV / 2.0;
					double left = tableParams.borderSpacingH / 2.0;
					AbsoluteInsets cellSpacing = new AbsoluteInsets(top, right, bottom, left);
					cellBox.prepareLayout(this.builder.getFlowBox().getLineSize(), this.tableBox, cellSpacing);
				} else {
					// つぶし境界
					List<Border[]> vborders, hborders;
					switch (rowGroupPos.rowGroupType) {
					case RowGroupType.HEADER: {
						vborders = this.headerVborders;
						hborders = this.headerHborders;
					}
						break;

					case RowGroupType.FOOTER: {
						vborders = this.footerVborders;
						hborders = this.footerHborders;
					}
						break;

					case RowGroupType.BODY: {
						vborders = this.bodyVborders;
						hborders = this.bodyHborders;
					}
						break;
					default:
						throw new IllegalStateException();
					}
					double pageFirst = 0, lineEnd = 0, pageLast = 0, lineStart = 0;
					int borderRow = hborders.size() - this.cellsUnit.size() + row;
					Border[] prevBorder = (Border[]) hborders.get(borderRow - 1);
					Border[] nextBorder = (Border[]) hborders
							.get(Math.min(hborders.size() - 1, borderRow + cell.rowspan - 1));
					Border[] lineBorder = (Border[]) vborders.get(borderRow - 1);
					for (int k = 0; k < cell.colspan; ++k) {
						int kk = i + k;
						if (kk >= this.columnSizes.length) {
							break;
						}
						if (prevBorder[kk] != null) {
							pageFirst = Math.max(pageFirst, prevBorder[kk].width / 2.0);
						}
						if (nextBorder[kk] != null) {
							pageLast = Math.max(pageLast, nextBorder[kk].width / 2.0);
						}
					}
					if (lineBorder[i] != null) {
						lineStart = Math.max(lineStart, lineBorder[i].width / 2.0);
					}
					if (lineBorder[i + cell.colspan] != null) {
						lineEnd = Math.max(lineEnd, lineBorder[i + cell.colspan].width / 2.0);
					}
					AbsoluteInsets spacing;
					if (this.vertical) {
						spacing = new AbsoluteInsets(lineStart, pageFirst, lineEnd, pageLast);
					} else {
						spacing = new AbsoluteInsets(pageFirst, lineEnd, pageLast, lineStart);
					}
					cellBox.prepareLayout(this.builder.getFlowBox().getLineSize(), this.tableBox, spacing);

				}

				double size = this.columnSizes[i];
				for (int j = 1; j < span; ++j) {
					size += this.columnSizes[++i];
				}
				if (this.vertical) {
					cellBox.setHeight(size);
					if (!cellBox.getBlockParams().flow.isVertical()) {
						cellBox.setWidth(cellBox.getBlockParams().fontStyle.getSize() * 10);
					}
				} else {
					cellBox.setWidth(size);
					if (cellBox.getBlockParams().flow.isVertical()) {
						cellBox.setHeight(cellBox.getBlockParams().fontStyle.getSize() * 10);
					}
				}
				final BlockBuilder cellBindBuilder = new BlockBuilder(this.builder, cellBox);
				cell.getBuilder().bind(cellBindBuilder);
				cellBindBuilder.close();
				Cell source = rowBox.addTableSourceCell(cellBox);
				this.cellToSource.put(cellBox, source);
			}
		}
		if (this.cellsUnit.size() == 1) {
			// rowspanによる連結がない場合の高さ計算
			final List<?> cells = (List<?>) this.cellsUnit.get(0);
			final TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(0);
			double rowSize = this.getSpecificRowSize(rowBox);
			double rowAscent = 0;
			for (int i = 0; i < cells.size(); ++i) {
				CellContent cell = (CellContent) cells.get(i);
				if (cell.isExtended()) {
					i += cell.colspan - 1;
					continue;
				}
				TableCellBox cellBox = cell.getCellBox();
				double firstAscent = cellBox.getFirstAscent();
				if (!StyleUtils.isNone(firstAscent) && firstAscent > rowAscent) {
					rowAscent = firstAscent;
				}
			}
			for (int i = 0; i < cells.size(); ++i) {
				final CellContent cell = (CellContent) cells.get(i);
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
			for (int j = 0; j < cells.size(); ++j) {
				CellContent cell = (CellContent) cells.get(j);
				if (cell.isExtended()) {
					continue;
				}
				TableCellBox cellBox = cell.getCellBox();
				cellBox.baseline(rowAscent);
				if (this.vertical) {
					cellBox.setWidth(rowSize);
				} else {
					cellBox.setHeight(rowSize);
				}
				cellBox.verticalAlign();
				j += cell.colspan - 1;
			}
			rowBox.setLineSize(this.tableInnerSize);
			rowBox.setPageSize(rowSize);
			this.bindRowGroupBox.addTableRow(rowBox);
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
				List<?> cells = (List<?>) this.cellsUnit.get(row);
				TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(row);
				InnerTableParams rowParams = rowBox.getInnerTableParams();
				double rowSize = this.getSpecificRowSize(rowBox);
				if (rowParams.size.getType() == LengthType.AUTO) {
					autoRows[row] = true;
				}
				double rowAscent = 0;
				for (int i = 0; i < cells.size(); ++i) {
					CellContent cell = (CellContent) cells.get(i);
					if (cell.isExtended()) {
						i += cell.colspan - 1;
						continue;
					}
					TableCellBox cellBox = cell.getCellBox();
					double firstAscent = cellBox.getFirstAscent();
					if (!StyleUtils.isNone(firstAscent) && firstAscent > rowAscent) {
						rowAscent = firstAscent;
					}
				}
				for (int i = 0; i < cells.size(); ++i) {
					CellContent cell = (CellContent) cells.get(i);
					if (cell.isExtended()) {
						i += cell.colspan - 1;
						continue;
					}
					final TableCellBox cellBox = cell.getCellBox();
					cellBox.baseline(rowAscent);
					final BlockParams cellParams = cellBox.getBlockParams();
					double cellSize = cellBox.getHeight();
					if (cellParams.size.getHeightType() == LengthType.ABSOLUTE) {
						double height = cellParams.size.getHeight();
						if (cellParams.boxSizing == BoxSizingMode.CONTENT_BOX) {
							height += cellBox.getFrame().getFrameHeight();
						}
						cellSize = Math.max(cellSize, height);
					}

					int cellRowspan = Math.min(this.cellsUnit.size() - row, cell.rowspan);
					if (cellRowspan <= 1) {
						// 連結されない行
						noAdjRows[row] = true;
						rowSize = Math.max(rowSize, cellSize);
					} else {
						// 連結された行
						Rowspan key = new Rowspan(row, cellRowspan);
						Rowspan rowspan = (Rowspan) rowspans.get(key);
						if (rowspan == null) {
							rowspan = key;
							rowspans.put(key, rowspan);
							rowspanList.add(rowspan);
						}
						rowspan.min = Math.max(rowspan.min, cellSize);
					}
					i += cell.colspan - 1;
				}
				rowBox.setPageSize(rowSize);
			}

			// rowspanで連結された行の高さの計算
			Collections.sort(rowspanList, Rowspan.SPAN_COMPARATOR);
			for (int j = 0; j < rowspanList.size(); ++j) {
				Rowspan rowspan = (Rowspan) rowspanList.get(j);
				double minSum = ((TableRowBox) this.rowsUnit.get(rowspan.row)).getPageSize();
				for (int k = 1; k < rowspan.span; ++k) {
					int kk = rowspan.row + k;
					if (kk < this.rowsUnit.size()) {
						TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(kk);
						minSum += rowBox.getPageSize();
					}
				}
				double minRem = rowspan.min - minSum;
				if (minRem > 0) {
					// minを分配
					double adjCount = 0, autoCount = 0;
					for (int k = 0; k < rowspan.span; ++k) {
						int kk = rowspan.row + k;
						if (kk >= this.rowsUnit.size()) {
							break;
						}
						if (!noAdjRows[kk] && autoRows[kk]) {
							++adjCount;
						}
						if (autoRows[kk]) {
							++autoCount;
						}
						// %の適用
						TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(kk);
						if (rowBox.getInnerTableParams().size.getType() == LengthType.RELATIVE) {
							double diff = minRem * rowBox.getInnerTableParams().size.getLength();
							minRem -= diff;
							rowBox.setPageSize(rowBox.getPageSize() + diff);
						}
					}
					if (adjCount > 0 && adjCount < rowspan.span) {
						// 連結により拡張したセルのだけの行に分配
						minRem /= adjCount;
						for (int k = 0; k < rowspan.span; ++k) {
							int kk = rowspan.row + k;
							if (kk >= this.rowsUnit.size()) {
								break;
							}
							if (!noAdjRows[kk] && autoRows[kk]) {
								TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(kk);
								double size = rowBox.getPageSize() + minRem;
								rowBox.setPageSize(size);
							}
						}
					} else if (autoCount > 0 && autoCount < rowspan.span) {
						// 自動高さの行に分配
						minRem /= autoCount;
						for (int k = 0; k < rowspan.span; ++k) {
							int kk = rowspan.row + k;
							if (kk >= this.rowsUnit.size()) {
								break;
							}
							if (autoRows[kk]) {
								TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(kk);
								double size = rowBox.getPageSize() + minRem;
								rowBox.setPageSize(size);
							}
						}
					} else {
						// 高さの分配
						minRem /= rowspan.span;
						for (int k = 0; k < rowspan.span; ++k) {
							int kk = rowspan.row + k;
							if (kk >= this.rowsUnit.size()) {
								break;
							}
							TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(kk);
							double size = rowBox.getPageSize() + minRem;
							rowBox.setPageSize(size);
						}
					}
				}
			}

			// 行グループ高さ
			if (rowGroupParams.size.getType() == LengthType.ABSOLUTE) {
				double rowSizeSum = 0;
				for (int row = 0; row < this.rowsUnit.size(); ++row) {
					TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(row);
					rowSizeSum += rowBox.getPageSize();
				}
				double groupSize = rowGroupParams.size.getLength();
				if (groupSize > rowSizeSum) {
					for (int row = 0; row < this.rowsUnit.size(); ++row) {
						TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(row);
						double rowSize = rowBox.getPageSize();
						if (rowSizeSum == 0) {
							rowBox.setPageSize(groupSize / this.rowsUnit.size());
						} else {
							rowBox.setPageSize(rowSize * groupSize / rowSizeSum);
						}
					}
				}
			}

			// 行の追加
			for (int row = 0; row < this.rowsUnit.size(); ++row) {
				TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(row);
				this.bindRowGroupBox.addTableRow(rowBox);
			}

			// セルの高さ設定
			for (int i = 0; i < this.rowsUnit.size(); ++i) {
				List<?> cells = (List<?>) this.cellsUnit.get(i);
				TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(i);
				for (int j = 0; j < cells.size(); ++j) {
					CellContent cell = (CellContent) cells.get(j);
					if (cell.isExtended()) {
						continue;
					}
					double rowSize = rowBox.getPageSize();
					for (int k = 1; k < cell.rowspan; ++k) {
						int kk = i + k;
						if (kk >= this.rowsUnit.size()) {
							break;
						}
						TableRowBox xrowBox = (TableRowBox) this.rowsUnit.get(kk);
						rowSize += xrowBox.getPageSize();
					}
					TableCellBox cellBox = cell.getCellBox();
					if (this.vertical) {
						cellBox.setWidth(rowSize);
					} else {
						cellBox.setHeight(rowSize);
					}
					cellBox.verticalAlign();
					j += cell.colspan - 1;
				}
			}
			if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
				// つぶし境界
				for (int i = 0; i < this.rowsUnit.size(); ++i) {
					TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(i);
					double rowSize = rowBox.getPageSize();
					this.pageSize += rowSize;
					this.addBorderRowSize(rowSize);
				}
			} else {
				for (int i = 0; i < this.rowsUnit.size(); ++i) {
					TableRowBox rowBox = (TableRowBox) this.rowsUnit.get(i);
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
				double pageBottom = this.builder.getPageLimit() - this.builder.getPageAxis();
				// System.err.println(this.pageAxis + "/" + pageBottom);
				if (StyleUtils.compare(this.pageSize, pageBottom) > 0) {
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
		final double firstFrame, lastFrame;
		if (this.tableBox.getTableParams().flow.isVertical()) {
			firstFrame = this.tableBox.getFrame().getFrameRight();
			lastFrame = this.tableBox.getFrame().getFrameLeft();
		} else {
			firstFrame = this.tableBox.getFrame().getFrameTop();
			lastFrame = this.tableBox.getFrame().getFrameBottom();
		}
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
			// System.err.println(row + "/" + bottom + "/" + pageLimit + "/"
			// + rowBox.getParams().augmentation);
			if (StyleUtils.compare(rowSize, rowSplitLine) > 0) {
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
		if (StyleUtils.compare(pageLimit, 0) > 0) {
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
		if (StyleUtils.compare(this.builder.getPageAxis(), 0) > 0) {
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
		TableRowGroupBox nextRowGroupBox = (TableRowGroupBox) rowGroupBox.splitPageAxis(pageLimit, mode, flags);
		if (nextRowGroupBox == null || nextRowGroupBox == rowGroupBox) {
			// 分割不可能
			return false;
		}
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

	private void complementRowspan() {
		if (!this.cellsUnit.isEmpty()) {
			// rowspanで連結されたセルの補完
			List<?> upperCells = (List<?>) this.cellsUnit.get(this.cellsUnit.size() - 1);
			while (upperCells.size() > this.cells.size()) {
				CellContent upperCell = (CellContent) upperCells.get(this.cells.size());
				if (upperCell.rowspan > 1) {
					for (int colspan = upperCell.colspan; colspan >= 1; --colspan) {
						this.cells.add(new CellContent(upperCell.getCellBox(), upperCell.rowspan - 1, colspan));
					}
				} else {
					break;
				}
			}
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

	private void makeBorder() {
		// つぶし境界
		int columnCount = this.columnSizes == null ? 0 : this.columnSizes.length;
		double[] headerRowSizes = null;
		Border[][] headerHborders = null;
		Border[][] headerVborders = null;

		// THEAD
		if (this.headerHborders != null) {
			int groupRowCount = this.headerVborders.size();
			headerRowSizes = this.headerRowSizes.toArray();
			headerHborders = new Border[columnCount][groupRowCount + 1];
			headerVborders = new Border[groupRowCount][];
			for (int i = 0; i < groupRowCount; ++i) {
				Border[] border = (Border[]) this.headerHborders.get(i);
				for (int j = 0; j < columnCount; ++j) {
					headerHborders[j][i] = border[j];
				}
				headerVborders[i] = (Border[]) this.headerVborders.get(i);
			}
			Border[] border = (Border[]) this.headerHborders.get(groupRowCount);
			for (int j = 0; j < columnCount; ++j) {
				headerHborders[j][headerRowSizes.length] = border[j];
			}
		}

		double[] bodyRowSizes = null;
		Border[][] bodyHborders = null;
		Border[][] bodyVborders = null;

		// TBODY
		if (this.bodyVborders != null) {
			int groupRowCount = this.bodyVborders.size();
			bodyRowSizes = this.bodyRowSizes.toArray();
			bodyHborders = new Border[columnCount][groupRowCount + 1];
			bodyVborders = new Border[groupRowCount][];
			for (int i = 0; i < groupRowCount; ++i) {
				Border[] border = (Border[]) this.bodyHborders.get(i);
				for (int j = 0; j < columnCount; ++j) {
					bodyHborders[j][i] = border[j];
				}
				bodyVborders[i] = (Border[]) this.bodyVborders.get(i);
			}
			Border[] border = (Border[]) this.bodyHborders.get(groupRowCount);
			for (int j = 0; j < columnCount; ++j) {
				bodyHborders[j][bodyRowSizes.length] = border[j];
			}
		}

		double[] footerRowSizes = null;
		Border[][] footerHborders = null;
		Border[][] footerVborders = null;

		// TFOOT
		if (this.footerHborders != null) {
			int groupRowCount = this.footerVborders.size();
			footerRowSizes = this.footerRowSizes.toArray();
			footerHborders = new Border[columnCount][groupRowCount + 1];
			footerVborders = new Border[groupRowCount][];
			for (int i = 0; i < groupRowCount; ++i) {
				Border[] border = (Border[]) this.footerHborders.get(i);
				for (int j = 0; j < columnCount; ++j) {
					footerHborders[j][i] = border[j];
				}
				footerVborders[i] = (Border[]) this.footerVborders.get(i);
			}
			Border[] border = (Border[]) this.footerHborders.get(groupRowCount);
			for (int j = 0; j < columnCount; ++j) {
				footerHborders[j][footerRowSizes.length] = border[j];
			}
		}

		TableCollapsedBorders borders = new TableCollapsedBorders(this.columnSizes, headerRowSizes, headerVborders,
				headerHborders, bodyRowSizes, bodyVborders, bodyHborders, footerRowSizes, footerVborders,
				footerHborders);
		this.tableBox.setCollapsedBorders(borders);

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

	public boolean isOnePass() {
		return true;
	}
}

class ColumnSize {
	final boolean percentage;
	final double length;

	ColumnSize(double length, boolean percentage) {
		this.length = length;
		this.percentage = percentage;
	}

	public String toString() {
		return this.length + (this.percentage ? "(rel)" : "");
	}
}

/**
 * 結合された行です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: OnePassTableBuilder.java 1613 2021-08-18 03:55:13Z miyabe $
 */
class Rowspan {
	/** 行番号(0オリジン) */
	public final int row;
	/** 結合される行の数 */
	public final int span;
	/** 最小幅 */
	public double min;

	public Rowspan(int row, int span) {
		assert span >= 2;
		this.row = row;
		this.span = span;
	}

	public boolean equals(Object o) {
		Rowspan rowspan = (Rowspan) o;
		return this.row == rowspan.row && this.span == rowspan.span;
	}

	public int hashCode() {
		int h = this.row;
		h = 31 * h + this.span;
		return h;
	}

	public static final Comparator<Rowspan> SPAN_COMPARATOR = new Comparator<Rowspan>() {
		public int compare(Rowspan o1, Rowspan o2) {
			Rowspan span1 = (Rowspan) o1;
			Rowspan span2 = (Rowspan) o2;
			if (span1.span > span2.span) {
				return 1;
			}
			if (span1.span < span2.span) {
				return -1;
			}
			return 0;
		}
	};
}
