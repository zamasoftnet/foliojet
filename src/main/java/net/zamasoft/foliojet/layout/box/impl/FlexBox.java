package net.zamasoft.foliojet.layout.box.impl;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.PageAtomicBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * A flex container (Flex F0b, 2026-08-02 --
 * consult-codex-2026-08-02-flexbox.txt; same structure as {@link GridBox}).
 *
 * <p>
 * For pagination, it remains a regular block ({@code BoxType.BLOCK}/{@code PosType.FLOW}).
 * It inherits rescue, drawing, and frame handling from {@link FlowBlockBox}.
 * By default, {@link PageAtomicBox} prohibits structural page-axis splitting through its type
 * (css-flexbox-1 §10 fragmentation is informative, so leaving it unsupported is valid.
 * If it does not fit, move it whole, then use visual rescue).
 * </p>
 *
 * <p>
 * <b>Row splitting (2026-08-07, Bug C)</b>: only when {@link #setFlexLines} sets line boundary
 * information does {@link #hasRowSplitLines()} return true, allowing {@link #split} to be called
 * through the special case in {@code PaginationContract} (see that file).
 * This applies a simplified version, without rowspan, of the table-row contract:
 * "forcibly split at the same physical cut line, and move items that cannot align with it
 * whole to the next fragment" (see {@code TableRowGroupBox}/{@code TableRowBox}).
 * Boundary information comes from row-direction lines ({@code FlexBuilder.placeRow}),
 * and <b>also synthesizes one row per item for a single column in the column direction</b>
 * (2026-08-18: app-shell body column flex containers (vertical flex with min-height:100vh)
 * made entire documents atomic in 37% of real documents, slicing lines at rescue-split band boundaries.
 * This applies to single-column {@code placeColumn} and the F4c degraded path {@code bindFallback}.
 * Introducing Line.start on 2026-08-19 also supports main-axis alignment with leading>0).
 * Without line boundaries it remains a PageAtomicBox, as before: move whole or fall back to visual rescue.
 * </p>
 *
 * <p>
 * At F0, content placement is single-column normal flow (= unchanged FlowBlockBox behavior).
 * From F1 onward, {@code FlexBuilder} handles line breaking, flexing, and alignment.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexBox extends FlowBlockBox implements PageAtomicBox, net.zamasoft.foliojet.layout.box.RowSplitBox {

	/**
	 * The page-axis ledger for one flex line (2026-08-07, Bug C).
	 *
	 * @param startFlow zero-based position of this line's first item in the container's flow list
	 *                  (= cumulative itemCount of preceding lines)
	 * @param itemCount number of items in this line
	 * @param start     page-axis start position of the line (origin at the container's inner edge;
	 *                  the same coordinates as the item's addFlow position). Added on 2026-08-19:
	 *                  boundary searches using cumulative sums drifted from actual drawing positions
	 *                  across generations because forced-split remainder row heights were estimates
	 *                  (lower bounds). Repeated page splitting made whole lines overlap upward
	 *                  (observed as overlapping labels in bootstrap-icons).
	 *                  The same design as {@link GridBox.Row#start}.
	 * @param pageSize  finalized page-axis size of this line (cross axis; vertical in row-direction),
	 *                  the same value as {@code lineExtents[li]} in {@code FlexBuilder.placeRow}
	 */
	public record Line(int startFlow, int itemCount, double start, double pageSize) {
	}

	/**
	 * Line boundaries (visual order = cross-axis order, matching the order of
	 * {@code addFlow} in {@code FlexBuilder.placeRow}). null or empty means "not eligible
	 * for row splitting", so {@link #split} uses the previous atomic fallback.
	 */
	private List<Line> lines;

	/**
	 * The actual items belonging to each line in {@link #lines} (in the same order
	 * as the container's flow list). Used to {@code split} items within a line directly:
	 * the container only provides flow replacement (transfer), with no generic API to
	 * retrieve individual items through it, so keeping a parallel flex-specific list here is simpler.
	 */
	private List<FlexItemBox> lineItems;

	/**
	 * Whether {@link #lines} were carried over by {@link #split} to this continuation (2026-10-09). Their sizes are then
	 * lower bounds set before the items were laid out again, which the items can outgrow; the lines FlexBuilder placed
	 * are the layout itself.
	 */
	private boolean carriedLines;

	/**
	 * Whether flex placement (placeRow/placeColumn/bindFallback in {@code FlexBuilder})
	 * actually ran. If not, the contents are single-column normal flow (F0 degradation;
	 * typically a column+auto-height app shell), with no flex placement to protect.
	 * Do not assert the atomic contract ({@link #isPageAtomicNow}; the same design decision
	 * as trackLayout in {@link GridBox#isPageAtomicNow}).
	 * On 2026-08-18, 37% of real documents, including bbc-japan, were entirely atomic,
	 * causing lines to be sliced at rescue-split band boundaries.
	 */
	private boolean flexLayout;

	public FlexBox(final FlexParams params, final FlowPos pos) {
		super(params, pos);
		// Generic restyle reconstruction (sequential stacking) destroys flex item placement
		// (main-axis alignment), so always use a container that restores it by self-anchoring (2026-08-08).
		// Previously, RowSplitContainer protected only split continuation fragments. When a flex line
		// with absolutely positioned children moved whole across pages (the containsAbsolute gate
		// in source replay falls back to restyle), its items became staggered like steps
		// -- the yahoo.co.jp weather module.
		this.container = new net.zamasoft.foliojet.layout.box.content.RowSplitContainer();
		this.container.setBox(this);
	}

	/**
	 * Whether this container's main (page-axis) size is a definite length: specified, or fixed by the flex basis it got
	 * as an item of a column laid out in normal flow ({@code flex: 0 0 100pt; min-height: 0}; 2026-10-08, codex review:
	 * its own {@code flex: 1} items shared the 100pt in Chrome but stacked at their content size here).
	 */
	public final boolean hasDefinitePageSize() {
		return this.size.getPageType(this.getFlexParams().flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
	}

	public final FlexParams getFlexParams() {
		return (FlexParams) this.params;
	}

	protected FlexBox(final FlexParams params, final FlowPos pos,
			final net.zamasoft.foliojet.layout.box.params.Dimension size,
			final net.zamasoft.foliojet.layout.box.params.Dimension minSize,
			final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame,
			final net.zamasoft.foliojet.layout.box.content.Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	/**
	 * Sets line boundary information (called once by {@code FlexBuilder.placeRow}
	 * immediately after placement).
	 */
	public final void setFlexLines(final List<Line> lines, final List<FlexItemBox> lineItems) {
		this.lines = lines;
		this.lineItems = lineItems;
	}

	/**
	 * Whether row splitting applies (2026-08-07). If false, {@code PaginationContract}
	 * uses the previous PageAtomicBox path (move whole/visual rescue).
	 */
	public final boolean hasRowSplitLines() {
		return this.lines != null && !this.lines.isEmpty();
	}

	@Override
	public final double[][] rowLedgerSnapshot() {
		if (this.lines == null || this.lines.isEmpty()) {
			return null;
		}
		final double[][] result = new double[this.lines.size()][];
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line line = this.lines.get(i);
			result[i] = new double[] { line.startFlow(), line.itemCount(), line.start(), line.pageSize() };
		}
		return result;
	}

	@Override
	public final void syncRowStarts(final double[] starts) {
		for (int i = 0; i < this.lines.size(); ++i) {
			final Line old = this.lines.get(i);
			if (old.start() != starts[i]) {
				this.lines.set(i, new Line(old.startFlow(), old.itemCount(), starts[i], old.pageSize()));
			}
		}
	}

	/** Records that flex placement ran (called by each placement path in {@code FlexBuilder}). */
	public final void markFlexLayout() {
		this.flexLayout = true;
	}

	/**
	 * Only containers where flex placement actually ran assert the atomic contract (2026-08-18;
	 * same form as {@link GridBox#isPageAtomicNow}). Single-column normal flow from F0 degradation
	 * paginates by line as a normal block.
	 */
	@Override
	public final boolean isPageAtomicNow() {
		return this.flexLayout;
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
		final FlexParams params = this.getFlexParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new FlexBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}

	/**
	 * Splits flex lines along the page axis (2026-08-07, Bug C).
	 *
	 * <p>
	 * The same form as {@code TableRowGroupBox.split}/{@code TableRowBox.split}
	 * ("leave fitting rows alone, forcibly split all items in the boundary row at the same
	 * physical line, and move later rows whole to the next fragment"), but much simpler
	 * without rowspan (cell spanning). Simply retrieve boundary-row items directly from
	 * {@link #lineItems} and split them; no need to backtrack and recalculate the cut line
	 * as with spanning cells.
	 * </p>
	 *
	 * <p>
	 * Cross-axis positions (which row an item belongs to) are recorded in {@code Container.Flow},
	 * not the items themselves, so leave items in fitting rows untouched in the original container.
	 * {@code fragmentRecipe} creates new continuation items for forced splits, so they do not
	 * inherit main-axis positions ({@code baseOffsetX}/{@code Y}). Read the original value with
	 * {@link FlexItemBox#getFlexLineOffset} and explicitly restore it with
	 * {@link FlexItemBox#setFlexLineOffset}.
	 * </p>
	 */
	public final SplitResult split(double pageLimit, final BreakMode mode, final byte flags) {
		// Unlike TableRowBox.split, this shares the generic BoxType.BLOCK path
		// (case BLOCK in FlowContainer.splitPageAxis) with normal blocks,
		// so calls with FLAGS_LAST can actually occur (2026-08-07; observed in multi-column layout
		// in RandomDocumentFuzzTest: an AssertionError stopped the entire conversion).
		// Table rows never see FLAGS_LAST because TableRowGroupBox removes it before calling:
		// that is the caller's contract, not a restriction of split itself.
		// The xflags construction below already excludes FLAGS_LAST,
		// so simply removing the check handles this safely.
		if (!this.hasRowSplitLines()) {
			if (!this.isPageAtomicNow()) {
				// F0 degradation (no flex placement) contains single-column normal flow,
				// so delegate to normal-block structural splitting (2026-08-18). Without this,
				// a closed box with "no ledger + non-atomic" is KEEP at the fragment start,
				// and content beyond the page bottom is not drawn and is lost (observed in stripe-docs:
				// a 3687 pt inner flex was placed whole on page 2, and everything after that disappeared).
				return super.split(pageLimit, mode, flags);
			}
			// Defensive fallback on the atomic side (normally the PaginationContract special case
			// prevents this method from being called at all without line boundaries).
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

		final double totalPageLimit = pageLimit;
		// The row crossing the cut line, or the first row starting at or beyond it (changed on 2026-08-19
		// from cumulative sums to direct Line.start comparison, as in GridBox.split;
		// see the Line Javadoc for the reason).
		int boundary = -1;
		boolean crosses = false;
		for (int li = 0; li < this.lines.size(); ++li) {
			final Line line = this.lines.get(li);
			if (LayoutUtils.compare(pageLimit, line.start()) <= 0) {
				boundary = li;
				break;
			}
			if (LayoutUtils.compare(pageLimit, line.start() + line.pageSize()) < 0) {
				boundary = li;
				crosses = true;
				break;
			}
		}
		if (boundary < 0 && this.carriedLines) {
			// A line of a continuation whose item, as laid out again, paints past the cut line crosses it all the same
			// (2026-10-09). The carried line sizes are lower bounds: the remainder's is the line less what the kept side
			// painted, and a box with a background broken inside paints down to the cut line though its last line ends
			// above it, so the line falls behind its item a little on every page (qiita-article: 96pt on page 33 of a
			// 24600pt row flex). Kept whole, the fragment ran past the page bottom and every page break repeated at the
			// same place until the livelock guard let the rest of the document overflow. An item a whole page taller
			// than its line was laid out again at another size, which splitting does not mend: it only clipped the item
			// and added an empty page (materialui).
			for (int li = 0; li < this.lines.size() && boundary < 0; ++li) {
				final Line line = this.lines.get(li);
				for (int k = 0; k < line.itemCount(); ++k) {
					final double painted = this.lineItems.get(line.startFlow() + k).paintedPageExtent(flow);
					if (LayoutUtils.compare(pageLimit, line.start() + painted) < 0
							&& LayoutUtils.compare(painted - line.pageSize(), pageLimit) <= 0) {
						boundary = li;
						crosses = true;
						break;
					}
				}
			}
		}
		if (boundary < 0) {
			// All lines fit before the cut line (which lies in trailing space).
			// Trim empty space here (KEEP) instead of carrying it to the next page: carrying space
			// created by reapplying min-height, etc. in continuation fragments only added blank pages
			// with no content (observed in k8s-docs, etc.). This deliberately differs from
			// the corresponding GridBox branch (an empty continuation fragment carries the space).
			// Grid's design carries min-height space as eligible for row splitting
			// (the root fix for gigazine) and protects its regressions, so do not make them match.
			return SplitResult.KEEP;
		}

		final byte xflags = (byte) (flags & (IPageBreakableBox.FLAGS_FIRST | IPageBreakableBox.FLAGS_SPLIT));
		final Line boundaryLine = this.lines.get(boundary);
		if (!crosses) {
			// The boundary row starts at or beyond the cut line: carry it whole without splitting.
			if (boundary == 0) {
				return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
			}
			final double keptExtent = boundaryLine.start();
			final net.zamasoft.foliojet.layout.box.content.RowSplitContainer cont = new net.zamasoft.foliojet.layout.box.content.RowSplitContainer();
			((Container) this.container).migrateFlowsFrom(boundaryLine.startFlow(), cont, keptExtent);
			cont.anchorCurrent(0);
			final AbstractContainerBox continuation = this.splitPage(cont, keptExtent, false);
			if (continuation instanceof FlexBox contFlex) {
				contFlex.markFlexLayout();
				contFlex.carriedLines = true;
				contFlex.setFlexLines(shiftLines(this.lines, boundary, keptExtent),
						new ArrayList<>(this.lineItems.subList(boundaryLine.startFlow(), this.lineItems.size())));
			}
			this.keepHeadLines(boundary, boundaryLine.startFlow());
			return new SplitResult.Split(continuation);
		}
		final double remaining = pageLimit - boundaryLine.start();
		final FlexItemBox[] boundaryItems = new FlexItemBox[boundaryLine.itemCount()];
		for (int k = 0; k < boundaryItems.length; ++k) {
			boundaryItems[k] = this.lineItems.get(boundaryLine.startFlow() + k);
		}

		SplitResult[] probed = new SplitResult[boundaryItems.length];
		boolean anySplit = (flags & IPageBreakableBox.FLAGS_SPLIT) != 0;
		if (!anySplit) {
			for (int k = 0; k < boundaryItems.length; ++k) {
				final SplitResult r = boundaryItems[k].split(remaining, mode, xflags);
				probed[k] = r;
				if (r instanceof SplitResult.Split) {
					anySplit = true;
				}
			}
		}

		if (!anySplit) {
			// No item in the boundary row can split: treat the entire row as the boundary and carry it whole.
			if (boundary == 0) {
				return (flags & IPageBreakableBox.FLAGS_FIRST) != 0 ? SplitResult.KEEP : SplitResult.MOVE;
			}
			final double keptExtent = boundaryLine.start();
			final net.zamasoft.foliojet.layout.box.content.RowSplitContainer cont = new net.zamasoft.foliojet.layout.box.content.RowSplitContainer();
			((Container) this.container).migrateFlowsFrom(boundaryLine.startFlow(), cont, keptExtent);
			cont.anchorCurrent(0);
			final AbstractContainerBox continuation = this.splitPage(cont, keptExtent, false);
			if (continuation instanceof FlexBox contFlex) {
				contFlex.markFlexLayout();
				contFlex.carriedLines = true;
				contFlex.setFlexLines(shiftLines(this.lines, boundary, keptExtent),
						new ArrayList<>(this.lineItems.subList(boundaryLine.startFlow(), this.lineItems.size())));
			}
			this.keepHeadLines(boundary, boundaryLine.startFlow());
			return new SplitResult.Split(continuation);
		}

		// Boundary row: forcibly split even items that were not split (Keep decision).
		final byte forcedFlags = (byte) (xflags | IPageBreakableBox.FLAGS_SPLIT);
		final FlexItemBox[] remainders = new FlexItemBox[boundaryItems.length];
		// Item height before splitting (for the remainder lower-bound calculation below).
		final double[] preExtents = new double[boundaryItems.length];
		for (int k = 0; k < boundaryItems.length; ++k) {
			preExtents[k] = boundaryItems[k].getPageExtent(flow);
		}
		for (int k = 0; k < boundaryItems.length; ++k) {
			final SplitResult r = probed[k] instanceof SplitResult.Split ? probed[k]
					: boundaryItems[k].split(remaining, mode, forcedFlags);
			if (!(r instanceof SplitResult.Split(final IPageBreakableBox remainder))
					|| !(remainder instanceof FlexItemBox typedRemainder)) {
				throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
						"FlexItemBox.split with FLAGS_SPLIT must return Split(FlexItemBox) but was " + r);
			}
			remainders[k] = typedRemainder;
		}

		// Actual extent consumed by the kept side (2026-08-19; same correction as in GridBox.split).
		// Splitting moves indivisible content whole to the remainder, so the kept fragment's actual content
		// can end before the cut line. Close the kept fragment at its actual painted end,
		// and subtract the same amount from the continuation line starts.
		double consumed = 0;
		for (int k = 0; k < boundaryItems.length; ++k) {
			consumed = Math.max(consumed, boundaryItems[k].paintedPageExtent(flow));
		}
		consumed = Math.min(consumed, remaining);
		// Even if structural splitting succeeds, a kept side with only empty or invisible items
		// can have paintedPageExtent=0. Continuing with keptEnd=0 would split the same boundary row
		// on every page without consuming anything. Overlap avoidance for later rows would spread
		// positions farther apart each generation (an empty flex row in a vertical-lr table grew from
		// 96 pt to 2.37e16 pt, hitting a drawing-coordinate assertion on page 58). Since splitting
		// succeeded, use the available extent as layout progress only when nothing is painted.
		// The normal path with painted content still uses the actual painted end.
		if (LayoutUtils.compare(consumed, 0) <= 0 && LayoutUtils.compare(remaining, 0) > 0) {
			consumed = remaining;
		}
		final double keptEnd = boundaryLine.start() + consumed;
		final net.zamasoft.foliojet.layout.box.content.RowSplitContainer cont = new net.zamasoft.foliojet.layout.box.content.RowSplitContainer();
		final boolean vertical = flow.isVertical();
		double newLinePageSize = 0;
		for (int k = 0; k < remainders.length; ++k) {
			remainders[k].setFlexLineOffset(boundaryItems[k].getFlexLineOffset(vertical), vertical);
			cont.addFlow(remainders[k], 0);
			newLinePageSize = Math.max(newLinePageSize, remainders[k].getPageExtent(flow));
		}
		// **Keep the continuation line height at least as large as the remainder** (2026-08-17;
		// guards against the same ledger error as the corresponding correction in GridBox.split).
		// The remainder is not laid out yet, so getPageExtent may return nearly 0. Flex boundary searches
		// use cumulative sums, avoiding grid's "empty continuation fragment" (an actual defect), but
		// cut positions for later lines in a multi-line continuation shift. Enforce a geometric lower bound.
		newLinePageSize = Math.max(newLinePageSize, boundaryLine.pageSize() - consumed);
		// Per item, also enforce "item height before splitting - measured height of the kept side"
		// (2026-08-18; same correction as in GridBox.split). The kept side can end before
		// the available extent because indivisible content moves to the remainder.
		for (int k = 0; k < boundaryItems.length; ++k) {
			newLinePageSize = Math.max(newLinePageSize, preExtents[k] - consumed);
		}
		final List<FlexItemBox> contItems = new ArrayList<>(remainders.length
				+ (this.lineItems.size() - (boundaryLine.startFlow() + boundaryItems.length)));
		for (final FlexItemBox rem : remainders) {
			contItems.add(rem);
		}
		final List<Line> contLines = new ArrayList<>();
		contLines.add(new Line(0, boundaryItems.length, 0, newLinePageSize));

		if (boundary + 1 < this.lines.size()) {
			final Line nextLine = this.lines.get(boundary + 1);
			((Container) this.container).migrateFlowsFrom(nextLine.startFlow(), cont, keptEnd);
			int shift = boundaryItems.length;
			for (int j = boundary + 1; j < this.lines.size(); ++j) {
				final Line old = this.lines.get(j);
				// Match start to the actual drawing position after transfer (-keptEnd).
				contLines.add(new Line(shift, old.itemCount(), old.start() - keptEnd, old.pageSize()));
				shift += old.itemCount();
			}
			contItems.addAll(this.lineItems.subList(nextLine.startFlow(), this.lineItems.size()));
		}

		cont.anchorCurrent(remainders.length);
		final AbstractContainerBox continuation = this.splitPage(cont, keptEnd, false);
		if (continuation instanceof FlexBox contFlex) {
			contFlex.markFlexLayout();
			contFlex.carriedLines = true;
			contFlex.setFlexLines(contLines, contItems);
		}
		this.keepHeadLines(boundary + 1, boundaryLine.startFlow() + boundaryItems.length);
		return new SplitResult.Split(continuation);
	}

	/**
	 * Rebuilds the leading fragment's records after splitting to include only kept lines and items (2026-09-17).
	 *
	 * <p>
	 * Previously, {@code lines}/{@code lineItems} remained unchanged after splitting and
	 * <b>kept referring to lines and items already transferred to the next fragment</b>.
	 * When the same leading fragment was split again, as in multi-column balancing,
	 * the stale records selected a "boundary row" and {@code split} already transferred items
	 * again to create remainders. The same content entered two fragments (the sweep's
	 * "content duplication": seed 2010872 drew T106 in two columns on the same page).
	 * </p>
	 */
	private void keepHeadLines(final int lineCount, final int itemCount) {
		this.lines = new ArrayList<>(this.lines.subList(0, Math.min(lineCount, this.lines.size())));
		this.lineItems = new ArrayList<>(this.lineItems.subList(0, Math.min(itemCount, this.lineItems.size())));
	}

	/**
	 * Returns a list of lines from {@code fromIndex} onward in {@code lines},
	 * rebased so the first starts at 0 (used when {@link #split} carries whole lines
	 * to the next fragment).
	 */
	private static List<Line> shiftLines(final List<Line> lines, final int fromIndex, final double keptExtent) {
		final List<Line> result = new ArrayList<>(lines.size() - fromIndex);
		int shift = 0;
		for (int j = fromIndex; j < lines.size(); ++j) {
			final Line old = lines.get(j);
			// Match start to the actual drawing position after transfer (-keptExtent).
			result.add(new Line(shift, old.itemCount(), old.start() - keptExtent, old.pageSize()));
			shift += old.itemCount();
		}
		return result;
	}
}
