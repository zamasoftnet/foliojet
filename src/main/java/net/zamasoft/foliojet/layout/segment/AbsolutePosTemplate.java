package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbsolutePos;
import net.zamasoft.foliojet.layout.box.params.AutoPosition;
import net.zamasoft.foliojet.layout.box.params.Fiducial;
import net.zamasoft.foliojet.layout.box.params.Insets;

/**
 * A template that freezes the contents of {@link AbsolutePos}
 * (absolute-positioning parameters used by {@link ReplacedRecipe
 * .Absolute}) and materializes an independent, fresh {@code AbsolutePos} on each call
 * (introduced 2026-07-22, M6d-A replaced-element support).
 *
 * <p>
 * {@code AbsolutePos} directly implements {@code Pos}
 * (does not extend {@code AbstractStaticPos} and has no {@code offset} ).
 * Its only fields are {@code location} ({@code Insets}, effectively immutable with only final fields)
 * and {@code autoPosition} /{@code fiducial} (enums).
 * Unlike the {@code FlowPos} family, it has no shared ancestor {@code *Fields} helper,
 * so it is self-contained (replaced with an immutable record in Stage2, 2026-07-22).
 * </p>
 */
public record AbsolutePosTemplate(Insets location, AutoPosition autoPosition, Fiducial fiducial) {
	public static AbsolutePosTemplate freeze(final AbsolutePos source) {
		return new AbsolutePosTemplate(source.location, source.autoPosition, source.fiducial);
	}

	/** Returns a fresh {@code AbsolutePos} on each call (multiple calls do not affect one another). */
	public AbsolutePos materialize() {
		final AbsolutePos pos = new AbsolutePos();
		pos.location = this.location;
		pos.autoPosition = this.autoPosition;
		pos.fiducial = this.fiducial;
		return pos;
	}
}
