package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.GridItemBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/**
 * Retained content for one Grid item (Grid G3a, 2026-07-31:
 * consult-codex-2026-07-31-grid-g3.txt Q1). Owns the body (TwoPass recording),
 * the intrinsic size snapshot at close, and the final item box.
 * At close, seals the authored range for element items or the child range for anonymous items,
 * and holds a lease until bind at the end of the Grid. Ineligibility fails the conversion.
 *
 * <p>
 * Uses {@code getIntrinsicSizes()} (simulated measurement by IntrinsicMeasurer) as the
 * authoritative intrinsic sizes. Scratch measurement by {@code intrinsicSizesMeasured()}
 * cannot apply to synthetic items (no anchor; cannot reproduce the % reference size) (review Q1).
 * </p>
 */
final class GridItemContent {

	final GridItemBox itemBox;

	final RangeHandle body;

	/** Even empty bodies and independent replay retain only finalized content and release the measurement builder. */
	private final TwoPassBlockBuilder.DeferredBind content;

	private final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator;
	private final ContinuationStats.TwoPassCensusTag censusTag;
	private final java.util.Set<Long> ownedAbsoluteAnchors;

	/** Intrinsic sizes at close (shadow observation only in G3a; used for auto/fr columns in G3b/c). */
	final IntrinsicSizes sizes;

	final boolean anonymous;

	/** Explicit placement (G4a: snapshot from the authored child's FlowPos). */
	final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec;

	/**
	 * Upper bound on the line-axis min-content contribution (2026-08-19; negative means unlimited).
	 * The automatic minimum size in css-grid §6.6: when an item has an <b>explicit declaration</b>
	 * of its minimum line-axis size (e.g., Tailwind's `min-w-0`), or is a scroll container
	 * (overflow≠visible), its automatic minimum size becomes the explicit value (such as 0),
	 * and the content's min-content size does not expand the track.
	 * (Observed in Chrome: tracks fit the container width for items with min-width:0; without it,
	 * they expand to the content's min-content size. The `main.min-w-0` on react.dev relies on the
	 * former behavior. Ignoring it pushed the body text outside the sheet to x=628 and beyond,
	 * leaving every page blank.)
	 */
	final double minContributionCap;

	/**
	 * Whether this is a takeover item (the authored box itself becomes the item box)
	 * (G7, 2026-08-29). With takeover, <b>the authored root's frame and declared sizes lie
	 * outside the recorded body</b>, so they must be added back to the intrinsic size contribution.
	 */
	final boolean takeover;

	GridItemContent(final GridItemBox itemBox, final TwoPassBlockBuilder body, final IntrinsicSizes sizes,
			final boolean anonymous, final net.zamasoft.foliojet.layout.box.params.GridItemSpec spec,
			final double minContributionCap, final boolean takeover) {
		this.itemBox = itemBox;
		this.content = body.detachDeferredBind();
		this.body = this.content.handle();
		this.pageGenerator = this.body == null ? null : body.getPageContext().getPageGenerator();
		this.censusTag = body.itemCensusTag();
		this.ownedAbsoluteAnchors = body.rangeOwnedAbsoluteAnchors();
		this.sizes = sizes;
		this.anonymous = anonymous;
		this.spec = spec;
		this.minContributionCap = minContributionCap;
		this.takeover = takeover;
	}

	/**
	 * Binds the body exactly once at the resolved track width (the TwoPass lifecycle of
	 * record → measure → bind, like a float's
	 * {@code contentBuilder.bind(floatBuilder); floatBuilder.close()}).
	 */
	void bind(final BlockBuilder host, final double trackWidth) {
		// Actual frame sizes have already been resolved where GridBuilder determines the width
		// (relative to the grid area's width; G7, 2026-08-29).
		this.itemBox.setTrackWidth(trackWidth);
		if (this.takeover) {
			// aspect-ratio (G7, 2026-08-29: the same reason as in FlexItemContent.bind).
			// The item box taken over does not go through calculateSize, so determine its page-axis size
			// from the ratio here, once the track width is available. Without this,
			// the grid item in 3080-MODERN-CSS/aspect-ratio collapses to its content height.
			this.itemBox.applyAspectRatio(trackWidth);
		}
		final BlockBuilder target = new BlockBuilder(host, this.itemBox);
		if (this.body == null) {
			this.content.bind(target);
		} else {
			if (ReplayIntent.current() == ReplayIntent.MEASURE) {
				this.body.measure(target, this.pageGenerator);
			} else {
				this.body.bind(target, this.pageGenerator);
			}
			if (this.censusTag != null) {
				this.censusTag.record(ReplayIntent.current() == ReplayIntent.MEASURE
						? ContinuationStats.TwoPassCensusEvent.MEASURE_RANGE : ContinuationStats.TwoPassCensusEvent.BIND);
			}
		}
		target.close();
		if (ReplayIntent.current() == ReplayIntent.MEASURE && this.body != null) this.body.completeScratchHost();
		// A takeover item (a root box that inherits authored params) applies its specified height
		// to itself (G7, 2026-08-29: like FlexItemContent.bind).
		// In normal flow, the parent's startFlowBlock applies it, but no one applies it to
		// the bind builder's root. Without this, the takeover item's
		// height declaration is lost entirely (observed: height:20mm became the content height of 2.7 mm).
		final net.zamasoft.foliojet.layout.box.params.BlockParams params = this.itemBox.getBlockParams();
		final boolean vertical = params.flow.isVertical();
		final net.zamasoft.foliojet.layout.box.params.LengthType pageType = vertical
				? params.size.getWidthType()
				: params.size.getHeightType();
		if (pageType == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			this.itemBox.applySpecifiedPageAxis(
					Math.max(0, vertical ? params.size.getWidth() : params.size.getHeight()));
		}
	}

	/** Only validates and lists entries to terminate after acquiring the parent lease. */
	boolean collectAbsorbable(final net.zamasoft.foliojet.layout.fragment.LayoutSource log,
			final long fromId, final long toId, final java.util.List<RangeHandle> outRanges,
			final java.util.Set<Long> anchors) {
		if (this.body == null) {
			return this.content.collectAbsorbableInto(log, fromId, toId, anchors);
		}
		if (this.body.state() != RangeHandle.State.OPEN || this.body.source() != log
				|| this.body.fromId() < fromId || this.body.toId() > toId) {
			return false;
		}
		if (!outRanges.contains(this.body)) {
			for (final long anchor : this.ownedAbsoluteAnchors) {
				if (!anchors.add(anchor)) {
					return false;
				}
			}
			outRanges.add(this.body);
		}
		return true;
	}
}
