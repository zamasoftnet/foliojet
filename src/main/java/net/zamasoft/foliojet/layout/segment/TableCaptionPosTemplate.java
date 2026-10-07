package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.CaptionSideMode;
import net.zamasoft.foliojet.layout.box.params.TableCaptionPos;

/**
 * A template that freezes the contents of {@link TableCaptionPos} (table-caption placement
 * parameters used by {@link BoxKind#CAPTION}) and materializes a fresh, independent
 * {@code TableCaptionPos} on each call (caption recipe conversion C1, 2026-08-01:
 * consult-codex-2026-08-01-caption-recipe.txt).
 *
 * <p>
 * Since {@code TableCaptionPos} is {@code FlowPos} plus {@code captionSide},
 * it only needs the same composition as {@link FlowPosTemplate}, with the addition of
 * {@code captionSide} (an immutable enum).
 * </p>
 */
public record TableCaptionPosTemplate(NormalFlowPosFields common, Align align, byte columnSpan,
		net.zamasoft.foliojet.layout.box.params.GridItemSpec gridItem,
		net.zamasoft.foliojet.layout.box.params.FlexItemSpec flexItem, CaptionSideMode captionSide) {
	public static TableCaptionPosTemplate freeze(final TableCaptionPos source) {
		return new TableCaptionPosTemplate(NormalFlowPosFields.freeze(source), source.align, source.columnSpan,
				source.gridItem, source.flexItem, source.captionSide);
	}

	/** Returns a fresh {@code TableCaptionPos} on each call. */
	public TableCaptionPos materialize() {
		final TableCaptionPos pos = new TableCaptionPos();
		this.common.materializeInto(pos);
		pos.align = this.align;
		pos.columnSpan = this.columnSpan;
		pos.gridItem = this.gridItem;
		pos.flexItem = this.flexItem;
		pos.captionSide = this.captionSide;
		return pos;
	}
}
