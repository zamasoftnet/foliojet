package jp.cssj.test.unit.fragment;

import java.util.function.DoubleUnaryOperator;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.fragment.ColumnBalancer;

/**
 * ColumnBalancer(カラムバランスの容量探索)のテストです(M5-B)。
 */
public class ColumnBalancerTest extends TestCase {
	/** 境界列から切り下げオラクルを作ります。 */
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

	/** 一様な行高の境界列です。 */
	private static double[] uniform(final int count, final double height) {
		final double[] boundaries = new double[count];
		for (int i = 0; i < count; ++i) {
			boundaries[i] = (i + 1) * height;
		}
		return boundaries;
	}

	public void testUniformOddLines() {
		// 21行(高さ10)を2段: 最適は 11行/10行 = 容量110
		final double[] b = uniform(21, 10);
		final double capacity = ColumnBalancer.balance(floorOf(b), 210, 2);
		assertTrue("capacity=" + capacity, capacity > 105 && capacity <= 110.5);
		// 実切断の模擬: 1段目は容量までの切り下げ境界
		final double c1 = floorOf(b).applyAsDouble(capacity);
		assertTrue("last column overflows: " + (210 - c1) + " > " + capacity, 210 - c1 <= capacity + 0.5);
	}

	public void testUniformThreeColumns() {
		// 10行(高さ12)を3段: 4/3/3 = 容量48
		final double[] b = uniform(10, 12);
		final double capacity = ColumnBalancer.balance(floorOf(b), 120, 3);
		final DoubleUnaryOperator floor = floorOf(b);
		final double c1 = floor.applyAsDouble(capacity);
		final double c2 = floor.applyAsDouble(c1 + capacity);
		assertTrue("last column overflows", 120 - c2 <= capacity + 0.5);
		assertTrue("capacity should be minimal: " + capacity, capacity <= 48.5);
	}

	public void testExactFit() {
		// 20行(高さ10)を2段: ちょうど 100/100
		final double[] b = uniform(20, 10);
		final double capacity = ColumnBalancer.balance(floorOf(b), 200, 2);
		assertEquals(100, capacity, 0.5);
	}

	public void testUnevenBoundaries() {
		// 不均一な境界(大きな図版を含む段落列)
		final double[] b = { 20, 40, 140, 160, 180, 200 };
		final double capacity = ColumnBalancer.balance(floorOf(b), 200, 2);
		final double c1 = floorOf(b).applyAsDouble(capacity);
		assertTrue("last column overflows: capacity=" + capacity + " c1=" + c1, 200 - c1 <= capacity + 0.5);
	}

	public void testUnbreakableContent() {
		// 境界がない(切断不能)内容: 均等割りのまま進めるしかない
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
