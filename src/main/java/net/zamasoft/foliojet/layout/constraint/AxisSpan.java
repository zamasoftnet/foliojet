package net.zamasoft.foliojet.layout.constraint;

/**
 * Interval on an axis (line or page direction; added 2026-07-23 when making exclusion spaces ConstraintSpace
 * inputs, based on the design in `design consultation
 * -exclusion-zone-codex.txt`).
 *
 * <p>
 * Retains values on the formatting context's logical line/page axes, not physical x/y (same coordinate system as
 * {@code lineStart}/{@code lineEnd} and {@code pageStart}/{@code pageEnd} of {@code
 * net.zamasoft.foliojet.layout.builder
 * .LayoutContext.Floating}).
 * </p>
 *
 * <p>
 * Deliberately does not validate {@code start <= end}: existing exclusion calculations (such as multicol avoidance
 * in `BlockBuilder.startFlowBlock`) permit negative line sizes when floats overlap heavily and pass them
 * downstream. This value type must faithfully reproduce that behavior (behavior changes are outside this P0 stage's
 * scope).
 * </p>
 */
public record AxisSpan(double start, double end) {
	public double extent() {
		return this.end - this.start;
	}
}
