package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.box.params.FootnotePos;
import net.zamasoft.foliojet.layout.box.params.PageFloatPos;
import net.zamasoft.foliojet.layout.box.params.PageMarginNotePos;
import net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams;

/**
 * A template that freezes the contents of {@link FloatPos}
 * (float positioning parameters used by {@link BoxKind#FLOAT_BLOCK} )
 * and materializes an independent, fresh {@code FloatPos} on each call
 * (introduced 2026-07-22, M6d-A3b).
 *
 * <p>
 * {@code FloatPos} extends the same {@code AbstractNormalFlowPos} as {@code FlowPos} ,
 * so {@link NormalFlowPosFields} (shared with `FlowPosTemplate`) handles ancestor fields.
 * Retains {@code floating} ({@code FloatSide}, an enum) unchanged
 * (replaced with an immutable record in Stage2, 2026-07-22).
 * {@code shapeOutside} ({@code shape-outside}, 2026-08-29) is an immutable value with all-final fields,
 * so its reference is retained unchanged.
 * Without carrying it here, shapes would silently disappear from floats after segment replay.
 * </p>
 */

public record FloatPosTemplate(NormalFlowPosFields common, FloatSide floating, Kind kind,
		ShapeOutsideParams shapeOutside) {
	public enum Kind {
		NORMAL, FOOTNOTE, PAGE_TOP, PAGE_BOTTOM, PAGE_BLOCK_START, PAGE_BLOCK_END, PAGE_NOTE_START, PAGE_NOTE_END
	}

	public static FloatPosTemplate freeze(final FloatPos source) {
		final Kind kind = source instanceof FootnotePos ? Kind.FOOTNOTE
				: source instanceof PageMarginNotePos note ? note.start ? Kind.PAGE_NOTE_START : Kind.PAGE_NOTE_END
				: source instanceof PageFloatPos pageFloat
						? pageFloat.physical ? pageFloat.top ? Kind.PAGE_TOP : Kind.PAGE_BOTTOM
								: pageFloat.top ? Kind.PAGE_BLOCK_START : Kind.PAGE_BLOCK_END
						: Kind.NORMAL;
		return new FloatPosTemplate(NormalFlowPosFields.freeze(source), source.floating, kind, source.shapeOutside);
	}

	/** Returns a fresh {@code FloatPos} on each call (multiple calls do not affect one another). */
	public FloatPos materialize() {
		final FloatPos pos = switch (this.kind) {
		case NORMAL -> new FloatPos();
		case FOOTNOTE -> new FootnotePos();
		case PAGE_TOP -> new PageFloatPos(true, true);
		case PAGE_BOTTOM -> new PageFloatPos(false, true);
		case PAGE_BLOCK_START -> new PageFloatPos(true, false);
		case PAGE_BLOCK_END -> new PageFloatPos(false, false);
		case PAGE_NOTE_START -> new PageMarginNotePos(true);
		case PAGE_NOTE_END -> new PageMarginNotePos(false);
		};
		this.common.materializeInto(pos);
		pos.floating = this.floating;
		pos.shapeOutside = this.shapeOutside;
		return pos;
	}
}
