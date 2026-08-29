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
 * {@code conic-gradient()}/{@code repeating-conic-gradient()}です
 * (css-images-4 §3.3、2026-08-29新設)。
 *
 * <p>
 * <b>PDFに円錐シェーディングは無い</b>(Type 4〜7のメッシュで作れるが、
 * 補間色を頂点に置くだけでも扇形ごとの三角形メッシュになり、ベクタの
 * 単純さが失われる)。そこで{@code Paint}ではなく{@link #fill}を上書きし、
 * 出力先が円錐グラデーション({@code CONIC_GRADIENT}——Java2D)を持てば
 * {@link ConicGradient}で厳密に塗り、持たなければ(PDF・SVG——SVGの
 * paint serverに円錐は無い)2822を報告して
 * 中心から放射する扇形を色停止の補間色で塗り分ける。扇形は
 * {@link #MAX_WEDGE}(2°)以下に刻み、色停止の位置には必ず境界を置く
 * (ハードストップがぼやけない)。180枚の扇形は1枚40バイト程度の
 * パス塗りで、PDFの大きさへの影響は無視できる。
 * </p>
 *
 * <p>
 * 隣り合う扇形の継ぎ目にビューアのアンチエイリアスで髪の毛ほどの隙間が
 * 出るのを防ぐため、各扇形を{@link #OVERLAP}だけ次へ重ねる。半透明の
 * 色停止では重なりが二重に合成されるが、幅0.03°未満で見えない。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class ConicGradientValue implements PaintValue {
	/** 扇形の最大角(ラジアン、2°)。 */
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
	 * {@code Paint}では表せないので、直接塗れない経路では最後の色で近似
	 * します({@link #fill}が本来の描画)。
	 */
	public Paint getPaint(Rectangle2D box) {
		return this.stops.lastColor();
	}

	@Override
	public void fill(final GC gc, final Shape shape, final Rectangle2D box) throws GraphicsException {
		final double w = box.getWidth(), h = box.getHeight();
		final double cx = box.getX() + GradientGeometry.resolve(this.posX, w);
		final double cy = box.getY() + GradientGeometry.resolve(this.posY, h);
		// 中心から最遠の角より外まで扇形を伸ばす(shapeでクリップする)
		double radius = 0;
		for (int i = 0; i < 4; ++i) {
			final double dx = ((i & 1) == 0 ? box.getMinX() : box.getMaxX()) - cx;
			final double dy = ((i & 2) == 0 ? box.getMinY() : box.getMaxY()) - cy;
			radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy));
		}
		radius = radius / Math.cos(MAX_WEDGE / 2) + 1;
		// 1周は有限なので繰り返しも1周ぶん展開する(64周期の打ち切りに
		// 掛かったときだけ近似)
		final GradientStops.Resolved r = this.stops.resolve(1, this.repeating, 1);
		if (r.capped()) {
			ApproximationGC.report(gc, "background-image", "repeating-conic-gradient() の繰り返しを64周期で打ち切り");
		}
		if (gc.supports(GC.Capability.CONIC_GRADIENT)) {
			try (final var state = gc.begin()) {
				gc.setFillPaint(new ConicGradient(cx, cy, this.fromAngle, r.fractions(), r.colors(),
						new AffineTransform(), SpreadMethod.PAD));
				gc.fill(shape);
			}
			return;
		}
		ApproximationGC.report(gc, "background-image", "conic-gradient() を扇形で近似");
		final double[] pos = r.fractions();
		final Color[] colors = r.colors();
		// 扇形の境界: 色停止の位置と、2°刻み
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
