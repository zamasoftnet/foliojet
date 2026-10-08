package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.MeasurePageGenerator;
import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.LayoutSource;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Measures intrinsic sizes through actual layout (M2c).
 *
 * <p>
 * Replays the target block's child-event range twice onto a scratch page
 * (line width ∞=max-content, line width 0=min-content), then reads used line-axis sizes from
 * the resulting box tree. Unlike the old two-pass simulated measurement (IntrinsicMeasurer),
 * this uses the actual layout rules, avoiding approximation drift.
 * Returns null when the range cannot be identified (invalid anchor or Opaque content),
 * letting the caller fall back to simulated measurement.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class MeasuredIntrinsics {
	/**
	 * A "sufficiently wide" line width for max-content measurement.
	 */
	static final double INFINITE = 1e6;

	private MeasuredIntrinsics() {
		// pure functions
	}

	/**
	 * Measures the target block's intrinsic sizes through actual layout.
	 *
	 * @param log      Source log (may be null if absent)
	 * @param box      Target block (anchor=SourceAnchor)
	 * @param template Computed parameters from which fonts and other settings are inherited
	 * @param ua       User agent
	 * @return Measured intrinsic sizes; null if the range cannot be identified (fall back to simulation)
	 */
	public static IntrinsicSizes of(final LayoutSource log, final AbstractContainerBox box, final BlockParams template,
			final UserAgent ua) {
		if (log == null) {
			return null;
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox) {
			// Fall back to simulated measurement for absolute positioning (E-6 increment 4e, 2026-07-24):
			// before increment 4e, anchors were recorded as Opaque, so endOf was unavailable and this
			// structurally returned null (= simulated measurement). Recipe recording (4e) aimed to make
			// body ranges eligible for bind. Applying M2c measurement changes sizes (and output), so it
			// was deferred under the unchanged-behavior constraint. Applying it requires a separate increment
			// that both rebaselines golden data and changes the sizes snapshot in AbsoluteBlockBox.DeferredBind
			// to be remeasured at bind time.
			return null;
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.OutsideMarkerBox
				|| box instanceof net.zamasoft.foliojet.layout.box.impl.InsideMarkerBox) {
			// Fall back to simulated measurement for list markers (both outside and inside):
			// actual layout drops trailing whitespace in marker content during line-end processing
			// (= actual measurement gives true max-content), but current marker placement relies on
			// the trailing whitespace's advance to create the gap between the marker and body text.
			// Preserve the old semantics until marker placement is redesigned
			// with an explicit gap (M3).
			return null;
		}
		final long selfId = box.getSourceAnchor();
		if (selfId < 0) {
			return null;
		}
		final long endId = log.endOf(selfId);
		if (endId < 0 || endId <= selfId + 1) {
			return null;
		}
		if (log.containsTable(selfId + 1, endId - 1)) {
			// Fall back to simulated measurement for ranges containing tables (table set, 2026-07-30:
			// before recipe recording, tables were recorded as Opaque and caught by containsOpaque below).
			// Applying this measurement (replay onto scratch pages of width ∞/0) to tables resolves
			// percentage cells and auto column widths against the scratch width, making max-content
			// diverge and breaking shrink-to-fit widths (G-1 measurement:
			// the four floats in 0070-table-layout/float-in-auto-4.html changed from
			// 376/414.5/276/216 to 500 pt each, the page width). Expanding actual measurement
			// changes output, so this remains a permanent gate, as with absolute positioning in E-6 increment 4e.
			return null;
		}
		if (!log.isIntact(selfId + 1, endId - 1)) {
			// **Use simulated measurement if the range has become sparse** (introduced on 2026-07-27).
			//
			// `compact()` retains only "open (unmatched) Start events" before the watermark.
			// Elements still open at a break therefore **retain only their Start while losing their contents**.
			// When they later close, `endOf()` returns a plausible end over the sparse retained sequence,
			// and the `contains*` gates inspect only that retained sequence,
			// so none of them detect the loss.
			//
			// The subsequent `SourceReplayer.measure` **throws without a fallback**
			// (the contract requires callers to determine ranges while they are still live).
			// Without this check, the entire conversion stops.
			//
			// The same kind of defect occurred in `RootBuilder.stampRanges`
			// (15 in 100,000 documents, `The absorbed replay range was lost`). Here,
			// no reproducer has been found, but the mechanism is identical and **returning null
			// already provides a safe escape**, so guard against it.
			return null;
		}
		if (log.containsOpaque(selfId + 1, endId - 1) || log.containsAbsolute(selfId + 1, endId - 1)
				|| log.containsMulticol(selfId + 1, endId - 1)
				|| log.containsMixedFlow(selfId + 1, endId - 1, template.flow)) {
			// Use simulated measurement for mixed writing directions; sub-builder replay context is undesigned.
			// Use simulated measurement for ranges with absolute positioning, as with Opaque recording before 4e.
			// (Recipe recording did not expand actual measurement because behavior had to remain unchanged.)
			return null;
		}
		if (log.containsGrid(selfId + 1, endId - 1)) {
			// Grid G1d (2026-07-31): actual measurement (scratch replay) activates GridBuilder and measures
			// using track placement, while measurement and bind on the main TwoPass path remain
			// at G0 (single column). Mixing them produces inconsistent widths and heights.
			// Fall back to simulated measurement until TwoPass item measurement arrives (G3).
			return null;
		}
		if (log.containsFlex(selfId + 1, endId - 1)) {
			// Flex F0c (2026-08-02): at F0, replay also degrades to a single column with identical behavior,
			// but F1d activates FlexBuilder only on the scratch replay side.
			// Preemptively fail closed against the same mismatch as Grid G1d.
			return null;
		}
		if (log.containsFloat(selfId + 1, endId - 1)) {
			// Use simulated measurement for content containing floats: max-content accumulates widths
			// of side-by-side floats, but reading the infinite-width scratch result cannot reconstruct
			// the accumulated width independently of float positions and alignment (the exact solution is
			// a line-occupancy tracker during placement, addressed in the float redesign, M6c).
			return null;
		}
		final WritingMode flow = template.flow;
		final boolean vertical = flow.isVertical();
		// max-content: lay out without wrapping at line width ∞. Line-axis percentages inside count as auto
		// (CyclicPercent), not as a share of ∞.
		final MeasurePageGenerator wide;
		try (CyclicPercent.Scope cyclic = CyclicPercent.enter(true)) {
			wide = SourceReplayer.measure(log, selfId + 1, endId - 1, template, ua, INFINITE, INFINITE, false);
		}
		// min-content: break at every opportunity at line width 0.
		final MeasurePageGenerator narrow;
		try (CyclicPercent.Scope cyclic = CyclicPercent.enter(false)) {
			narrow = SourceReplayer.measure(log, selfId + 1, endId - 1, template, ua, vertical ? INFINITE : 0,
					vertical ? 0 : INFINITE, false);
		}
		if (wide.getLastPage() == null || narrow.getLastPage() == null) {
			return null;
		}
		if (containsMulticolBox(wide.getLastPage().getContainer())) {
			// **Use simulated measurement for ranges containing auto-height multi-column layout**
			// (2026-08-21, sweep seed 615921). The containsMulticol gate above indexed only fixed-size
			// multi-column layout (MulticolumnBlockBox), letting auto multi-column layout
			// (FlowBlockBox column-count) pass through. M2c measurement returned a min-content size
			// inflated by the column count without the columnInflated flag, disabling shrinkToFit's
			// multi-column clamp. A float:right inside vertical-writing multi-column layout
			// was placed before the line start (off the paper). Simulated measurement
			// (IntrinsicMeasurer) sets the flag, so the existing clamp works.
			return null;
		}
		final double maxContent = usedLineExtent(wide.getLastPage().getContainer(), flow, true);
		final double minContent = usedLineExtent(narrow.getLastPage().getContainer(), flow, false);
		final double minPage = wide.getLastPage().getContainer().getContentSize();
		return new IntrinsicSizes(minContent, maxContent, minPage);
	}

	/** Returns whether the completed tree contains auto multi-column layout (containers with at least two columns). */
	private static boolean containsMulticolBox(final Container container) {
		final boolean[] found = { false };
		container.eachFlowBox(box -> {
			if (found[0]) {
				return;
			}
			if (box instanceof AbstractContainerBox block) {
				// Auto-height multi-column layout keeps getColumnCount() at 1; inspect the specified count.
				if (block.getColumnCount() >= 2 || block.getBlockParams().columns.count >= 2
						|| containsMulticolBox(block.getContainer())) {
					found[0] = true;
				}
			}
		});
		return found[0];
	}

	/**
	 * Returns the completed box tree's used line-axis size (the width actually occupied by content
	 * along the line axis). AUTO-width blocks expand to the available width on scratch pages,
	 * so read actual sizes of text lines, replaced elements, and fixed-width blocks
	 * (also used for margin-box max-content measurement).
	 */
	public static double usedLineExtent(final Container container, final WritingMode flow) {
		return usedLineExtent(container, flow, true);
	}

	/**
	 * {@link #usedLineExtent(Container, WritingMode)} of the max-content ({@code maxContent}) or the min-content
	 * scratch layout: a block whose line-axis size is a cyclic percentage counts with its content, as auto, and a form
	 * control among them with its natural size for max-content, 0 for min-content (CSS Sizing 3 §5.2, 2026-10-09).
	 */
	private static double usedLineExtent(final Container container, final WritingMode flow, final boolean maxContent) {
		final double[] max = { 0 };
		container.eachFlowBox(box -> max[0] = Math.max(max[0], boxLineExtent(box, flow, maxContent)));
		if (container instanceof FlowContainer fc) {
			// Floats contribute directly to the used width.
			max[0] = Math.max(max[0], fc.floatingsLineExtent(flow));
		}
		return max[0];
	}

	/**
	 * Returns the sum of specified line-axis margins (absolute values only;
	 * treat percentages and auto as 0 for intrinsic measurement).
	 */
	private static double specifiedLineMargins(final AbstractContainerBox block, final WritingMode flow) {
		final net.zamasoft.foliojet.layout.box.params.Insets margin = block.getBlockParams().frame.margin;
		final net.zamasoft.foliojet.layout.box.params.LengthType abs = net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
		if (flow.isVertical()) {
			return (margin.getTopType() == abs ? margin.getTop() : 0)
					+ (margin.getBottomType() == abs ? margin.getBottom() : 0);
		}
		return (margin.getLeftType() == abs ? margin.getLeft() : 0)
				+ (margin.getRightType() == abs ? margin.getRight() : 0);
	}

	private static double boxLineExtent(final IBox box, final WritingMode flow, final boolean maxContent) {
		final boolean vertical = flow.isVertical();
		switch (box.getType()) {
		case TEXT_BLOCK:
			final TextBlockBox text = (TextBlockBox) box;
			return text.getBlockParams().flow.isVertical() == vertical ? text.getLineSize()
					: vertical ? text.getHeight() : text.getWidth();
		case BLOCK: {
			final AbstractContainerBox block = (AbstractContainerBox) box;
			// Reconstruct margins from specified values, not used values: on scratch pages, resolving
			// available width (over-constrained adjustment) absorbs the block's used end margin.
			// Treat percentages and auto as 0 for intrinsic sizes.
			final double margins = specifiedLineMargins(block, flow);
			final boolean orthogonal = block.getBlockParams().flow.isVertical() != vertical;
			final net.zamasoft.foliojet.layout.box.params.LengthType lineType = block.getBlockParams().size
					.getLineType(flow);
			// A percentage (or calc() with one) is cyclic here: the block counts with its content, as auto (CSS Sizing 3
			// §5.2, 2026-10-09); the scratch page resolved it against 10^6 or 0.
			final boolean cyclic = lineType == net.zamasoft.foliojet.layout.box.params.LengthType.RELATIVE
					|| lineType == net.zamasoft.foliojet.layout.box.params.LengthType.MIXED;
			if (orthogonal || (!block.isAutoLineSize() && !cyclic)) {
				// For an orthogonal child, the parent's line axis is the child's page axis; use physical sizes after layout.
				// For a block with a specified width, that width is the used width.
				return margins + (vertical ? block.getHeight() : block.getWidth());
			}
			double inner = block.getFrame().getBorderLineExtent(flow)
					+ usedLineExtent(block.getContainer(), flow, maxContent);
			if (cyclic && block.getBlockParams().naturalLineSize > 0) {
				// A form control (input display: block; width: 100%): its natural size for max-content, 0 for
				// min-content (compressible).
				inner = block.getFrame().getBorderLineExtent(flow)
						+ (maxContent ? block.getBlockParams().naturalLineSize : 0);
			}
			// Clamp by min-width/max-width (absolute lengths only; 2026-08-08,
			// css-sizing outer contribution). On scratch pages, available-width resolution may not reflect
			// min-width in an auto-width block's actual size. Wrappers of nested grids with
			// min-width:100px (NHK navigation section pills) were flex-shrunk to text width,
			// making pill backgrounds overlap adjacent pills. Exclude percentages and calc
			// because their reference size is indefinite.
			final net.zamasoft.foliojet.layout.box.params.BlockParams bp = block.getBlockParams();
			final double bb = bp.boxSizing == net.zamasoft.foliojet.layout.box.params.BoxSizingMode.BORDER_BOX
					? block.getFrame().getBorderLineExtent(flow)
					: 0;
			if (bp.maxSize.getLineType(flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				inner = Math.min(inner, Math.max(0, bp.maxSize.getLineLength(flow) - bb)
						+ block.getFrame().getBorderLineExtent(flow));
			}

			if (bp.minSize.getLineType(flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				inner = Math.max(inner, Math.max(0, bp.minSize.getLineLength(flow) - bb)
						+ block.getFrame().getBorderLineExtent(flow));
			}
			return margins + inner;
		}
		default:
			// Use actual sizes for replaced elements and the like (their percentages were resolved as auto, or
			// against 0, during the measurement: LayoutUtils.calculateReplacedSize).
			return vertical ? box.getHeight() : box.getWidth();
		}
	}
}
