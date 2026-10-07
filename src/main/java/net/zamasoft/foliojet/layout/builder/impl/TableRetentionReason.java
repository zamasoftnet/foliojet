package net.zamasoft.foliojet.layout.builder.impl;

/**
 * Reasons a table requires Retained (an execution plan that retains the entire table before committing)
 * (C4-B, 2026-07-19. An external design review established that current routing is not fixed versus
 * auto, so the single boolean from the old `LayoutUtils.needsIntrinsicSizing()` was replaced
 * with typed reasons).
 *
 * @author MIYABE Tatsuhiko
 */
public enum TableRetentionReason {
	/** table-layout:auto: measuring column widths requires access to all cells (CSS2.1 §17.5.2). */
	AUTO_COLUMNS,
	/** The line-axis size (width in horizontal writing) is auto: measurement is required. */
	AUTO_LINE_SIZE,
	/** The page-axis size (height in horizontal writing) is specified: row heights must be distributed across the table. */
	SPECIFIED_PAGE_SIZE,
	/** Placement other than normal flow (FLOW): not the FLOW that IncrementalTableBuilder.startLayout() assumes. */
	OUT_OF_FLOW,
	/** Inside a nested measurement pass (TwoPassBlockBuilder): builder.isMain() is false. */
	NESTED_LAYOUT,
	/**
	 * The table's own writing mode has axes different from the currently open flow
	 * (page/column context; horizontal writing ⇄ vertical writing): M6b Phase B5e (2026-07-21).
	 * {@code IncrementalTableBuilder.pageBreak()} calls {@code
	 * BreakableBuilder.forceBreak()} directly, bypassing the {@code breakDepth} barrier
	 * (which suppresses page breaks inside an orthogonal writing mode) used by normal automatic
	 * page breaks. Handling this case with Incremental therefore actually reaches the legacy
	 * `OpenChain` path, which has only the typed capability barrier
	 * {@code ContinuationCapability.ORTHOGONAL_FLOW} and has not yet been made iterative
	 * (confirmed by observation on 2026-07-21; currently protected only by the emergency
	 * `ContinuationInvariantViolationException` guard). Routing to RETAINED closes this entry point.
	 */
	ORTHOGONAL_WRITING_MODE
}
