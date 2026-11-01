package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

import junit.framework.TestCase;

/**
 * {@link SVGWriter#number} writes most coordinates without {@link BigDecimal} (2026-10-31: half of what a paged SVG page
 * allocated). Its text must stay what the exact expansion gives.
 */
public class SVGWriterNumberTest extends TestCase {
	private static String exact(final double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return Long.toString((long) value);
		}
		return new BigDecimal(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}

	private static void check(final double value) {
		assertEquals(Double.toString(value), exact(value), SVGWriter.number(value));
		assertEquals(Double.toString(-value), exact(-value), SVGWriter.number(-value));
	}

	public void testSpecialValues() {
		for (final double value : new double[] { 0, 1, 0.5, 1.5, 0.1, 0.000001, 0.0000005, 0.0000004, 1e-7, 1e-300, 999999.9999995,
				999999.9999994, 1e6, 1000000.5, 1234567.1234565, 1e11 + 0.5, 1e12 + 0.5, 1e15, 1e15 + 2, 1.0000005, 2.5000005,
				0.1234565, 0.1234575, 72.0000004, 72.0000005, 72.0000006, 841.8897637795275, 595.2755905511812 }) {
			check(value);
		}
	}

	/** Coordinates of every size, with few and with many decimals. */
	public void testRandomValues() {
		final Random random = new Random(20261031);
		for (int i = 0; i < 2_000_000; ++i) {
			final double magnitude = Math.pow(10, random.nextInt(19) - 9);
			check(random.nextDouble() * magnitude);
			check(Math.round(random.nextDouble() * 1e4) / 1e4 * magnitude);
		}
	}

	/** Values on the rounding ties and next to them, where the product in floating point could tip over. */
	public void testTies() {
		final Random random = new Random(1031);
		for (int i = 0; i < 2_000_000; ++i) {
			final double tie = (random.nextInt(1 << 30) + 0.5) / 1e6 * (i % 2 == 0 ? 1 : 1e-3);
			check(tie);
			check(Math.nextUp(tie));
			check(Math.nextDown(tie));
		}
	}
}
