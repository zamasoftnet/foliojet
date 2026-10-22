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

	/** The balancing oracle of lines between the boundaries: no boundary after the column start means no line fits. */
	private static ColumnBalancer.CutOracle linesOf(final double... boundaries) {
		final DoubleUnaryOperator floor = floorOf(boundaries);
		return (from, x) -> floor.applyAsDouble(x);
	}

	/** The balancing oracle of content whose boundaries it does not see (a table): taken as cut where proposed. */
	private static ColumnBalancer.CutOracle unseenOf(final double... boundaries) {
		final DoubleUnaryOperator floor = floorOf(boundaries);
		return (from, x) -> {
			final double cut = floor.applyAsDouble(x);
			return cut > from + 0.5 ? cut : x;
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
		final double capacity = ColumnBalancer.balance(linesOf(b), 210, 2);
		assertTrue("capacity=" + capacity, capacity > 105 && capacity <= 110.5);
		// Simulate an actual cut: round down to a boundary within capacity for the first column.
		final double c1 = floorOf(b).applyAsDouble(capacity);
		assertTrue("last column overflows: " + (210 - c1) + " > " + capacity, 210 - c1 <= capacity + 0.5);
	}

	public void testUniformThreeColumns() {
		// 10 lines (height 12) in 3 columns: 4/3/3 = capacity 48.
		final double[] b = uniform(10, 12);
		final double capacity = ColumnBalancer.balance(linesOf(b), 120, 3);
		final DoubleUnaryOperator floor = floorOf(b);
		final double c1 = floor.applyAsDouble(capacity);
		final double c2 = floor.applyAsDouble(c1 + capacity);
		assertTrue("last column overflows", 120 - c2 <= capacity + 0.5);
		assertTrue("capacity should be minimal: " + capacity, capacity <= 48.5);
	}

	public void testExactFit() {
		// 20 lines (height 10) in 2 columns: exactly 100/100.
		final double[] b = uniform(20, 10);
		final double capacity = ColumnBalancer.balance(linesOf(b), 200, 2);
		assertEquals(100, capacity, 0.5);
	}

	public void testUnevenBoundaries() {
		// Nonuniform boundaries (a sequence of paragraphs containing a large figure).
		final double[] b = { 20, 40, 140, 160, 180, 200 };
		final double capacity = ColumnBalancer.balance(linesOf(b), 200, 2);
		final double c1 = floorOf(b).applyAsDouble(capacity);
		assertTrue("last column overflows: capacity=" + capacity + " c1=" + c1, 200 - c1 <= capacity + 0.5);
	}

	public void testUnbreakableContent() {
		// One line taller than an even share stays whole in the first column (it got half until 2026-10-22).
		assertEquals(100, ColumnBalancer.balance(linesOf(100), 100, 2), 0.5);
	}

	public void testFewerLinesThanColumns() {
		// One line (13.2) in four columns got 3.3 and two lines 6.6 until 2026-10-22 (sweep seed 30896161).
		assertEquals(13.2, ColumnBalancer.balance(linesOf(13.2), 13.2, 4), 0.6);
		assertEquals(13.2, ColumnBalancer.balance(linesOf(13.2, 26.4), 26.4, 4), 0.6);
	}

	public void testUnseenBoundaries() {
		// Content whose boundaries the oracle does not see is divided evenly; a boundary it sees is still used.
		assertEquals(50, ColumnBalancer.balance(unseenOf(), 200, 4), 0.5);
		final double capacity = ColumnBalancer.balance(unseenOf(20, 100), 100, 2);
		assertTrue("capacity=" + capacity, capacity > 79 && capacity <= 80.5);
	}

	public void testSingleColumn() {
		assertEquals(100, ColumnBalancer.balance(linesOf(uniform(10, 10)), 100, 1), 0.5);
	}

	public void testEmptyContent() {
		assertEquals(0, ColumnBalancer.balance(linesOf(), 0, 3), 0.01);
	}
}
