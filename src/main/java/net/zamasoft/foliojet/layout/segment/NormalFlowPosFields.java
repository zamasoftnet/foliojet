package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractNormalFlowPos;
import net.zamasoft.foliojet.layout.box.params.ClearMode;

/**
 * Freeze/materialize processing for the field ({@code clear}) that {@code AbstractNormalFlowPos}
 * adds to {@code AbstractBlockLevelPos} .
 * Introduced 2026-07-22, M6d-A3b; package-private and shared by
 * {@link FlowPosTemplate} and {@link FloatPosTemplate} .
 * Delegates ancestor ({@code AbstractStaticPos}/{@code AbstractBlockLevelPos}) fields
 * to {@link BlockLevelPosFields} (composition).
 *
 * <p>
 * All fields in existing implementations (value classes/enums) have been confirmed effectively immutable,
 * so defensive copies are unnecessary; simply retain and write back values unchanged.
 * </p>
 */
record NormalFlowPosFields(BlockLevelPosFields common, ClearMode clear) {
	static NormalFlowPosFields freeze(final AbstractNormalFlowPos source) {
		return new NormalFlowPosFields(BlockLevelPosFields.freeze(source), source.clear);
	}

	void materializeInto(final AbstractNormalFlowPos target) {
		this.common.materializeInto(target);
		target.clear = this.clear;
	}
}
