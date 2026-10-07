package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.css.value.FlexBasisValue;

/**
 * Flex item sizing and alignment settings (Flex F1a, 2026-08-02;
 * consult-codex-2026-08-02-flexbox.txt Q2). Like {@link GridItemSpec}, occupies one reference in
 * {@link FlowPos} and is also carried into source replay and recipes via FlowPosTemplate
 * (replay determinism). All-default settings share the {@link #DEFAULT} singleton, so the permanent
 * retention cost for non-Flex elements is one reference.
 *
 * <p>
 * {@code minWidthAuto}/{@code minHeightAuto} restore the automatic minimum size (§4.5).
 * Since {@code BlockParams.minSize} converts auto to 0 for ordinary blocks, these preserve the fact
 * that the author has not declared min-width/min-height. alignSelf is parsed in F3c, order in F5a
 * (default values until then).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record FlexItemSpec(double grow, double shrink, FlexBasisValue basis, BoxAlignment alignSelf, int order,
		boolean minWidthAuto, boolean minHeightAuto) {

	/** All defaults (grow 0, shrink 1, basis auto, alignSelf auto, order 0, both minima auto). */
	public static final FlexItemSpec DEFAULT = new FlexItemSpec(0, 1, FlexBasisValue.AUTO_VALUE,
			BoxAlignment.AUTO, 0, true, true);

	public static FlexItemSpec of(final double grow, final double shrink, final FlexBasisValue basis,
			final BoxAlignment alignSelf, final int order, final boolean minWidthAuto,
			final boolean minHeightAuto) {
		if (grow == 0 && shrink == 1 && basis.isAuto() && alignSelf == BoxAlignment.AUTO && order == 0
				&& minWidthAuto && minHeightAuto) {
			return DEFAULT;
		}
		return new FlexItemSpec(grow, shrink, basis, alignSelf, order, minWidthAuto, minHeightAuto);
	}

	public boolean isDefault() {
		return this == DEFAULT;
	}
}
