package net.zamasoft.foliojet.layout.constraint;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * {@link FloatExclusion}/{@link AxisSpan}の値型としての基本契約
 * (構築時検証・値等価性)を固定する単体テストです(2026-07-23新設)。
 */
public class FloatExclusionTest extends TestCase {
	public FloatExclusionTest(String name) {
		super(name);
	}

	public void testAxisSpanAllowsInvertedRange() {
		// 意図的に検証しない: 既存の排除域計算は浮動体の重なりが大きい
		// 場合に負のline sizeを許容する既存挙動を持つため、この値型も
		// それを忠実に再現できる必要がある(AxisSpan.java参照)。
		final AxisSpan span = new AxisSpan(10, 5);
		assertEquals(-5.0, span.extent(), 0);
	}

	public void testAxisSpanAllowsZeroExtent() {
		final AxisSpan span = new AxisSpan(10, 10);
		assertEquals(0.0, span.extent(), 0);
	}

	public void testAxisSpanExtent() {
		assertEquals(90.0, new AxisSpan(10, 100).extent(), 0);
	}

	public void testFloatExclusionValueEquality() {
		final FloatExclusion a = new FloatExclusion(1, FloatSide.START, new AxisSpan(0, 10), new AxisSpan(0, 100));
		final FloatExclusion b = new FloatExclusion(1, FloatSide.START, new AxisSpan(0, 10), new AxisSpan(0, 100));
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	public void testFloatExclusionRejectsNullSide() {
		try {
			new FloatExclusion(1, null, new AxisSpan(0, 10), new AxisSpan(0, 100));
			fail("expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			// ok
		}
	}
}
