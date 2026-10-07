package net.zamasoft.foliojet.layout.constraint;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * Unit tests that lock down the basic value-type contracts of {@link FloatExclusion}/{@link AxisSpan}
 * (construction-time validation and value equality) (added 2026-07-23).
 */
public class FloatExclusionTest extends TestCase {
	public FloatExclusionTest(String name) {
		super(name);
	}

	public void testAxisSpanAllowsInvertedRange() {
		// Deliberately not validated: existing exclusion-area calculations allow negative
		// line sizes when floats overlap substantially, so this value type must faithfully
		// reproduce that behavior as well (see AxisSpan.java).
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
