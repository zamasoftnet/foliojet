package jp.cssj.test.unit.fragment;

import java.util.function.DoubleUnaryOperator;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.ColumnBalancer;

/**
 * Tests for ColumnBalancer (capacity search for column balancing) (M5-B).
 */
public class ColumnBalancerTest extends TestCase {
	/** Create a round-down oracle from a sequence of boundaries. */
	private static DoubleUnaryOperator floorOf(final double... boundaries) {
		return x -> {
			double result = 0;
			for (final double b : boundaries) {
				if (b > x + 0.5) {
					break;
				}
				result = b;
			}
			return result;
		};
	}

	/** A sequence of boundaries with uniform line height. */
	private static double[] uniform(final int count, final double height) {
		final double[] boundaries = new double[count];
		for (int i = 0; i < count; ++i) {
			boundaries[i] = (i + 1) * height;
		}
		return boundaries;
	}

	public void testUniformOddLines() {
		// 21 lines (height 10) in 2 columns: optimum is 11/10 lines = capacity 110.
		final double[] b = uniform(21, 10);
		final double capacity = ColumnBalancer.balance(floorOf(b), 210, 2);
		assertTrue("capacity=" + capacity, capacity > 105 && capacity <= 110.5);
		// Simulate an actual cut: round down to a boundary within capacity for the first column.
		final double c1 = floorOf(b).applyAsDouble(capacity);
		assertTrue("last column overflows: " + (210 - c1) + " > " + capacity, 210 - c1 <= capacity + 0.5);
	}

	public void testUniformThreeColumns() {
		// 10 lines (height 12) in 3 columns: 4/3/3 = capacity 48.
		final double[] b = uniform(10, 12);
		final double capacity = ColumnBalancer.balance(floorOf(b), 120, 3);
		final DoubleUnaryOperator floor = floorOf(b);
		final double c1 = floor.applyAsDouble(capacity);
		final double c2 = floor.applyAsDouble(c1 + capacity);
		assertTrue("last column overflows", 120 - c2 <= capacity + 0.5);
		assertTrue("capacity should be minimal: " + capacity, capacity <= 48.5);
	}

	public void testExactFit() {
		// 20 lines (height 10) in 2 columns: exactly 100/100.
		final double[] b = uniform(20, 10);
		final double capacity = ColumnBalancer.balance(floorOf(b), 200, 2);
		assertEquals(100, capacity, 0.5);
	}

	public void testUnevenBoundaries() {
		// Nonuniform boundaries (a sequence of paragraphs containing a large figure).
		final double[] b = { 20, 40, 140, 160, 180, 200 };
		final double capacity = ColumnBalancer.balance(floorOf(b), 200, 2);
		final double c1 = floorOf(b).applyAsDouble(capacity);
		assertTrue("last column overflows: capacity=" + capacity + " c1=" + c1, 200 - c1 <= capacity + 0.5);
	}

	public void testUnbreakableContent() {
		// Content with no boundaries (unsplittable): the only option is to proceed with equal division.
		final double capacity = ColumnBalancer.balance(floorOf(), 100, 2);
		assertTrue("capacity=" + capacity, capacity >= 50 - 0.5);
	}

	public void testSingleColumn() {
		assertEquals(100, ColumnBalancer.balance(floorOf(uniform(10, 10)), 100, 1), 0.5);
	}

	public void testEmptyContent() {
		assertEquals(0, ColumnBalancer.balance(floorOf(), 0, 3), 0.01);
	}
}
