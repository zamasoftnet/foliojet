package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.FlexItemBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.foliojet.layout.fragment.RangeHandle;
import net.zamasoft.foliojet.layout.fragment.ReplayIntent;
import net.zamasoft.foliojet.layout.sizing.IntrinsicSizes;

/**
 * Retained content for one Flex item (Flex F1d, 2026-08-02; same structure as
 * {@code GridItemContent}). Owns the body (TwoPass recording), intrinsic size snapshot
 * at close, and item box. Retains range leases for elements and anonymous items from
 * close until bind at Flex end. Ineligible cases fail the conversion.
 */
final class FlexItemContent {

	final FlexItemBox itemBox;

	final RangeHandle body;

	/** Empty bodies and independent replay also retain only finalized bodies, releasing the measurement builder. */
	private final TwoPassBlockBuilder.DeferredBind content;

	private final net.zamasoft.foliojet.layout.builder.PageGenerator pageGenerator;
	private final ContinuationStats.TwoPassCensusTag censusTag;
	private final java.util.Set<Long> ownedAbsoluteAnchors;

	/** Intrinsic sizes at close (used for auto/content basis). */
	final IntrinsicSizes sizes;

	final boolean anonymous;

	/** Flex sizing properties (snapshot of the authored child's FlowPos.flexItem). */
	final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec;

	FlexItemContent(final FlexItemBox itemBox, final TwoPassBlockBuilder body, final IntrinsicSizes sizes,
			final boolean anonymous, final net.zamasoft.foliojet.layout.box.params.FlexItemSpec spec) {
		this.itemBox = itemBox;
		this.content = body.detachDeferredBind();
		this.body = this.content.handle();
		this.pageGenerator = this.body == null ? null : body.getPageContext().getPageGenerator();
		this.censusTag = body.itemCensusTag();
		this.ownedAbsoluteAnchors = body.rangeOwnedAbsoluteAnchors();
		this.sizes = sizes;
		this.anonymous = anonymous;
		this.spec = spec;
	}

	/**
	 * Binds the body exactly once with the resolved inner main-axis size
	 * (the TwoPass record → measure → bind lifecycle; same as {@code GridItemContent.bind}).
	 */
	void bind(final BlockBuilder host, final double mainSize, final double insetBase) {
		// **Resolve used frame sizes** (2026-08-04). For normal flow boxes,
		// firstPassLayout / calculateSize converts relative padding/margin values (% and em)
		// to used sizes (AbsoluteInsets). **Flex item boxes pass through neither**,
		// so their used sizes remained 0. As a result, row flex items lost
		// all padding and margins. Bootstrap grids use
		// `.row > * { padding-inline: … }`, so on real pages,
		// column content touched the frame (found in checkout-form in the sixth real-world
		// corpus wave, where the first character of labels was clipped).
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = this.itemBox.getFrame();
		net.zamasoft.foliojet.layout.util.LayoutUtils.computePaddings(frame.padding, frame.frame.padding,
				insetBase);
		net.zamasoft.foliojet.layout.util.LayoutUtils.computeMarginsAutoToZero(frame.margin, frame.frame.margin,
				insetBase);
		this.itemBox.setFlexMainSize(mainSize, this.itemBox.getBlockParams().flow.isVertical());
		// aspect-ratio (2026-08-29): flex items skip calculateSize, so once the line-axis size is set,
		// derive the page-axis size from the ratio here (if content is taller, grow only for
		// overflow:visible — the same rule as FlowBlockBox.calculateSize).
		this.itemBox.applyAspectRatio(mainSize);
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
		// A takeover item (root box inheriting authored params) applies its specified height
		// itself. In normal flow, the parent startFlowBlock applies it, but
		// a bind builder root has no such caller (F1d: absolute lengths only;
		// F1e unifies %, min/max, and border-box normalization in cross-axis measurement).
		final net.zamasoft.foliojet.layout.box.params.BlockParams params = this.itemBox.getBlockParams();
		final boolean vertical = params.flow.isVertical();
		final net.zamasoft.foliojet.layout.box.params.LengthType pageType = vertical
				? params.size.getWidthType()
				: params.size.getHeightType();
		if (pageType == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			this.itemBox.setPageAxis(Math.max(0, vertical ? params.size.getWidth() : params.size.getHeight()));
		}
	}

	/** Validates only and lists items to terminate after acquiring the parent lease. */
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
