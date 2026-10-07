package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.GridBox;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * Entry point for the Grid build lifecycle (Grid G1b: the same thin structure as TableBuilderLifecycle.
 * It handles only G1 eligibility and symmetric start/end operations, not execution plan selection).
 */
public final class GridBuilderLifecycle {
	private GridBuilderLifecycle() {
		// Static utility
	}

	/**
	 * Determines whether track placement applies to this Grid
	 * (consult-codex-2026-07-31-grid-g1.txt §1.1). Ineligible grids (with a non-block host)
	 * fall back to the G0 single-column flow. All column types are eligible:
	 * fixed (G1), auto (G3b), and fr (G3c). In addition to BlockBuilder, TwoPass hosts are
	 * eligible (G3d1: record the execution plan as a GridEvent and bind after the width is resolved)
	 * (consult-codex-2026-07-31-grid-g3.txt Q3).
	 */
	public static boolean eligible(final GridBox gridBox, final Builder builder) {
		// Grids without grid-template-columns (= an implicit single auto column) are also eligible (2026-08-09).
		// Previously, they fell back to the G0 single-column flow, so item alignment such as place-items
		// had no effect at all (centering icons in buttons on NHK News).
		// GridBox is PageAtomic even when ineligible, so making it eligible does not change
		// page break behavior. The GridBuilder constructor fills in implicit tracks.
		// Grids with grid-template-rows are also eligible (2026-08-29). Previously they fell back to
		// a G0 single column, but real pages that use grid-template-areas almost always
		// also specify row templates. Fixed-length rows use that height; auto/fr/% rows use the content height
		// (see GridBuilder.bind).
		// Vertical writing is also eligible (2026-10-05, jigensha report 3). Previously, vertical writing other than
		// sideways-lr fell back to a G0 single column, disabling columns, gaps, and alignment. bind uses logical axes,
		// so it also works correctly for vertical writing, placing items at almost the same positions as Chrome.
		return builder instanceof BlockBuilder || builder instanceof TwoPassBlockBuilder;
	}

	/** Starts a GridBuilder (eligibility must already be checked). */
	public static GridBuilder start(final Builder builder, final GridBox gridBox) {
		return new GridBuilder(builder, gridBox);
	}
}
