package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.Offset;

/**
 * Freeze/materialize processing for the sole field ({@code offset}) in {@code AbstractStaticPos}
 * (introduced 2026-07-22, M6d-A3b; package-private, composed by {@link BlockLevelPosFields} ).
 *
 * <p>
 * {@code offset} needs no copy and is retained unchanged:
 * the existing {@code Offset} implementation has only final fields and is effectively immutable.
 * </p>
 */
record PosFields(Offset offset) {
	static PosFields freeze(final AbstractStaticPos source) {
		return new PosFields(source.offset);
	}

	void materializeInto(final AbstractStaticPos target) {
		target.offset = this.offset;
	}
}
