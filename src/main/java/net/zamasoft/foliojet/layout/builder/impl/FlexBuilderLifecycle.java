package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.params.FlexDirection;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * Entry point for the Flex construction lifecycle (Flex F1d, the same thin structure as
 * {@code GridBuilderLifecycle}). Ineligible cases fall back to single-column flow (F0).
 */
public final class FlexBuilderLifecycle {
	private FlexBuilderLifecycle() {
		// Static utility
	}

	/**
	 * Checks whether Flex row placement applies (consult-codex-2026-08-02-
	 * flexbox.txt, "phased fallback rules", F1: TB+row+nowrap only).
	 * Eligible hosts include BlockBuilder and TwoPass (F1f: record the execution plan as
	 * a FlexEvent and bind after width resolution; column = F4, reverse/wrap = F2/F5,
	 * vertical writing = F6).
	 */
	public static boolean eligible(final FlexBox flexBox, final Builder builder) {
		final FlexParams params = flexBox.getFlexParams();
		if (!params.flexDirection.isRow()) {
			// F4b/F4d: column requires a definite main axis (absolute length). Content-dependent basis
			// is permanently outside the subset by the F4c decision; the bindColumn classifier rejects it.
			// wrap additionally requires an absolute cross (line-axis) length (F4c recommendation:
			// eligibility requires sizing the column width on the cross axis in advance).
			if (params.size.getPageType(params.flow) != net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				return false;
			}
			if (params.flexWrap != FlexWrap.NOWRAP
					&& params.size.getLineType(params.flow) != net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				return false;
			}
			return builder instanceof BlockBuilder || builder instanceof TwoPassBlockBuilder;
		}
		final net.zamasoft.foliojet.layout.box.params.LengthType crossType = params.flow.isVertical()
				? params.size.getWidthType()
				: params.size.getHeightType();
		if (params.flexWrap.isWrap()
				&& crossType != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
				&& crossType != net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			// F3d: a definite cross size for wrap is allowed only for absolute lengths (% cross sizes
			// remain outside the subset).
			return false;
		}
		return builder instanceof BlockBuilder || builder instanceof TwoPassBlockBuilder;
	}

	/** Starts a FlexBuilder (eligibility must already be checked). */
	public static FlexBuilder start(final Builder builder, final FlexBox flexBox) {
		return new FlexBuilder(builder, flexBox);
	}
}
