package net.zamasoft.foliojet.css.util;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import junit.framework.TestCase;

/**
 * Parsing tests for {@link SvgPathData} (SVG path data→Path2D)
 * (2026-08-29, for clip-path: path()).
 */
public class SvgPathDataTest extends TestCase {

	/** Return the sequence of segment types (one letter each: M/L/C/Q/Z). */
	private static String segments(final Path2D path) {
		final StringBuilder sb = new StringBuilder();
		final double[] c = new double[6];
		for (final PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
			switch (it.currentSegment(c)) {
			case PathIterator.SEG_MOVETO -> sb.append('M');
			case PathIterator.SEG_LINETO -> sb.append('L');
			case PathIterator.SEG_CUBICTO -> sb.append('C');
			case PathIterator.SEG_QUADTO -> sb.append('Q');
			case PathIterator.SEG_CLOSE -> sb.append('Z');
			default -> sb.append('?');
			}
		}
		return sb.toString();
	}

	public void testAbsoluteAndRelativeLines() {
		final Path2D p = SvgPathData.parse("M10,20 L30 20 l0 30 H10 v-10 Z");
		assertEquals("MLLLLZ", segments(p));
		final Rectangle2D b = p.getBounds2D();
		assertEquals(10.0, b.getMinX(), 1e-9);
		assertEquals(30.0, b.getMaxX(), 1e-9);
		assertEquals(20.0, b.getMinY(), 1e-9);
		assertEquals(50.0, b.getMaxY(), 1e-9);
		assertTrue(p.contains(new Point2D.Double(20, 35)));
		assertFalse(p.contains(new Point2D.Double(5, 35)));
	}

	public void testImplicitRepeatAndCompactNumbers() {
		// Repeated M means lineto; ".5.5" is two numbers; exponential notation; negative numbers without separators.
		final Path2D p = SvgPathData.parse("M0 0 10 0 10 10z m.5.5 1e1-2Z");
		assertEquals("MLLZMLZ", segments(p));
	}

	public void testCurvesAndSmoothReflection() {
		final Path2D p = SvgPathData.parse("M0 0 C 0 10, 10 10, 10 0 S 20 -10, 20 0 Q 25 5 30 0 T 40 0 z");
		assertEquals("MCCQQZ", segments(p));
		final double[] c = new double[6];
		final PathIterator it = p.getPathIterator(null);
		it.next(); // M
		it.next(); // C
		it.currentSegment(c);
		// S's first control point reflects the preceding C's second point (10,10) about current point (10,0): (10,-10).
		assertEquals(10.0, c[0], 1e-9);
		assertEquals(-10.0, c[1], 1e-9);
	}

	public void testArcIsApproximatedByCubics() {
		// A semicircle of radius 10 from (0,0) to (20,0). sweep=1 follows positive angles
		// (clockwise on screen in downward-y coordinates), so it passes through the upper side (y<0).
		final Path2D p = SvgPathData.parse("M0 0 A10 10 0 0 1 20 0");
		final String segs = segments(p);
		assertTrue("弧はベジェへ分割される: " + segs, segs.matches("MC+"));
		final Rectangle2D b = p.getBounds2D();
		assertEquals(0.0, b.getMinX(), 1e-6);
		assertEquals(20.0, b.getMaxX(), 1e-6);
		assertEquals(-10.0, b.getMinY(), 0.05);
		assertEquals(0.0, b.getMaxY(), 0.05);
		// sweep=0 passes through the lower side.
		assertEquals(10.0, SvgPathData.parse("M0 0 A10 10 0 0 0 20 0").getBounds2D().getMaxY(), 0.05);
		// Arc flags may be concatenated ("a10 10 0 01 20 0").
		final Path2D q = SvgPathData.parse("M0 0 a10 10 0 01 20 0");
		assertEquals(-10.0, q.getBounds2D().getMinY(), 0.05);
		// An arc with too small a radius is enlarged (F.6.6.2): radius 1 from (0,0) to (20,0) becomes radius 10.
		final Path2D r = SvgPathData.parse("M0 0 A1 1 0 0 1 20 0");
		assertEquals(-10.0, r.getBounds2D().getMinY(), 0.05);
		// Inside/outside a closed quarter-circle sector: center (10,10), clockwise from top (10,0) to right (20,10).
		final Path2D s = SvgPathData.parse("M10 0 A10 10 0 0 1 20 10 L10 10 Z");
		assertTrue(s.contains(new Point2D.Double(16, 4)));
		assertTrue(s.contains(new Point2D.Double(12, 2)));
		assertTrue(s.contains(new Point2D.Double(19, 9)));
		assertFalse(s.contains(new Point2D.Double(4, 4)));
		assertFalse(s.contains(new Point2D.Double(19.5, 0.5)));
		final Rectangle2D sb = s.getBounds2D();
		assertEquals(10.0, sb.getMinX(), 0.05);
		assertEquals(20.0, sb.getMaxX(), 0.05);
		assertEquals(0.0, sb.getMinY(), 0.05);
		assertEquals(10.0, sb.getMaxY(), 0.05);
	}

	public void testErrors() {
		for (final String bad : new String[] { "L0 0", "M0", "M0 0 X", "M0 0 A1 1 0 2 0 5 5", "M0 0 L" }) {
			try {
				SvgPathData.parse(bad);
				fail("エラーにならない: " + bad);
			} catch (final IllegalArgumentException e) {
				// expected
			}
		}
		assertEquals("", segments(SvgPathData.parse("")));
	}
}
