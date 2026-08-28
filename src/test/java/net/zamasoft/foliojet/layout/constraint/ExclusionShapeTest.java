package net.zamasoft.foliojet.layout.constraint;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.params.FloatSide;

/**
 * {@link ExclusionShape}の帯照会を固定する単体テストです
 * (css-shapes-1 shape-outside、2026-08-29新設)。
 *
 * <p>
 * 行ボックスは自身の高さ全体で形状を避けるので、帯[v0, v1]の照会は
 * 帯の中の<b>最大</b>張り出しを返さなければならない。円の上側では
 * 帯の下端、下側では帯の上端が最も張り出す——両方を検算する。
 * </p>
 */
public class ExclusionShapeTest extends TestCase {
	private static final double EPS = 0.5; // LayoutUtils.THRESHOLD

	/** 半径50・中心(50,50)の円を100×100のマージンボックスに置いたもの。 */
	private static ExclusionShape circle() {
		final Shape circle = new Ellipse2D.Double(0, 0, 100, 100);
		return ExclusionShape.ofShape(circle, new AxisSpan(0, 100), new AxisSpan(0, 100));
	}

	private static double chord(final double v) {
		// 円周上のu(右端)。v=50で最大の100
		return 50 + Math.sqrt(2500 - (v - 50) * (v - 50));
	}

	public void testCircleUpperBandUsesLowerEdge() {
		// 帯[0,12]: 下端v=12の弦が最大(≈82.5)
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
		// 帯[96,108]: 上端v=96の弦が最大(≈69.6)。帯は円の下端を越えてよい
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
		// v1 < v0 は v0 の1点
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
		// 半径100の円: マージンボックスの外へは広がらない(§4.1)
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
		// v=10..20は膨らんだ分(角は丸い)
		assertNotNull(shape.lineSpanAt(11, 15));
		assertNull(shape.lineSpanAt(0, 9));
		// 0以下は無変更
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
		// 4×3のARGB画像: 中央2画素だけ不透明(alpha 255)、周囲は半透明(alpha 100)
		final java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(4, 3,
				java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 3; ++y) {
			for (int x = 0; x < 4; ++x) {
				img.setRGB(x, y, 100 << 24);
			}
		}
		img.setRGB(1, 1, 0xFF000000);
		img.setRGB(2, 1, 0xFF000000);
		// 閾値0.5: 不透明画素だけ
		net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams.ShapeImage m = net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams.ShapeImage
				.extract(img, 0.5);
		assertEquals(-1, m.rowMin()[0]);
		assertEquals(1, m.rowMin()[1]);
		assertEquals(2, m.rowMax()[1]);
		assertEquals(-1, m.colMin()[0]);
		assertEquals(1, m.colMin()[1]);
		assertEquals(1, m.colMax()[2]);
		// 閾値0(既定): alpha>0の全画素
		m = net.zamasoft.foliojet.layout.box.params.ShapeOutsideParams.ShapeImage.extract(img, 0);
		assertEquals(0, m.rowMin()[0]);
		assertEquals(3, m.rowMax()[2]);
		assertEquals(0, m.colMin()[3]);
	}
}
