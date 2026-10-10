package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;

/**
 * A pure calculation deriving Flex item main-axis measurements ({@link FlexItemMetrics}) from
 * authored styles and intrinsic sizes (Flex F1b, 2026-08-02:
 * consult-codex-2026-08-02-flexbox.txt Q3, "basis from TwoPass intrinsics").
 * Initial subset for horizontal-writing rows:
 *
 * <ol>
 * <li>flex base size (§9.2.3): definite basis→that value (without min/max).
 * Resolve percentage basis if the container's main axis is definite; otherwise treat it as auto.
 * basis:auto→the width property; if that is also auto, or basis:content→max-content</li>
 * <li>min-width:auto (§4.5): 0 if scrollable; otherwise
 * min(min-content, definite preferred width) (min-content if preferred is auto),
 * further clamped by a definite max</li>
 * <li>hypothetical main size: clamp base by used min/max</li>
 * </ol>
 *
 * <p>
 * All inputs are in pt. For box-sizing:border-box, subtract the frame (border+padding)
 * from preferred/min/max and resolved percentage basis to normalize to inner content-box sizes
 * (floor at 0). Intrinsic sizes (minContent/maxContent) are already inner sizes.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class FlexItemMetricsResolver {

	private FlexItemMetricsResolver() {
	}

	/**
	 * Input for one item. Pass sizes outside the content box (as authored);
	 * the resolver normalizes them.
	 *
	 * @param preferredMain Main-axis width (auto=NaN)
	 * @param minMain min-width (auto=NaN; {@code FlexItemSpec.minWidthAuto})
	 * @param maxMain max-width (none=+∞)
	 * @param mainFrame Total main-axis border+padding
	 * @param outerMargin Total main-axis margin
	 * @param borderBox Whether box-sizing:border-box applies
	 * @param scrollable Whether overflow is other than visible (excludes the automatic minimum in §4.5)
	 * @param minContent Intrinsic minimum (inner size)
	 * @param maxContent Intrinsic maximum (inner size)
	 * @param containerInnerMain Container's inner main size (indefinite=NaN)
	 */
	public record Input(int sourceIndex, double grow, double shrink, FlexBasisValue basis, double preferredMain,
			double minMain, double maxMain, double mainFrame, double outerMargin, boolean borderBox,
			boolean scrollable, double minContent, double maxContent, double containerInnerMain) {
	}

	public static FlexItemMetrics resolve(final Input in) {
		final double preferred = inner(in.preferredMain(), in);
		double max = inner(in.maxMain(), in);
		// Flex base size (initial subset of §9.2.3).
		double base;
		final Double basisSize = basisSize(in);
		if (basisSize != null) {
			base = Math.max(0, basisSize);
		} else if (!in.basis().isContent() && in.basis().isAuto() && !Double.isNaN(preferred)) {
			// Only auto uses the size property: an unresolved percentage basis behaves as content (§7.2.3)
			base = preferred;
		} else {
			base = in.maxContent();
		}
		// Automatic minimum size (§4.5).
		double min = inner(in.minMain(), in);
		if (Double.isNaN(min)) {
			if (in.scrollable()) {
				min = 0;
			} else {
				min = Double.isNaN(preferred) ? in.minContent() : Math.min(in.minContent(), preferred);
				min = Math.min(min, max);
			}
		}
		// min-width wins over max-width (CSS 2 §10.4; 2026-10-10), here and in the §9.7 clamp, which takes this max.
		// An item with min-width: max-content and max-width: 40px was laid out 40px wide where its contribution to the
		// container (FlexBuilder.lineContributions) and Chrome make it its max-content.
		max = Math.max(max, min);
		// max-content should be finite, but adding sentinels for unresolved percentage replaced elements
		// during intrinsic measurement can saturate to Infinity. Passing Infinity to shrink calculations
		// for a finite container makes scaled factors Infinity/Infinity=NaN, preventing §9.7
		// from progressing. Reduce cyclic intrinsic contributions to the automatic minimum (min-content).
		if (!Double.isFinite(base)) {
			base = Double.isFinite(min) ? Math.max(0, min) : 0;
		}
		final double hypothetical = Math.min(Math.max(base, min), max);
		return new FlexItemMetrics(in.sourceIndex(), base, hypothetical, min, max,
				in.outerMargin() + in.mainFrame(), in.grow(), in.shrink());
	}

	/**
	 * Resolves basis sizes (absolute lengths, percentages, and mixed absolute+percentage calc()).
	 * Returns null for auto/content/unresolved percentages (to the auto path).
	 */
	private static Double basisSize(final Input in) {
		if (in.basis().isAuto() || in.basis().isContent()) {
			return null;
		}
		final QuantityValue size = in.basis().getSize();
		if (size instanceof PercentageValue percent) {
			if (Double.isNaN(in.containerInnerMain())) {
				return null;
			}
			return inner(in.containerInnerMain() * percent.getRatio(), in);
		}
		if (size instanceof AbsoluteLengthValue length) {
			return inner(length.getLength(), in);
		}
		if (size instanceof net.zamasoft.foliojet.css.value.CalcLengthValue calc) {
			// E.g., calc(50% - 16px). As with percentage components, resolve only when the container's
			// main axis is definite (otherwise treat as auto). The old implementation treated all calc
			// values as auto, collapsing flex-basis:calc(50% - 16px) on the asahi.com home page
			// to min-content (2026-08-08).
			if (Double.isNaN(in.containerInnerMain())) {
				return null;
			}
			return inner(calc.getAbsolute() + in.containerInnerMain() * calc.getRatio(), in);
		}
		return null;
	}

	/** Converts a border-box size to an inner content-box size (leaves auto=NaN unchanged). */
	private static double inner(final double size, final Input in) {
		if (Double.isNaN(size) || !in.borderBox()) {
			return size;
		}
		return Math.max(0, size - in.mainFrame());
	}
}
