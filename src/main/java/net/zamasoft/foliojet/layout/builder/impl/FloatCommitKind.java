package net.zamasoft.foliojet.layout.builder.impl;

/**
 * Kinds of placement commits for new floats (introduced 2026-07-23, exclusion area P1
 * increment 2 — design in `design consultation`).
 *
 * <p>
 * Classification result from splitting the former {@code transferFloatToNextPage}
 * (named as a predicate, but with the side effect of adding to {@code breakFloats})
 * into side-effect-free classification ({@code BlockBuilder.classifyFloatPlacement})
 * and a recording hook ({@code BlockBuilder.recordBreakFloat}).
 * Depends only on the measured physical position (overflow relative to the fragment
 * boundary and whether it is at page start), without introducing logical provenance.
 * </p>
 */
enum FloatCommitKind {
	/**
	 * Normal placement: register in the exclusion area ledger without changing {@code breakFloats}
	 * (no overflow, or a REPLACED float retained at page start).
	 */
	PLACED,
	/**
	 * Placement pending a split: register in the exclusion area ledger and add to
	 * {@code breakFloats} (the subsequent {@code splitFloatings} splits an overflowing
	 * BLOCK float at the fragment boundary).
	 */
	SPLIT_AT_BREAK,
	/**
	 * Move intact to the next fragment: add to {@code breakFloats} without registering
	 * in the exclusion area ledger (a BLOCK with avoid, or a REPLACED float not at page start).
	 */
	MOVE_TO_NEXT,
	/**
	 * Defer due to clear: place a float that clears an already deferred float at the
	 * fragment boundary (pageLimit) without searching, and send it to the next fragment.
	 * Add it to {@code breakFloats} without registering in the exclusion area ledger.
	 * Preserve the current rule that updates parent extent only directly under the root,
	 * rather than via normal {@code extendParents} (2026-07-23, codex design:
	 * do not silently normalize this asymmetry in P1).
	 */
	MOVE_BY_CLEAR
}
