package net.zamasoft.foliojet.css.value.css3;

import java.awt.geom.AffineTransform;
import net.zamasoft.pdfg2d.gc.paint.SpreadMethod;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.RadialGradient;

/**
 * {@code radial-gradient()}/{@code repeating-radial-gradient()}
 * (css-images-3 §3.2, added on 2026-08-29).
 *
 * <p>
 * pdfg2d's {@link RadialGradient} can represent only circles (as can PDF Type 3 shading).
 * Draws an ellipse by adding a transform to the pattern matrix that scales a circle
 * of radius {@code rx} vertically by {@code ry/rx} while keeping its center fixed.
 * Both PDF and SVG ({@code gradientTransform}) accept matrices, so no pdfg2d changes are needed.
 * </p>
 *
 * <p>
 * Size keywords (closest-side, etc.) and percentages resolve only once the box to fill is known,
 * so they remain {@link QuantityValue} objects until {@link #getPaint}.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class RadialGradientValue implements PaintValue {
	public enum Size {
		CLOSEST_SIDE, FARTHEST_SIDE, CLOSEST_CORNER, FARTHEST_CORNER, EXPLICIT
	}

	protected final boolean circle;
	protected final Size size;
	/** Explicit sizes (for EXPLICIT). Circles use only {@code sizeX}. */
	protected final QuantityValue sizeX, sizeY;
	protected final QuantityValue posX, posY;
	protected final GradientStops stops;
	protected final boolean repeating;

	public RadialGradientValue(final boolean circle, final Size size, final QuantityValue sizeX,
			final QuantityValue sizeY, final QuantityValue posX, final QuantityValue posY, final GradientStops stops,
			final boolean repeating) {
		this.circle = circle;
		this.size = size;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.posX = posX;
		this.posY = posY;
		this.stops = stops;
		this.repeating = repeating;
	}

	public boolean isCircle() {
		return this.circle;
	}

	public Size getSize() {
		return this.size;
	}

	public boolean isRepeating() {
		return this.repeating;
	}

	public GradientStops getStops() {
		return this.stops;
	}

	public Paint getPaint(Rectangle2D box) {
		return this.paint(box, null);
	}

	/**
	 * Creates the paint. If the destination supports periodic repetition, uses one period
	 * plus {@code SpreadMethod.REPEAT} (exact); otherwise, expands to the farthest corner
	 * (reports 2822 only when capped at 64 periods; 2026-08-29).
	 *
	 * @param gc the drawing destination (for capability queries and reporting; null means expansion)
	 */
	private Paint paint(final Rectangle2D box, final GC gc) {
		final double w = box.getWidth(), h = box.getHeight();
		final double cx = box.getX() + GradientGeometry.resolve(this.posX, w);
		final double cy = box.getY() + GradientGeometry.resolve(this.posY, h);
		final double[] radii = this.radii(box, cx, cy);
		final double rx = radii[0], ry = radii[1];
		if (!(rx > 0) || !(ry > 0)) {
			// Specification: treat a zero radius as an infinitesimal shape, filling the entire area with the end color.
			return this.stops.lastColor();
		}
		final AffineTransform at = new AffineTransform();
		if (Math.abs(rx - ry) > 1e-9) {
			at.translate(cx, cy);
			at.scale(1, ry / rx);
			at.translate(-cx, -cy);
		}
		if (this.repeating && gc != null && gc.supports(GC.Capability.REPEATING_GRADIENT)) {
			final GradientStops.Period p = this.stops.resolvePeriod(rx);
			if (p != null) {
				return new RadialGradient(cx, cy, rx * p.length(), cx, cy, p.fractions(), p.colors(), at,
						SpreadMethod.REPEAT);
			}
		}
		double cover = 1;
		if (this.repeating) {
			// Expand periods to the farthest corner. For ellipses, scale vertically by rx/ry and
			// measure distance in the circle's coordinate system.
			cover = Math.max(1, farthestCorner(box, cx, cy, rx / ry) / rx);
		}
		final GradientStops.Resolved r = this.stops.resolve(rx, this.repeating, cover);
		if (r.capped()) {
			ApproximationGC.report(gc, "background-image", "2822.repeat-capped-radial");
		}
		return new RadialGradient(cx, cy, rx * cover, cx, cy, r.fractions(), r.colors(), at);
	}

	@Override
	public void fill(final GC gc, final Shape shape, final Rectangle2D box) throws GraphicsException {
		gc.setFillPaint(this.paint(box, gc));
		gc.fill(shape);
	}

	/** Distance from the center to the box's farthest corner (in coordinates scaled vertically by {@code k}). */
	private static double farthestCorner(final Rectangle2D box, final double cx, final double cy, final double k) {
		double max = 0;
		for (int i = 0; i < 4; ++i) {
			final double dx = ((i & 1) == 0 ? box.getMinX() : box.getMaxX()) - cx;
			final double dy = (((i & 2) == 0 ? box.getMinY() : box.getMaxY()) - cy) * k;
			max = Math.max(max, Math.sqrt(dx * dx + dy * dy));
		}
		return max;
	}

	/** Returns the ending shape's radii [rx, ry] in pt (the sizing rules in css-images-3 §3.2.1). */
	private double[] radii(final Rectangle2D box, final double cx, final double cy) {
		final double w = box.getWidth(), h = box.getHeight();
		if (this.size == Size.EXPLICIT) {
			final double rx = Math.abs(GradientGeometry.resolve(this.sizeX, w));
			final double ry = this.circle ? rx : Math.abs(GradientGeometry.resolve(this.sizeY, h));
			return new double[] { rx, ry };
		}
		final double left = cx - box.getMinX(), right = box.getMaxX() - cx;
		final double top = cy - box.getMinY(), bottom = box.getMaxY() - cy;
		final boolean closest = this.size == Size.CLOSEST_SIDE || this.size == Size.CLOSEST_CORNER;
		// Distances to sides (negative means the center is outside the box; use the absolute distance to that side).
		final double sx = closest ? Math.min(Math.abs(left), Math.abs(right))
				: Math.max(Math.abs(left), Math.abs(right));
		final double sy = closest ? Math.min(Math.abs(top), Math.abs(bottom))
				: Math.max(Math.abs(top), Math.abs(bottom));
		if (this.circle) {
			final double r;
			switch (this.size) {
			case CLOSEST_SIDE:
				r = Math.min(sx, sy);
				break;
			case FARTHEST_SIDE:
				r = Math.max(sx, sy);
				break;
			case CLOSEST_CORNER:
				r = cornerDistance(box, cx, cy, 1, true);
				break;
			default:
				r = cornerDistance(box, cx, cy, 1, false);
				break;
			}
			return new double[] { r, r };
		}
		switch (this.size) {
		case CLOSEST_SIDE:
		case FARTHEST_SIDE:
			return new double[] { sx, sy };
		default: {
			// An ellipse through a corner, with the same aspect ratio as closest-side/farthest-side.
			if (!(sx > 0) || !(sy > 0)) {
				return new double[] { 0, 0 };
			}
			final double k = sx / sy;
			final double ry = cornerDistance(box, cx, cy, k, this.size == Size.CLOSEST_CORNER) / k;
			return new double[] { ry * k, ry };
		}
		}
	}

	/**
	 * Distance from the center to a box corner (in coordinates scaled horizontally by {@code 1/k}:
	 * shrink the ellipse horizontally to measure it as a circle, then restore the horizontal scale).
	 * Uses the closest corner if {@code closest}, otherwise the farthest.
	 */
	private static double cornerDistance(final Rectangle2D box, final double cx, final double cy, final double k,
			final boolean closest) {
		double best = closest ? Double.MAX_VALUE : 0;
		for (int i = 0; i < 4; ++i) {
			final double dx = (((i & 1) == 0 ? box.getMinX() : box.getMaxX()) - cx) / k;
			final double dy = ((i & 2) == 0 ? box.getMinY() : box.getMaxY()) - cy;
			final double d = Math.sqrt(dx * dx + dy * dy) * k;
			best = closest ? Math.min(best, d) : Math.max(best, d);
		}
		return best;
	}

	@Override
	public String toString() {
		final StringBuilder s = new StringBuilder();
		s.append(this.repeating ? "repeating-" : "").append("radial(").append(this.circle ? "circle " : "ellipse ");
		if (this.size == Size.EXPLICIT) {
			s.append(GradientGeometry.describe(this.sizeX));
			if (!this.circle) {
				s.append(' ').append(GradientGeometry.describe(this.sizeY));
			}
		} else {
			s.append(this.size.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
		}
		s.append(" at ").append(GradientGeometry.describe(this.posX)).append(' ')
				.append(GradientGeometry.describe(this.posY)).append(';').append(this.stops).append(')');
		return s.toString();
	}
}
