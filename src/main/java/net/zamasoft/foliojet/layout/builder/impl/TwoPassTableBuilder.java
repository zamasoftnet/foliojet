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
 * 自動レイアウトのテーブルを構築します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TwoPassTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TwoPassTableBuilder implements TableBuilder, TwoPass {
	/**
	 * 構築中のテーブルセルです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: TwoPassTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
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
	 * 右の境界の中央から左の中央までを基準としたカラムの最小幅、指定幅、推奨幅です。
	 */
	private double[] columnMins, columnSpecs, columnDesiredWidths;

	/**
	 * カラムの指定幅のタイプです。
	 */
	private byte[] columnTypes;

	private static final byte PARAM_COUNT = 3;

	/**
	 * テーブルの外側の最小幅、最大幅です。
	 */
	private double minLineSize = 0, maxLineSize = 0;

	public TwoPassTableBuilder(LayoutStack layoutStack, TableBox tableBox) {
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
		double min = this.minLineSize, max = this.maxLineSize;
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
			final List<TableRowBox> rows = (ArrayList<TableRowBox>) this.rowGroupToRows.get(rowGroup);
			rows.add(row);
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
		}
			break;
		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	/**
	 * つぶし境界を生成します。
	 */
	private TableCollapsedBorders createBorders(int columnCount, int headerRowCount, int bodyRowCount,
			int footerRowCount, List<List<?>> rowLists, List<List<?>> cellLists) {
		final TableParams params = this.tableBox.getTableParams();

		// つぶし境界
		final TableCollapsedBorders borders = new TableCollapsedBorders(columnCount, headerRowCount, bodyRowCount,
				footerRowCount);
		int rowCount = headerRowCount + bodyRowCount + footerRowCount;

		RectBorder border = params.frame.border;
		final BorderAxes ax = this.vertical ? BorderAxes.VERTICAL : BorderAxes.HORIZONTAL;

		// テーブル境界
		for (int col = 0; col < columnCount; ++col) {
			borders.collapseHBorder(col, 0, false, ax.hStart().apply(border));
			borders.collapseHBorder(col, rowCount, true, ax.hEnd().apply(border));
		}
		for (int row = 0; row < rowCount; ++row) {
			borders.collapseVBorder(row, 0, ax.vStart().apply(border));
			borders.collapseVBorder(row, columnCount, ax.vEnd().apply(border));
		}

		// カラムグループ境界
		// カラム境界
		TableColumnGroupBox columnGroup = this.columnGroupBox;
		if (columnGroup != null) {
			int col = 0;
			List<Object> stack = new ArrayList<Object>();
			int i = 0;
			RECURSE: for (;;) {
				for (; i < columnGroup.getTableColumnCount(); ++i) {
					TableColumnBox column = columnGroup.getTableColumn(i);
					TableColumnPos colPos = column.getTableColumnPos();
					InnerTableParams colParams = column.getInnerTableParams();
					int colspan;
					if (column.getType() == BoxType.TABLE_COLUMN_GROUP
							&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
						colspan = ((TableColumnGroupBox) column).getTableColumnCount();
					} else {
						colspan = colPos.span;
					}
					for (int j = 0; j < colspan; ++j) {
						int jj = col + j;
						// 列グループ始端・終端(H)
						borders.collapseHBorder(jj, 0, false, ax.hStart().apply(colParams.border));
						borders.collapseHBorder(jj, rowCount, true, ax.hEnd().apply(colParams.border));
					}
					for (int j = 0; j < rowCount; ++j) {
						// 列グループ始端・終端(V)
						borders.collapseVBorder(j, col, ax.vStart().apply(colParams.border));
						borders.collapseVBorder(j, col + colspan, ax.vEnd().apply(colParams.border));
					}
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
		// 行境界
		{
			int row = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				TableRowGroupBox rowGroup = (TableRowGroupBox) this.rowGroups.get(i);
				InnerTableParams rowGroupParams = rowGroup.getInnerTableParams();
				List<?> rows = (List<?>) rowLists.get(i);
				int rowspan = rows.size();
				for (int j = 0; j < columnCount; ++j) {
					// 行グループ始端・終端(H)
					borders.collapseHBorder(j, row, false, ax.hStart().apply(rowGroupParams.border));
					borders.collapseHBorder(j, row + rowspan, true, ax.hEnd().apply(rowGroupParams.border));
				}
				for (int j = 0; j < rowspan; ++j) {
					int jj = row + j;
					// 行グループ始端・終端(V)
					borders.collapseVBorder(jj, 0, ax.vStart().apply(rowGroupParams.border));
					borders.collapseVBorder(jj, columnCount, ax.vEnd().apply(rowGroupParams.border));

					InnerTableParams rowParams = ((TableRowBox) rows.get(j)).getInnerTableParams();
					// 行始端・終端(V)
					borders.collapseVBorder(jj, 0, ax.vStart().apply(rowParams.border));
					borders.collapseVBorder(jj, columnCount, ax.vEnd().apply(rowParams.border));

					List<?> cells = (List<?>) cellLists.get(row + j);
					for (int k = 0; k < cells.size(); ++k) {
						CellContent cell = (CellContent) cells.get(k);
						TableCellPos cellPos = cell.getCellBox().getTableCellPos();
						// 行始端(H)
						if (cell.rowspan == cellPos.rowspan) {
							borders.collapseHBorder(k, jj, false, ax.hStart().apply(rowParams.border));
						}
						// 行終端(H)
						if (cell.rowspan == 1) {
							borders.collapseHBorder(k, jj + 1, true, ax.hEnd().apply(rowParams.border));
						}
					}
				}
				row += rowspan;
			}
		}

		// セル境界
		{
			int row = 0;
			for (int i = 0; i < rowGroups.size(); ++i) {
				List<?> rows = (List<?>) rowLists.get(i);
				for (int j = 0; j < rows.size(); ++j) {
					List<?> cells = (List<?>) cellLists.get(row);
					for (int col = 0; col < cells.size(); ++col) {
						CellContent cell = (CellContent) cells.get(col);
						if (cell.isExtended()) {
							continue;
						}
						BlockParams cellParams = cell.getCellBox().getBlockParams();
						TableCellPos cellPos = cell.getCellBox().getTableCellPos();
						int bottom = row + cellPos.rowspan;
						for (int k = 0; k < cellPos.colspan; ++k) {
							int kk = col + k;
							if (kk >= columnCount) {
								break;
							}
							// セル始端(H)
							borders.collapseHBorder(kk, row, false, ax.hStart().apply(cellParams.frame.border));
							for (int l = 1; l < cellPos.rowspan; ++l) {
								int ll = row + l;
								if (ll > rowCount) {
									break;
								}
								borders.collapseHBorder(kk, ll, false, Border.NONE_BORDER);
							}
							// セル終端(H)
							borders.collapseHBorder(kk, Math.min(rowCount, bottom), true,
									ax.hEnd().apply(cellParams.frame.border));
						}
						int right = col + cellPos.colspan;
						for (int k = 0; k < cellPos.rowspan; ++k) {
							int kk = row + k;
							if (kk >= rowCount) {
								break;
							}
							// セル始端(V)
							borders.collapseVBorder(kk, col, ax.vStart().apply(cellParams.frame.border));
							for (int l = 1; l < cellPos.colspan; ++l) {
								int ll = col + l;
								borders.collapseVBorder(kk, ll, Border.NONE_BORDER);
							}
							if (right <= columnCount) {
								// セル終端(V)
								borders.collapseVBorder(kk, right, ax.vEnd().apply(cellParams.frame.border));
							}
						}
						col = right - 1;
					}
					++row;
				}
			}
		}
		return borders;
	}

	/**
	 * テーブルと各カラムの最大幅、最小幅を確定します。 内側のテーブルから順に実行します。
	 */
	public void prepareLayout() {
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
		// カラム数と行数のカウント
		int columnCount;
		if (this.columnGroupBox != null) {
			columnCount = 0;
			List<Object> stack = new ArrayList<Object>();
			TableColumnGroupBox colgroup = this.columnGroupBox;
			this.tableBox.setTableColumnGroup(colgroup);
			int i = 0;
			RECURSE: for (;;) {
				for (; i < colgroup.getTableColumnCount(); ++i) {
					TableColumnBox column = colgroup.getTableColumn(i);
					TableColumnPos colPos = column.getTableColumnPos();
					if (column.getType() == BoxType.TABLE_COLUMN_GROUP
							&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
						stack.add(colgroup);
						stack.add(NumberUtils.intValue(i + 1));
						colgroup = (TableColumnGroupBox) column;
						i = 0;
						continue RECURSE;
					} else {
						columnCount += colPos.span;
					}
				}
				if (stack.isEmpty()) {
					break;
				}
				i = ((Integer) stack.remove(stack.size() - 1)).intValue();
				colgroup = (TableColumnGroupBox) stack.remove(stack.size() - 1);
			}
		} else {
			columnCount = 0;
		}

		int headerRowCount = 0, bodyRowCount = 0, footerRowCount = 0;
		List<List<?>> rowLists = new ArrayList<List<?>>();
		List<List<?>> cellLists = new ArrayList<List<?>>();
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			TableRowGroupBox rowGroup = (TableRowGroupBox) this.rowGroups.get(i);
			List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroup);
			rowLists.add(rows);
			for (int j = 0; j < rows.size(); ++j) {
				TableRowBox row = (TableRowBox) rows.get(j);
				List<?> cells = (List<?>) this.rowToCells.get(row);
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
			int col = 0;
			List<Object> stack = new ArrayList<Object>();
			TableColumnGroupBox colgroup = this.columnGroupBox;
			int i = 0;
			RECURSE: for (;;) {
				for (; i < colgroup.getTableColumnCount(); ++i) {
					TableColumnBox column = colgroup.getTableColumn(i);
					TableColumnPos colPos = column.getTableColumnPos();
					InnerTableParams colParams = column.getInnerTableParams();
					int span;
					if (column.getType() == BoxType.TABLE_COLUMN_GROUP
							&& ((TableColumnGroupBox) column).getTableColumnCount() > 0) {
						span = ((TableColumnGroupBox) column).getTableColumnCount();
					} else {
						span = colPos.span;
					}
					switch (colParams.size.getType()) {
					case ABSOLUTE:
						widths.specFixed(col, span, colParams.size.getLength() + lineBorderSpacing);
						break;
					case RELATIVE:
						widths.specPercent(col, span, colParams.size.getLength());
						break;
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

		// セルの幅計算
		int row = 0;
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			List<?> rows = (List<?>) this.rowGroupToRows.get(this.rowGroups.get(i));
			for (int j = 0; j < rows.size(); ++j) {
				List<?> cells = (List<?>) this.rowToCells.get(rows.get(j));
				// 指定幅
				for (int col = 0; col < cells.size(); ++col) {
					final CellContent cell = (CellContent) cells.get(col);
					if (cell.isExtended()) {
						continue;
					}
					final int span = cell.colspan;
					final TableCellBox cellBox = cell.getCellBox();
					final BlockParams cellParams = cellBox.getBlockParams();
					final TableCellPos cellPos = cellBox.getTableCellPos();
					if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
						// 分離境界
						double top = tableParams.borderSpacingV / 2.0;
						double right = tableParams.borderSpacingH / 2.0;
						double bottom = tableParams.borderSpacingV / 2.0;
						double left = tableParams.borderSpacingH / 2.0;
						AbsoluteInsets cellSpacing = new AbsoluteInsets(top, right, bottom, left);
						cellBox.prepareLayout(this.layoutStack.getFlowBox().getLineSize(), this.tableBox, cellSpacing);
					} else {
						// つぶし境界
						double pageFirst = 0, lineEnd = 0, pageLast = 0, lineStart = 0;
						int bottomIndex = row + cellPos.rowspan;
						for (int k = 0; k < cellPos.colspan; ++k) {
							int kk = col + k;
							if (kk >= columnCount) {
								break;
							}
							pageFirst = Math.max(pageFirst, this.borders.getHBorder(kk, row).width / 2.0);
							if (bottomIndex <= rowCount) {
								pageLast = Math.max(pageLast, this.borders.getHBorder(kk, bottomIndex).width / 2.0);
							}
						}
						int rightIndex = col + cellPos.colspan;
						for (int k = 0; k < cellPos.rowspan; ++k) {
							int kk = row + k;
							if (kk >= rowCount) {
								break;
							}
							lineStart = Math.max(lineStart, this.borders.getVBorder(kk, col).width / 2.0);
							if (rightIndex <= columnCount) {
								lineEnd = Math.max(lineEnd, this.borders.getVBorder(kk, rightIndex).width / 2.0);
							}
						}
						final AbsoluteInsets spacing;
						if (this.vertical) {
							spacing = new AbsoluteInsets(lineStart, pageFirst, lineEnd, pageLast);
						} else {
							spacing = new AbsoluteInsets(pageFirst, lineEnd, pageLast, lineStart);
						}
						cellBox.prepareLayout(this.layoutStack.getFlowBox().getLineSize(), this.tableBox, spacing);
					}

					final double cellFrame;
					if (this.vertical) {
						cellFrame = cellBox.getFrame().getFrameHeight();
					} else {
						cellFrame = cellBox.getFrame().getFrameWidth();
					}
					final IntrinsicSizes cellSizes = cell.getBuilder().getIntrinsicSizes();
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

		final AutoColumnWidths.Result widths2 = widths.finish(tableFrame);
		this.columnMins = widths2.mins();
		this.columnSpecs = widths2.specs();
		this.columnDesiredWidths = widths2.desired();
		this.columnTypes = widths2.types();
		this.minLineSize = widths2.minLineSize();
		this.maxLineSize = widths2.maxLineSize();
	}

	/**
	 * テーブルを構築します。 外側のテーブルから順に実行します。
	 * 
	 * @param builder
	 */
	public void bind(final BlockBuilder builder) {
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

		final int columnCount = this.columnMins.length;
		double[] columnSizes;
		if (this.fixed) {
			// 固定レイアウト
			if (LayoutUtils.isNone(tableSize)) {
				tableSize = lineSize;
			}
			tableSize -= tableFrame;
			double refSize = tableSize;
			if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
				// 分離境界
				refSize -= columnCount * lineBorderSpacing;
			}
			refSize = Math.max(0, refSize);
			final FixedColumnWidths.Spec[] colgroupSpecs;
			if (this.columnGroupBox != null) {
				this.tableBox.setTableColumnGroup(this.columnGroupBox);
				// 注: 歴史的に縦書きでも borderSpacingH を加算していた(要調査。
				// OnePassTableBuilder は論理軸の lineBorderSpacing を使う)
				colgroupSpecs = TableColumnSpecs.colgroupSpecs(this.columnGroupBox, columnCount, refSize,
						tableParams.borderCollapse == TableParams.BORDER_SEPARATE ? tableParams.borderSpacingH : 0);
			} else {
				colgroupSpecs = new FixedColumnWidths.Spec[columnCount];
			}
			final List<?> cells = (List<?>) this.rowToCells.get(this.firstRowBox);
			final FixedColumnWidths.Spec[] cellSpecs = new FixedColumnWidths.Spec[columnCount];
			for (int i = 0; i < columnCount; ++i) {
				if (i >= cells.size()) {
					continue;
				}
				final CellContent cell = (CellContent) cells.get(i);
				final FixedColumnWidths.Spec spec = this.fixedCellSpec(cell, refSize);
				cellSpecs[i] = spec;
				for (int j = 1; j < cell.colspan; ++j) {
					++i;
					if (i < columnCount) {
						cellSpecs[i] = spec;
					}
				}
			}
			final FixedColumnWidths.Result result = FixedColumnWidths.distribute(colgroupSpecs, cellSpecs, tableSize);
			columnSizes = result.sizes();
			tableSize = result.innerSize() + tableFrame;
		} else {
			// 自動レイアウト
			// CSS 2.1 17.5.2.2 [Column widths influence the final table width
			// as
			// follows]
			columnSizes = new double[columnCount];
			if (columnCount > 0) {
				final double maxTableSize = blockBox.getLineSize();
				if (LayoutUtils.isNone(tableSize)) {
					tableSize = this.maxLineSize;
					if (tableSize < maxTableSize && columnCount > 1) {
						// パーセント幅によるテーブルの拡張
						int pctCount = 0, effColumnCount = 0;
						double pctSum = 0, noPctDesiredSum = 0;
						double w = tableSize - tableFrame;
						for (int i = 0; i < columnCount; ++i) {
							double des = this.columnDesiredWidths[i];
							if (this.columnTypes[i] != AutoColumnWidths.COLUMN_TYPE_PCT && des == 0) {
								continue;
							}
							++effColumnCount;
							if (this.columnTypes[i] != AutoColumnWidths.COLUMN_TYPE_PCT) {
								noPctDesiredSum += des;
								continue;
							}
							++pctCount;
							double pct = this.columnSpecs[i];
							pctSum += pct;
							if (pct != 1 && pct != 0) {
								w = Math.max(w, des / pct);
							} else {
								w = maxTableSize - tableFrame;
							}
							if (w >= maxTableSize - tableFrame) {
								break;
							}
						}
						if (pctCount != 0 && pctCount != effColumnCount) {
							if (pctSum != 1 && pctSum != 0) {
								w = Math.max(w, noPctDesiredSum / (1 - pctSum));
							} else if (noPctDesiredSum > 0) {
								w = maxTableSize - tableFrame;
							}
						}
						tableSize = w + tableFrame;
					}
				}
				// this.minLineAxisにはtableFrameが含まれていることに注意
				if (tableSize < this.minLineSize) {
					tableSize = this.minLineSize;
				}
				if (tableSize > maxTableSize) {
					tableSize = maxTableSize;
				}
				double innerSize = tableSize - tableFrame;
				// ％幅の計算
				if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
					// 分離境界
					final double refSize = innerSize - columnCount * lineBorderSpacing;
					for (int i = 0; i < columnCount; ++i) {
						if (this.columnTypes[i] != AutoColumnWidths.COLUMN_TYPE_PCT) {
							continue;
						}
						this.columnSpecs[i] *= refSize;
						if (tableParams.borderCollapse == TableParams.BORDER_SEPARATE) {
							// 分離境界
							this.columnSpecs[i] += lineBorderSpacing;
						}
					}
				} else {
					// つぶし境界
					for (int i = 0; i < columnCount; ++i) {
						if (this.columnTypes[i] != AutoColumnWidths.COLUMN_TYPE_PCT) {
							continue;
						}
						this.columnSpecs[i] *= innerSize;
					}
				}

				// 列幅の分配 (css-tables-3)
				double[] startSizes = this.columnMins;
				double minSum = 0;
				for (int i = 0; i < columnCount; ++i) {
					minSum += this.columnMins[i];
				}
				if (minSum > maxTableSize) {
					// 最小幅の合計が最大表幅を超える場合は比例縮小する
					startSizes = new double[columnCount];
					for (int i = 0; i < columnCount; ++i) {
						startSizes[i] = this.columnMins[i] * (maxTableSize - tableFrame) / minSum;
					}
				}
				final ColumnDistribution.ColumnType[] types = new ColumnDistribution.ColumnType[columnCount];
				for (int i = 0; i < columnCount; ++i) {
					types[i] = switch (this.columnTypes[i]) {
					case AutoColumnWidths.COLUMN_TYPE_FIX -> ColumnDistribution.ColumnType.CONSTRAINED;
					case AutoColumnWidths.COLUMN_TYPE_PCT -> ColumnDistribution.ColumnType.PERCENT;
					default -> ColumnDistribution.ColumnType.AUTO;
					};
				}
				columnSizes = ColumnDistribution.distribute(startSizes, this.columnSpecs, types, innerSize);
			} else {
				if (LayoutUtils.isNone(tableSize)) {
					tableSize = 0;
				}
			}
		}

		final double specifiedPageSize;
		if (this.vertical) {
			// 縦書き
			switch (tableParams.size.getWidthType()) {
			case ABSOLUTE:
				specifiedPageSize = tableParams.size.getWidth() - this.tableBox.getFrame().getFrameWidth();
				break;
			case RELATIVE:
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
			List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroups.get(i));
			rowCount += rows.size();
		}

		// 行高さの計算
		double[] rowRatios = new double[rowCount]; // パーセント高さ
		double rowSizeSum = 0; // 行高さの合計
		int autoRowCount = 0;
		{
			int rowIndex = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
				List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroupBox);

				// 連結された行
				Map<Rowspan, Rowspan> rowspans = new HashMap<Rowspan, Rowspan>();
				List<Rowspan> rowspanList = new ArrayList<Rowspan>();
				boolean[] noAdjRows = new boolean[rows.size()];
				boolean[] autoRows = new boolean[rows.size()];

				// 行高さ/セルのレイアウト
				for (int j = 0; j < rows.size(); ++j) {
					TableRowBox rowBox = (TableRowBox) rows.get(j);
					double rowSize;

					// 指定された行高さの計算
					InnerTableParams rowParams = rowBox.getInnerTableParams();
					switch (rowParams.size.getType()) {
					case ABSOLUTE:
						rowSize = rowParams.size.getLength();
						break;
					case RELATIVE:
						rowRatios[rowIndex] = rowParams.size.getLength();
						if (rowRatios[rowIndex] > 0) {
							rowSize = 0;
							break;
						}
					case AUTO:
						++autoRowCount;
						autoRows[j] = true;
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

					// セル内のレイアウト
					List<?> cells = (List<?>) this.rowToCells.get(rowBox);
					for (int k = 0; k < cells.size(); ++k) {
						CellContent cell = (CellContent) cells.get(k);
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

						// セルの中身を再構築
						double size = columnSizes[k];
						for (int l = 1; l < span; ++l) {
							size += columnSizes[++k];
							assert !LayoutUtils.isNone(columnSizes[k]);
						}
						if (this.vertical) {
							cellBox.setHeight(size);
							if (!cellParams.flow.isVertical()) {
								cellBox.setWidth(cell.getBuilder().getIntrinsicSizes().maxContent() + cellBox.getFrame().getFrameWidth()
										+ tableParams.borderSpacingH);
							}
						} else {
							cellBox.setWidth(size);
							if (cellParams.flow.isVertical()) {
								cellBox.setHeight(cell.getBuilder().getIntrinsicSizes().maxContent()
										+ cellBox.getFrame().getFrameHeight() + tableParams.borderSpacingV);
							}
						}
						final BlockBuilder cellBindBuilder = new BlockBuilder(this.layoutStack, cellBox);
						cell.getBuilder().bind(cellBindBuilder);
						cellBindBuilder.close();

						this.cellToSource.put(cellBox, rowBox.addTableSourceCell(cellBox));
						int cellRowspan = Math.min(rows.size() - j, cell.rowspan);
						if (cellRowspan <= 1) {
							// 連結されない行
							noAdjRows[j] = true;
						} else {
							// 連結された行(連結では％高さはautoとする)
							Rowspan key = new Rowspan(j, cellRowspan);
							Rowspan rowspan = (Rowspan) rowspans.get(key);
							if (rowspan == null) {
								rowspan = key;
								rowspans.put(key, rowspan);
								rowspanList.add(rowspan);
							}
							double cellSize;
							if (this.vertical) {
								cellSize = cellBox.getWidth();
								if (cellParams.size.getWidthType() == LengthType.ABSOLUTE) {
									double width = cellParams.size.getWidth();
									if (cellParams.boxSizing == BoxSizingMode.CONTENT_BOX) {
										width += cellBox.getFrame().getFrameWidth();
									}
									cellSize = Math.max(cellSize, width);
								}
							} else {
								cellSize = cellBox.getHeight();
								if (cellParams.size.getHeightType() == LengthType.ABSOLUTE) {
									double height = cellParams.size.getHeight();
									if (cellParams.boxSizing == BoxSizingMode.CONTENT_BOX) {
										height += cellBox.getFrame().getFrameHeight();
									}
									cellSize = Math.max(cellSize, height);
								}
							}
							rowspan.min = Math.max(rowspan.min, cellSize);
						}
					}

					// ベースラインをそろえる
					for (int k = 0; k < cells.size(); ++k) {
						final CellContent cell = (CellContent) cells.get(k);
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
						rowSizes[j] = ((TableRowBox) rows.get(j)).getPageSize();
					}
					RowLayoutEngine.distributeSpannedRowSizes(rowSizes, rowspanList, noAdjRows, autoRows,
							java.util.Arrays.copyOfRange(rowRatios, groupStart, rowIndex));
					for (int j = 0; j < rows.size(); ++j) {
						((TableRowBox) rows.get(j)).setPageSize(rowSizes[j]);
					}
				}
				// 内容の高さ計算
				for (int j = 0; j < rows.size(); ++j) {
					TableRowBox rowBox = (TableRowBox) rows.get(j);
					rowSizeSum += rowBox.getPageSize();
				}
			}
		}

		// 行のパーセント高さ計算
		{
			double remainder = specifiedPageSize - rowSizeSum;
			int rowIndex = 0;
			for (int i = 0; remainder > 0 && i < this.rowGroups.size(); ++i) {
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
				List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroupBox);
				for (int j = 0; remainder > 0 && j < rows.size(); ++j) {
					TableRowBox rowBox = (TableRowBox) rows.get(j);
					double rowRatio = rowRatios[rowIndex];
					if (rowRatio > 0) {
						double rowHeight = rowBox.getPageSize();
						double diff = Math.min(remainder, specifiedPageSize * rowRatio - rowHeight);
						if (diff > 0) {
							remainder -= diff;
							rowHeight += diff;
							rowSizeSum += diff;
							rowBox.setPageSize(rowHeight);
						}
					}
					++rowIndex;
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
			List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroupBox);
			final double[] rowSizes = new double[rows.size()];
			for (int j = 0; j < rows.size(); ++j) {
				rowSizes[j] = ((TableRowBox) rows.get(j)).getPageSize();
			}
			rowSizeSum += RowLayoutEngine.distributeGroupSize(rowSizes, params.size.getLength());
			for (int j = 0; j < rows.size(); ++j) {
				((TableRowBox) rows.get(j)).setPageSize(rowSizes[j]);
			}
		}

		// テーブル高さを適用
		if (rowSizeSum < specifiedPageSize) {
			if (autoRowCount > 0 && autoRowCount < rowCount) {
				// 固定高さの行がある場合
				double remainder = specifiedPageSize - rowSizeSum;
				for (int i = 0; i < this.rowGroups.size(); ++i) {
					TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
					List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroupBox);
					for (int j = 0; j < rows.size(); ++j) {
						TableRowBox rowBox = (TableRowBox) rows.get(j);
						InnerTableParams params = rowBox.getInnerTableParams();
						if (params.size.getType() == LengthType.AUTO) {
							continue;
						}
						rowSizeSum -= rowBox.getPageSize();
					}
				}
				for (int i = 0; i < this.rowGroups.size(); ++i) {
					TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
					List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroupBox);
					for (int j = 0; j < rows.size(); ++j) {
						TableRowBox rowBox = (TableRowBox) rows.get(j);
						InnerTableParams params = rowBox.getInnerTableParams();
						if (params.size.getType() != LengthType.AUTO) {
							continue;
						}
						double rowSize = rowBox.getPageSize();
						if (rowSizeSum <= 0) {
							rowSize += remainder / autoRowCount;
						} else {
							rowSize += remainder * rowSize / rowSizeSum;
						}
						rowBox.setPageSize(rowSize);
					}
				}
			} else {
				for (int i = 0; i < this.rowGroups.size(); ++i) {
					TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
					List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroupBox);
					for (int j = 0; j < rows.size(); ++j) {
						TableRowBox rowBox = (TableRowBox) rows.get(j);
						double rowHeight = rowBox.getPageSize();
						if (rowSizeSum <= 0) {
							rowBox.setPageSize(specifiedPageSize / rowCount);
						} else {
							rowBox.setPageSize(specifiedPageSize * rowHeight / rowSizeSum);
						}
					}
				}
			}
		}

		// セル高さ確定
		{
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				TableRowGroupBox rowGroup = (TableRowGroupBox) this.rowGroups.get(i);
				List<?> rows = (List<?>) this.rowGroupToRows.get(rowGroup);
				for (int j = 0; j < rows.size(); ++j) {
					TableRowBox rowBox = (TableRowBox) rows.get(j);

					rowBox.setLineSize(tableInnerSize);
					rowGroup.addTableRow(rowBox);
					List<?> cells = (List<?>) this.rowToCells.get(rowBox);

					// セル高さ設定
					double rowAscent = 0;
					for (int k = 0; k < cells.size(); ++k) {
						CellContent cell = (CellContent) cells.get(k);
						if (cell.isExtended()) {
							continue;
						}
						TableCellBox cellBox = cell.getCellBox();
						double firstAscent = cellBox.getFirstAscent();
						if (!LayoutUtils.isNone(firstAscent) && firstAscent > rowAscent) {
							rowAscent = firstAscent;
						}
					}
					for (int k = 0; k < cells.size(); ++k) {
						CellContent cell = (CellContent) cells.get(k);
						if (cell.isExtended()) {
							continue;
						}
						TableCellBox cellBox = cell.getCellBox();
						double rowSize = rowBox.getPageSize();
						int rowspan = Math.min(rows.size() - j, cellBox.getTableCellPos().rowspan);
						for (int l = 1; l < rowspan; ++l) {
							int m = j + l;
							TableRowBox xrow = (TableRowBox) rows.get(m);
							rowSize += xrow.getPageSize();
						}
						cellBox.baseline(rowAscent);
						if (this.vertical) {
							cellBox.setWidth(rowSize);
						} else {
							cellBox.setHeight(rowSize);
						}
						cellBox.verticalAlign();
					}
				}
			}
		}

		if (this.headerGroup != null) {
			this.tableBox.setTableHeader(this.headerGroup);
		}
		for (int i = 0; i < this.bodyGroups.size(); ++i) {
			this.tableBox.addTableBody((TableRowGroupBox) this.bodyGroups.get(i));
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
			final double pageSize;
			if (this.vertical) {
				pageSize = this.tableBox.getInnerWidth();
			} else {
				pageSize = this.tableBox.getInnerHeight();
			}
			int col = 0;
			List<Object> stack = new ArrayList<Object>();
			TableColumnGroupBox colgroup = this.columnGroupBox;
			this.tableBox.setTableColumnGroup(colgroup);
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
						size += columnSizes[col + j];
					}
					column.setLineSize(size);
					column.setPageSize(pageSize);

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

		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// つぶし境界
			for (int i = 0; i < columnSizes.length; ++i) {
				assert !LayoutUtils.isNone(columnSizes[i]);
				this.borders.setColumnSize(i, columnSizes[i]);
			}
			int row = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				List<?> rows = (List<?>) this.rowGroupToRows.get(this.rowGroups.get(i));
				for (int j = 0; j < rows.size(); ++j) {
					double rowHeight = ((TableRowBox) rows.get(j)).getPageSize();
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

	public boolean isOnePass() {
		return false;
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
}

/**
 * 結合された列です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TwoPassTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
