package net.zamasoft.foliojet.css.value.css3;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * {@code linear-gradient()}/{@code repeating-linear-gradient()}です。
 *
 * <p>
 * 勾配線はcss-images-3 §3.1.1どおり、箱の中心を通り角度{@code angle}
 * (0=上向き、時計回り)の向きで、長さ{@code |w·sinθ|+|h·cosθ|}——
 * 始点・終点の垂線が箱の角を通る長さ(2026-08-29に修正。それまでは
 * 向きによらず箱の高さを勾配線の長さにしていたため、{@code to right}
 * では横長の箱で端の色が箱の内側に来ていた)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class LinearGradientValue implements PaintValue {
	protected final double angle;
	protected final GradientStops stops;
	protected final boolean repeating;

	public LinearGradientValue(final double angle, final GradientStops stops, final boolean repeating) {
		this.angle = angle;
		this.stops = stops;
		this.repeating = repeating;
	}

	public LinearGradientValue(double angle, double[] fractions, Color[] colors) {
		this(angle, GradientStops.ofFractions(fractions, colors), false);
	}

	public boolean isRepeating() {
		return this.repeating;
	}

	public GradientStops getStops() {
		return this.stops;
	}

	public double getAngle() {
		return this.angle;
	}

	public Paint getPaint(Rectangle2D box) {
		final double w = box.getWidth(), h = box.getHeight();
		final double sin = Math.sin(this.angle), cos = Math.cos(this.angle);
		final double length = Math.abs(w * sin) + Math.abs(h * cos);
		if (!(length > 0)) {
			return this.stops.lastColor();
		}
		final double cx = box.getCenterX(), cy = box.getCenterY();
		final double dx = sin * length / 2, dy = -cos * length / 2;
		final GradientStops.Resolved r = this.stops.resolve(length, this.repeating, 1);
		return new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, r.fractions(), r.colors(),
				new AffineTransform());
	}

	@Override
	public String toString() {
		return String.format(java.util.Locale.ROOT, "%slinear(%.0fdeg;%s)", this.repeating ? "repeating-" : "",
				Math.toDegrees(this.angle), this.stops);
	}
}
