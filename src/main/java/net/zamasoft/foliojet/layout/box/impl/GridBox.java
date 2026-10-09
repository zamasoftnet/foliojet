package net.zamasoft.foliojet.layout.box.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.PageAtomicBox;
import net.zamasoft.foliojet.layout.box.RowSplitBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.RowSplitContainer;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.GridParams;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * A grid container (Grid G0, 2026-07-31 --
 * consult-codex-2026-07-31-grid.txt §3).
 *
 * <p>
 * For pagination, it remains a regular block ({@code BoxType.BLOCK}/{@code PosType.FLOW}).
 * It inherits rescue, drawing, and frame handling from {@link FlowBlockBox}.
 * By default, {@link PageAtomicBox} prohibits structural page-axis splitting through its type
 * (if it does not fit, move it whole, then use visual rescue).
 * </p>
 *
 * <p>
 * <b>Row splitting (2026-08-10, G6)</b>: only when {@code GridBuilder.bind} sets row boundary
 * information does {@link #hasRowSplitLines()} return true, allowing {@link #split} to be
 * called through the special case in {@code PaginationContract} ({@link RowSplitBox}).
 * This is the second application, porting {@link FlexBox#split} (2026-08-07, Bug C) to grid;
 * the original is the table-row contract ({@code TableRowGroupBox}/{@code TableRowBox}):
 * "leave fitting rows alone, forcibly split all items in the boundary row at the same physical
 * cut line, and move later rows whole to the next fragment".
 * Configurations without a ledger (rowSpan&gt;1, explicit placement with non-row-major flow order,
 * vertical writing, or leading space from align-content) remain atomic as before:
 * move whole or fall back to visual rescue. Extra space from min-height is already distributed
 * to row heights by the default align-content:stretch, so row splitting handles it directly
 * (the root fix for the leading blank page on gigazine.net).
 * </p>
 *
 * <p>
 * At G0, content placement is single-column normal flow (= unchanged FlowBlockBox behavior;
 * template=none semantics). From G1 onward, {@code GridBuilder} handles track resolution
 * and item placement.
 * </p>
 */
public class GridBox extends FlowBlockBox implements PageAtomicBox, RowSplitBox {

	/**
	 * The page-axis ledger for one grid row (2026-08-10, G6 row splitting).
	 *
	 * <p>
	 * Unlike {@link FlexBox.Line}, this explicitly carries {@code start}: rowGap, empty rows
	 * (explicit-rows-sparse), and min-height distribution make row spacing nonuniform in grid.
	 * Carry the placed row start positions directly instead of using cumulative sums.
	 * </p>
	 *
	 * @param startFlow zero-based position of this row's first item in the container's flow list
	 * @param itemCount number of items in this row
	 * @param start     page-axis start position of the row (origin at the container's inner edge;
	 *                  {@code rowStarts[r]} in {@code GridBuilder.bind})
	 * @param extent    page-axis size of the row (including align-content:stretch distribution)
	 * @param itemsEnd  actual end of items in the row (relative to row start; maximum including
	 *                  align-self offsets). If {@code extent} exceeds this due to min-height
	 *                  distribution, etc., the difference is blank space. This ledger allows
	 *                  splitting that space without splitting items when the cut line falls in it
	 *                  (slack split).
	 * @param gapBefore the space between the end of the previous row and the start of this one as GridBuilder placed
	 *                  them (row-gap, empty tracks; 0 for the first row), kept through splits (2026-10-09, as
	 *                  {@link FlexBox.Line#gapBefore}). NaN until {@link #setGridRows} takes it from the geometry.
	 */
	public record Row(int startFlow, int itemCount, double start, double extent, double itemsEnd, double gapBefore) {
		/** A row as GridBuilder places it; {@link #setGridRows} takes its gap from the geometry. */
		public Row(final int startFlow, final int itemCount, final double start, final double extent,
				final double itemsEnd) {
			this(startFlow, itemCount, start, extent, itemsEnd, Double.NaN);
		}
	}

	/**
	 * Row boundaries (row-major order, matching the order of {@code addFlow} in
	 * {@code GridBuilder.bind}). null or empty means "not eligible for row splitting",
	 * so {@link #split} uses the previous atomic fallback.
	 */
	private List<Row> rows;

	/**
	 * The actual items belonging to each row in {@link #rows} (in the same order as
	 * the container's flow list). Used to {@code split} items within a row directly
	 * (for the same reason as {@code FlexBox.lineItems}).
	 */
	private List<GridItemBox> rowItems;

	/**
	 * Whether track placement ({@code GridBuilder.bind}) actually ran. If not, the contents
	 * are single-column normal flow (G0 degradation with TwoPass inactive), with no track
	 * placement to protect, so do not assert the atomic contract ({@link #isPageAtomicNow}).
	 */
	private boolean trackLayout;

	public GridBox(final GridParams params, final FlowPos pos) {
		super(params, pos);
		// Generic restyle reconstruction (sequential stacking) destroys grid item placement
		// (line-axis track positions + row starts), so use a container that restores it from anchors
		// (2026-08-10; a generalization of what FlexBox introduced for the same reason on 2026-08-08).
		// The same reconstruction path handles both row-split continuations and whole-grid moves
		// across pages when the grid has absolutely positioned children.
		this.container = new RowSplitContainer();
		this.container.setBox(this);
	}

	public final GridParams getGridParams() {
		return (GridParams) this.params;
	}

	/**
	 * Sets the used size received by a row subgrid from its parent exactly,
	 * without constraints from authored height/min/max-height or aspect-ratio (2026-09-03).
	 */
	public final void setExactUsedPageSize(final double pageSize) {
		this.restoreContentExtent(Math.max(0, pageSize));
		this.minPageAxis = 0;
		this.maxPageAxis = Double.MAX_VALUE;
		this.specifiedPageAxis = false;
	}

	protected GridBox(final GridParams params, final FlowPos pos,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame,
			final net.zamasoft.foliojet.layout.box.content.Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	/**
	 * Sets row boundary information (called once by {@code GridBuilder.bind} immediately
	 * after placement; also used to rebase continuation-fragment ledgers during splitting).
	 */
	public final void setGridRows(final List<Row> rows, final List<GridItemBox> rowItems) {
		List<Row> withGaps = rows;
		for (int i = 0; i < rows.size(); ++i) {
			final Row row = rows.get(i);
			if (Double.isNaN(row.gapBefore())) {
				if (withGaps == rows) {
					withGaps = new ArrayList<>(rows);
				}
				final double gap = i == 0 ? 0
						: Math.max(0, row.start() - (rows.get(i - 1).start() + rows.get(i - 1).extent()));
				withGaps.set(i, new Row(row.startFlow(), row.itemCount(), row.start(), row.extent(), row.itemsEnd(), gap));
			}
		}
		this.rows = withGaps;
		this.rowItems = rowItems;
	}

	/**
	 * Whether row splitting applies (2026-08-10). If false, {@code PaginationContract}
	 * uses the previous PageAtomicBox path (move whole/visual rescue).
	 */
	public final boolean hasRowSplitLines() {
		return this.rows != null && !this.rows.isEmpty();
	}

	@Override
	public final double[][] rowLedgerSnapshot() {
		if (this.rows == null || this.rows.isEmpty()) {
			return null;
		}
		final double[][] result = new double[this.rows.size()][];
		for (int i = 0; i < this.rows.size(); ++i) {
			final Row row = this.rows.get(i);
			result[i] = new double[] { row.startFlow(), row.itemCount(), row.start(), row.extent(), row.gapBefore() };
		}
		return result;
	}

	@Override
	public final void syncRowStarts(final double[] starts) {
		for (int i = 0; i < this.rows.size(); ++i) {
			final Row old = this.rows.get(i);
			if (old.start() != starts[i]) {
				this.rows.set(i, new Row(old.startFlow(), old.itemCount(), starts[i], old.extent(),
						old.itemsEnd(), old.gapBefore()));
			}
		}
	}

	@Override
	public final void syncRowGeometry(final double[] starts, final double[] extents) {
		for (int i = 0; i < this.rows.size(); ++i) {
			final Row old = this.rows.get(i);
			if (old.start() != starts[i] || old.extent() != extents[i]) {
				this.rows.set(i, new Row(old.startFlow(), old.itemCount(), starts[i], extents[i], old.itemsEnd(),
						old.gapBefore()));
			}
		}
	}

	/** Records that track placement ran (called by {@code GridBuilder.bind}). */
	public final void markTrackLayout() {
		this.trackLayout = true;
	}

	/**
	 * Only grids where track placement actually ran assert the atomic contract
	 * (2026-08-10; the design decision is centralized in {@link PageAtomicBox#isPageAtomicNow}).
	 */
	@Override
	public final boolean isPageAtomicNow() {
		return this.trackLayout;
	}

	/**
	 * Splits grid rows along the page axis (2026-08-10, G6).
	 *
	 * <p>
	 * The same form as {@link FlexBox#split} ("leave fitting rows alone, forcibly split all items
	 * in the boundary row at the same physical cut line, and move later rows whole to the next fragment").
	 * Only boundary detection differs: compare {@link Row#start} directly instead of cumulative sums,
	 * because rowGap, empty rows, and align-content distribution make row spacing nonuniform.
	 * If the cut line falls after the last row (in trailing space), an empty continuation
	 * fragment carries only that space.
	 * </p>
	 */
	public final SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		if (!this.hasRowSplitLines()) {
			if (!this.isPageAtomicNow()) {
				// G0 degradation (no track placement) contains single-column normal flow,
				// so delegate to normal-block structural splitting (2026-08-19; same as FlexBox's
				// F0 delegation). Without this, a closed box with "no ledger + non-atomic" is KEEP
				// at the fragment start, and content beyond the page bottom is not drawn and is lost
				// (observed in the trailing 1,636 pt G0 grid in stripe-docs).
				return super.split(pageLimit, mode, flags);
			}
			// Defensive fallback on the atomic side (normally the PaginationContract special case
			// prevents this method from being called at all without row boundaries).
			return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
		}
		final WritingMode flow = this.getBlockParams().flow;
		pageLimit -= this.frame.getFramePageStart(flow);
		if (LayoutUtils.compare(pageLimit, 0) < 0) {
			return SplitResult.MOVE;
		}
		if (LayoutUtils.compare(pageLimit, this.getPageExtent(flow)) >= 0) {
			return SplitResult.KEEP;
		}
		if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0
				&& this.getBlockParams().pageBreakInside == PageBreakMode.AVOID) {
			return SplitResult.MOVE;
		}

		// The first row that crosses the cut line (crosses) or starts at or beyond it.
		int boundary = -1;
		boolean crosses = false;
		for (int ri = 0; ri < this.rows.size(); ++ri) {
			final Row row = this.rows.get(ri);
			if (LayoutUtils.compare(pageLimit, row.start()) <= 0) {
				boundary = ri;
				break;
			}
			if (LayoutUtils.compare(pageLimit, row.start() + this.rowExtent(row, flow)) < 0) {
				boundary = ri;
				crosses = true;
				break;
			}
		}
		if (boundary < 0) {
			// All rows fit before the cut line (which lies in trailing space): an empty
			// continuation fragment carries the remaining space.
			final RowSplitContainer cont = new RowSplitContainer();
			cont.anchorCurrent(0);
			final AbstractContainerBox continuation = this.splitPage(cont, pageLimit, false);
			if (continuation instanceof GridBox contGrid) {
				contGrid.markTrackLayout();
			}
			return new SplitResult.Split(continuation);
		}

		// Only the items of the first row are at the page start (2026-10-09, as TableRowGroupBox clears it after
		// row 0): a nested grid in a later row that took FLAGS_FIRST kept its first row across the page bottom
		// instead of moving (primer-css's prop tables).
		final int firstFlag = boundary == 0 ? IPageBreakableBox.FLAGS_FIRST : 0;
		final byte xflags = (byte) (flags & (firstFlag | IPageBreakableBox.FLAGS_SPLIT));
		final Row boundaryRow = this.rows.get(boundary);
		if (crosses) {
			final double remaining = pageLimit - boundaryRow.start();
			final GridItemBox[] boundaryItems = new GridItemBox[boundaryRow.itemCount()];
			for (int k = 0; k < boundaryItems.length; ++k) {
				boundaryItems[k] = this.rowItems.get(boundaryRow.startFlow() + k);
			}
			// Item height before splitting (for the remainder lower bound below). Measured before the probe, which
			// already splits the items it can (2026-10-09, as FlexBox.split): measured after it, it was the kept height.
			final double[] preExtents = new double[boundaryItems.length];
			final double[] contentExtents = new double[boundaryItems.length];
			double contentExtent = 0;
			for (int k = 0; k < boundaryItems.length; ++k) {
				preExtents[k] = boundaryItems[k].getPageExtent(flow);
				contentExtents[k] = RowSplitBox.contentPageExtent(boundaryItems[k], preExtents[k], flow);
				contentExtent = Math.max(contentExtent, contentExtents[k]);
			}
			final boolean followsContent = LayoutUtils.compare(this.rowExtent(boundaryRow, flow), contentExtent) <= 0;
			final SplitResult[] probed = new SplitResult[boundaryItems.length];
			boolean anySplit = (flags & IPageBreakableBox.FLAGS_SPLIT) != 0;
			// **Check slack before attempting to split** (2026-08-29, G7). Items now stretch to row height,
			// so checking later would let even undecorated items return Split first,
			// making the path that splits min-height-derived blank space unreachable.
			final boolean slack = !anySplit
					&& LayoutUtils.compare(this.itemsEnd(boundaryRow, boundaryItems, flow), remaining) <= 0;
			if (!anySplit && !slack) {
				for (int k = 0; k < boundaryItems.length; ++k) {
					final SplitResult r = boundaryItems[k].split(remaining, mode, xflags);
					probed[k] = r;
					if (r instanceof SplitResult.Split) {
						anySplit = true;
					}
				}
			}
			if (slack) {
				// All items in the boundary row fit before the cut line; only trailing row space
				// (such as min-height-derived align-content:stretch distribution) overflows.
				// Split within the blank space without splitting items (slack split).
				// This is the root fix for gigazine.net's case where a grid with min-height:800px
				// sank whole to the next page along with its small contents.
				final RowSplitContainer cont = new RowSplitContainer();
				final List<Row> contRows = new ArrayList<>();
				final List<GridItemBox> contItems = new ArrayList<>();
				if (boundary + 1 < this.rows.size()) {
					final Row nextRow = this.rows.get(boundary + 1);
					((Container) this.container).migrateFlowsFrom(nextRow.startFlow(), cont, pageLimit);
					int shift = 0;
					for (int j = boundary + 1; j < this.rows.size(); ++j) {
						final Row old = this.rows.get(j);
						contRows.add(new Row(shift, old.itemCount(), old.start() - pageLimit, old.extent(),
								old.itemsEnd(), j == boundary + 1 ? 0 : old.gapBefore()));
						shift += old.itemCount();
					}
					contItems.addAll(this.rowItems.subList(nextRow.startFlow(), this.rowItems.size()));
				}
				cont.anchorCurrent(0);
				final AbstractContainerBox continuation = this.splitPage(cont, pageLimit, false);
				if (continuation instanceof GridBox contGrid) {
					contGrid.markTrackLayout();
					if (!contRows.isEmpty()) {
						contGrid.setGridRows(contRows, contItems);
					}
				}
				this.keepHeadRows(boundary + 1);
				return new SplitResult.Split(continuation);
			}
			if (anySplit) {
				// Boundary row: forcibly split even items that were not split (Keep decision).
				final byte forcedFlags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
				final GridItemBox[] remainders = new GridItemBox[boundaryItems.length];
				for (int k = 0; k < boundaryItems.length; ++k) {
					final SplitResult r = probed[k] instanceof SplitResult.Split ? probed[k]
							: boundaryItems[k].split(remaining, mode, forcedFlags);
					if (!(r instanceof SplitResult.Split(final IPageBreakableBox remainder))
							|| !(remainder instanceof GridItemBox typedRemainder)) {
						throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
								"GridItemBox.split with FLAGS_SPLIT must return Split(GridItemBox) but was " + r);
					}
					remainders[k] = typedRemainder;
				}
				// **Actual extent consumed by the kept side** (2026-08-19). Splitting moves indivisible content
				// whole to the remainder, so the kept fragment's actual content can end before the cut line
				// remaining (observed: 50 pt in smolcss). Previously, transfers and kept-fragment dimensions
				// used the cut line as their basis, so the remainder's actual content overlapped the next row
				// whose start was fixed at "old geometry - cut line". Use the actual painted end
				// (paintedPageExtent; boxes with visible frames use the full box, conservatively falling
				// on the cut line as before) to close the kept fragment at its actual consumed extent
				// and subtract the same amount from the continuation row starts.
				double consumed = 0;
				for (int k = 0; k < boundaryItems.length; ++k) {
					consumed = Math.max(consumed, boundaryItems[k].paintedPageExtent(flow));
				}
				consumed = Math.min(consumed, remaining);
				final double keptEnd = boundaryRow.start() + consumed;
				final RowSplitContainer cont = new RowSplitContainer();
				double newRowExtent = 0;
				for (int k = 0; k < remainders.length; ++k) {
					remainders[k].setGridLineOffset(boundaryItems[k].getGridLineOffset());
					cont.addFlow(remainders[k], 0);
					newRowExtent = Math.max(newRowExtent, remainders[k].getPageExtent(flow));
				}
				// **Keep the continuation row height at least as large as the remainder** (2026-08-17).
				// The remainder is not laid out at this point (before anchor restoration),
				// so getPageExtent may return nearly 0. Recording that in the ledger makes the next
				// split's boundary search misread "all rows fit before the cut line" and return
				// an **empty continuation fragment**. The remainder (tens of thousands of points in documents
				// where one item spans multiple pages) stays in the leading fragment and draws off the paper
				// (observed in an eLife paper: 95 pages of content piled onto page 3).
				// Geometrically, the remainder cannot be smaller than "original row height -
				// extent consumed on this page".
				// A row its items' content alone set (followsContent) is what is left of that content after the kept
				// side (2026-10-09, as FlexBox.split; gd2 fell 4pt behind a page with the bounds below).
				if (followsContent) {
					for (int k = 0; k < boundaryItems.length; ++k) {
						newRowExtent = Math.max(newRowExtent,
								contentExtents[k] - FlexBox.keptContentEnd(boundaryItems[k], consumed, flow));
					}
				} else {
					newRowExtent = Math.max(newRowExtent, this.rowExtent(boundaryRow, flow) - consumed);
				}
				// Also, per item, it cannot be smaller than "item height before splitting - measured height
				// of the kept side" (2026-08-18). Splitting moves indivisible content (lines, atomic blocks)
				// whole to the remainder, so the kept side may consume less than the available remaining extent.
				// The above lower bound (extent-remaining) alone would under-record the remainder,
				// making later rows overlap the continuation row's actual content on the next page
				// (observed in smolcss: the kept side sent an atomic demo box onward and ended ~65 pt early,
				// so the next article's body text overlapped the previous article's footer).
				for (int k = 0; k < boundaryItems.length && !followsContent; ++k) {
					newRowExtent = Math.max(newRowExtent, preExtents[k] - consumed);
				}
				final List<GridItemBox> contItems = new ArrayList<>(
						remainders.length + this.rowItems.size() - (boundaryRow.startFlow() + boundaryItems.length));
				for (final GridItemBox rem : remainders) {
					contItems.add(rem);
				}
				final List<Row> contRows = new ArrayList<>();
				// The remainders' painted end is not known before they are laid out: NaN has the next split
				// measure them (itemsEnd). The lower bound of the row height stood in for it until 2026-10-09.
				contRows.add(new Row(0, boundaryItems.length, 0, newRowExtent, Double.NaN, 0));
				if (boundary + 1 < this.rows.size()) {
					final Row nextRow = this.rows.get(boundary + 1);
					// The later rows keep their gap after the remainder row (2026-10-09, as FlexBox.split)
					final double gap = Double.isNaN(nextRow.gapBefore()) ? 0 : nextRow.gapBefore();
					final double down = newRowExtent + gap - (nextRow.start() - keptEnd);
					((Container) this.container).migrateFlowsFrom(nextRow.startFlow(), cont, keptEnd - down);
					int shift = boundaryItems.length;
					for (int j = boundary + 1; j < this.rows.size(); ++j) {
						final Row old = this.rows.get(j);
						contRows.add(new Row(shift, old.itemCount(), old.start() - keptEnd + down, old.extent(),
								old.itemsEnd(), old.gapBefore()));
						shift += old.itemCount();
					}
					contItems.addAll(this.rowItems.subList(nextRow.startFlow(), this.rowItems.size()));
				}
				cont.anchorCurrent(remainders.length);
				final AbstractContainerBox continuation = this.splitPage(cont, keptEnd, false);
				if (continuation instanceof GridBox contGrid) {
					contGrid.markTrackLayout();
					contGrid.setGridRows(contRows, contItems);
				}
				this.keepHeadRows(boundary + 1);
				return new SplitResult.Split(continuation);
			}
			// No item in the boundary row can split: treat the entire row as the boundary and carry it whole.
		}
		if (boundary == 0) {
			if ((flags & IPageBreakableBox.FLAGS_FIRST) == 0) {
				return SplitResult.MOVE;
			}
			if (crosses && this.rows.size() > 1) {
				// The first row on a fresh page neither fits nor splits (an unbreakable item taller than the page,
				// or a remainder stretched to the lower bound of its row): keep that row alone and carry the later
				// rows on (2026-10-09). Keeping the whole grid put every later row below the paper and stopped
				// pagination (openprops: 46 pages became 7, with 5000 words off the page).
				return this.carryRowsFrom(1, boundaryRow.start() + this.rowExtent(boundaryRow, flow));
			}
			return SplitResult.KEEP;
		}
		return this.carryRowsFrom(boundary, boundaryRow.start());
	}

	/** Keeps the rows before {@code firstRow} and carries the rest whole to a continuation fragment. */
	private SplitResult carryRowsFrom(final int firstRow, final double keptExtent) {
		final Row first = this.rows.get(firstRow);
		final RowSplitContainer cont = new RowSplitContainer();
		((Container) this.container).migrateFlowsFrom(first.startFlow(), cont, keptExtent);
		cont.anchorCurrent(0);
		final AbstractContainerBox continuation = this.splitPage(cont, keptExtent, false);
		if (continuation instanceof GridBox contGrid) {
			contGrid.markTrackLayout();
			contGrid.setGridRows(shiftRows(this.rows, firstRow, keptExtent),
					new ArrayList<>(this.rowItems.subList(first.startFlow(), this.rowItems.size())));
		}
		this.keepHeadRows(firstRow);
		return new SplitResult.Split(continuation);
	}

	/**
	 * The extent of a row: the ledger's, or for a row of remainders (itemsEnd NaN, recorded before they were laid out
	 * with only a lower bound) at least what its items take now that they are laid out (2026-10-09). With the ledger's
	 * lower bound alone, a remainder that grew past the page bottom looked as if it fitted: the split went after it,
	 * and what followed it in the item was placed below the paper (smolcss: a card grid's last row and the footer
	 * after it at y=807 on a 770pt page).
	 */
	private double rowExtent(final Row row, final WritingMode flow) {
		double extent = row.extent();
		if (Double.isNaN(row.itemsEnd())) {
			for (int k = 0; k < row.itemCount(); ++k) {
				extent = Math.max(extent, this.rowItems.get(row.startFlow() + k).getPageExtent(flow));
			}
		}
		return extent;
	}

	/**
	 * The end the items of a row paint, relative to the row start: the ledger's value, or for a row of remainders
	 * (NaN, recorded before they were laid out) the largest painted extent of its items, which sit at the row start
	 * (2026-10-09).
	 */
	private double itemsEnd(final Row row, final GridItemBox[] items, final WritingMode flow) {
		if (!Double.isNaN(row.itemsEnd())) {
			return row.itemsEnd();
		}
		double end = 0;
		for (final GridItemBox item : items) {
			end = Math.max(end, item.paintedPageExtent(flow));
		}
		return end;
	}

	/**
	 * Rebuilds the leading fragment's records after splitting to include only kept rows and items (2026-09-17).
	 * For the same reason as the corresponding {@link FlexBox} operation: if the same leading fragment
	 * is split again while still referring to transferred rows, it splits transferred items again
	 * and duplicates content.
	 */
	private void keepHeadRows(final int rowCount) {
		final int rows = Math.min(rowCount, this.rows.size());
		final int items = rows == 0 ? 0
				: Math.min(this.rowItems.size(), this.rows.get(rows - 1).startFlow() + this.rows.get(rows - 1).itemCount());
		this.rows = new ArrayList<>(this.rows.subList(0, rows));
		this.rowItems = new ArrayList<>(this.rowItems.subList(0, items));
	}

	/**
	 * Returns a list of rows from {@code fromIndex} onward in {@code rows}, with flow positions
	 * and row starts rebased to 0 (used when {@link #split} carries whole rows to the next fragment).
	 */
	private static List<Row> shiftRows(final List<Row> rows, final int fromIndex, final double keptExtent) {
		final List<Row> result = new ArrayList<>(rows.size() - fromIndex);
		final int flowShift = rows.get(fromIndex).startFlow();
		for (int j = fromIndex; j < rows.size(); ++j) {
			final Row old = rows.get(j);
			result.add(new Row(old.startFlow() - flowShift, old.itemCount(), old.start() - keptExtent, old.extent(),
					old.itemsEnd(), j == fromIndex ? 0 : old.gapBefore()));
		}
		return result;
	}

	/**
	 * <b>Creates continuation fragments with the same type</b> (2026-08-05).
	 *
	 * <p>
	 * {@link FlowBlockBox#fragmentRecipe()} directly uses {@code new FlowBlockBox(...)},
	 * so <b>without an override, continuation fragments become plain blocks</b>.
	 * {@code ContinuationValidator} detects the type mismatch and <b>stops the entire conversion</b>.
	 * This caused {@code ecma262} in real-world corpus wave 23 (the ECMAScript specification,
	 * a 7.5 MB single page) to fail after producing 2.9 MB of output.
	 * Only {@code MulticolumnBlockBox} had an override.
	 * </p>
	 */
	@Override
	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final net.zamasoft.foliojet.layout.box.params.GridParams params = this.getGridParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new GridBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}
}
