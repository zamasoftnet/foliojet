package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Background;
import net.zamasoft.foliojet.layout.box.params.FirstLineParams;

/**
 * A template that freezes the contents of {@link FirstLineParams} and materializes an independent,
 * fresh {@code FirstLineParams} on each call (introduced 2026-07-22, M6d-A3b Stage1).
 * When {@code BlockParams.firstLineStyle} is non-null, uses this type to freeze/materialize it recursively.
 *
 * <p>
 * Holds {@link LineParamsFields} (shared ancestor fields) plus {@code background}
 * (the existing {@code Background} implementation has only final fields and is effectively immutable;
 * no copy needed).
 * Replaced with an immutable record in Stage2, 2026-07-22.
 * </p>
 */
public record FirstLineParamsTemplate(LineParamsFields common, Background background) {
	public static FirstLineParamsTemplate freeze(final FirstLineParams source) {
		return new FirstLineParamsTemplate(LineParamsFields.freeze(source), source.background);
	}

	/** Returns a fresh {@code FirstLineParams} on each call (multiple calls do not affect one another). */
	public FirstLineParams materialize() {
		final FirstLineParams p = new FirstLineParams();
		this.common.materializeInto(p);
		p.background = this.background;
		return p;
	}
}
