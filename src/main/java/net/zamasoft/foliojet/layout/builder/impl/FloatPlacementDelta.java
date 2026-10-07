package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.constraint.AxisSpan;

/**
 * Placement plan for a new float (introduced 2026-07-23, exclusion area P1 increment 3 —
 * design in `design consultation`).
 *
 * <p>
 * A value computed without side effects by {@code BlockBuilder.tryFloatPlacement}.
 * Layout state remains entirely unchanged until commit; trying and discarding the plan
 * is sufficient rollback (the design decision is to create neither an undo log nor a
 * transaction). All coordinates are absolute physical positions within the formatting
 * context. This is a short-lived value; the contract requires committing it on the same
 * builder with no intervening layout operation (no Flow owner, generation, or rollback closure).
 * </p>
 */
record FloatPlacementDelta(IFloatBox box, FloatSide side, AxisSpan lineSpan, AxisSpan pageSpan,
		FloatCommitKind kind) {
}
