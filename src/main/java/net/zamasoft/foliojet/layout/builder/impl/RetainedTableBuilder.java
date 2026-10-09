package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.RetainedTextLimit;

import net.zamasoft.foliojet.layout.sizing.AutoColumnWidths;
import net.zamasoft.foliojet.layout.sizing.FixedColumnWidths;

import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

import net.zamasoft.foliojet.layout.box.params.BoxSizingMode;

import net.zamasoft.foliojet.layout.box.params.Fiducial;

import net.zamasoft.foliojet.layout.box.params.AutoPosition;

import net.zamasoft.foliojet.layout.box.params.RowGroupType;

import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FloatBlockBox;
import net.zamasoft.foliojet.layout.box.impl.FlowBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.IncompleteTablePlan;
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
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.PosType;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;
import net.zamasoft.foliojet.layout.box.params.TableCellPos;
import net.zamasoft.foliojet.layout.box.params.TableColumnPos;
import net.zamasoft.foliojet.layout.box.params.TableParams;

import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.LayoutStack;
import net.zamasoft.foliojet.layout.part.AbsoluteInsets;
import net.zamasoft.foliojet.layout.part.TableCollapsedBorders;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.util.DebugFlags;

/**
 * Builds tables using the Retained execution plan (retain the whole table before committing).
 * Correction on 2026-07-19: routing here is not limited to table-layout:auto; it also follows
 * {@link TableRetentionReason}, such as non-FLOW placement, specified page-axis size, or
 * auto line-axis size. Fixed column widths are handled by {@code this.fixed} too:
 * this is not "auto layout only". See the TableLayout Javadoc and development plan "C4"
 * for details.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: RetainedTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class RetainedTableBuilder implements net.zamasoft.foliojet.layout.builder.RetainedTable {

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
		if (this.tableBox == null) return this.rowEmissionCaptionInsetStart;
		final AbsoluteInsets margin = this.tableBox.getFrame().margin;
		return this.vertical ? margin.top : margin.left;
	}

	/** Caption inset at the line-axis end. Same reason as captionInsetStart. */
	private double captionInsetEnd() {
		if (this.tableBox == null) return this.rowEmissionCaptionInsetEnd;
		final AbsoluteInsets margin = this.tableBox.getFrame().margin;
		return this.vertical ? margin.bottom : margin.right;
	}

	/**
	 * Table cell under construction.
	 *  
	 * @author MIYABE Tatsuhiko
	 * @version $Id: RetainedTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
	 */

	private final boolean vertical, fixed;
	private final boolean sliceCellText;
	private final LayoutStack layoutStack;
	private TableBox tableBox;
	/** Line-axis insets used by the bottom caption after emission. Retains no ownership of the table or frame. */
	private double rowEmissionCaptionInsetStart, rowEmissionCaptionInsetEnd;
	private long tableSourceAnchor = -1;
	private BreakableBuilder.IncompleteTableResult rowEmission;
	private java.util.EnumSet<TableBuildPlanner.RowEmissionExclusion> rowEmissionExclusions;
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
	 * CellContent for the currently open cell (E-6 increment 5a, 2026-07-24).
	 * Set by newContext(TABLE_CELL) and consumed by {@link #sealCellContext} at cell close.
	 * Cells are sequential within a row (only one open at a time), and nested tables have
	 * their own RetainedTableBuilder, so a single field suffices.
	 */
	private CellContent pendingSealCell = null;

	/**
	 * Minimum, specified, and preferred column widths, measured between the centers of the right and left borders.
	 */
	private AutoColumnWidths.Result columnWidths;

	/**
	 * Shadow verification hook for table Pass B (row measurement) (E-6 increment 5b-1,
	 * 2026-07-24; test-only, following LayoutSourceTestHooks in 3b-2). Remains null in
	 * production with no behavioral change. Shadow tests observe immediately before/after
	 * cell bind and verify that independent {@link CellPassBMeasurer} measurements match
	 * actual bound dimensions. Tests setting this must always clear it in finally (shared static state).
	 */
	interface CellBindShadow {
		/** Immediately before cell bind ({@code cell.bind}); column widths already applied. */
		void beforeCellBind(CellContent cell, TableCellBox cellBox, LayoutStack layoutStack, boolean vertical);

		/** Immediately after cell bind + builder close. */
		void afterCellBind(CellContent cell, TableCellBox cellBox, boolean vertical);
	}

	/** Test-only shadow observation hook (production=null). */
	static CellBindShadow cellBindShadow = null;

	/** T5a observations by stage. Called from the DirectSession conversion thread (test-only). */
	static volatile java.util.function.BiConsumer<String, net.zamasoft.foliojet.layout.fragment.LayoutSource> retentionObserver;

	/** Checks release of plan references at the same observation points (test-only; save and restore when using). */
	static volatile java.util.function.BiConsumer<String, RetainedTableBuilder> retentionPlanObserver;

	/** For B-2c. The parent owns current-page bodies; repetitions share headers/footers. The fields do not overlap. */
	public record RowRetention(int pendingRows, int pendingCells, int boundRows, int boundCells,
			int currentPageRows, int currentPageCells, int repeatedRows, int repeatedCells) {
	}

	/** Count trees only when observing. Do not confuse pending plans with bound body trees. */
	public RowRetention rowRetention() {
		int pendingCells = 0;
		for (final List<CellContent> cells : this.rowToCells.values()) {
			for (final CellContent cell : cells) if (!cell.isExtended()) ++pendingCells;
		}
		int boundRows = 0, boundCells = 0;
		for (final TableRowGroupBox group : this.bodyGroups) {
			boundRows += group.getTableRowCount();
			boundCells += cellCount(group);
		}
		final TableRowGroupBox current = this.rowEmission == null ? null : this.rowEmission.body();
		return new RowRetention(this.rowToCells.size(), pendingCells, boundRows, boundCells,
				current == null ? 0 : current.getTableRowCount(), cellCount(current),
				(this.headerGroup == null ? 0 : this.headerGroup.getTableRowCount())
						+ (this.footerGroup == null ? 0 : this.footerGroup.getTableRowCount()),
				cellCount(this.headerGroup) + cellCount(this.footerGroup));
	}

	private static int cellCount(final TableRowGroupBox group) {
		int count = 0;
		if (group != null) {
			for (int i = 0; i < group.getTableRowCount(); ++i) {
				final TableRowBox row = group.getTableRow(i);
				for (int j = 0; j < row.getCellCount(); ++j) if (row.getCell(j).isSource()) ++count;
			}
		}
		return count;
	}

	/** Null before Pass B. Thereafter, reasons for choosing the normal path; an empty set means an emission candidate. */
	public java.util.Set<TableBuildPlanner.RowEmissionExclusion> rowEmissionExclusions() {
		return this.rowEmissionExclusions == null ? null : Collections.unmodifiableSet(this.rowEmissionExclusions);
	}

	/** Actual fragments detached by the latest parent notification. 0 before acceptance. */
	public int rowEmittedFragments() {
		return this.rowEmission == null ? 0 : this.rowEmission.emittedFragments();
	}

	private void observeRetention(final String stage) {
		final var planObserver = retentionPlanObserver;
		if (planObserver != null) planObserver.accept(stage, this);
		final var observer = retentionObserver;
		if (observer != null && this.layoutStack.getPageContext() != null) {
			final var source = this.layoutStack.getPageContext().getPageGenerator().getLayoutSource();
			if (source != null) observer.accept(stage, source);
		}
	}

	public RetainedTableBuilder(LayoutStack layoutStack, TableBox tableBox) {
		this.layoutStack = layoutStack;
		this.tableBox = tableBox;
		TableParams tableParams = tableBox.getTableParams();
		this.vertical = tableParams.flow.isVertical();
		this.fixed = tableParams.layout == TableParams.LAYOUT_FIXED
				&& ((this.vertical ? tableParams.size.getHeightType()
						: tableParams.size.getWidthType()) != LengthType.AUTO);
		// A normal-flow table directly under RootBuilder is not absorbed into a parent TwoPass; it binds in place.
		// Tables in multi-column layout, positioned tables, and tables in cells/floats retain their existing leases.
		this.sliceCellText = layoutStack instanceof RootBuilder && layoutStack.getMulticolumnBox() == null
				&& tableBox.getBlockBox() instanceof FlowBlockBox;
	}

	private void compactCellText(final boolean force) {
		if (!this.sliceCellText) return;
		final var generator = this.layoutStack.getPageContext().getPageGenerator();
		// The scratch borrowing log also contains inputs not yet placed for real.
		if (net.zamasoft.foliojet.layout.fragment.ReplayIntent.current()
				== net.zamasoft.foliojet.layout.fragment.ReplayIntent.MEASURE
				|| generator instanceof net.zamasoft.foliojet.layout.MeasurePageGenerator) return;
		final var source = generator.getLayoutSource();
		if (source != null) source.compactRetainedTable(this.getSourceAnchor(),
				Math.min(source.nextId(), generator.getDeliveredEventEnd()), force);
	}

	public IntrinsicSizes getIntrinsicSizes() {
		final TableParams tableParams = this.tableBox.getTableParams();
		double min = this.columnWidths == null ? 0 : this.columnWidths.minLineSize();
		double max = this.columnWidths == null ? 0 : this.columnWidths.maxLineSize();
		// The table’s own specified size sets a lower bound on intrinsic size.
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

	/** Null after MAIN row emission transfers ownership to the parent. Use getSourceAnchor to identify the plan. */
	public final TableBox getTableBox() {
		return this.tableBox;
	}

	@Override
	public final long getSourceAnchor() {
		return this.tableBox == null ? this.tableSourceAnchor : this.tableBox.getSourceAnchor();
	}

	public final void startInnerTable(final AbstractInnerTableBox box) {

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
				final TableColumnGroupBox parentColumnGroup = (TableColumnGroupBox) this.innerTableStack
						.get(this.innerTableStack.size() - 1);
				parentColumnGroup.addTableColumn(column);
			}
		}
			break;
		case TABLE_ROW_GROUP: {
			// Row group
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
			// Row
			final TableRowGroupBox rowGroup = (TableRowGroupBox) this.innerTableStack
					.get(this.innerTableStack.size() - 1);
			final TableRowBox row = (TableRowBox) box;
			final List<TableRowBox> rows = this.rowGroupToRows.get(rowGroup);
			rows.add(row);
			// Collecting one row is **actual work completed**. A retained table emits no pages
			// until all rows are read, so this is its only progress signal
			// (2026-07-27: the cause of the 37.5-second no-output interval for 400,000 rows).
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

		switch (box.getType()) {
		case TABLE_COLUMN:
		case TABLE_COLUMN_GROUP: {
			// Column
		}
			break;
		case TABLE_ROW_GROUP: {
			// Row group
			this.upperRow = null;
			this.observeRetention("before-table-end");
		}
			break;

		case TABLE_ROW: {
			// Row
			final TableRowBox rowBox = (TableRowBox) box;
			this.complementRowspan(rowBox);
			this.upperRow = rowBox;
			if (this.firstRowBox == null) {
				this.firstRowBox = rowBox;
			}
			// Collect live counts before table end on the same conversion thread (test-only).
			if ((retentionObserver != null || retentionPlanObserver != null) && this.rowToCells.size() % 2000 == 0) {
				this.observeRetention("after-input-rows-" + this.rowToCells.size());
			}
		}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	private void complementRowspan(TableRowBox row) {
		if (this.upperRow != null) {
			// Fill in cells joined by rowspan (shared core — P2-2).
			CellContent.complementRowspan(this.rowToCells.get(row), this.rowToCells.get(this.upperRow));
		}
	}

	public final Builder newContext(AbstractContainerBox box) {
		final Builder builder = new TwoPassBlockBuilder(this.layoutStack, box);
		((TwoPassBlockBuilder) builder).tagRootKind(box.getType() == BoxType.TABLE_CELL
				? net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind.RETAINED_CELL
				: net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassRootKind.RETAINED_CAPTION);
		switch (box.getType()) {
		case BLOCK: {
			// Caption
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
			// Cell
			// TODO For vertical content in a horizontal table, BlockBuilder must constrain the line width.
			final TableRowBox rowBox = (TableRowBox) this.innerTableStack.get(this.innerTableStack.size() - 1);
			List<CellContent> cells = (ArrayList<CellContent>) this.rowToCells.get(rowBox);
			this.complementRowspan(rowBox);
			CellContent cell = new CellContent((TwoPassBlockBuilder) builder);
			cells.add(cell);
			for (int colspan = cell.colspan; colspan > 1; --colspan) {
				cells.add(new CellContent(cell.getCellBox(), cell.rowspan, colspan));
			}
			// E-6 increment 5a: remember the seal-on-cell-close target.
			this.pendingSealCell = cell;
		}
			break;
		default:
			throw new IllegalStateException();
		}
		return builder;
	}

	/**
	 * Whether absorbed into the parent's range representation (table absorption = codex
	 * increment 5, 2026-07-30). If true, {@link #prepareLayout}/{@link #bind} violate the
	 * contract: parent range replay reconstructs the entire table from source, so this
	 * plan is never used.
	 */
	private boolean abandoned;

	/**
	 * Validation phase of table absorption (codex increment 5, 2026-07-30;
	 * <b>no side effects</b>). Determines whether this recorded Retained plan can be absorbed
	 * into the parent's range representation, and lists unsealed cell builders (and their
	 * descendants) in {@code out}. Eligibility conditions (fail closed):
	 * <ul>
	 * <li>No captions (a parent's range containing a captioned table should already be rejected
	 * with OPAQUE_RANGE due to the caption's Opaque record, making this structurally
	 * unreachable, but this is a second barrier)</li>
	 * <li>Sealed cell leases are on the same LayoutSource and contained in the parent range</li>
	 * <li>All descendant records, captions, etc. of unsealed cells are absorbable</li>
	 * </ul>
	 */
	boolean collectAbsorbableInto(final net.zamasoft.foliojet.layout.fragment.LayoutSource log, final long fromId,
			final long toId, final List<TwoPassBlockBuilder> out, final List<RetainedTableBuilder> outTables,
			final List<net.zamasoft.foliojet.layout.fragment.RangeHandle> outRanges,
			final java.util.Set<Long> ownedAbsoluteAnchors, final java.util.Set<TwoPassBlockBuilder> seen) {
		if (this.abandoned) {
			return false;
		}
		// Caption recipes C4 (2026-08-01): captions are also absorbed into the parent range
		// (C3 seal-on-close retains SourceRangeBody; validation checks that its lease is within
		// the parent range, and parent subsume handles the commit phase). Absorbed
		// table plans are discarded together with bindRows by abandonForParentRange,
		// so caption bind never runs after subsumption.
		for (int c = 0; c < this.topCaptions.size(); ++c) {
			if (!(this.topCaptions.get(c) instanceof TwoPassBlockBuilder caption)
					|| !caption.collectAbsorbableSelf(log, fromId, toId, out, outTables, outRanges, ownedAbsoluteAnchors, seen)) {
				return false;
			}
		}
		for (int c = 0; c < this.bottomCaptions.size(); ++c) {
			if (!(this.bottomCaptions.get(c) instanceof TwoPassBlockBuilder caption)
					|| !caption.collectAbsorbableSelf(log, fromId, toId, out, outTables, outRanges, ownedAbsoluteAnchors, seen)) {
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
						if (!sealed.collectAbsorbableInto(log, fromId, toId, ownedAbsoluteAnchors)) {
							return false;
						}
						continue;
					}
					final TwoPassBlockBuilder unsealed = cell.unsealedBuilderOrNull();
					if (unsealed == null || !unsealed.collectAbsorbableSelf(log, fromId, toId, out, outTables, outRanges,
							ownedAbsoluteAnchors, seen)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Absorption into the parent's range representation (commit phase of table absorption,
	 * codex increment 5). Release sealed cell leases and make subsequent prepareLayout/bind
	 * a contract violation. Validation already listed unsealed cell builders for parent
	 * absorption, and the parent's commit subsumes them, so leave them untouched here.
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
	 * Seals the range at cell close (recording completion point) (E-6 increment 5a, 2026-07-24;
	 * codex design §4.2/§4.3). If eligible, switch CellContent for the cell just opened by
	 * newContext to retain "IntrinsicSizes values + SourceRange (+lease)" and release
	 * records (TextImpl glyph sequences/live boxes). Eligibility fails closed in the same
	 * way as {@code TwoPassBlockBuilder.sealBodyForRangeBind} (nested builders such as tables
	 * and floats inside cells are ineligible with NESTED_BUILDER). Column width calculation
	 * ({@link #prepareLayout}) receives the emulated measurement (IntrinsicMeasurer) values
	 * resolved at seal time unchanged; it does not reread the tape to derive column widths.
	 * Captions are excluded because they use Opaque records (continue retaining their builders;
	 * calls without a pending cell are ignored here).
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
		cell.sealForRangeBind(this.sliceCellText);
		this.compactCellText(false);
	}

	/**
	 * Creates collapsed borders. The rules are in CollapsedBorderRules.collapseRow
	 * (the same as OnePass streaming accumulation); this method simply loops over all rows
	 * at once. assemble sets row/column dimensions later.
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
	 * Resolves maximum/minimum widths for the table and each column, starting with innermost tables.
	 */
	public void prepareLayout() {
		if (this.abandoned) {
			// Table absorption (codex increment 5): unreachable because parent range replay reconstructs the table.
			throw new IllegalStateException("親のrange化に吸収済みの表計画へのprepareLayout");
		}
		this.observeRetention("before-pass-b");
		this.compactCellText(true);
		TableParams tableParams = this.tableBox.getTableParams();

		// Normalize row order
		if (this.headerGroup != null) {
			this.rowGroups.add(this.headerGroup);
		}
		for (int i = 0; i < this.bodyGroups.size(); ++i) {
			this.rowGroups.add(this.bodyGroups.get(i));
		}
		if (this.footerGroup != null) {
			this.rowGroups.add(this.footerGroup);
		}

		// Automatic table layout: SPEC CSS 2.1 17.5.2.2
		// Count columns and rows (maximum leaf column position + span).
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

		// Borders
		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// Collapsed borders
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
		// Calculate column group widths
		if (this.columnGroupBox != null) {
			// Specified width
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
					// Column widths combining absolute lengths and percentages via calc() are unsupported
					// because this AUTO-layout column specification API assumes either absolute or percentage.
					// Treat them conservatively as AUTO (unspecified); see the development plan.
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

		// Calculate cell widths
		int row = 0;
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			List<TableRowBox> rows = this.rowGroupToRows.get(this.rowGroups.get(i));
			for (int j = 0; j < rows.size(); ++j) {
				List<CellContent> cells = this.rowToCells.get(rows.get(j));
				// Specified width
				for (int col = 0; col < cells.size(); ++col) {
					final CellContent cell = cells.get(col);
					if (cell.isExtended()) {
						continue;
					}
					final int span = cell.colspan;
					final TableCellBox cellBox = cell.getCellBox();
					final BlockParams cellParams = cellBox.getBlockParams();
					final TableCellPos cellPos = cellBox.getTableCellPos();
					// Cell spacing (shared core — P2-5 (c))
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
					// E-6 increment 5a: for sealed cells, read the emulated measurement snapshot
					// resolved at close (equivalent to the old read through the builder).
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
					if (cellSizes.columnInflated()) {
						// Do not use minimum content widths inflated by the column count as a column floor
						// (columns can shrink; the same reason as the clamp in AbstractStaticBlockBox
						// and inflatedCap in GridBuilder). Keep the table’s min-content guarantee
						// only for authored indivisible content
						// (2026-08-22, sweep seed 1931755: nested multi-column layout inside display:table
						// expanded the table to 3.3 times the sheet size).
						final double avail = this.layoutStack.getFlowBox().getLineSize() - tableFrame;
						if (avail > 0 && min > avail) {
							min = avail;
						}
					}
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
							// Table cell heights mixing components via calc() are unsupported because AUTO-layout’s
							// column height negotiation assumes either absolute or percentage.
							// Treat them conservatively as AUTO (see the development plan).
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
							// Table cell widths mixing components via calc() are unsupported because AUTO-layout’s
							// column width negotiation assumes either absolute or percentage.
							// Treat them conservatively as AUTO (see the development plan).
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

		// E-6 increment 1 (2026-07-24): observe retained-shape high-water marks. Measurement basis
		// for spill thresholds and target selection (only reads/updates maxima; no behavioral effect).
		int realCellCount = 0;
		for (int i = 0; i < cellLists.size(); ++i) {
			final List<CellContent> cells = cellLists.get(i);
			for (int j = 0; j < cells.size(); ++j) {
				final CellContent cell = cells.get(j);
				if (!cell.isExtended()) {
					++realCellCount;
				}
			}
		}
		TableBuildStats.reportRetainedTableShape(rowCount, realCellCount, (long) rowCount * columnCount, headerRowCount,
				footerRowCount, widths.colspanConstraintCount());
	}

	/** Table geometry (dimensions, column widths, anonymous block), passed between bind stages. */
	private record TableShape(BlockBuilder anonBuilder, PosType position, AbstractBlockBox blockBox, double tableSize,
			double[] columnSizes, double specifiedPageSize, double tableInnerSize) {
	}

	/**
	 * Builds the table, starting with outermost tables.
	 *  
	 * @param builder
	 */
	public void bind(final net.zamasoft.foliojet.layout.builder.Builder host) {
		if (this.abandoned) {
			// Table absorption (codex increment 5): unreachable because parent range replay reconstructs the table.
			throw new IllegalStateException("親のrange化に吸収済みの表計画へのbind");
		}
		// bind drives measured content through actual layout again; the host is always
		// BlockBuilder (both direct addTable and TwoPassBlockBuilder TableEvent
		// replay pass a real builder). Narrow the type once here.
		// Full type propagation (making the bind chain and SourceReplayer.bindTwoPassRange
		// use Builder) is recorded as A-2b in PLAN.md §1.5.
		final BlockBuilder builder = (BlockBuilder) host;
		this.tableSourceAnchor = this.tableBox.getSourceAnchor();
		final TableShape shape = this.resolveShape(builder);
		final RetainedTextLimit limit = RetainedTextLimit.get(builder);
		try (var retained = limit == null ? null
				: limit.enter(RetainedTextLimit.elementName(this.tableBox.getParams(), "table"))) {
			final double[] rowSizes = this.bindRows(shape);
			if (this.rowEmission == null) this.assemble(shape, rowSizes);
			this.finishTable(builder, shape, retained);
			this.rowEmission = null;
		}
		this.observeRetention("after-table-end");
		this.compactCellText(true);
	}

	/**
	 * Whether the table is a grid or flex item itself: its flow container is an item box that wraps the item element
	 * (not one taken over by an authored element, whose children are in normal block flow). A flex item's wrapper has
	 * the neutral params of {@code FlexBuilder.startNeutralElementItem}, with no element (2026-10-09).
	 */
	private boolean isItemTable(final AbstractContainerBox containerBox) {
		if (containerBox instanceof net.zamasoft.foliojet.layout.box.impl.GridItemBox item && !item.isTakeover()
				|| containerBox instanceof net.zamasoft.foliojet.layout.box.impl.FlexItemBox flexItem
						&& flexItem.getParams().element == null) {
			return true;
		}
		// An item of a column flex laid out in normal flow, stretched across the column by the container's
		// align-items (a table's position does not carry align-self; an auto margin across the column centers it)
		final net.zamasoft.foliojet.layout.box.params.Insets margin = this.tableBox.getTableParams().frame.margin;
		final boolean vertical = this.tableBox.getTableParams().flow.isVertical();
		final boolean autoMargin = vertical
				? margin.getTopType() == LengthType.AUTO || margin.getBottomType() == LengthType.AUTO
				: margin.getLeftType() == LengthType.AUTO || margin.getRightType() == LengthType.AUTO;
		return BlockBuilder.isStreamedColumnFlex(containerBox) && !autoMargin
				&& BlockBuilder.streamedColumnAlign(containerBox, null) == null;
	}

	/**
	 * Resolves table/column sizes and opens the anonymous block (P2-5 (a): bind stage 1).
	 */
	private TableShape resolveShape(final BlockBuilder builder) {
		final TableParams tableParams = this.tableBox.getTableParams();
		final AbstractContainerBox containerBox = this.layoutStack.getFlowBox();
		final boolean sameAxis = containerBox.getBlockParams().flow.isVertical() == tableParams.flow.isVertical();
		// Orthogonal flow uses LayoutStack.getOrthogonalLineBasis (fragmentainer if no ancestor
		// has an explicit size). Keep that decision in one place.
		final double lineSize = sameAxis ? containerBox.getLineSize()
				: this.layoutStack.getOrthogonalLineBasis(tableParams.flow);
		if (DebugFlags.TABLE_BASIS) {
			System.err.println("[tableBasis] sameAxis=" + sameAxis + " lineSize=" + lineSize + " vertical=" + this.vertical
					+ " container=" + containerBox.getClass().getSimpleName() + " containerFlowVertical="
					+ containerBox.getBlockParams().flow.isVertical() + " tableFlowVertical=" + tableParams.flow.isVertical()
					+ " size=" + tableParams.size + " maxSize=" + tableParams.maxSize);
		}
		// Table width
		double tableSize;
		// Retain the upper bound after resolution so it also applies to auto-width tables (auto layout below).
		double lineMaxSize;
		final double tableFrame, lineBorderSpacing;
		if (this.vertical) {
			// Vertical writing
			tableSize = LayoutUtils.computeDimensionHeight(tableParams.size, lineSize);
			double minSize = LayoutUtils.computeDimensionHeight(tableParams.minSize, lineSize);
			tableSize = Math.max(minSize, tableSize);
			double maxSize = LayoutUtils.computeDimensionHeight(tableParams.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxSize) && !LayoutUtils.isNone(tableSize)) {
				tableSize = Math.min(maxSize, tableSize);
			}
			lineMaxSize = maxSize;
			if (tableParams.size.getHeightType() != LengthType.AUTO) {
				tableSize += this.tableBox.getFrame().margin.getFrameHeight();
			}
			tableFrame = this.tableBox.getFrame().getFrameHeight();
			lineBorderSpacing = tableParams.borderSpacingV;
		} else {
			// Horizontal writing
			tableSize = LayoutUtils.computeDimensionWidth(tableParams.size, lineSize);
			double minSize = LayoutUtils.computeDimensionWidth(tableParams.minSize, lineSize);
			tableSize = Math.max(minSize, tableSize);
			double maxSize = LayoutUtils.computeDimensionWidth(tableParams.maxSize, lineSize);
			if (!LayoutUtils.isNone(maxSize) && !LayoutUtils.isNone(tableSize)) {
				tableSize = Math.min(maxSize, tableSize);
			}
			lineMaxSize = maxSize;
			if (tableParams.size.getWidthType() != LengthType.AUTO) {
				tableSize += this.tableBox.getFrame().margin.getFrameWidth();
			}
			tableFrame = this.tableBox.getFrame().getFrameWidth();
			lineBorderSpacing = tableParams.borderSpacingH;
		}

		// Start anonymous block
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
			// 2026-08-01: fixed a missing throw (the exception was constructed and discarded).
			// A table with placement other than FLOW/INLINE/FLOAT/ABSOLUTE is a logic error.
			throw new IllegalStateException(String.valueOf(blockBox.getPos().getType()));
		}

		final int columnCount = this.columnWidths.mins().length;
		double[] columnSizes;
		if (this.fixed) {
			// Fixed layout
			if (LayoutUtils.isNone(tableSize)) {
				tableSize = lineSize;
			}
			tableSize -= tableFrame;
			if (this.columnGroupBox != null) {
				this.tableBox.setTableColumnGroup(this.columnGroupBox);
			}
			// Obtain line-axis border spacing along the logical axis (the old implementation added
			// borderSpacingH even in vertical writing; normalized to the same logical axis as OnePass).
			// Pinned by 0390-writing-mode/vert-fixed-colgroup-spacing.html.
			final FixedColumnWidths.Result result = FixedTableSizing.resolve(this.columnGroupBox,
					this.rowToCells.get(this.firstRowBox), columnCount, tableSize,
					tableParams.borderCollapse == TableParams.BORDER_SEPARATE, lineBorderSpacing,
					this::fixedCellSpec);
			columnSizes = result.sizes();
			tableSize = result.innerSize() + tableFrame;
		} else {
			// Auto layout (shared core — P2-4)
			// **max-width also applies to auto-width tables** (2026-09-16, CSS 2.1 §17.5.2
			// used width). Previously, min was taken only when the size was definite,
			// discarding the upper bound for auto-width tables and expanding them to all available width
			// (on 200 pt paper, `max-width: 50%` became 200 pt).
			double available = blockBox.getLineSize();
			if (!LayoutUtils.isNone(lineMaxSize) && LayoutUtils.isNone(tableSize)
					&& LayoutUtils.compare(lineMaxSize, available) < 0) {
				available = lineMaxSize;
			}
			// An auto-width table that is itself a grid item fills the item (2026-10-09, css-grid-1 §6.2 stretch;
			// Chrome does the same). The item's width is already resolved by its alignment: stretched to its area, or
			// fit-content (= this table's own shrink-to-fit) otherwise. Shrink-to-fit inside the stretched item left
			// a 2-column-wide table at its content width. A flex item's width is its main size (flex: 1) or its cross
			// size (stretched in a column), so the table fills it too.
			final double specified = LayoutUtils.isNone(tableSize) && isItemTable(containerBox) ? available : tableSize;
			final AutoColumnWidths.Sized sized = this.columnWidths.resolve(specified, available,
					tableFrame, lineBorderSpacing, tableParams.borderCollapse == TableParams.BORDER_SEPARATE);
			tableSize = sized.tableSize();
			columnSizes = sized.columnSizes();
		}

		final double specifiedPageSize;
		if (this.vertical) {
			// Vertical writing
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
			// Horizontal writing
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
		// Page breaks replace the original FLOW wrapper. Do not retain the previous page’s body tree between stages.
		return new TableShape(anonBuilder, blockBox.getPos().getType(),
				blockBox.getPos().getType() == PosType.FLOW ? null : blockBox,
				tableSize, columnSizes, specifiedPageSize, tableInnerSize);
	}

	/**
	 * Binds captions and row groups, resolving row heights (bind stage 2).
	 *
	 * @return resolved row heights retained until border dimensions are applied (header, body, footer order)
	 */
	private double[] bindRows(final TableShape shape) {
		final TableParams tableParams = this.tableBox.getTableParams();
		final BlockBuilder anonBuilder = shape.anonBuilder();
		final double[] columnSizes = shape.columnSizes();
		final double specifiedPageSize = shape.specifiedPageSize();
		final double tableInnerSize = shape.tableInnerSize();
		// Top caption
		this.observeRetention("before-top-captions");
		for (int i = 0; i < this.topCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.topCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			anonBuilder.startFlowBlock(captionBox, this.captionInsetStart(), this.captionInsetEnd());
			captionBuilder.bind(anonBuilder);
			anonBuilder.endFlowBlock();
		}

		this.observeRetention("after-top-captions");

		// Header, body, footer
		int rowCount = 0; // Row count
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			List<TableRowBox> rows = this.rowGroupToRows.get(rowGroups.get(i));
			rowCount += rows.size();
		}

		// E-6 increment 5b-2 (2026-07-24): check eligibility for table Pass C (row-wise sequential bind)
		// per table, failing closed (codex design §4.4). If eligible, subsequent row height calculation
		// reads only scratch measurements from Pass B without binding. Bind runs row by row
		// in the "finalize cell heights" loop after row heights resolve (Pass C). Otherwise, retain
		// the old "bind all cells before row height calculation" path. Calculation code is shared:
		// only the input source and bind timing change.
		final boolean rowSequentialBind = this.isRowSequentialBindEligible();
		if (rowSequentialBind) {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTablePassC();
		} else {
			net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTableLegacyBindRows();
		}
		// Pass B measurements (used page-axis size for each actual cell box). Null on the old path.
		Map<TableCellBox, Double> measuredPageAxis = rowSequentialBind
				? new IdentityHashMap<TableCellBox, Double>()
				: null;

		// Calculate row heights
		double[] rowRatios = new double[rowCount]; // Percentage heights
		double rowSizeSum = 0; // Sum of row heights
		{
			int rowIndex = 0;
			for (int i = 0; i < this.rowGroups.size(); ++i) {
				TableRowGroupBox rowGroupBox = (TableRowGroupBox) rowGroups.get(i);
				List<TableRowBox> rows = this.rowGroupToRows.get(rowGroupBox);

				// Spanning rows
				Map<Rowspan, Rowspan> rowspans = new HashMap<Rowspan, Rowspan>();
				List<Rowspan> rowspanList = new ArrayList<Rowspan>();
				boolean[] noAdjRows = new boolean[rows.size()];
				boolean[] autoRows = new boolean[rows.size()];

				// Row height/cell layout
				for (int j = 0; j < rows.size(); ++j) {
					TableRowBox rowBox = rows.get(j);
					double rowSize;

					// Calculate specified row heights (shared core — P2-5 (c))
					final RowLayoutEngine.RowSpec rowSpec = RowLayoutEngine.rowSpec(rowBox.getInnerTableParams());
					rowSize = rowSpec.size();
					rowRatios[rowIndex] = rowSpec.ratio();
					if (rowSpec.auto()) {
						autoRows[j] = true;
					}

					// Layout inside cells
					List<CellContent> cells = this.rowToCells.get(rowBox);
					for (int k = 0; k < cells.size(); ++k) {
						CellContent cell = cells.get(k);
						int span = cell.colspan;
						TableCellBox cellBox = cell.getCellBox();
						if (cell.isExtended()) {
							k += span - 1;
							Cell rcell = (Cell) this.cellToSource.get(cellBox);
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

						// Reconstruct cell content (axis dimensions use the shared TableCellMetrics core).
						final double size = TableCellMetrics.spannedLineSize(columnSizes, k, span);
						k += span - 1;
						TableCellMetrics.applyLineAxis(cellBox, cell::getIntrinsicSizes, size, this.vertical,
								tableParams);
						if (measuredPageAxis != null) {
							// E-6 increment 5b-2, Pass B: scratch measurement without bind (discard the tree
							// built on the replica box after collecting values; at this point,
							// no bound cell body trees exist). Row height calculation reads only
							// these measurements. Bit-for-bit equality with actual bound sizes was proved in 5b-1
							// by RetainedCellPassBShadowTest.
							final CellPassBMeasurer.Result measured = CellPassBMeasurer.measure(cell, this.layoutStack,
									this.vertical);
							if (measured == null) {
								// Cannot occur: isRowSequentialBindEligible already checked eligibility.
								throw new IllegalStateException("Pass C適格表のセルがPass B計測できません");
							}
							measuredPageAxis.put(cellBox, measured.pageAxisSize());
							net.zamasoft.foliojet.layout.fragment.ContinuationStats.recordTablePassBCellMeasure();
						} else {
							// Old path: bind all cells before row height calculation.
							// E-6 increment 5a: sealed cells use SegmentExecutor range execution;
							// ineligible cells use the old records replay (CellContent.bind selects the path).
							this.bindCell(cell, cellBox);
						}

						this.cellToSource.put(cellBox, rowBox.addTableSourceCell(cellBox));
						int cellRowspan = Math.min(rows.size() - j, cell.rowspan);
						if (cellRowspan <= 1) {
							// Non-spanning rows
							noAdjRows[j] = true;
						} else {
							// Spanning rows (treat % heights as auto for spans)
							// Use the shared core for both requested sizes and registration (A-4).
							final double cellSize = RowLayoutEngine.demandPageSize(
									this.boundPageAxisSize(measuredPageAxis, cellBox), cellParams, cellBox,
									this.vertical);
							RowLayoutEngine.addSpannedDemand(rowspans, rowspanList, j, cellRowspan, cellSize);
						}
					}

					// Align baselines
					for (int k = 0; k < cells.size(); ++k) {
						final CellContent cell = cells.get(k);
						if (cell.isExtended()) {
							continue;
						}
						final TableCellBox cellBox = cell.getCellBox();
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

				// Calculate heights of rows joined by rowspan (shared engine — P2-2).
				// rowRatios is written with global indices, so pass a slice for this group.
				// The old implementation started at 0, reading the first group’s ratios,
				// so percentage rows in second and subsequent groups received no distribution.
				// Corrected in 0242-table-height/percent-rowspan-groups.html.
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
				// Calculate content height
				for (int j = 0; j < rows.size(); ++j) {
					TableRowBox rowBox = rows.get(j);
					rowSizeSum += rowBox.getPageSize();
				}
			}
		}

		// source/extended construction is complete for all rows. From now on, read only each row’s own Cell chain.
		this.cellToSource.clear();

		// Calculate percentage row heights (shared engine — P2-4)
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

		// Apply row group heights (shared engine — P2-4)
		this.observeRetention("before-group-page-size");
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
			// Discard the return value (increment); nothing reads it afterward (P2, external design review 2026-07-19).
			// The immediately following "apply table height" block rereads rowBox.getPageSize() each time,
			// so later values of rowSizeSum were dead.
			RowLayoutEngine.distributeGroupSize(rowSizes, params.size.getLength());
			for (int j = 0; j < rows.size(); ++j) {
				rows.get(j).setPageSize(rowSizes[j]);
			}
		}

		this.observeRetention("after-group-page-size");

		// Apply table height (shared engine — P2-4). Identify auto rows by directly checking
		// the specified type. This can branch differently from the old autoRowCount, which counted
		// rows specifying %0 as auto, but that already disagreed with selection for distribution;
		// normalize to a consistent direct check.
		final double[] rowSizes = new double[rowCount];
		{
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

		// Pass C bind and row height application do not read the measurement map. Retain only the path decision.
		measuredPageAxis = null;
		this.rowEmissionExclusions = this.rowEmissionExclusions(shape, rowSequentialBind);
		if (rowSequentialBind
				&& net.zamasoft.foliojet.layout.fragment.ReplayIntent.current()
						== net.zamasoft.foliojet.layout.fragment.ReplayIntent.MAIN) {
			final var observer = RetainedTextLimit.beforeTableMainBind;
			final RetainedTextLimit limit = RetainedTextLimit.get(this.layoutStack);
			if (observer != null && limit != null) observer.accept(limit);
		}
		this.observeRetention("after-pass-b");
		final boolean emitRows = this.rowEmissionExclusions.isEmpty();
		this.compactCellText(true);
		// Finalize cell heights (shared core — P2-5 (c))
		final boolean releaseRowPlans = net.zamasoft.foliojet.layout.fragment.ReplayIntent.current()
				== net.zamasoft.foliojet.layout.fragment.ReplayIntent.MAIN;
		int boundRows = 0;
		if (releaseRowPlans) {
			this.firstRowBox = null;
			this.upperRow = null;
		}
		for (int i = 0; i < this.rowGroups.size(); ++i) {
			TableRowGroupBox rowGroup = this.rowGroups.get(i);
			List<TableRowBox> rows = releaseRowPlans ? this.rowGroupToRows.remove(rowGroup)
					: this.rowGroupToRows.get(rowGroup);
			boolean emitBody = emitRows && rowGroup != this.headerGroup;
			final double[] groupRowSizes = new double[rows.size()];
			for (int j = 0; j < rows.size(); ++j) {
				groupRowSizes[j] = rows.get(j).getPageSize();
			}
			for (int j = 0; j < rows.size(); ++j) {
				TableRowBox rowBox = rows.get(j);
				rowBox.setLineSize(tableInnerSize);
				if (this.rowEmission == null) {
					rowGroup.addTableRow(rowBox);
				} else {
					this.rowEmission.body().addTableRow(rowBox);
				}
				// Finalizing one row is **actual work completed** (2026-07-27, progress signal for the deadline).
				this.noteTableProgress();
				final List<CellContent> cells = this.rowToCells.get(rowBox);
				if (rowSequentialBind) {
					// E-6 increment 5b-2, Pass C: row-wise sequential bind. Bind the current row’s actual
					// cells just before applying resolved row heights (applyCellExtents) and
					// baseline alignment (maxFirstAscent). Actual cell sizes and firstAscent after bind
					// match Pass B measurements bit for bit, so subsequent reads get the same values
					// as the old path. Bind order (row order, then cell order within a row) is unchanged too.
					this.bindRowCells(cells);
				}
				CellContent.applyCellExtents(cells, groupRowSizes, j, CellContent.maxFirstAscent(cells),
						this.vertical);
				// border-collapse in assemble reads only row heights. rowGroup retains completed rows/body trees;
				// rowspan in subsequent rows uses groupRowSizes and the Cell chain.
				rowSizes[boundRows++] = rowBox.getPageSize();
				if (releaseRowPlans) {
					// MEASURE retains body ownership, and parent absorption/termination reads the plan.
					this.rowToCells.remove(rowBox);
					cells.clear();
					rows.set(j, null);
				}
				if (emitBody) {
					boolean notified = false;
					if (this.rowEmission != null) {
						anonBuilder.getPageContext().noteRetainedTableRowsBound(1);
						// Splits after notification cannot be undone. Defer the visible end within the boundary until later rows or complete.
						if (j == rows.size() - 1 || this.rowEmission.hasRowEmissionOverflow()) {
							this.observeRetention("before-row-emission");
							if (j == rows.size() - 1) this.rowEmission.complete();
							else this.rowEmission.rowsAppended();
							notified = true;
						}
					} else if (j < rows.size() - 1 && this.tableBox.emissionCutDetermined(
							((BreakableBuilder) anonBuilder).getPageLimit() - anonBuilder.getPageAxis(), rowGroup,
							this.headerGroup == null ? -1 : this.headerGroup.getPageSize())) {
						// Check the host state after bind too, before suppressing termination.
						if (!((BreakableBuilder) anonBuilder).supportsIncompleteTableIntake()) {
							this.rowEmissionExclusions.add(TableBuildPlanner.RowEmissionExclusion.UNSUPPORTED_HOST);
							emitBody = false;
						} else {
							// Enough subsequent rows are visible to split. The parent decides splitting, movement, and termination.
							this.observeRetention("before-row-emission");
							this.attachGroups();
							this.sizeColumns(columnSizes);
							this.tableBox.markIncomplete();
							this.tableBox.setIncompletePlan(new IncompleteTablePlan(groupRowSizes,
									this.headerGroup == null ? 0 : this.headerGroup.getPageSize()));
							// Acceptance also draws the preceding fragment. Transfer body group, column tree, and table ownership first.
							this.bodyGroups.clear();
							this.rowGroups.set(i, null);
							rowGroup = null;
							this.columnGroupBox = null;
							// First bound body row passed to acceptance. Do not advance for short tables that never start emission.
							anonBuilder.getPageContext().noteRetainedTableRowsBound(j + 1);
							this.rowEmission = this.acceptRows((BreakableBuilder) anonBuilder);
							notified = true;
						}
					}
					if (notified && (j == rows.size() - 1
							|| this.rowEmission.emittedFragments() > 0
							|| this.rowEmission.status() == BreakableBuilder.IncompleteTableStatus.SPLIT
							|| this.rowEmission.status() == BreakableBuilder.IncompleteTableStatus.MOVED)) {
						this.observeRetention(j == rows.size() - 1 ? "after-row-completion" : "after-row-emission");
					}
				}
				this.compactCellText(false);
				if (j == rows.size() / 2) this.observeRetention("during-pass-c");
				if ((retentionObserver != null || retentionPlanObserver != null) && boundRows % 2000 == 0) {
					this.observeRetention("after-row-" + boundRows);
				}
			}
		}
		return rowSizes;
	}

	private BreakableBuilder.IncompleteTableResult acceptRows(final BreakableBuilder host) {
		final TableBox table = this.tableBox;
		// Acceptance proceeds through page breaks and drawing, so save only required values before transferring ownership.
		// Line-axis margins do not change on table splits. Completed tables read their own frame as before.
		this.rowEmissionCaptionInsetStart = this.captionInsetStart();
		this.rowEmissionCaptionInsetEnd = this.captionInsetEnd();
		this.tableBox = null;
		final var result = host.acceptIncompleteTable(table);
		if (!result.isAccepted()) {
			throw new IllegalStateException("The eligible incomplete table was not accepted");
		}
		return result;
	}

	private java.util.EnumSet<TableBuildPlanner.RowEmissionExclusion> rowEmissionExclusions(
			final TableShape shape, final boolean passCEligible) {
		return TableBuildPlanner.rowEmissionExclusionsAfterPassB(this.tableBox, shape.anonBuilder(),
				passCEligible, this.bodyGroups.size(), this.footerGroup != null,
				!this.topCaptions.isEmpty(), shape.columnSizes().length,
				this.columnGroupBox != null, this.headerGroup, this.rowGroups, this.rowGroupToRows, this.rowToCells);
	}

	/**
	 * Table-wide eligibility for Pass C (row-wise sequential bind) (E-6 increment 5b-2,
	 * 2026-07-24; Pass B/C in codex design §4.4; fail closed). Eligibility:
	 * <ul>
	 * <li>All actual cells support Pass B measurement ({@link CellContent#isPassBMeasurable}:
	 * sealed ranges or empty cells with no records). Any cell ineligible for sealing,
	 * such as one with nested builders, or any multi-column cell sends the whole table
	 * to the old path.</li>
	 * </ul>
	 *
	 * <p>
	 * 2026-07-30 (DP increment 5): removed the former "no captions" condition.
	 * Caption bind lies entirely outside row processing (top: before row height calculation;
	 * bottom: after addBound), so switching to Pass C does not affect it.
	 * Enabled after a captioned-table fixture in {@code RetainedCellPassBShadowTest}
	 * proved bit-for-bit equality (maxDiff=0.0) between Pass B measurements and actual
	 * sizes from legacy all-at-once bind. Caption bind itself (records replay)
	 * remains outside this eligibility check.
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
	 * Used cell page-axis size read by row height calculation (E-6 increment 5b-2).
	 * Pass C tables ({@code measured != null}) use Pass B scratch measurements
	 * (bit-for-bit equality with actual bound sizes proved in 5b-1);
	 * the old path uses actual sizes from bound cell boxes.
	 */
	private double boundPageAxisSize(final Map<TableCellBox, Double> measured, final TableCellBox cellBox) {
		if (measured != null) {
			return measured.get(cellBox);
		}
		return this.vertical ? cellBox.getWidth() : cellBox.getHeight();
	}

	/**
	 * Binds one cell (shared core for the old all-at-once bind and Pass C row-order bind).
	 * E-6 increment 5a: sealed cells use SegmentExecutor range execution; ineligible cells
	 * replay records, selected by {@code CellContent.bind}. E-6 increment 5b-1:
	 * the shadow verification hook (test-only, production=null) observes immediately
	 * before and after bind.
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
	 * Binds a row's actual cells in cell order (E-6 increment 5b-2, Pass C).
	 * Extended slots (rowspan/colspan continuations) are or will be bound at their owning row/column.
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
	 * Assembles row groups into the table, applies column/border dimensions, and closes it (bind stage 3).
	 */
	private void assemble(final TableShape shape, final double[] rowSizes) {
		final TableParams tableParams = this.tableBox.getTableParams();
		final BlockBuilder anonBuilder = shape.anonBuilder();
		final double[] columnSizes = shape.columnSizes();
		final double specifiedPageSize = shape.specifiedPageSize();
		final double tableSize = shape.tableSize();
		final int columnCount = this.columnWidths.mins().length;
		this.attachGroups();
		if (rowSizes.length == 0 || columnCount == 0) {
			if (this.vertical) {
				this.tableBox.setSize(specifiedPageSize, tableSize - this.tableBox.getFrame().getFrameHeight());
			} else {
				this.tableBox.setSize(tableSize - this.tableBox.getFrame().getFrameWidth(), specifiedPageSize);
			}
		}

		// Columns
		this.sizeColumns(columnSizes);

		if (tableParams.borderCollapse == TableParams.BORDER_COLLAPSE) {
			// Collapsed borders
			for (int i = 0; i < columnSizes.length; ++i) {
				assert !LayoutUtils.isNone(columnSizes[i]);
				this.borders.setColumnSize(i, columnSizes[i]);
			}
			for (int row = 0; row < rowSizes.length; ++row) {
				this.borders.setRowSize(row, rowSizes[row]);
			}
		}

		anonBuilder.addBound(this.tableBox);
	}

	private void attachGroups() {
		if (this.headerGroup != null) this.tableBox.setTableHeader(this.headerGroup);
		for (int i = 0; i < this.bodyGroups.size(); ++i) {
			this.tableBox.addTableBody(this.bodyGroups.get(i));
		}
		if (this.footerGroup != null) this.tableBox.setTableFooter(this.footerGroup);
	}

	private void sizeColumns(final double[] sizes) {
		if (this.columnGroupBox == null) return;
		final double pageSize = this.vertical ? this.tableBox.getInnerWidth() : this.tableBox.getInnerHeight();
		this.tableBox.setTableColumnGroup(this.columnGroupBox);
		this.columnGroupBox.eachColumn((column, col, span) -> {
			double size = 0;
			for (int j = 0; j < span; ++j) size += sizes[col + j];
			column.setLineSize(size);
			column.setPageSize(pageSize);
		});
	}

	/** Closes exactly once after submitting a completed table or completing an unfinished-table handle. */
	private void finishTable(final BlockBuilder builder, final TableShape shape,
			final RetainedTextLimit.Scope retained) {
		final BlockBuilder anonBuilder = shape.anonBuilder();
		final AbstractBlockBox blockBox = shape.blockBox();
		if (retained != null) retained.close();

		// Bottom caption
		for (int i = 0; i < this.bottomCaptions.size(); ++i) {
			TwoPassBlockBuilder captionBuilder = (TwoPassBlockBuilder) this.bottomCaptions.get(i);
			FlowBlockBox captionBox = (FlowBlockBox) captionBuilder.getRootBox();
			anonBuilder.startFlowBlock(captionBox, this.captionInsetStart(), this.captionInsetEnd());
			captionBuilder.bind(anonBuilder);
			anonBuilder.endFlowBlock();
		}

		switch (shape.position()) {
		case FLOW:
			builder.endFlowBlock();
			break;
		case INLINE:
			anonBuilder.close();
			// Added by DocumentBuilder
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
				// Added by DocumentBuilder
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
		// Retained can commit only after all rows have been read (A-2).
		host.addTable(this);
	}

	/**
	 * Returns a column specification from a first-row cell in fixed layout (null for AUTO).
	 * Divide the specification equally by the cell's colspan.
	 *
	 * @param cell    cell
	 * @param refSize reference size for percentage specifications
	 * @return column specification
	 */
	private FixedColumnWidths.Spec fixedCellSpec(final CellContent cell, final double refSize) {
		// Specification derivation is unified in FixedColumnWidths (P2-2).
		return FixedColumnWidths.cellSpec(cell.getCellBox(), cell.colspan,
				this.tableBox.getTableParams().flow, refSize);
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
	 * Row finalization is monotonic work done once per row, so an idle loop cannot fake progress.
	 * </p>
	 */
	private void noteTableProgress() {
		this.layoutStack.getPageContext().getPageGenerator().getUserAgent().noteProgress();
	}

}

/**
 * Joined columns.
 *  
 * @author MIYABE Tatsuhiko
 * @version $Id: RetainedTableBuilder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
