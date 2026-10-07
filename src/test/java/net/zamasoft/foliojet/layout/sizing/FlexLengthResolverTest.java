package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;

/**
 * Pure calculation tests for flexible length resolution in §9.7 (Flex F1c;
 * validation conditions in consult-codex-2026-08-02-flexbox.txt Q3: grow/shrink,
 * scaled shrink, factor sum&lt;1, pre-freezing, iterative min/max violations,
 * zero guards, gaps, outer margins).
 */
public class FlexLengthResolverTest extends TestCase {

	private static FlexItemMetrics item(final double base, final double hypothetical, final double min,
			final double max, final double extra, final double grow, final double shrink) {
		return new FlexItemMetrics(0, base, hypothetical, min, max, extra, grow, shrink);
	}

	private static FlexItemMetrics simple(final double base, final double grow, final double shrink) {
		return item(base, base, 0, Double.POSITIVE_INFINITY, 0, grow, shrink);
	}

	private static void assertSizes(final double[] expected, final double[] actual) {
		assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; ++i) {
			assertEquals("[" + i + "]", expected[i], actual[i], 1e-9);
		}
	}

	public void testEmpty() {
		assertEquals(0, FlexLengthResolver.resolve(List.of(), 100, 0).length);
	}

	/** Equal growth. */
	public void testGrowEqual() {
		assertSizes(new double[] { 150, 150 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(100, 1, 1)), 300, 0));
	}

	/** Growth is proportional to factors (§9.7.9.c). */
	public void testGrowProportional() {
		assertSizes(new double[] { 125, 175 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(100, 3, 1)), 300, 0));
	}

	/** A factor sum<1 reduces free space to initial free space × sum (§9.7.9.b). */
	public void testGrowSumFactorsBelowOne() {
		assertSizes(new double[] { 150 }, FlexLengthResolver.resolve(List.of(simple(100, 0.5, 1)), 200, 0));
	}

	/** Shrinkage is proportional to scaled factors (factor × inner base) (§9.7.9.c). */
	public void testShrinkScaled() {
		assertSizes(new double[] { 80, 160 },
				FlexLengthResolver.resolve(List.of(simple(100, 0, 1), simple(200, 0, 1)), 240, 0));
	}

	/** Freeze and redistribute on a max violation (§9.7.9.e). */
	public void testMaxViolationRedistributes() {
		final FlexItemMetrics capped = item(100, 100, 0, 120, 0, 1, 1);
		assertSizes(new double[] { 120, 180 },
				FlexLengthResolver.resolve(List.of(capped, simple(100, 1, 1)), 300, 0));
	}

	/** Freeze and redistribute on a min violation. */
	public void testMinViolationRedistributes() {
		final FlexItemMetrics floored = item(100, 100, 90, Double.POSITIVE_INFINITY, 0, 0, 1);
		assertSizes(new double[] { 90, 150 },
				FlexLengthResolver.resolve(List.of(floored, simple(200, 0, 1)), 240, 0));
	}

	/** Pre-freeze on a direction mismatch (base>hypothetical during growth) (§9.7.3). */
	public void testDirectionMismatchPreFreeze() {
		final FlexItemMetrics clampedDown = item(300, 100, 0, 100, 0, 1, 1);
		assertSizes(new double[] { 100, 150 },
				FlexLengthResolver.resolve(List.of(clampedDown, simple(100, 1, 1)), 250, 0));
	}

	/** Pre-freeze a zero-factor item at its hypothetical size. */
	public void testZeroFactorFrozen() {
		assertSizes(new double[] { 100, 200 },
				FlexLengthResolver.resolve(List.of(simple(100, 0, 1), simple(100, 1, 1)), 300, 0));
	}

	/** Deduct gaps from free space first (preparation for F2c). */
	public void testGapReducesFreeSpace() {
		assertSizes(new double[] { 150, 150 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(100, 1, 1)), 320, 20));
	}

	/** Include outer margins (outerMainExtra) in free-space calculation (§9.7.4). */
	public void testOuterExtraCountsAgainstFreeSpace() {
		assertSizes(new double[] { 150, 150 }, FlexLengthResolver.resolve(
				List.of(item(100, 100, 0, Double.POSITIVE_INFINITY, 10, 1, 1),
						item(100, 100, 0, Double.POSITIVE_INFINITY, 10, 1, 1)),
				320, 0));
	}

	/** Shrinking with all inner bases at 0 preserves bases without division by zero. */
	public void testShrinkAllZeroBases() {
		assertSizes(new double[] { 0, 0 },
				FlexLengthResolver.resolve(List.of(simple(0, 0, 1), simple(0, 0, 1)), -10, 0));
	}

	/** On an exact fit, every item equals its base (remaining 0). */
	public void testExactFit() {
		assertSizes(new double[] { 100, 200 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(200, 1, 1)), 300, 0));
	}
}
