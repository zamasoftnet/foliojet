package net.zamasoft.foliojet.layout.box.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.builder.impl.BlockBuilder;
import net.zamasoft.foliojet.layout.fragment.Continuation.SourceRange;
import net.zamasoft.foliojet.layout.fragment.OpenShape;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * A container that protects the item placement of containers with row splitting
 * (flex: Bug C, 2026-08-07/grid: G6, 2026-08-10) from generic restyle reconstruction
 * (formerly FlexRowContainer; generalized to share the implementation between flex/grid
 * and renamed when grid row splitting was introduced).
 *
 * <p>
 * The path that reconstructs builder state without a chain after a page is finalized
 * (the "uncollectable break" branch of {@code RootBuilder.resumeFragmentChain}
 * -- boxes for which {@code plan.selects()} is false take this path) registers each flow child
 * again through {@code BlockBuilder.startFlowBlock} as a normal sequential block stack.
 * Table rows bypass this path (the TABLE branch of {@code FlowContainer.restyleItem}
 * only uses {@code replayFromSource}/{@code addBound}, without generically reconstructing
 * cell contents), but flex boxes with {@code BoxType.BLOCK} pass straight through it.
 * Flex items merely feed their cross-axis positions into the container's one-dimensional
 * flow ledger (pageAxis), so this generic reconstruction destroys main-axis alignment
 * (observed: three forcibly split cards were staggered like steps).
 * </p>
 *
 * <p>
 * All restyle side effects (structure tags, string-set, recursive processing of grandchildren, etc.)
 * remain delegated to {@code super.restyle}. Immediately afterward, only item positions are
 * restored to the values recorded by {@link #anchor}. This asymmetric design overwrites the damage
 * from the generic mechanism each time rather than curing the symptom, but has a narrower impact
 * than changing the generic mechanism itself.
 * </p>
 */
public final class RowSplitContainer extends FlowContainer {

	/**
	 * A snapshot of finalized item placement (extended from pageAxis alone on 2026-08-08).
	 * Restyle reconstruction overwrites width and height when resolving them again
	 * ({@code startFlowBlock.calculateSize} sets the main-axis size to the containing width,
	 * and {@code endFlowBlock} sets it to the content height), so restore them with the position.
	 */
	private record Anchor(double pageAxis, double width, double height, boolean restoreExtent) {
	}

	private Map<IFlowBox, Anchor> anchored;

	/**
	 * Whether this container anchored itself on a restyle (2026-10-10): its box moved whole to another page, it was not
	 * split. Such a box keeps its definite page-axis size when its content ends on the page
	 * ({@link #restoreAnchoredPageAxis}); a split continuation takes its content's size, which later splits of its rows
	 * measure.
	 */
	private boolean movedWhole;

	/**
	 * Snapshots the placement of all items currently in {@link #flows} as authoritative.
	 * Called once, immediately after {@code FlexBox.split} finishes assembling {@code cont}
	 * with {@code addFlow}/{@code migrateFlowsFrom} (before restyle can touch it).
	 * Other reconstruction paths (see {@link #restyle}) anchor themselves on entry.
	 */
	public void anchorCurrent() {
		this.anchorCurrent(this.flows == null ? 0 : this.flows.size());
	}

	/**
	 * Anchors the current items, accepting post-restyle dimensions only for the first
	 * {@code reflowablePrefixCount} items. New remainder items created by forced row splitting
	 * may shrink to fit the next fragment, while items transferred whole from later rows must
	 * retain their finalized dimensions from before the split. Resolving percentage dimensions
	 * again for the latter would use the enlarged continuation box as a new basis and enlarge
	 * them in every generation.
	 */
	public void anchorCurrent(final int reflowablePrefixCount) {
		if (this.flows == null) {
			return;
		}
		if (reflowablePrefixCount < 0 || reflowablePrefixCount > this.flows.size()) {
			throw new IllegalArgumentException("reflowablePrefixCount=" + reflowablePrefixCount
					+ ", flows=" + this.flows.size());
		}
		if (this.anchored == null) {
			this.anchored = new HashMap<>();
		}
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow f = this.flows.get(i);
			this.anchored.put(f.box,
					new Anchor(f.pageAxis, f.box.getWidth(), f.box.getHeight(), i >= reflowablePrefixCount));
		}
	}

	/** The content end of the current flows (maximum pageAxis + item content height). */
	private double contentEnd() {
		final boolean vertical = this.box != null && this.box.getBlockParams().flow.isVertical();
		double end = 0;
		for (final Flow f : this.flows) {
			end = Math.max(end, f.pageAxis + (vertical ? f.box.getWidth() : f.box.getHeight()));
		}
		return end;
	}

	@Override
	public void restyle(final BlockBuilder builder, final OpenShape shape, final boolean restyleAbsolutes,
			final List<SourceRange> prefix) {
		if (this.anchored == null) {
			// For restyle paths other than split continuation fragments (already anchored by
			// FlexBox.split immediately after assembly), such as whole-box moves across pages,
			// anchor the current positions in place as authoritative before restyle touches them
			// (2026-08-08: flows before restyle retain the main-axis alignment finalized
			// by FlexBuilder.placeRow). This path also restores dimensions
			// (resolution by startFlowBlock/endFlowBlock overwrites width:auto with the containing width
			// and height:auto with the content height -- ranking badges on yahoo.co.jp).
			this.anchorCurrent(0);
			this.movedWhole = true;
		}
		final boolean vertical = this.box.getBlockParams().flow.isVertical();
		// When restyling an empty container, flows itself has not been created yet.
		final List<Flow> restyleFlows = this.flows == null ? List.of() : new ArrayList<>(this.flows);
		for (final Flow f : restyleFlows) {
			if (f == null) {
				continue;
			}
			final Anchor want = this.anchored.get(f.box);
			if (want != null && f.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox item) {
				item.prepareRestyleLineExtent(want.width(), want.height(), vertical);
			}
		}
		try {
			super.restyle(builder, shape, restyleAbsolutes, prefix);
		} finally {
			for (final Flow f : restyleFlows) {
				if (f == null) {
					continue;
				}
				if (f.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox item) {
					item.clearRestyleLineExtent();
				}
			}
		}
		this.restoreAnchoredPageAxis(builder);
	}

	private void restoreAnchoredPageAxis(final BlockBuilder builder) {
		if (this.anchored == null || this.flows == null) {
			return;
		}
		final double stackedEnd = this.contentEnd();
		for (int i = 0; i < this.flows.size(); ++i) {
			final Flow f = this.flows.get(i);
			final Anchor want = this.anchored.get(f.box);
			if (want == null) {
				continue;
			}
			if (f.pageAxis != want.pageAxis()) {
				this.flows.set(i, new Flow(f.serial, f.box, want.pageAxis()));
			}
			if (want.restoreExtent() && f.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox item
					&& (item.getWidth() != want.width() || item.getHeight() != want.height())) {
				item.restoreExtents(want.width(), want.height());
			}
		}
		this.pushDownOverlappingRows();
		if (builder != null) {
			// Rewind the builder cursor advanced by vertical-stack registration by the difference
			// between the content ends before and after restoring positions. The subsequent
			// endFlowBlock writes the container's height/contentSize from the cursor, so correcting
			// this also corrects the box height (the gap between the heatstroke index and
			// rain radar in the yahoo.co.jp weather module). Until 2026-08-10, this applied only to
			// self-anchoring that restored all item dimensions, using the content end at anchoring time.
			// However, (1) vertical stacking also inflates split continuation boxes and pushes later content down
			// (observed with grid row splitting in row-split-carry: a continuation fragment of two 56 pt items
			// grew to 112 pt), and (2) item heights are not finalized (0) when anchored just after splitting,
			// so cannot serve as a basis. Always rewind using the measured content end after restoration instead.
			// Restoring the **dimensions** of split remainder items breaks the ledger for subsequent splits
			// (restoreExtent=false in the loop above); rewinding the cursor
			// does not.
			final double trueEnd = this.contentEnd();
			final double delta = stackedEnd - trueEnd;
			// If pushing rows down (pushDownOverlappingRows below) extends the content,
			// the same formula handles it as a negative delta = cursor advance (2026-08-19).
			if (delta != 0) {
				builder.setPageAxis(builder.getPageAxis() - delta);
				if (this.box instanceof net.zamasoft.foliojet.layout.box.impl.FlowBlockBox host) {
					// Vertical-stack contentSize written to the parent by each item's endFlowBlock remains
					// because Math.max only increases it; reset it by assignment (shared by flex/grid).
					// Content running past the page grows the box as before: the page breaks that carry the overflow
					// on measure the box (a flex of width: 0 in vertical-rl went off the paper when it kept its size).
					if (this.movedWhole && this.contentEndsOnPage(builder, trueEnd)) {
						host.restoreContentExtentWithin(trueEnd);
					} else {
						host.restoreContentExtent(trueEnd);
					}
				}
			}
		}
	}

	/**
	 * Whether the content of this container ends on the page (2026-10-10): both the cursor and the farthest item end,
	 * measured from the container's content start, are within the page limit. The cursor alone does not tell: a
	 * negative trailing margin pulls it back over items that still run past the page (an 80pt item kept in a 20pt flex
	 * went off the paper). When a break during the restyle left another flow open, the content did not end here.
	 */
	private boolean contentEndsOnPage(final BlockBuilder builder, final double trueEnd) {
		if (!(builder instanceof net.zamasoft.foliojet.layout.builder.impl.BreakableBuilder breakable)) {
			return false;
		}
		final double limit = breakable.getPageLimit();
		if (LayoutUtils.compare(builder.getPageAxis(), limit) > 0) {
			return false;
		}
		final net.zamasoft.foliojet.layout.builder.LayoutContext.Flow flow = builder.getFlow();
		return flow.box == this.box && LayoutUtils.compare(flow.pageAxis + trueEnd, limit) <= 0;
	}

	/**
	 * <b>After restoration, pushes down rows that overlap the preceding row's actual content</b> (2026-08-19).
	 *
	 * <p>
	 * The first row (remainder) of a continuation fragment created by forced row splitting
	 * can grow taller than the original slice geometry during reconstruction (restyle)
	 * (observed in smolcss: over 100 pt due to different line wrapping, etc.).
	 * Later rows are anchored to the geometry at split time, so without adjustment,
	 * the remainder's actual content overlaps the next row.
	 * For each group in the row ledger ({@link net.zamasoft.foliojet.layout.box.RowSplitBox}),
	 * uniformly push down rows that start before "the preceding row's actual content end
	 * + the ledger's row gap", and synchronize the anchors and ledger start positions
	 * (the ledger is used for boundary searches in subsequent splits).
	 * </p>
	 */
	private void pushDownOverlappingRows() {
		if (!(this.box instanceof net.zamasoft.foliojet.layout.box.RowSplitBox rowSplit)
				|| !rowSplit.hasRowSplitLines()) {
			return;
		}
		final double[][] rows = rowSplit.rowLedgerSnapshot();
		if (rows == null || rows.length < 2) {
			return;
		}
		final boolean vertical = this.box.getBlockParams().flow.isVertical();
		final double[] newStarts = new double[rows.length];
		final double[] newExtents = new double[rows.length];
		newStarts[0] = rows[0][2];
		// Actual content end of row 0.
		double prevEnd = this.rowContentEnd(rows[0], vertical);
		newExtents[0] = Math.max(rows[0][3], prevEnd - rows[0][2]);
		boolean shifted = newExtents[0] != rows[0][3];
		for (int r = 1; r < rows.length; ++r) {
			// The gap the ledger keeps (2026-10-09), else inferred from the ledger. Inferred, the difference between a
			// row's content as laid out again and its ledger extent became a gap the next time (eurekalert: 19.57pt).
			final double ledgerGap = rows[r].length > 4 && !Double.isNaN(rows[r][4]) ? rows[r][4]
					: Math.max(0, rows[r][2] - (rows[r - 1][2] + rows[r - 1][3]));
			final double required = prevEnd + ledgerGap;
			double start = rows[r][2];
			if (LayoutUtils.compare(start, required) < 0) {
				final double delta = required - start;
				final int from = (int) rows[r][0];
				final int count = (int) rows[r][1];
				for (int k = from; k < from + count && k < this.flows.size(); ++k) {
					final Flow f = this.flows.get(k);
					this.flows.set(k, new Flow(f.serial, f.box, f.pageAxis + delta));
					final Anchor a = this.anchored.get(f.box);
					if (a != null) {
						// Update the anchors too, so the next restyle does not undo the downward shift.
						this.anchored.put(f.box,
								new Anchor(a.pageAxis() + delta, a.width(), a.height(), a.restoreExtent()));
					}
				}
				start = required;
				shifted = true;
			}
			newStarts[r] = start;
			final double rowEnd = this.rowContentEnd(new double[] { rows[r][0], rows[r][1], start, rows[r][3] }, vertical);
			newExtents[r] = Math.max(rows[r][3], rowEnd - start);
			shifted |= newExtents[r] != rows[r][3];
			prevEnd = Math.max(prevEnd, rowEnd);
		}
		if (shifted) {
			rowSplit.syncRowGeometry(newStarts, newExtents);
		}
	}

	/** Actual content end of a row ({first flow index, item count, start, extent}). */
	private double rowContentEnd(final double[] row, final boolean vertical) {
		final int from = (int) row[0];
		final int count = (int) row[1];
		double end = row[2];
		for (int k = from; k < from + count && k < this.flows.size(); ++k) {
			final Flow f = this.flows.get(k);
			end = Math.max(end, f.pageAxis + (vertical ? f.box.getWidth() : f.box.getHeight()));
		}
		return end;
	}
}
