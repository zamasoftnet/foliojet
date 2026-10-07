package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.Align;
import net.zamasoft.foliojet.layout.box.params.FlowPos;

/**
 * A template that freezes the contents of {@link FlowPos}
 * (normal-flow positioning parameters used by {@link BoxKind#FLOW} /{@link BoxKind#MULTICOL})
 * and materializes an independent, fresh {@code FlowPos} on each call
 * (introduced 2026-07-22, M6d-A3b Stage1).
 *
 * <p>
 * {@link NormalFlowPosFields} (shared with `FloatPosTemplate`) handles ancestor fields
 * ({@code AbstractNormalFlowPos}/{@code AbstractBlockLevelPos}/{@code AbstractStaticPos}).
 * Retains {@code align} /{@code columnSpan} (specific to `FlowPos`) unchanged:
 * their existing implementations are value classes or primitives and effectively immutable.
 * The {@code Pos} hierarchy has no truly mutable fields like the {@code AffineTransform} /arrays in the
 * {@code Params} family, so defensive copies are unnecessary
 * (replaced with an immutable record in Stage2, 2026-07-22).
 * </p>
 */
public record FlowPosTemplate(NormalFlowPosFields common, Align align, byte columnSpan,
		net.zamasoft.foliojet.layout.box.params.GridItemSpec gridItem,
		net.zamasoft.foliojet.layout.box.params.FlexItemSpec flexItem) {
	public static FlowPosTemplate freeze(final FlowPos source) {
		return new FlowPosTemplate(NormalFlowPosFields.freeze(source), source.align, source.columnSpan,
				source.gridItem, source.flexItem);
	}

	/** Returns a fresh {@code FlowPos} on each call (multiple calls do not affect one another). */
	public FlowPos materialize() {
		final FlowPos pos = new FlowPos();
		this.common.materializeInto(pos);
		pos.align = this.align;
		pos.columnSpan = this.columnSpan;
		pos.gridItem = this.gridItem;
		pos.flexItem = this.flexItem;
		return pos;
	}
}
