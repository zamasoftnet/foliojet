package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * A template that freezes the contents of {@link BlockParams}
 * (a representative {@code Params} implementation used by M6d-A's {@link BoxKind#FLOW} , etc.)
 * and materializes an independent, fresh {@code BlockParams} on each call.
 * Introduced 2026-07-22, M6d-A3b; replaced with an immutable record in Stage2 later on 2026-07-22.
 *
 * <p>
 * {@link BlockParamsFields} handles the fields, shared with the template for
 * `TableParams` (which directly extends `BlockParams`).
 * </p>
 */
public record BlockParamsTemplate(BlockParamsFields fields) {
	public static BlockParamsTemplate freeze(final BlockParams source) {
		return new BlockParamsTemplate(BlockParamsFields.freeze(source));
	}

	/**
	 * Returns the frozen writing direction (E-6 increment 3b-4;
	 * {@code LayoutSource.containsMixedFlow} reads it from frozen Starts).
	 */
	public WritingMode flow() {
		return this.fields.common().text().flow();
	}

	/**
	 * Returns whether it has multi-column layout (column-count of at least 2)
	 * ({@code LayoutSource}'s multi-column index reads it from frozen Starts; 2026-08-21).
	 */
	public boolean hasMultipleColumns() {
		return this.fields.columns() != null && this.fields.columns().count >= 2;
	}

	/** Returns a fresh {@code BlockParams} on each call (multiple calls do not affect one another). */
	public BlockParams materialize() {
		final BlockParams p = new BlockParams();
		this.fields.materializeInto(p);
		return p;
	}

	/** Restores only shared fields for measurement wrappers, etc., preserving Grid/Flex-specific fields. */
	public void materializeInto(final BlockParams target) {
		this.fields.materializeInto(target);
	}
}
