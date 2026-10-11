package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * Entry point for the Grid build lifecycle (Grid G1b: the same thin structure as TableBuilderLifecycle.
 * It handles only symmetric start/end operations, not execution plan selection).
 */
public final class GridBuilderLifecycle {
	private GridBuilderLifecycle() {
		// Static utility
	}

	/**
	 * Starts a GridBuilder. Every grid takes track placement, whatever its host (2026-10-11: the eligibility check
	 * here returned whether the host was a BlockBuilder or a TwoPass, which every builder is). Grids without
	 * grid-template-columns (2026-08-09), with grid-template-rows (2026-08-29) and in vertical writing (2026-10-05)
	 * were made eligible one by one before; GridBox is PageAtomic either way.
	 */
	public static GridBuilder start(final Builder builder, final GridBox gridBox) {
		return new GridBuilder(builder, gridBox);
	}
}
