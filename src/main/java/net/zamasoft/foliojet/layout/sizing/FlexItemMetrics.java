package net.zamasoft.foliojet.layout.sizing;

/**
 * Main-axis measurements of a Flex item (Flex F1b, 2026-08-02:
 * consult-codex-2026-08-02-flexbox.txt Q3). A numerical snapshot of one item, used as input
 * to §9.7 flexible-length resolution (FlexLengthResolver) and line breaking (FlexLineBreaker).
 * All values are inner content-box sizes in pt; {@link FlexItemMetricsResolver}
 * has already normalized box-sizing.
 *
 * <p>
 * {@code outerMainExtra} (margin+border+padding) is separate as required by §9.7:
 * free space and factor selection use outer size, while scaled shrink factors use inner flex base size,
 * so inner sizes alone are insufficient.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record FlexItemMetrics(int sourceIndex, double flexBaseMain, double hypotheticalMain, double minMain,
		double maxMain, double outerMainExtra, double grow, double shrink) {

	/** Outer hypothetical main size (the unit for line breaking and free-space calculation; §9.3). */
	public double outerHypotheticalMain() {
		return this.hypotheticalMain + this.outerMainExtra;
	}

	/** Outer flex base size (the unit for initial free-space calculation; §9.7.4). */
	public double outerBaseMain() {
		return this.flexBaseMain + this.outerMainExtra;
	}
}
