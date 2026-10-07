package net.zamasoft.foliojet.layout.constraint;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * Unit tests that lock down band queries for {@link ExclusionShape}
 * (css-shapes-1 shape-outside, added 2026-08-29).
 *
 * <p>
 * A line box avoids the shape over its entire height, so a query for band [v0, v1] must return
 * the <b>maximum</b> protrusion within the band. In the upper half of a circle, the bottom of the band
 * protrudes most; in the lower half, the top does. Check the calculations for both.
 * </p>
 */
public class ExclusionShapeTest extends TestCase {
	private static final double EPS = 0.5; // LayoutUtils.THRESHOLD

	/** A circle with radius 50 and center (50,50) in a 100×100 margin box. */
	private static ExclusionShape circle() {
		final Shape circle = new Ellipse2D.Double(0, 0, 100, 100);
		return ExclusionShape.ofShape(circle, new AxisSpan(0, 100), new AxisSpan(0, 100));
	}

	private static double chord(final double v) {
		// u on the circumference (right edge). The maximum is 100 at v=50.
		return 50 + Math.sqrt(2500 - (v - 50) * (v - 50));
	}

	public void testCircleUpperBandUsesLowerEdge() {
		// Band [0,12]: the chord at the bottom, v=12, gives the maximum (≈82.5).
		final AxisSpan span = circle().lineSpanAt(0, 12);
		assertNotNull(span);
		assertEquals(chord(12), span.end(), EPS);
		assertEquals(100 - chord(12), span.start(), EPS);
	}

	public void testCircleMiddleBandReachesFullWidth() {
		final AxisSpan span = circle().lineSpanAt(40, 60);
		assertNotNull(span);
		assertEquals(100.0, span.end(), EPS);
		assertEquals(0.0, span.start(), EPS);
	}

	public void testCircleLowerBandUsesUpperEdge() {
		// Band [96,108]: the chord at the top, v=96, gives the maximum (≈69.6). The band may extend below the circle.
		final AxisSpan span = circle().lineSpanAt(96, 108);
		assertNotNull(span);
		assertEquals(chord(96), span.end(), EPS);
	}

	public void testBandOutsideShapeIsNull() {
		assertNull(circle().lineSpanAt(101, 113));
		assertNull(circle().lineSpanAt(-20, -1));
	}

	public void testDegenerateBandIsTreatedAsSingleLine() {
		final AxisSpan span = circle().lineSpanAt(50, 50);
		assertNotNull(span);
		assertEquals(100.0, span.end(), EPS);
		// v1 < v0 means the single point v0.
		final AxisSpan reversed = circle().lineSpanAt(50, 40);
		assertNotNull(reversed);
		assertEquals(100.0, reversed.end(), EPS);
	}

	public void testRectangleInsideBoxLeavesGapsAboveAndBelow() {
		final ExclusionShape shape = ExclusionShape.ofShape(new Rectangle2D.Double(20, 20, 60, 60),
				new AxisSpan(0, 100), new AxisSpan(0, 100));
		assertNull(shape.lineSpanAt(0, 10));
		final AxisSpan span = shape.lineSpanAt(10, 30);
		assertNotNull(span);
		assertEquals(20.0, span.start(), EPS);
		assertEquals(80.0, span.end(), EPS);
		assertNull(shape.lineSpanAt(81, 100));
	}

	public void testShapeIsClippedToBounds() {
		// A circle with radius 100: does not extend beyond the margin box (§4.1).
		final ExclusionShape shape = ExclusionShape.ofShape(new Ellipse2D.Double(-50, -50, 200, 200),
				new AxisSpan(0, 100), new AxisSpan(0, 100));
		final AxisSpan span = shape.lineSpanAt(40, 60);
		assertNotNull(span);
		assertEquals(0.0, span.start(), EPS);
		assertEquals(100.0, span.end(), EPS);
		assertNull(shape.lineSpanAt(101, 120));
	}

	public void testDilateExpandsInBothAxes() {
		final Shape dilated = ExclusionShape.dilate(new Rectangle2D.Double(20, 20, 60, 60), 10);
		final ExclusionShape shape = ExclusionShape.ofShape(dilated, new AxisSpan(0, 100), new AxisSpan(0, 100));
		final AxisSpan middle = shape.lineSpanAt(40, 60);
		assertEquals(10.0, middle.start(), EPS);
		assertEquals(90.0, middle.end(), EPS);
		// v=10..20 is the expanded part (corners are round).
		assertNotNull(shape.lineSpanAt(11, 15));
		assertNull(shape.lineSpanAt(0, 9));
		// No change for values of 0 or less.
		assertSame(dilated, ExclusionShape.dilate(dilated, 0));
	}

	public void testProfileUnionsRowsInBand() {
		final double[] min = { Double.NaN, 30, 20, Double.NaN, 40 };
		final double[] max = { Double.NaN, 70, 80, Double.NaN, 60 };
		final ExclusionShape shape = ExclusionShape.ofProfile(100, 10, min, max);
		assertNull(shape.lineSpanAt(100, 109));
		AxisSpan span = shape.lineSpanAt(110, 130);
		assertEquals(20.0, span.start(), 0);
		assertEquals(80.0, span.end(), 0);
		span = shape.lineSpanAt(135, 150);
		assertEquals(40.0, span.start(), 0);
		assertEquals(60.0, span.end(), 0);
		assertNull(shape.lineSpanAt(150, 200));
		assertNull(shape.lineSpanAt(0, 99));
	}

	public void testFloatExclusionWithoutShapeReturnsLineSpan() {
		final FloatExclusion rect = new FloatExclusion(0, FloatSide.START, new AxisSpan(0, 100),
				new AxisSpan(0, 100));
		assertNull(rect.shape());
		assertSame(rect.lineSpan(), rect.lineSpanAt(0, 12));
		final FloatExclusion shaped = new FloatExclusion(0, FloatSide.START, new AxisSpan(0, 100),
				new AxisSpan(0, 100), circle());
		assertEquals(chord(12), shaped.lineSpanAt(0, 12).end(), EPS);
	}

	public void testShapeImageExtractAppliesThreshold() {
		// A 4×3 ARGB image: only the central two pixels are opaque (alpha 255); the rest are translucent (alpha 100).
		final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 3,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 3; ++y) {
			for (int x = 0; x < 4; ++x) {
				img.setRGB(x, y, 100 << 24);
			}
		}
		img.setRGB(1, 1, 0xFF000000);
		img.setRGB(2, 1, 0xFF000000);
		// Threshold 0.5: opaque pixels only.
		net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams.ShapeImage m = net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams.ShapeImage
				.extract(img, 0.5);
		assertEquals(-1, m.rowMin()[0]);
		assertEquals(1, m.rowMin()[1]);
		assertEquals(2, m.rowMax()[1]);
		assertEquals(-1, m.colMin()[0]);
		assertEquals(1, m.colMin()[1]);
		assertEquals(1, m.colMax()[2]);
		// Threshold 0 (default): all pixels with alpha>0.
		m = net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams.ShapeImage.extract(img, 0);
		assertEquals(0, m.rowMin()[0]);
		assertEquals(3, m.rowMax()[2]);
		assertEquals(0, m.colMin()[3]);
	}
}
