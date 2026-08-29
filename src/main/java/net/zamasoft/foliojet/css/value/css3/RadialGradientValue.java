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
 * {@code radial-gradient()}/{@code repeating-radial-gradient()}です
 * (css-images-3 §3.2、2026-08-29新設)。
 *
 * <p>
 * pdfg2dの{@link RadialGradient}は円しか表せない(PDFのType 3シェーディングも
 * 同じ)。楕円は半径{@code rx}の円を、中心を固定して縦に{@code ry/rx}倍する
 * 変換をパターン行列に載せて描く。PDF・SVG({@code gradientTransform})の
 * どちらも行列を受け付けるので、pdfg2d側の変更は要らない。
 * </p>
 *
 * <p>
 * 寸法キーワード(closest-side等)と割合は塗る箱が決まって初めて解決できる
 * ので、{@link #getPaint}まで{@link QuantityValue}のまま持ち回る。
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
	/** 明示寸法(EXPLICITのとき)。円は{@code sizeX}のみ。 */
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
	 * 塗りを作ります。繰り返しは、出力先が周期の繰り返しを持てば
	 * 1周期+{@code SpreadMethod.REPEAT}(厳密)、持たなければ最遠の角まで
	 * 展開する(64周期で打ち切ったときだけ2822を報告。2026-08-29)。
	 *
	 * @param gc 描画先(能力の問い合わせと報告用。nullなら展開)
	 */
	private Paint paint(final Rectangle2D box, final GC gc) {
		final double w = box.getWidth(), h = box.getHeight();
		final double cx = box.getX() + GradientGeometry.resolve(this.posX, w);
		final double cy = box.getY() + GradientGeometry.resolve(this.posY, h);
		final double[] radii = this.radii(box, cx, cy);
		final double rx = radii[0], ry = radii[1];
		if (!(rx > 0) || !(ry > 0)) {
			// 仕様: 半径0は極小の形として扱い、端の色で全面が塗られる
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
			// 最遠の角まで周期を展開する。楕円は縦をrx/ry倍して円の
			// 座標系で距離を測る
			cover = Math.max(1, farthestCorner(box, cx, cy, rx / ry) / rx);
		}
		final GradientStops.Resolved r = this.stops.resolve(rx, this.repeating, cover);
		if (r.capped()) {
			ApproximationGC.report(gc, "background-image", "repeating-radial-gradient() の繰り返しを64周期で打ち切り");
		}
		return new RadialGradient(cx, cy, rx * cover, cx, cy, r.fractions(), r.colors(), at);
	}

	@Override
	public void fill(final GC gc, final Shape shape, final Rectangle2D box) throws GraphicsException {
		gc.setFillPaint(this.paint(box, gc));
		gc.fill(shape);
	}

	/** 中心から箱の最遠の角までの距離(縦を{@code k}倍した座標系)。 */
	private static double farthestCorner(final Rectangle2D box, final double cx, final double cy, final double k) {
		double max = 0;
		for (int i = 0; i < 4; ++i) {
			final double dx = ((i & 1) == 0 ? box.getMinX() : box.getMaxX()) - cx;
			final double dy = (((i & 2) == 0 ? box.getMinY() : box.getMaxY()) - cy) * k;
			max = Math.max(max, Math.sqrt(dx * dx + dy * dy));
		}
		return max;
	}

	/** 終了形状の半径[rx, ry]をptで返します(css-images-3 §3.2.1の寸法規則)。 */
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
		// 辺までの距離(負=中心が箱の外。その辺の距離は絶対値で測る)
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
			// 角を通り、closest-side/farthest-sideと同じ縦横比の楕円
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
	 * 中心から箱の角までの距離(横を{@code 1/k}倍した座標系——楕円を横方向に
	 * 縮めて円として測り、横の尺へ戻す)。{@code closest}なら最近、でなければ最遠。
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
