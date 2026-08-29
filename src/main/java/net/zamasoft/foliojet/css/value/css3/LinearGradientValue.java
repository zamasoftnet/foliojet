package net.zamasoft.foliojet.css.value.css3;

import java.awt.geom.AffineTransform;
import net.zamasoft.pdfg2d.gc.paint.SpreadMethod;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import java.awt.Shape;
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
		return this.paint(box, null);
	}

	/**
	 * 塗りを作ります。繰り返しは、出力先が周期の繰り返しを持てば
	 * 1周期+{@code SpreadMethod.REPEAT}(厳密)、持たなければ勾配線の範囲へ
	 * 展開する(64周期で打ち切ったときだけ2822を報告。2026-08-29)。
	 *
	 * @param gc 描画先(能力の問い合わせと報告用。nullなら展開)
	 */
	private Paint paint(final Rectangle2D box, final GC gc) {
		final double w = box.getWidth(), h = box.getHeight();
		final double sin = Math.sin(this.angle), cos = Math.cos(this.angle);
		final double length = Math.abs(w * sin) + Math.abs(h * cos);
		if (!(length > 0)) {
			return this.stops.lastColor();
		}
		final double cx = box.getCenterX(), cy = box.getCenterY();
		final double dx = sin * length / 2, dy = -cos * length / 2;
		if (this.repeating && gc != null && gc.supports(GC.Capability.REPEATING_GRADIENT)) {
			final GradientStops.Period p = this.stops.resolvePeriod(length);
			if (p != null) {
				final double x1 = cx - dx, y1 = cy - dy;
				return new LinearGradient(x1, y1, x1 + dx * 2 * p.length(), y1 + dy * 2 * p.length(), p.fractions(),
						p.colors(), new AffineTransform(), SpreadMethod.REPEAT);
			}
		}
		final GradientStops.Resolved r = this.stops.resolve(length, this.repeating, 1);
		if (r.capped()) {
			ApproximationGC.report(gc, "background-image", "repeating-linear-gradient() の繰り返しを64周期で打ち切り");
		}
		return new LinearGradient(cx - dx, cy - dy, cx + dx, cy + dy, r.fractions(), r.colors(),
				new AffineTransform());
	}

	@Override
	public void fill(final GC gc, final Shape shape, final Rectangle2D box) throws GraphicsException {
		gc.setFillPaint(this.paint(box, gc));
		gc.fill(shape);
	}

	@Override
	public String toString() {
		return String.format(java.util.Locale.ROOT, "%slinear(%.0fdeg;%s)", this.repeating ? "repeating-" : "",
				Math.toDegrees(this.angle), this.stops);
	}
}
