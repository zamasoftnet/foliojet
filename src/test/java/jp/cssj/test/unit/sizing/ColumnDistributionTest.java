package jp.cssj.test.unit.sizing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.sizing.ColumnDistribution;
import net.zamasoft.foliojet.layout.sizing.ColumnDistribution.ColumnType;

/**
 * Tests for column-width distribution in css-tables-3.
 */
public class ColumnDistributionTest extends TestCase {
	private static final double DELTA = 1e-9;

	private static ColumnType[] types(ColumnType... types) {
		return types;
	}

	public void testAllAutoFit() {
		// If space permits, AUTO columns expand to max-content width.
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 20 }, new double[] { 30, 50 },
				types(ColumnType.AUTO, ColumnType.AUTO), 100);
		// After targets are reached, distribute the remaining 20 proportionally to current widths (30:50).
		assertEquals(30 + 20 * 30.0 / 80, sizes[0], DELTA);
		assertEquals(50 + 20 * 50.0 / 80, sizes[1], DELTA);
	}

	public void testAllAutoProportionalDeficit() {
		// If surplus space is insufficient, distribute it proportionally to deficits.
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 20 }, new double[] { 30, 50 },
				types(ColumnType.AUTO, ColumnType.AUTO), 55);
		// Distribute the surplus of 25 proportionally to deficits (20:30).
		assertEquals(10 + 25 * 20.0 / 50, sizes[0], DELTA);
		assertEquals(20 + 25 * 30.0 / 50, sizes[1], DELTA);
	}

	public void testMinimumGuaranteed() {
		// Minimum widths are guaranteed even when available width is at or below the sum of minima.
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 20 }, new double[] { 30, 50 },
				types(ColumnType.AUTO, ColumnType.AUTO), 15);
		assertEquals(10, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testPercentPriority() {
		// PERCENT columns expand to their targets before AUTO columns.
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 60, 60 },
				types(ColumnType.PERCENT, ColumnType.AUTO), 80);
		// The PERCENT column reaches 60 first; the remaining 10 goes to the AUTO column.
		assertEquals(60, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testConstrainedBeforeAuto() {
		// CONSTRAINED columns expand to their targets before AUTO columns.
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 40, 60 },
				types(ColumnType.CONSTRAINED, ColumnType.AUTO), 60);
		assertEquals(40, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testExcessGoesToAutoFirst() {
		// After all columns reach their targets, distribute surplus to AUTO columns.
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 20, 20 },
				types(ColumnType.CONSTRAINED, ColumnType.AUTO), 60);
		assertEquals(20, sizes[0], DELTA);
		assertEquals(40, sizes[1], DELTA);
	}

	public void testExcessToConstrainedWhenNoAuto() {
		// If there are no AUTO columns, surplus goes to CONSTRAINED columns.
		double[] sizes = ColumnDistribution.distribute(new double[] { 10, 10 }, new double[] { 20, 20 },
				types(ColumnType.CONSTRAINED, ColumnType.PERCENT), 60);
		assertEquals(40, sizes[0], DELTA);
		assertEquals(20, sizes[1], DELTA);
	}

	public void testExcessEqualSplitWhenZeroWidth() {
		// If all columns receiving surplus have width 0, distribute equally.
		double[] sizes = ColumnDistribution.distribute(new double[] { 0, 0 }, new double[] { 0, 0 },
				types(ColumnType.AUTO, ColumnType.AUTO), 50);
		assertEquals(25, sizes[0], DELTA);
		assertEquals(25, sizes[1], DELTA);
	}

	public void testTargetBelowMinIgnored() {
		// Do not shrink columns whose target width is below their minimum width.
		double[] sizes = ColumnDistribution.distribute(new double[] { 30, 10 }, new double[] { 20, 40 },
				types(ColumnType.PERCENT, ColumnType.AUTO), 70);
		assertEquals(30, sizes[0], DELTA);
		assertEquals(40, sizes[1], DELTA);
	}

	public void testEmpty() {
		double[] sizes = ColumnDistribution.distribute(new double[0], new double[0], types(), 100);
		assertEquals(0, sizes.length);
	}
}
