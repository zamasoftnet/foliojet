package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.content.VerticalAlignPolicy;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.Offset;

/**
 * A template that freezes the contents of {@link InlinePos}
 * (positioning parameters used by {@link BoxKind#INLINE} )
 * and materializes an independent, fresh {@code InlinePos} on each call
 * (introduced 2026-07-22, M6d-A3b Stage1).
 *
 * <p>
 * Retains {@code offset} (from ancestor {@code AbstractStaticPos} , a confirmed immutable value class)
 * and {@code lineHeight} (primitive) unchanged.
 * {@code verticalAlign} ({@code VerticalAlignPolicy}) is a stateless policy object representing behavior
 * (the {@code CSSVerticalAlignPolicy} implementation is used as shared singletons such as `BASELINE_POLICY`).
 * Treats it as a shareable immutable service, like {@code FontManager} , and retains the reference without
 * copying (replaced with an immutable record in Stage2, 2026-07-22).
 * </p>
 */
public record InlinePosTemplate(Offset offset, VerticalAlignPolicy verticalAlign, double lineHeight) {
	public static InlinePosTemplate freeze(final InlinePos source) {
		return new InlinePosTemplate(source.offset, source.verticalAlign, source.lineHeight);
	}

	/** Returns a fresh {@code InlinePos} on each call (multiple calls do not affect one another). */
	public InlinePos materialize() {
		final InlinePos pos = new InlinePos();
		pos.offset = this.offset;
		pos.verticalAlign = this.verticalAlign;
		pos.lineHeight = this.lineHeight;
		return pos;
	}
}
