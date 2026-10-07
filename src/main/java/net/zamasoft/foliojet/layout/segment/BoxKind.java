package net.zamasoft.foliojet.layout.segment;

/**
 * Box kinds in the canonical Segment model (introduced 2026-07-22, M6d-A3a;
 * held by {@link BoxRecipe} and consumed by {@code SourceReplayer} as of 2026-07-25).
 *
 * <p>
 * Values have the same order as
 * {@code net.zamasoft.foliojet.layout.fragment.LayoutSource.BoxKind} ,
 * but this is deliberately a separate type.
 * The M6d-A canonical model (the {@code layout.segment} package) must not depend on the three old
 * representations being unified (including {@code LayoutSource} ),
 * for the same reason that the existing `css.style.Segment` and final `Segment` must not be confused.
 * A one-way adapter handles conversion
 * (M6d-A3c, {@code LayoutSource} → {@code SegmentEvent} ).
 * </p>
 */
public enum BoxKind {
	FLOW, MULTICOL, INLINE, MARKER, FLOAT_BLOCK, INLINE_BLOCK, INSIDE_MARKER,
	/**
	 * Table (TableBox; reconstructs blockBox using shared params and the inner pos).
	 * Removed after the G-1 investigation, then restored with user approval of the table-set implementation
	 * (2026-07-30, revision of the G-1 decision).
	 */
	TABLE, TABLE_ROW_GROUP, TABLE_ROW, TABLE_CELL, TABLE_COLUMN_GROUP, TABLE_COLUMN,
	/** Absolutely positioned block (AbsoluteBlockBox). Added in E-6 increment 4e (2026-07-24). */
	ABSOLUTE,
	/** Grid container (Grid G0c; keep the order aligned with LayoutSource.BoxKind). */
	GRID,
	/**
	 * Table caption (caption recipes C1, 2026-08-01;
	 * consult-codex-2026-08-01-caption-recipe.txt).
	 * A context-dependent kind: requires the corresponding TABLE Start established within the same range
	 * and cannot be the range root (C2's context-complete gate is authoritative).
	 * Appended to preserve existing ordinals.
	 */
	CAPTION,
	/**
	 * Flex container (Flex F0c, 2026-08-02; consult-codex-2026-08-02-flexbox.txt).
	 * Keep the order aligned with LayoutSource.BoxKind. Appended to preserve existing ordinals.
	 */
	FLEX;
}
