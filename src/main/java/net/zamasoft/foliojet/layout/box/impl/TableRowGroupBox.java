package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import java.util.Deque;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.FramesStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.TableForceBreakMode;
import net.zamasoft.foliojet.layout.box.impl.TableRowBox.Cell;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InnerTableParams;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.TableRowGroupPos;

import net.zamasoft.foliojet.layout.draw.BackgroundBorderDrawable;
import net.zamasoft.foliojet.layout.draw.Drawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * Table row group implementation.
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: TableRowGroupBox.java 1622 2022-05-02 06:22:56Z miyabe $
 */
public class TableRowGroupBox extends AbstractInnerTableBox implements IPageBreakableBox {

	protected final TableRowGroupPos pos;
	private Drawer pendingDrawer = null;

	protected List<TableRowBox> rows = null;

	/** Dry run of automatic cutting (B-2b-6). {@code TableBox} deducts the table frame and header. */
	public boolean emissionCutDetermined(final double pageLimit) {
		return net.zamasoft.foliojet.layout.builder.impl.TableBuildPlanner.cutDetermined(this.rowPageSizes(),
				this.getPageSize(), pageLimit);
	}

	/** Returns each row's page-direction size in cut-traversal order. An empty array if there are no rows. */
	public double[] rowPageSizes() {
		if (this.rows == null) return new double[0];
		final double[] sizes = new double[this.rows.size()];
		for (int i = 0; i < sizes.length; ++i) sizes[i] = this.rows.get(i).getPageSize();
		return sizes;
	}

	/** The same plan as TableBox. Not set on complete tables. */
	IncompleteTablePlan incompletePlan;

	void updateIncompleteSize() {
		this.pageSize = this.incompletePlan.visibleGroupSize();
	}

	public TableRowGroupBox(final InnerTableParams params, final TableRowGroupPos pos) {
		super(params);
		this.pos = pos;
	}

	public final BoxType getType() {
		return BoxType.TABLE_ROW_GROUP;
	}

	public final Pos getPos() {
		return this.pos;
	}

	public final TableRowGroupPos getTableRowGroupPos() {
		return this.pos;
	}

	public final void addTableRow(TableRowBox row) {
		assert row != null;
		if (this.rows == null) {
			this.rows = new ArrayList<TableRowBox>();
		}
		this.rows.add(row);
		this.pageSize += row.getPageSize();
		if (row.getLineSize() > this.lineSize) {
			this.lineSize = row.getLineSize();
		}
	}

	@Override
	public void forEachAssignmentChild(final java.util.function.Consumer<IBox> action) {
		for (int i = 0; i < this.getTableRowCount(); ++i) {
			action.accept(this.getTableRow(i));
		}
	}

	public final int getTableRowCount() {
		if (this.rows == null) {
			return 0;
		}
		return this.rows.size();
	}

	public final TableRowBox getTableRow(int i) {
		if (this.rows == null) {
			throw new ArrayIndexOutOfBoundsException(i);
		}
		return (TableRowBox) this.rows.get(i);
	}

	/** Whether the group has a visible row background or cell content. False for anonymous empty rows. */
	@Override
	public boolean paintsAnything() {
		if (this.params.opacity == 0) {
			return false;
		}
		if (this.params.background.isVisible()) {
			return true;
		}
		for (int i = 0; i < this.getTableRowCount(); ++i) {
			if (this.getTableRow(i).paintsAnything()) {
				return true;
			}
		}
		return false;
	}

	public final void finishLayoutSelf(IFramedBox containerBox) {
	}

	public final void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
		// Push in reverse order (last row first) to preserve the original traversal order (first row first).
		for (int j = this.getTableRowCount() - 1; j >= 0; --j) {
			worklist.push(IBox.step(this.getTableRow(j), containerBox));
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
		if (this.rows == null) {
			return;
		}
		// First calculate row drawing coordinates without side effects, then push
		// in **reverse order** to preserve the original traversal order.
		// Centralized logical-position → physical-coordinate conversion in LayoutUtils.drawX/drawY
		// (2026-07-25, vertical-lr support; previously used handwritten RL-only formulas here).
		final int n = this.rows.size();
		final double[] xs = new double[n];
		final double[] ys = new double[n];
		double framePageStart = 0;
		for (int i = 0; i < n; ++i) {
			final TableRowBox row = (TableRowBox) this.rows.get(i);
			final double framePageEnd = framePageStart + row.getPageSize();
			xs[i] = LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, framePageStart, framePageEnd, 0);
			ys[i] = LayoutUtils.drawY(this.tableParams.flow, y, framePageStart, 0);
			framePageStart = framePageEnd;
		}
		for (int i = n - 1; i >= 0; --i) {
			TableRowBox row = (TableRowBox) this.rows.get(i);
			worklist.push(AbstractInnerTableBox.framesStep(row, pageBox, drawer, clip, transform, xs[i], ys[i]));
		}
	}

	public final void floats(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		if (this.params.opacity == 0) {
			return;
		}
		if (this.rows == null) {
			return;
		}
		double pageStart = 0;
		for (int i = 0; i < this.rows.size(); ++i) {
			final TableRowBox row = (TableRowBox) this.rows.get(i);
			final double pageEnd = pageStart + row.getPageSize();
			row.floats(pageBox, drawer, visitor, clip, transform, contextX, contextY,
					LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, pageStart, pageEnd, 0),
					LayoutUtils.drawY(this.tableParams.flow, y, pageStart, 0));
			pageStart = pageEnd;
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
				final Drawer newDrawer = new Drawer(params, transform);
				drawer.visitDrawer(newDrawer);
				drawer = newDrawer;
			}
		}
		if (this.rows == null) {
			return;
		}
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
		// First calculate row drawing coordinates without side effects, then push
		// in **reverse order** to preserve the original traversal order.
		final int n = this.rows.size();
		final double[] drawXs = new double[n];
		final double[] drawYs = new double[n];
		double pageStart = 0;
		for (int i = 0; i < n; ++i) {
			final TableRowBox row = (TableRowBox) this.rows.get(i);
			final double pageEnd = pageStart + row.getPageSize();
			drawXs[i] = LayoutUtils.drawX(this.tableParams.flow, x, this.pageSize, pageStart, pageEnd, 0);
			drawYs[i] = LayoutUtils.drawY(this.tableParams.flow, y, pageStart, 0);
			pageStart = pageEnd;
		}
		final Drawer fdrawer = drawer;
		worklist.push(w -> pageBox.endStruct(fdrawer, this.params.element, structCount, x, y));
		for (int i = n - 1; i >= 0; --i) {
			final TableRowBox row = (TableRowBox) this.rows.get(i);
			worklist.push(
					IBox.drawStep(row, pageBox, drawer, visitor, clip, transform, contextX, contextY, drawXs[i], drawYs[i]));
		}
	}

	public final void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist) {
		if (this.rows == null) {
			return;
		}
		// Push onto the stack in reverse order to preserve the original traversal order.
		for (int i = this.rows.size() - 1; i >= 0; --i) {
			IBox box = (IBox) this.rows.get(i);
			worklist.push(IBox.getTextStep(box, textBuff));
		}
	}

	public final SplitResult split(double pageLimit, BreakMode mode, final byte flags) {
		if (this.incompletePlan != null) {
			final SplitResult result = this.splitRows(pageLimit, mode, flags);
			if (result instanceof SplitResult.Split split) {
				final TableRowGroupBox next = (TableRowGroupBox) split.remainder();
				next.incompletePlan = this.incompletePlan.split(this, next,
						mode instanceof BreakMode.ForceBreakMode ? IncompleteTablePlan.SplitKind.FORCED
								: IncompleteTablePlan.SplitKind.AUTO);
				this.pageSize = this.incompletePlan.visibleGroupSize();
				next.pageSize = next.incompletePlan.visibleGroupSize();
			}
			return result;
		}
		return this.splitRows(pageLimit, mode, flags);
	}

	private SplitResult splitRows(double pageLimit, BreakMode mode, final byte flags) {
		assert (flags & IPageBreakableBox.FLAGS_LAST) == 0;
		if (mode instanceof BreakMode.ForceBreakMode) {
			// Forced page break
			TableForceBreakMode force = (TableForceBreakMode) mode;
			TableRowGroupBox nextRowGroup = this.splitTableRowGroup();
			int row = force.row;
			if (row != -1) {
				// Split vertically spanning cells when carrying them forward. Only the automatic page-break
				// path (prevRow.cutRowspanCells() below) called this; the forced page-break path did not.
				// As a result, rows moved to the next page still held extended cells that referenced
				// source cells on the previous page, so the spanning cells' backgrounds, borders,
				// and remaining content were not drawn on the next page
				// (found in an independent review, 2026-07-25).
				if (row + 1 < this.rows.size()) {
					final TableRowBox movedHead = (TableRowBox) this.rows.get(row + 1);
					movedHead.cutRowspanCells();
					// Also cut spanning cells without extension entries (paired with the automatic page-break path;
					// see TableRowBox.cutUnextendedRowspanCells).
					double above = 0;
					for (int k = row; k >= 0; --k) {
						final TableRowBox keptRow = (TableRowBox) this.rows.get(k);
						above += keptRow.getPageSize();
						keptRow.cutUnextendedRowspanCells(row + 1 - k, above, movedHead);
					}
				}
				for (int j = row + 1; j < this.rows.size(); ++j) {
					TableRowBox rowBox = (TableRowBox) this.rows.get(j);
					this.pageSize -= rowBox.getPageSize();
					nextRowGroup.addTableRow(rowBox);
				}
				for (int j = this.rows.size() - 1; j > row; --j) {
					this.rows.remove(j);
				}
			}
			return new SplitResult.Split(nextRowGroup);
		}

		if (LayoutUtils.compare(pageLimit, 0) < 0) {
			// If below the cut line
			return SplitResult.KEEP;
		}
		if (LayoutUtils.compare(pageLimit, this.getPageSize()) >= 0) {
			// No movement
			return SplitResult.KEEP;
		}
		InnerTableParams con = this.params;
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0
				&& (con.pageBreakInside == PageBreakMode.AVOID || LayoutUtils.compare(pageLimit, 0) < 0)) {
			// Move everything
			return SplitResult.MOVE;
		}

		// If empty
		if (this.rows == null || this.rows.isEmpty()) {
			return net.zamasoft.foliojet.layout.fragment.TableCutter.keepOrMoveAll(flags);
		}

		// Move the overflowing row
		TableRowGroupBox nextRowGroup = null;
		int i;
		boolean ignoreBreakAvoid = false;
		final double savePageLimit = pageLimit;
		// Check from top to bottom.
		for (i = 0; i < this.rows.size(); ++i) {
			final TableRowBox prevRow = (TableRowBox) this.rows.get(i);
			double prevRowSize = prevRow.getPageSize();
			if (i < this.rows.size() - 1 && LayoutUtils.compare(pageLimit, prevRowSize) > 0) {
				// Advance to the row intersected by the cut line.
				pageLimit -= prevRowSize;
				continue;
			}
			byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
			{
				// Row flags at the start of a page (decision extracted into pure logic in TableCutter).
				// Check whether the first row has spanning cells.
				boolean linkedToTop = false;
				if ((xflags & IPageBreakableBox.FLAGS_FIRST) != 0 && i > 0) {
					final TableRowBox topRow = (TableRowBox) this.rows.get(0);
					for (int j = 0; j < prevRow.getCellCount() && j < topRow.getCellCount(); ++j) {
						if (prevRow.getCell(j).getCellBox().getParams() == topRow.getCell(j).getCellBox()
								.getParams()) {
							linkedToTop = true;
							break;
						}
					}
				}
				xflags = net.zamasoft.foliojet.layout.fragment.TableCutter.firstRowFlags(xflags, i, linkedToTop);
			}
			final SplitResult rowResult = prevRow.split(pageLimit, mode, xflags);
			if (rowResult instanceof SplitResult.Keep) {
				if (!ignoreBreakAvoid && i == 0 && (flags & IPageBreakableBox.FLAGS_FIRST) != 0) {
					// At the start of a page, retry while ignoring page-break avoidance.
					ignoreBreakAvoid = true;
					pageLimit = savePageLimit;
					i = -1;
					continue;
				}
				pageLimit -= prevRowSize;
				continue;
			}
			// Once a split occurs, carry subsequent rows forward.
			if (rowResult instanceof SplitResult.Move) {
				if (i == 0) {
					// At the start, move everything.
					assert ((xflags & IPageBreakableBox.FLAGS_FIRST) == 0);
					return SplitResult.MOVE;
				}
				TableRowBox beforeRow = (TableRowBox) this.rows.get(i - 1);
				if (!ignoreBreakAvoid) {
					// Page-break avoidance between rows (decision extracted into pure logic in TableCutter).
					final boolean tableVertical = this.tableParams.flow.isVertical();
					final boolean[] cuttable = new boolean[beforeRow.getCellCount()];
					final boolean[] extended = new boolean[beforeRow.getCellCount()];
					final boolean[] flowMatch = new boolean[beforeRow.getCellCount()];
					for (int j = 0; j < beforeRow.getCellCount(); ++j) {
						final Cell cell = beforeRow.getCell(j);
						final BlockParams cellParams = cell.getCellBox().getBlockParams();
						flowMatch[j] = cellParams.flow.isVertical() == tableVertical;
						// Only an explicit author declaration of auto opts out of the avoid-equivalent behavior
						// between rowspan rows (manual 4550) (2026-08-27: the computed value AUTO became the default
						// after removal of the UA's default cell avoid).
						cuttable[j] = cellParams.pageBreakInside == PageBreakMode.AUTO
								&& cell.getCellBox().getTableCellPos().breakInsideDeclaredAuto && flowMatch[j];
						extended[j] = cell.getNextExtendedCell() != null;
					}
					if (net.zamasoft.foliojet.layout.fragment.TableCutter.rowBreakAvoid(i,
							(flags & IPageBreakableBox.FLAGS_FIRST) != 0, beforeRow.getTableRowPos().pageBreakAfter,
							prevRow.getTableRowPos().pageBreakBefore, cuttable, extended, flowMatch)) {
						// Row page-break avoidance
						if ((xflags & IPageBreakableBox.FLAGS_FIRST_ROW) != 0) {
							// For the first row on a page, retry while ignoring page-break avoidance.
							ignoreBreakAvoid = true;
							pageLimit = savePageLimit;
							i = -1;
							continue;
						}
						// Go back one row and cut the previous row at its end.
						pageLimit = beforeRow.getPageSize() - LayoutUtils.THRESHOLD * 2;
						i -= 2;
						continue;
					}
				}

				// Never break if writing modes differ (decision extracted into pure logic in TableCutter).
				{
					final boolean tableVertical = this.tableParams.flow.isVertical();
					final boolean[] flowMatch = new boolean[prevRow.getCellCount()];
					for (int j = 0; j < prevRow.getCellCount(); ++j) {
						flowMatch[j] = prevRow.getCell(j).getCellBox().getBlockParams().flow
								.isVertical() == tableVertical;
					}
					if (net.zamasoft.foliojet.layout.fragment.TableCutter.mixedFlowKeep(flowMatch)) {
						return SplitResult.KEEP;
					}
				}

				// Split vertically spanning cells when carrying them forward.
				prevRow.cutRowspanCells();
				// Spanning cells without extension entries (gaps among empty tr elements) have no entry
				// in the moving row, so the above misses them. Cut them directly from the retained rows
				// (see TableRowBox.cutUnextendedRowspanCells).
				{
					double above = 0;
					for (int k = i - 1; k >= 0; --k) {
						final TableRowBox keptRow = (TableRowBox) this.rows.get(k);
						above += keptRow.getPageSize();
						keptRow.cutUnextendedRowspanCells(i - k, above, prevRow);
					}
				}
				nextRowGroup = this.splitTableRowGroup();
				break;
			}
			nextRowGroup = this.splitTableRowGroup();
			prevRowSize -= prevRow.getPageSize();
			this.pageSize -= prevRowSize;
			nextRowGroup.addTableRow(net.zamasoft.foliojet.layout.fragment.TableCutter.requireSplitRemainder(rowResult,
					TableRowBox.class, "TableRowBox.split at the row-group cut line"));
			++i;
			break;
		}
		if (nextRowGroup == null) {
			return net.zamasoft.foliojet.layout.fragment.TableCutter.keepOrMoveAll(flags);
		}

		int remove = 0;
		for (int j = i; j < this.rows.size(); ++j) {
			TableRowBox prevRow = (TableRowBox) this.rows.get(j);
			this.pageSize -= prevRow.getPageSize();
			nextRowGroup.addTableRow(prevRow);
			++remove;
		}
		for (int j = 0; j < remove; ++j) {
			this.rows.remove(this.rows.size() - 1);
		}
		return new SplitResult.Split(nextRowGroup);
	}

	private TableRowGroupBox splitTableRowGroup() {
		// A split fragment is a continuation (no anchor; not replayed as a fresh box. P0).
		final TableRowGroupBox nextRowGroup = new TableRowGroupBox(this.params, this.pos);
		nextRowGroup.setTableParams(this.tableParams);
		nextRowGroup.lineSize = this.lineSize;
		return nextRowGroup;
	}
}
