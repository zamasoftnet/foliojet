package net.zamasoft.foliojet.css.value.css3;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.TreeSet;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.ConicGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.paint.SpreadMethod;

/**
 * {@code conic-gradient()}/{@code repeating-conic-gradient()}
 * (css-images-4 §3.3, added on 2026-08-29).
 *
 * <p>
 * Overrides {@link #fill} instead of using {@code Paint}. If the destination supports conic gradients
 * ({@code CONIC_GRADIENT}: Java2D and PDF Type 4 meshes), fills exactly with {@link ConicGradient}.
 * Otherwise (e.g., SVG paint servers), reports 2822 and fills wedges radiating from the center
 * with colors interpolated between color stops. Each wedge spans at most {@link #MAX_WEDGE} (2°),
 * and boundaries always fall on color stops (so hard stops remain sharp).
 * The 180 wedges use path fills of about 40 bytes each, with negligible impact on PDF size.
 * </p>
 *
 * <p>
 * To prevent hairline gaps from viewer antialiasing at the seams between adjacent wedges,
 * each wedge overlaps the next by {@link #OVERLAP}. With semitransparent color stops,
 * the overlap is composited twice, but its width is less than 0.03° and is invisible.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class ConicGradientValue implements PaintValue {
	/** Maximum wedge angle (in radians, 2°). */
	private static final double MAX_WEDGE = Math.PI / 90;
	private static final double OVERLAP = 0.0005;

	protected final double fromAngle;
	protected final QuantityValue posX, posY;
	protected final GradientStops stops;
	protected final boolean repeating;

	public ConicGradientValue(final double fromAngle, final QuantityValue posX, final QuantityValue posY,
			final GradientStops stops, final boolean repeating) {
		this.fromAngle = fromAngle;
		this.posX = posX;
		this.posY = posY;
		this.stops = stops;
		this.repeating = repeating;
	}

	public boolean isRepeating() {
		return this.repeating;
	}

	public GradientStops getStops() {
		return this.stops;
	}

	public double getFromAngle() {
		return this.fromAngle;
	}

	/**
	 * Since {@code Paint} cannot represent this gradient, paths that cannot fill directly
	 * approximate it with the last color ({@link #fill} performs the actual rendering).
	 */
	public Paint getPaint(Rectangle2D box) {
		return this.stops.lastColor();
	}

	@Override
	public void fill(final GC gc, final Shape shape, final Rectangle2D box) throws GraphicsException {
		final double w = box.getWidth(), h = box.getHeight();
		final double cx = box.getX() + GradientGeometry.resolve(this.posX, w);
		final double cy = box.getY() + GradientGeometry.resolve(this.posY, h);
		// Extend wedges beyond the corner farthest from the center (clip to shape).
		double radius = 0;
		for (int i = 0; i < 4; ++i) {
			final double dx = ((i & 1) == 0 ? box.getMinX() : box.getMaxX()) - cx;
			final double dy = ((i & 2) == 0 ? box.getMinY() : box.getMaxY()) - cy;
			radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy));
		}
		radius = radius / Math.cos(MAX_WEDGE / 2) + 1;
		// A full turn is finite, so expand repetitions over one turn as well (approximate only
		// when the 64-period cap is reached).
		final GradientStops.Resolved r = this.stops.resolve(1, this.repeating, 1);
		if (r.capped()) {
			ApproximationGC.report(gc, "background-image", "2822.repeat-capped-conic");
		}
		if (gc.supports(GC.Capability.CONIC_GRADIENT)) {
			try (final var state = gc.begin()) {
				gc.setFillPaint(new ConicGradient(cx, cy, this.fromAngle, r.fractions(), r.colors(),
						new AffineTransform(), SpreadMethod.PAD));
				gc.fill(shape);
			}
			return;
		}
		ApproximationGC.report(gc, "background-image", "2822.conic-wedges");
		final double[] pos = r.fractions();
		final Color[] colors = r.colors();
		// Wedge boundaries: color-stop positions and 2° intervals.
		final TreeSet<Double> bounds = new TreeSet<Double>();
		bounds.add(0.0);
		bounds.add(1.0);
		for (final double p : pos) {
			bounds.add(p);
		}
		final int steps = (int) Math.ceil(Math.PI * 2 / MAX_WEDGE);
		for (int i = 1; i < steps; ++i) {
			bounds.add((double) i / steps);
		}
		try (final var state = gc.begin()) {
			gc.clip(shape);
			Double prev = null;
			for (final Double b : bounds) {
				if (prev != null && b - prev > 1e-9) {
					final Color color = GradientStops.colorAt(pos, colors, (prev + b) / 2);
					final double a0 = this.fromAngle + prev * Math.PI * 2;
					final double a1 = this.fromAngle + b * Math.PI * 2 + OVERLAP;
					final Path2D.Double wedge = new Path2D.Double();
					wedge.moveTo(cx, cy);
					wedge.lineTo(cx + radius * Math.sin(a0), cy - radius * Math.cos(a0));
					wedge.lineTo(cx + radius * Math.sin(a1), cy - radius * Math.cos(a1));
					wedge.closePath();
					gc.setFillPaint(color);
					gc.fill(wedge);
				}
				prev = b;
			}
		}
	}

	@Override
	public String toString() {
		return String.format(java.util.Locale.ROOT, "%sconic(from %.0fdeg at %s %s;%s)",
				this.repeating ? "repeating-" : "", Math.toDegrees(this.fromAngle),
				GradientGeometry.describe(this.posX), GradientGeometry.describe(this.posY), this.stops);
	}
}
