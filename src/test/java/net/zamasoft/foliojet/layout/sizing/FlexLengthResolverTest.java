package net.zamasoft.foliojet.layout.sizing;

import java.util.List;

import junit.framework.TestCase;

/**
 * §9.7伸縮解決の純粋計算テストです(Flex F1c——
 * consult-codex-2026-08-02-flexbox.txt Q3の検証条件: grow/shrink、
 * scaled shrink、factor合計&lt;1、事前freeze、min/max violation反復、
 * 0防御、gap、outer margin)。
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

	/** 均等grow。 */
	public void testGrowEqual() {
		assertSizes(new double[] { 150, 150 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(100, 1, 1)), 300, 0));
	}

	/** growはfactor比例(§9.7.9.c)。 */
	public void testGrowProportional() {
		assertSizes(new double[] { 125, 175 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(100, 3, 1)), 300, 0));
	}

	/** factor合計<1はinitial free space×合計で縮小(§9.7.9.b)。 */
	public void testGrowSumFactorsBelowOne() {
		assertSizes(new double[] { 150 }, FlexLengthResolver.resolve(List.of(simple(100, 0.5, 1)), 200, 0));
	}

	/** shrinkはscaled factor(factor×inner base)比例(§9.7.9.c)。 */
	public void testShrinkScaled() {
		assertSizes(new double[] { 80, 160 },
				FlexLengthResolver.resolve(List.of(simple(100, 0, 1), simple(200, 0, 1)), 240, 0));
	}

	/** max violationのfreezeと再配分(§9.7.9.e)。 */
	public void testMaxViolationRedistributes() {
		final FlexItemMetrics capped = item(100, 100, 0, 120, 0, 1, 1);
		assertSizes(new double[] { 120, 180 },
				FlexLengthResolver.resolve(List.of(capped, simple(100, 1, 1)), 300, 0));
	}

	/** min violationのfreezeと再配分。 */
	public void testMinViolationRedistributes() {
		final FlexItemMetrics floored = item(100, 100, 90, Double.POSITIVE_INFINITY, 0, 0, 1);
		assertSizes(new double[] { 90, 150 },
				FlexLengthResolver.resolve(List.of(floored, simple(200, 0, 1)), 240, 0));
	}

	/** 方向不一致(base>hypothetical、伸長)は事前freeze(§9.7.3)。 */
	public void testDirectionMismatchPreFreeze() {
		final FlexItemMetrics clampedDown = item(300, 100, 0, 100, 0, 1, 1);
		assertSizes(new double[] { 100, 150 },
				FlexLengthResolver.resolve(List.of(clampedDown, simple(100, 1, 1)), 250, 0));
	}

	/** factor 0はhypotheticalで事前freeze。 */
	public void testZeroFactorFrozen() {
		assertSizes(new double[] { 100, 200 },
				FlexLengthResolver.resolve(List.of(simple(100, 0, 1), simple(100, 1, 1)), 300, 0));
	}

	/** gapはfree spaceから先に控除(F2c予備)。 */
	public void testGapReducesFreeSpace() {
		assertSizes(new double[] { 150, 150 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(100, 1, 1)), 320, 20));
	}

	/** outer margin(outerMainExtra)はfree space計算に入る(§9.7.4)。 */
	public void testOuterExtraCountsAgainstFreeSpace() {
		assertSizes(new double[] { 150, 150 }, FlexLengthResolver.resolve(
				List.of(item(100, 100, 0, Double.POSITIVE_INFINITY, 10, 1, 1),
						item(100, 100, 0, Double.POSITIVE_INFINITY, 10, 1, 1)),
				320, 0));
	}

	/** inner base全0のshrinkは0除算せずbase維持。 */
	public void testShrinkAllZeroBases() {
		assertSizes(new double[] { 0, 0 },
				FlexLengthResolver.resolve(List.of(simple(0, 0, 1), simple(0, 0, 1)), -10, 0));
	}

	/** ちょうど収まるときは全item=base(remaining 0)。 */
	public void testExactFit() {
		assertSizes(new double[] { 100, 200 },
				FlexLengthResolver.resolve(List.of(simple(100, 1, 1), simple(200, 1, 1)), 300, 0));
	}
}
