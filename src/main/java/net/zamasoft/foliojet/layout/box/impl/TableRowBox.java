package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableRowPos;
import net.zamasoft.foliojet.layout.box.params.TypesettingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Table row implementation.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowBox.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class TableRowBox extends AbstractInnerTableBox implements IPageBreakableBox {

	protected final TableRowPos pos;
	private Drawer pendingDrawer = null;

	public static interface Cell {
		public boolean isSource();

		public TableCellBox getCellBox();

		public Cell getSource();

		public void setNextExtendedCell(ExtendedCell extended);

		public ExtendedCell getNextExtendedCell();

		public TableRowBox getTableRow();

		/**
		 * Line-axis offset of this cell from the row's line start, or {@code NaN} when the cell sits after the
		 * preceding cells of the row (the usual case). See {@link TableRowBox#cutUnextendedRowspanCells}.
		 */
		public double getLineOffset();
	}

	public static interface ExtendedCell extends Cell {
		public void setSourceCell(Cell source);
	}

	protected static abstract class AbstractCell implements Cell {
		protected ExtendedCell extended;
		protected final TableRowBox row;

		protected AbstractCell(TableRowBox row) {
			this.row = row;
		}

		public ExtendedCell getNextExtendedCell() {
			return this.extended;
		}

		public void setNextExtendedCell(ExtendedCell extended) {
			this.extended = extended;
		}

		public TableRowBox getTableRow() {
			return this.row;
		}

		public double getLineOffset() {
			return Double.NaN;
		}
	}

	protected static class SourceCellImpl extends AbstractCell {
		protected final TableCellBox cell;
		private final double lineOffset;

		public SourceCellImpl(TableCellBox cell, TableRowBox row) {
			this(cell, row, Double.NaN);
		}

		SourceCellImpl(TableCellBox cell, TableRowBox row, double lineOffset) {
			super(row);
			this.cell = cell;
			this.lineOffset = lineOffset;
		}

		@Override
		public double getLineOffset() {
			return this.lineOffset;
		}

		public boolean isSource() {
			return true;
		}

		public Cell getSource() {
			return this;
		}

		public TableCellBox getCellBox() {
			return this.cell;
		}
	}

	protected static class ExtendedCellImpl extends AbstractCell implements ExtendedCell {
		protected Cell source;

		public ExtendedCellImpl(TableRowBox row) {
			super(row);
		}

		public boolean isSource() {
			return false;
		}

		public Cell getSource() {
			return this.source;
		}

		public TableCellBox getCellBox() {
			return this.source.getCellBox();
		}

		public void setSourceCell(Cell source) {
			this.source = source;
		}
	}

	protected final List<Cell> cells = new ArrayList<Cell>();

	public TableRowBox(final InnerTableParams params, final TableRowPos pos) {
		super(params);
		this.pos = pos;
	}

	public final BoxType getType() {
		return BoxType.TABLE_ROW;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableRowPos getTableRowPos() {
		return this.pos;
	}

	public final void setLineSize(double lineSize) {
		this.lineSize = lineSize;
	}

	public final void setPageSize(double pageSize) {
		this.pageSize = pageSize;
	}

	public final Cell addTableSourceCell(TableCellBox cellBox) {
		return this.addTableSourceCell(cellBox, Double.NaN);
	}

	/** Adds a source cell at an explicit line-axis offset ({@link Cell#getLineOffset}; {@code NaN} for none). */
	private Cell addTableSourceCell(final TableCellBox cellBox, final double lineOffset) {
		final Cell source = new SourceCellImpl(cellBox, this, lineOffset);
		this.cells.add(source);
		return source;
	}

	/**
	 * Line-axis offset of cell {@code index} from the row's line start, accumulated the same way the drawing loops do:
	 * the line extents of the preceding cells that have no explicit offset.
	 */
	private double lineOffsetOf(final int index) {
		final double explicit = this.cells.get(index).getLineOffset();
		if (!Double.isNaN(explicit)) {
			return explicit;
		}
		final boolean vertical = this.tableParams.flow.isVertical();
		double offset = 0;
		for (int i = 0; i < index; ++i) {
			final Cell cell = this.cells.get(i);
			if (Double.isNaN(cell.getLineOffset())) {
				offset += vertical ? cell.getCellBox().getHeight() : cell.getCellBox().getWidth();
			}
		}
		return offset;
	}

	public final ExtendedCell addTableExtendedCell(Cell cell) {
		ExtendedCell extended = new ExtendedCellImpl(this);
		extended.setSourceCell(cell.getSource());
		cell.setNextExtendedCell(extended);
		this.cells.add(extended);
		return extended;
	}

	public final Cell getCell(int i) {
		return (Cell) this.cells.get(i);
	}

	public final int getCellCount() {
		return this.cells.size();
	}

	/** Whether the row has a visible background or source cells. Do not revisit rowspan extension cells. */
	@Override
	public boolean paintsAnything() {
		if (this.params.opacity == 0) {
			return false;
		}
		if (this.params.background.isVisible()) {
			return true;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			final Cell cell = this.cells.get(i);
			if (cell.isSource() && cell.getCellBox().paintsAnything()) {
				return true;
			}
		}
		return false;
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
	}

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		for (final Cell cell : this.cells) {
			if (cell.isSource()) {
				action.accept(cell.getCellBox());
			}
		}
	}

	public final void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		if (this.cells == null) {
			return;
		}
		// Push in reverse order (last cell first) to preserve the original traversal order (first cell first).
		for (int i = this.cells.size() - 1; i >= 0; --i) {
			Cell cell = (Cell) this.cells.get(i);
			if (cell.isSource()) {
				worklist.push(IBox.step(cell.getCellBox(), containerBox));
			}
		}
	}

	public final void pushFramesSteps(PageBox pageBox, Drawer drawer, Shape clip, AffineTransform transform, double x,
			double y, Deque<FramesStep> worklist) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.params.isStackingContext()) {
			final Drawer newDrawer = new Drawer(this.params, transform);
			drawer.visitDrawer(newDrawer);
			drawer = newDrawer;
			this.pendingDrawer = newDrawer;
		}
		if (this.params.background.isVisible()) {
			Drawable drawable = new BackgroundBorderDrawable(pageBox, clip, this.params.opacity, transform,
					this.params.background, this.params.border, null, this.getWidth(), this.getHeight()).withBlendMode(this.params.blendMode).withFilter(this.params.filter);
			drawer.visitDrawable(drawable, x, y);
		}
		if (this.cells == null) {
			return;
		}
		// First calculate which cells to draw and their coordinates without side effects, then push
		// in **reverse order** to preserve the original traversal order.
		final int n = this.cells.size();
		final TableCellBox[] sourceCells = new TableCellBox[n];
		final double[] xs = new double[n];
		final double[] ys = new double[n];
		int count = 0;
		if (this.tableParams.flow.isVertical()) {
			// Vertical writing
			final boolean bottomToTop = this.tableParams.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(this.tableParams.flow,
							this.tableParams.writingModeVariant,
							this.tableParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
			final double lineOrigin = y;
			double logicalLine = 0;
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				final double line = Double.isNaN(cell.getLineOffset()) ? logicalLine : cell.getLineOffset();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					sourceCells[count] = cellBox;
					// Spanning cells exceed the row's page-axis size. Delegate direction handling to
					// LayoutUtils.drawX (previously, handwritten RL-only formulas displaced spanning cells
					// outside the table in vertical-lr. For ordinary cells, width == row page-axis size
					// canceled the error, so it appeared only in rowspan cells.
					// Found in an independent review, 2026-07-25).
					xs[count] = LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, 0, cellBox.getWidth(), 0);
					ys[count] = bottomToTop
							? lineOrigin + LayoutUtils.inlineToPhysical(this.tableParams, this.getHeight(), line,
									line + cellBox.getHeight())
							: Double.isNaN(cell.getLineOffset()) ? y : lineOrigin + line;
					++count;
				}
				if (Double.isNaN(cell.getLineOffset())) {
					y += cellBox.getHeight();
					logicalLine += cellBox.getHeight();
				}
			}
		} else {
			// Horizontal writing
			final double lineOrigin = x;
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					sourceCells[count] = cellBox;
					xs[count] = Double.isNaN(cell.getLineOffset()) ? x : lineOrigin + cell.getLineOffset();
					ys[count] = y;
					++count;
				}
				if (Double.isNaN(cell.getLineOffset())) {
					x += cellBox.getWidth();
				}
			}
		}
		for (int i = count - 1; i >= 0; --i) {
			worklist.push(
					AbstractContainerBox.framesStep(sourceCells[i], pageBox, drawer, clip, transform, xs[i], ys[i]));
		}
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.cells == null) {
			return;
		}
		if (this.tableParams.flow.isVertical()) {
			final boolean bottomToTop = this.tableParams.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(this.tableParams.flow,
							this.tableParams.writingModeVariant,
							this.tableParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
			final double lineOrigin = y;
			double logicalLine = 0;
			for (int i = 0; i < this.cells.size(); ++i) {
				// Vertical writing
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				final double line = Double.isNaN(cell.getLineOffset()) ? logicalLine : cell.getLineOffset();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					final double drawY = bottomToTop
							? lineOrigin + LayoutUtils.inlineToPhysical(this.tableParams, this.getHeight(), line,
									line + cellBox.getHeight())
							: Double.isNaN(cell.getLineOffset()) ? y : lineOrigin + line;
					cellBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY,
							LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, 0, cellBox.getWidth(), 0), drawY);

				}
				if (Double.isNaN(cell.getLineOffset())) {
					y += cellBox.getHeight();
					logicalLine += cellBox.getHeight();
				}
			}
		} else {
			// Horizontal writing
			final double lineOrigin = x;
			for (int i = 0; i < this.cells.size(); ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource() && cellBox.getTableCellPos().offset == null) {
					cellBox.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY,
							Double.isNaN(cell.getLineOffset()) ? x : lineOrigin + cell.getLineOffset(), y);

				}
				if (Double.isNaN(cell.getLineOffset())) {
					x += cellBox.getWidth();
				}
			}
		}
	}

	public final void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip,
			AffineTransform transform, double contextX, double contextY, double x, double y,
			java.util.Deque<DrawStep> worklist) {
		visitor.visitBox(transform, this, drawer, x, y);

		if (this.params.opacity == 0) {
			return;
		}
		if (this.params.zIndexType == Params.Z_INDEX_SPECIFIED) {
			if (this.pendingDrawer != null) {
				drawer = this.pendingDrawer;
				this.pendingDrawer = null;
			} else {
				final Drawer newDrawer = new Drawer(this.params, transform);
				drawer.visitDrawer(newDrawer);
				drawer = newDrawer;
			}
		}
		if (this.cells == null) {
			return;
		}
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
		// First calculate which cells to draw and their coordinates without side effects, then push
		// in **reverse order** to preserve the original traversal order.
		final int n = this.cells.size();
		final TableCellBox[] sourceCells = new TableCellBox[n];
		final double[] drawXs = new double[n];
		final double[] drawYs = new double[n];
		int sourceCount = 0;
		if (this.tableParams.flow.isVertical()) {
			// Vertical writing
			final boolean bottomToTop = this.tableParams.writingModeVariant != WritingModeVariant.NORMAL
					&& TypesettingMode.inlineProgression(this.tableParams.flow,
							this.tableParams.writingModeVariant,
							this.tableParams.direction) == TypesettingMode.InlineProgression.BOTTOM_TO_TOP;
			final double lineOrigin = y;
			double logicalLine = 0;
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				final double line = Double.isNaN(cell.getLineOffset()) ? logicalLine : cell.getLineOffset();
				if (cell.isSource()) {
					sourceCells[sourceCount] = cellBox;
					drawXs[sourceCount] = LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, 0, cellBox.getWidth(), 0);
					drawYs[sourceCount] = bottomToTop
							? lineOrigin + LayoutUtils.inlineToPhysical(this.tableParams, this.getHeight(), line,
									line + cellBox.getHeight())
							: Double.isNaN(cell.getLineOffset()) ? y : lineOrigin + line;
					++sourceCount;
				}
				if (Double.isNaN(cell.getLineOffset())) {
					y += cellBox.getHeight();
					logicalLine += cellBox.getHeight();
				}
			}
		} else {
			// Horizontal writing
			final double lineOrigin = x;
			for (int i = 0; i < n; ++i) {
				Cell cell = (Cell) this.cells.get(i);
				TableCellBox cellBox = cell.getCellBox();
				if (cell.isSource()) {
					sourceCells[sourceCount] = cellBox;
					drawXs[sourceCount] = Double.isNaN(cell.getLineOffset()) ? x : lineOrigin + cell.getLineOffset();
					drawYs[sourceCount] = y;
					++sourceCount;
				}
				if (Double.isNaN(cell.getLineOffset())) {
					x += cellBox.getWidth();
				}
			}
		}
		final Drawer fdrawer = drawer;
		final double fx = x, fy = y;
		worklist.push(w -> pageBox.endStruct(fdrawer, this.params.element, structCount, fx, fy));
		for (int i = sourceCount - 1; i >= 0; --i) {
			worklist.push(IBox.drawStep(sourceCells[i], pageBox, drawer, visitor, clip, transform, contextX,
					contextY, drawXs[i], drawYs[i]));
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		if (this.cells == null) {
			return;
		}
		// Push onto the stack in reverse order to preserve the original traversal order.
		for (int i = this.cells.size() - 1; i >= 0; --i) {
			Cell cell = (Cell) this.cells.get(i);
			worklist.push(IBox.getTextStep(cell.getCellBox(), textBuff));
		}
	}

	/**
	 * Cell cut position (C4-T3). For spanning cells (rowspan), adds the page-axis sizes from the
	 * source row through this row: the cut line is relative to the cell's top edge.
	 *
	 * <p>
	 * A-3b physical alignment contract (documented 2026-07-24; `the development records` is the authoritative record):
	 * All cells in a row are cut at **the same physical split line** (if a later cell first determines
	 * the split, go back and force splits on the cells already processed). Each cell receives content
	 * coordinates that further subtract {@code verticalAlign} (the difference between the measured final
	 * cell height and content height) from this top-relative position (see {@code TableCellBox.split}).
	 * This table-specific split mechanism is not integrated into the common FragmentRecipe continuation IR
	 * (an intentionally isolated area: the common IR cannot structurally represent multiple cells open
	 * in parallel. Decided during design consultation; see the document above for conditions for revisiting).
	 * </p>
	 */
	private static double cellCutPageAxis(final Cell cell, final double pageLimit) {
		double cutPageAxis = pageLimit;
		if (!cell.isSource()) {
			final Cell sCell = cell.getSource();
			cutPageAxis += sCell.getTableRow().getPageSize();
			for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell
					.getNextExtendedCell()) {
				if (xcell == cell) {
					break;
				}
				cutPageAxis += xcell.getTableRow().getPageSize();
			}
		}
		return cutPageAxis;
	}

	/**
	 * Checks, without mutating cells, whether the cut interval assigned to this row captures at least
	 * one splittable unit of cell content. A cell's own frame and padding alone do not count as progress.
	 */
	private static boolean cellFragmentTakesContent(final Cell cell, final double pageLimit) {
		final TableCellBox cellBox = cell.getCellBox();
		final Container container = cellBox.getContainer();
		if (!container.hasNonDecorationContent()) {
			return false;
		}

		final double fragmentEnd = cellCutPageAxis(cell, pageLimit);
		final double fragmentStart = fragmentEnd - pageLimit;
		final double frameStart = cellBox.getFrame().getFramePageStart(cellBox.getBlockParams().flow);
		double alignment = cellBox.verticalAlign;
		if (alignment > 0) {
			final double fragmentInner = fragmentEnd - frameStart;
			final double firstUnitEnd = container.getCutPoint(0);
			if (LayoutUtils.compare(alignment + firstUnitEnd, fragmentInner) > 0) {
				alignment = Math.max(0, fragmentInner - firstUnitEnd);
			}
		}
		final double contentStart = Math.max(0, fragmentStart - frameStart - alignment);
		final double contentEnd = fragmentEnd - frameStart - alignment;
		if (LayoutUtils.compare(contentEnd, contentStart) <= 0) {
			return false;
		}

		// For floats/absolute positioning whose positions cannot be queried without side effects,
		// conservatively assume progress to avoid content loss.
		if (container.hasFloatings()) {
			return true;
		}
		final boolean[] hasAbsolute = new boolean[1];
		container.eachAbsoluteBox(box -> hasAbsolute[0] = true);
		if (hasAbsolute[0]) {
			return true;
		}
		return LayoutUtils.compare(container.getCutPointBelow(contentEnd), contentStart) > 0;
	}

	private boolean fragmentTakesContent(final double pageLimit) {
		for (int i = 0; i < this.cells.size(); ++i) {
			if (cellFragmentTakesContent(this.cells.get(i), pageLimit)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Explicitly checks the FLAGS_SPLIT cell-splitting contract (always returns {@code Split}, with a
	 * {@code TableCellBox} remainder) and returns the remainder cell. Previously, an unchecked cast
	 * surfaced contract violations as {@code ClassCastException} (2026-07-24 architecture review E-1:
	 * only clarifies the exception type for contract violations; normal-path logic remains unchanged).
	 */
	private static TableCellBox forcedCellRemainder(final TableCellBox cellBox, final double cutPageAxis,
			final BreakMode mode, final byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_SPLIT) != 0;
		return net.zamasoft.foliojet.layout.fragment.TableCutter.requireSplitRemainder(
				cellBox.split(cutPageAxis, mode, flags), TableCellBox.class, "TableCellBox.split with FLAGS_SPLIT");
	}

	public final SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;

		final boolean vertical = this.tableParams.flow.isVertical();
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) == 0) {
			// Preliminary decisions extracted into pure logic in TableCutter (C4-T3).
			final double[] cellPageExtents = new double[this.cells.size()];
			final boolean[] cellFlowMatch = new boolean[this.cells.size()];
			final boolean[] cellInsideAvoid = new boolean[this.cells.size()];
			final boolean[] cellCollapsedAtStart = new boolean[this.cells.size()];
			for (int i = 0; i < this.cells.size(); ++i) {
				final TableCellBox cellBox = ((Cell) this.cells.get(i)).getCellBox();
				final BlockParams cellParams = cellBox.getBlockParams();
				cellPageExtents[i] = cellBox.getPageExtent(this.tableParams.flow);
				cellFlowMatch[i] = cellParams.flow.isVertical() == vertical;
				cellInsideAvoid[i] = cellParams.pageBreakInside == PageBreakMode.AVOID;
				cellCollapsedAtStart[i] = cellBox.getFrame().getFramePageStart(this.tableParams.flow) <= 0
						&& LayoutUtils.compare(cellBox.getInnerPageExtent(this.tableParams.flow), 0) <= 0;
			}
			final boolean pageFirst = (flags & IPageBreakableBox.FLAGS_FIRST) != 0;
			final boolean firstRow = (flags & IPageBreakableBox.FLAGS_FIRST_ROW) != 0;
			final double fragmentCapacity = mode instanceof BreakMode.AutoBreakMode auto ? auto.fragmentCapacity : -1;
			final SplitResult pre = net.zamasoft.foliojet.layout.fragment.TableCutter.rowPreDecide(pageFirst,
					firstRow, pageLimit, this.getPageSize(),
					this.params.pageBreakInside == PageBreakMode.AVOID, cellPageExtents, cellFlowMatch,
					cellInsideAvoid, cellCollapsedAtStart, fragmentCapacity);
			if (pre != null) {
				return pre;
			}
			// In vertical writing, FIRST_ROW exempts rows joined to the first row by rowspan from ordinary
			// row-level MOVE. If such a row fits entirely in the next fragmentainer and no content unit
			// can be taken here, move the whole row instead of creating an initial fragment containing
			// only a frame. Preserve the existing split order for horizontal writing.
			if (vertical && !pageFirst && firstRow && fragmentCapacity > 0
					&& LayoutUtils.compare(this.getPageSize(), fragmentCapacity) <= 0
					&& !this.fragmentTakesContent(pageLimit)) {
				return SplitResult.MOVE;
			}
		}
		byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
		final double pageWindow = this.pageSize - pageLimit;
		TableRowBox nextRowBox = null;
		if ((flags & IPageBreakableBox.FLAGS_SPLIT) != 0) {
			// Always cut.
			// A split fragment is a continuation (no anchor; not replayed as a fresh box. P0).
			nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
			nextRowBox.setTableParams(this.tableParams);
			this.pageSize = pageLimit;
			nextRowBox.pageSize = pageWindow;
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			TableCellBox prevCellBox = cell.getCellBox();
			TableCellBox nextCellBox;
			final double cutPageAxis = cellCutPageAxis(cell, pageLimit);
			final SplitResult cellResult = prevCellBox.split(cutPageAxis, mode, xflags);
			if (cellResult instanceof SplitResult.Split(final IPageBreakableBox cellRemainder)) {
				if (!(cellRemainder instanceof TableCellBox typedRemainder)) {
					throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
							"TableCellBox.split remainder must be a TableCellBox but was " + cellRemainder);
				}
				nextCellBox = typedRemainder;
			} else {
				if (nextRowBox == null) {
					continue;
				}
				// Force a cut if another cell has already split.
				byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
				nextCellBox = forcedCellRemainder(prevCellBox, cutPageAxis, mode, xxflags);
			}
			if (nextRowBox == null) {
				nextRowBox = new TableRowBox(this.params, this.getTableRowPos());
				nextRowBox.setTableParams(this.tableParams);
				this.pageSize = pageLimit;
				nextRowBox.pageSize = pageWindow;
				for (int j = 0; j < i; ++j) {
					Cell cell2 = (Cell) this.cells.get(j);
					TableCellBox prevCell2 = cell2.getCellBox();
					final double cutPageAxis2 = cellCutPageAxis(cell2, pageLimit);
					byte xxflags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
					TableCellBox nextCell2 = forcedCellRemainder(prevCell2, cutPageAxis2, mode, xxflags);
					if (vertical) {
						prevCell2.setWidth(cutPageAxis2);
					} else {
						prevCell2.setHeight(cutPageAxis2);
					}
					this.restyleCell(nextCell2);
					Cell source = nextRowBox.addTableSourceCell(nextCell2, cell2.getLineOffset());
					ExtendedCell xcell = cell2.getNextExtendedCell();
					double span = 1;
					if (xcell != null) {
						source.setNextExtendedCell(xcell);
						do {
							++span;
							xcell.setSourceCell(source);
							xcell = xcell.getNextExtendedCell();
						} while (xcell != null);
					}
					nextRowBox.pageSize = Math.max(nextRowBox.pageSize,
							nextCell2.getPageExtent(this.tableParams.flow) / span);
				}
			}
			if (vertical) {
				prevCellBox.setWidth(cutPageAxis);
			} else {
				prevCellBox.setHeight(cutPageAxis);
			}
			this.restyleCell(nextCellBox);
			Cell source = nextRowBox.addTableSourceCell(nextCellBox, cell.getLineOffset());
			ExtendedCell xcell = cell.getNextExtendedCell();
			double span = 1;
			if (xcell != null) {
				source.setNextExtendedCell(xcell);
				do {
					++span;
					xcell.setSourceCell(source);
					xcell = xcell.getNextExtendedCell();
				} while (xcell != null);
			}
			nextRowBox.pageSize = Math.max(nextRowBox.pageSize, nextCellBox.getPageExtent(this.tableParams.flow) / span);
		}

		if (nextRowBox == null) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
				return SplitResult.KEEP;
			}
			// Carry the current row forward.
			return SplitResult.MOVE;
		}

		// Post-split processing
		for (int i = 0; i < nextRowBox.cells.size(); ++i) {
			Cell cell = (Cell) nextRowBox.cells.get(i);
			double rowSize = nextRowBox.pageSize;
			for (ExtendedCell xcell = cell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				rowSize += xcell.getTableRow().getPageSize();
			}
			TableCellBox nextCell = cell.getCellBox();
			if (vertical) {
				nextCell.setWidth(rowSize);
			} else {
				nextCell.setHeight(rowSize);
			}
		}
		return new SplitResult.Split(nextRowBox);
	}

	/**
	 * Cuts spanning cells <b>without extension entries</b> (2026-08-22).
	 *
	 * <p>
	 * When an empty {@code <tr>} or a short row creates a gap among rowspans, continuation cells in columns
	 * beyond the gap get no extension (ExtendedCell) in the row list ({@code CellContent.complementRowspan}
	 * stops scanning at the ending column: list indices represent column positions, so inserting a
	 * placeholder in the middle would shift the cell's own column and is not possible). Without an
	 * extension, {@link #cutRowspanCells} does not find the cell when a row moves (it follows extension
	 * entries in the moving row), leaving the cell at full height on the previous page and reversing
	 * reading order (sweep seeds 1472118/1173267). Here, cut directly from retained rows those cells whose
	 * pos rowspan reaches the moving region but whose extension chain does not, and add their remainders
	 * to the moving row. The remainders are appended to the row list.
	 * </p>
	 *
	 * <p>
	 * A remainder keeps its cell's line-axis offset ({@link Cell#getLineOffset}, 2026-10-08). Placed by its list
	 * position, it moved toward the line start by the column gaps, or, when the moving row's own cells cover its
	 * column (a colspan overlapping the rowspan, an HTML table model error), past them out of the table: fit sweep
	 * seed 12070374 put T5 at y=125.33 instead of 58.83 (Chrome 57.59) on 60 pt paper (triage §23). Chrome draws
	 * such overlapping cells over each other, as now.
	 * </p>
	 *
	 * @param rowsToCut   number of rows from this row to the moving row (the next row = 1)
	 * @param cutPageAxis distance from the top of this row's cells to the cut line
	 * @param target      moving row to which remainders are added
	 */
	public final void cutUnextendedRowspanCells(final int rowsToCut, final double cutPageAxis,
			final TableRowBox target) {
		final boolean vertical = this.tableParams.flow.isVertical();
		for (int i = 0; i < this.cells.size(); ++i) {
			final Cell cell = (Cell) this.cells.get(i);
			if (!cell.isSource()) {
				continue;
			}
			final TableCellBox cellBox = cell.getCellBox();
			if (cellBox.getTableCellPos().rowspan <= rowsToCut) {
				// Does not reach the moving region.
				continue;
			}
			int chain = 0;
			for (ExtendedCell xcell = cell.getNextExtendedCell(); xcell != null; xcell = xcell
					.getNextExtendedCell()) {
				++chain;
			}
			if (chain >= rowsToCut) {
				// The extension reaches the moving region; cutRowspanCells handles it.
				continue;
			}
			if (LayoutUtils.compare(cellBox.getPageExtent(this.tableParams.flow), cutPageAxis) <= 0) {
				// The actual cell does not reach the cut line (remains empty).
				continue;
			}
			final double lineOffset = this.lineOffsetOf(i);
			final TableCellBox nextCell = forcedCellRemainder(cellBox, cutPageAxis, BreakMode.DEFAULT_BREAK_MODE,
					IPageBreakableBox.FLAGS_SPLIT);
			if (vertical) {
				cellBox.setWidth(cutPageAxis);
			} else {
				cellBox.setHeight(cutPageAxis);
			}
			target.restyleCell(nextCell);
			target.addTableSourceCell(nextCell, lineOffset);
			target.pageSize = Math.max(target.pageSize,
					nextCell.getPageExtent(this.tableParams.flow) / Math.max(1,
							cellBox.getTableCellPos().rowspan - rowsToCut));
		}
	}

	public final void cutRowspanCells() {
		net.zamasoft.foliojet.layout.builder.impl.TableBuildStats.ROWSPAN_CUTS.incrementAndGet();
		// Force a cut on spanning cells.
		final boolean vertical = this.tableParams.flow.isVertical();
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			if (cell.isSource()) {
				continue;
			}
			TableCellBox prevCell = cell.getCellBox();
			// The cut plane is the row's top edge: cell height minus the current row's height.
			Cell sCell = cell.getSource();
			double cutPageAxis = sCell.getTableRow().getPageSize();
			for (ExtendedCell xcell = sCell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				if (xcell == cell) {
					break;
				}
				cutPageAxis += xcell.getTableRow().getPageSize();
			}
			TableCellBox nextCell = forcedCellRemainder(prevCell, cutPageAxis, BreakMode.DEFAULT_BREAK_MODE,
					IPageBreakableBox.FLAGS_SPLIT);
			if (vertical) {
				prevCell.setWidth(cutPageAxis);
			} else {
				prevCell.setHeight(cutPageAxis);
			}
			this.restyleCell(nextCell);
			// Update directly instead of using addTableSourceCell.
			Cell source = new SourceCellImpl(nextCell, this);
			this.cells.set(i, source);
			ExtendedCell xcell = cell.getNextExtendedCell();
			int span = 1;
			if (xcell != null) {
				source.setNextExtendedCell(xcell);
				do {
					++span;
					xcell.setSourceCell(source);
					xcell = xcell.getNextExtendedCell();
				} while (xcell != null);
			}
			// Update the row height to the cell height divided by the number of rows.
			this.pageSize = Math.max(this.pageSize, nextCell.getPageExtent(this.tableParams.flow) / span);
		}
		for (int i = 0; i < this.cells.size(); ++i) {
			Cell cell = (Cell) this.cells.get(i);
			double rowSize = this.pageSize;
			for (ExtendedCell xcell = cell.getNextExtendedCell(); xcell != null; xcell = xcell.getNextExtendedCell()) {
				rowSize += xcell.getTableRow().getPageSize();
			}
			TableCellBox nextCell = cell.getCellBox();
			if (vertical) {
				nextCell.setWidth(rowSize);
			} else {
				nextCell.setHeight(rowSize);
			}
		}
	}

	private final void restyleCell(TableCellBox nextCell) {
		// FIXED boxes do not participate in relayout, so pageContextBuilder may be null.
		final BlockBuilder cellBindBuilder = new BlockBuilder(null, nextCell);
		nextCell.restyle(cellBindBuilder, net.zamasoft.foliojet.layout.fragment.OpenShape.CLOSED);
		cellBindBuilder.close();
	}
}
