package net.zamasoft.foliojet.css.value.css3;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;

/**
 * A utility that converts gradient positions and sizes ({@link QuantityValue} objects
 * retained during parsing) to pt once the box to fill is known (2026-08-29).
 *
 * <p>
 * Font-relative lengths such as em are not resolved in the computed value of
 * {@code background-image} (it retains the values as is), so they cannot be made absolute
 * even here. Treats them as zero in that case (a documented approximation).
 * </p>
 */
final class GradientGeometry {
	private GradientGeometry() {
		// unused
	}

	/** Resolves a quantity against the reference length {@code ref} (pt). Zero if it cannot be resolved. */
	static double resolve(final QuantityValue value, final double ref) {
		if (value == null) {
			return 0;
		}
		if (value instanceof PercentageValue p) {
			return p.getRatio() * ref;
		}
		if (value instanceof AbsoluteLengthValue a) {
			return a.getLength();
		}
		if (value instanceof CalcLengthValue c) {
			return c.getAbsolute() + c.getRatio() * ref;
		}
		return 0;
	}

	/** A short representation for dumps. */
	static String describe(final QuantityValue value) {
		if (value == null) {
			return "auto";
		}
		if (value instanceof PercentageValue p) {
			return String.format(java.util.Locale.ROOT, "%.0f%%", p.getPercentage());
		}
		if (value instanceof AbsoluteLengthValue a) {
			return String.format(java.util.Locale.ROOT, "%.2fpt", a.getLength());
		}
		if (value instanceof CalcLengthValue c) {
			return String.format(java.util.Locale.ROOT, "calc(%.2fpt+%.0f%%)", c.getAbsolute(), c.getRatio() * 100);
		}
		return value.toString();
	}
}
