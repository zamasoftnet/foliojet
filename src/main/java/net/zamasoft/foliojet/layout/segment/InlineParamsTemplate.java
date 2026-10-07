package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * A template that freezes the contents of {@link InlineParams}
 * (the {@code Params} implementation used by {@link BoxKind#INLINE} ;
 * directly extends {@code AbstractTextParams} and has no line-specific fields)
 * and materializes an independent, fresh {@code InlineParams} on each call
 * (introduced 2026-07-22, M6d-A3b Stage1).
 *
 * <p>
 * {@link TextParamsFields} handles ancestor ({@code Params}/{@code AbstractTextParams}) fields.
 * {@code frame} needs no copy: the existing {@code RectFrame} implementation has only final fields
 * and is effectively immutable (replaced with an immutable record in Stage2, 2026-07-22).
 * </p>
 */
public record InlineParamsTemplate(TextParamsFields common, RectFrame frame) {
	public static InlineParamsTemplate freeze(final InlineParams source) {
		return new InlineParamsTemplate(TextParamsFields.freeze(source), source.frame);
	}

	/**
	 * Returns the frozen writing direction (E-6 increment 3b-4;
	 * {@code LayoutSource.containsMixedFlow} reads it from frozen Starts).
	 */
	public WritingMode flow() {
		return this.common.flow();
	}

	/** Returns a fresh {@code InlineParams} on each call (multiple calls do not affect one another). */
	public InlineParams materialize() {
		final InlineParams p = new InlineParams();
		this.common.materializeInto(p);
		p.frame = this.frame;
		return p;
	}
}
