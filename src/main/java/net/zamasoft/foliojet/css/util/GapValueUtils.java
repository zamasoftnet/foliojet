package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Entry point for reading {@code row-gap} / {@code column-gap} / {@code gap} values.
 *
 * <p>
 * <b>Three places read the same form</b> ({@code RowGap}, {@code ColumnGap},
 * {@code GapShorthand}), so centralize conversion here. Scattered handling made it
 * easy to <b>change only one place while believing calc() support had been added</b>.
 *
 * <p>
 * <b>Always allow calc()</b> (2026-08-04, real-world corpus wave 9). Until then,
 * {@code gap} read only plain lengths and discarded the whole declaration for
 * {@code calc()}. **Tailwind CSS v4 expands {@code gap-4} to
 * {@code gap: calc(var(--spacing) * 4)}**, so <b>gaps on Tailwind v4 pages all became zero</b>.
 * This was harder to notice because the same {@code calc()} worked in {@code margin}
 * and {@code padding}. Regression: {@code files/unittest/0510-flex/gap-calc.html}.
 *
 * @author MIYABE Tatsuhiko
 */
public final class GapValueUtils {

	private GapValueUtils() {
		// Utility
	}

	/**
	 * Reads a gap value ({@code <length>} or {@code calc()}). Negative values and
	 * unitless numbers are invalid (as specified). Returns {@code null} if unreadable.
	 */
	public static Value toGap(UserAgent ua, CssToken token) {
		final Value calc = CalcValueUtils.toCalc(ua, token);
		if (calc != null) {
			// A <length> context rejects unitless numeric calc() results (e.g. calc(1 + 2))
			if (calc instanceof net.zamasoft.foliojet.css.value.RealValue) {
				return null;
			}
			if (calc instanceof net.zamasoft.foliojet.css.value.QuantityValue quantity && quantity.isNegative()) {
				return null;
			}
			return calc;
		}
		final Value value = ValueUtils.toLength(ua, token);
		if (value instanceof net.zamasoft.foliojet.css.value.QuantityValue quantity && quantity.isNegative()) {
			return null;
		}
		return value;
	}
}
