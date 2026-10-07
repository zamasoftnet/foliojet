package net.zamasoft.foliojet.layout.fragment;

/**
 * Typed result of {@code AbstractContainerBox.prepareColumnCut()} (added 2026-07-21, M6b Phase B B4). Replaces the
 * old {@code newColumn()} return value's three meanings, null/self/other (interpreted by callers through identity
 * comparisons), with explicit types.
 */
public sealed interface ColumnCutResult {
	/** Keeps everything in the current active column (formerly null). */
	record Keep() implements ColumnCutResult {
	}

	/** Sends everything to the next column (formerly returned the active column itself). */
	record Move() implements ColumnCutResult {
	}

	/** Cut internally. {@link PreparedColumnCut} is not yet committed to the owner. */
	record Cut(PreparedColumnCut prepared) implements ColumnCutResult {
	}
}
