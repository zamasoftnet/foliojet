package net.zamasoft.foliojet.layout.box.params;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * {@code clip-path}の基本形状です(css-shapes-1、2026-08-22新設)。
 *
 * <p>
 * スタイル計算時に長さを{@link Length}(絶対/割合/混在)へ確定した形で
 * 保持し、描画時に参照ボックスの実寸で{@link #resolve}して
 * {@link java.awt.Shape}を得る。PDF側のクリップは任意形状に対応済み
 * ({@code PDFGC.applyClip}がPathIteratorをW/W*で書く)なので、
 * ここで作ったShapeはそのまま流れる。
 * </p>
 *
 * <p>
 * <b>第1弾の範囲</b>: {@code inset()}(roundつき、コーナー半径はx=y)・
 * {@code circle()}・{@code ellipse()}・{@code polygon()}と参照ボックス
 * 4種。{@code path()}・{@code url()}参照・インライン要素への適用は
 * 未対応(マニュアル5300参照)。ページ跨ぎで分割されたボックスは
 * 断片ごとに自身の参照ボックスで解決する(仕様の「ボックス全体で1つの
 * 形状」とは異なる割り切り)。
 * </p>
 */
public abstract class ClipPathShape {
	/** 参照ボックス(css-shapes-1の&lt;geometry-box&gt;のうちshape-box)。 */
	public enum ReferenceBox {
		BORDER_BOX, PADDING_BOX, CONTENT_BOX, MARGIN_BOX
	}

	public final ReferenceBox referenceBox;

	protected ClipPathShape(final ReferenceBox referenceBox) {
		this.referenceBox = referenceBox;
	}

	/**
	 * 参照ボックスの実寸で形状を解決します。
	 *
	 * @param x 参照ボックス左上のx(物理座標)
	 * @param y 参照ボックス左上のy(物理座標)
	 * @param w 参照ボックスの幅
	 * @param h 参照ボックスの高さ
	 */
	public abstract Shape resolve(double x, double y, double w, double h);

	/** {@code inset(top right bottom left round r1 r2 r3 r4)}。 */
	public static final class Inset extends ClipPathShape {
		private final Length top, right, bottom, left;
		/** 角丸半径(TL, TR, BR, BL。x=y)。丸めなしはnull。 */
		private final Length[] radii;

		public Inset(final ReferenceBox referenceBox, final Length top, final Length right, final Length bottom,
				final Length left, final Length[] radii) {
			super(referenceBox);
			this.top = top;
			this.right = right;
			this.bottom = bottom;
			this.left = left;
			this.radii = radii;
		}

		@Override
		public Shape resolve(final double x, final double y, final double w, final double h) {
			final double t = LayoutUtils.computeLength(this.top, h);
			final double r = LayoutUtils.computeLength(this.right, w);
			final double b = LayoutUtils.computeLength(this.bottom, h);
			final double l = LayoutUtils.computeLength(this.left, w);
			final double rw = Math.max(0, w - l - r);
			final double rh = Math.max(0, h - t - b);
			if (this.radii == null) {
				return new Rectangle2D.Double(x + l, y + t, rw, rh);
			}
			// 単一半径(TL)のみRoundRectangleへ、それ以外はパスで組む
			final double[] rr = new double[4];
			for (int i = 0; i < 4; ++i) {
				// 半径の%は参照ボックスの対応軸だが、x=y簡略化に合わせて
				// 短辺基準で解決する(css-shapesの厳密解釈との既知の差)
				rr[i] = Math.max(0, LayoutUtils.computeLength(this.radii[i], Math.min(rw, rh)));
				rr[i] = Math.min(rr[i], Math.min(rw, rh) / 2);
			}
			if (rr[0] == rr[1] && rr[1] == rr[2] && rr[2] == rr[3]) {
				return new RoundRectangle2D.Double(x + l, y + t, rw, rh, rr[0] * 2, rr[0] * 2);
			}
			return roundedRectPath(x + l, y + t, rw, rh, rr);
		}
	}

	/** 4隅の半径(x=y)つき角丸矩形パス。 */
	static Shape roundedRectPath(final double x, final double y, final double w, final double h, final double[] rr) {
		final Path2D.Double p = new Path2D.Double();
		final double k = 0.5522847498; // 円弧の3次ベジェ近似係数
		p.moveTo(x + rr[0], y);
		p.lineTo(x + w - rr[1], y);
		if (rr[1] > 0) {
			p.curveTo(x + w - rr[1] + k * rr[1], y, x + w, y + rr[1] - k * rr[1], x + w, y + rr[1]);
		}
		p.lineTo(x + w, y + h - rr[2]);
		if (rr[2] > 0) {
			p.curveTo(x + w, y + h - rr[2] + k * rr[2], x + w - rr[2] + k * rr[2], y + h, x + w - rr[2], y + h);
		}
		p.lineTo(x + rr[3], y + h);
		if (rr[3] > 0) {
			p.curveTo(x + rr[3] - k * rr[3], y + h, x, y + h - rr[3] + k * rr[3], x, y + h - rr[3]);
		}
		p.lineTo(x, y + rr[0]);
		if (rr[0] > 0) {
			p.curveTo(x, y + rr[0] - k * rr[0], x + rr[0] - k * rr[0], y, x + rr[0], y);
		}
		p.closePath();
		return p;
	}

	/** {@code circle(r at cx cy)}。半径キーワードはr==nullで表す。 */
	public static final class Circle extends ClipPathShape {
		/** null=キーワード(closestSideで判別)。 */
		private final Length radius;
		private final boolean farthestSide;
		private final Length cx, cy;

		public Circle(final ReferenceBox referenceBox, final Length radius, final boolean farthestSide,
				final Length cx, final Length cy) {
			super(referenceBox);
			this.radius = radius;
			this.farthestSide = farthestSide;
			this.cx = cx;
			this.cy = cy;
		}

		@Override
		public Shape resolve(final double x, final double y, final double w, final double h) {
			final double px = LayoutUtils.computeLength(this.cx, w);
			final double py = LayoutUtils.computeLength(this.cy, h);
			final double r;
			if (this.radius != null) {
				// %の基準はsqrt(w^2+h^2)/sqrt(2)(css-shapes-1 §3.1.1)
				r = LayoutUtils.computeLength(this.radius, Math.sqrt(w * w + h * h) / Math.sqrt(2));
			} else if (this.farthestSide) {
				r = Math.max(Math.max(px, w - px), Math.max(py, h - py));
			} else {
				r = Math.min(Math.min(px, w - px), Math.min(py, h - py));
			}
			return new Ellipse2D.Double(x + px - r, y + py - r, r * 2, r * 2);
		}
	}

	/** {@code ellipse(rx ry at cx cy)}。 */
	public static final class Ellipse extends ClipPathShape {
		private final Length rx, ry;
		private final boolean rxFarthest, ryFarthest;
		private final Length cx, cy;

		public Ellipse(final ReferenceBox referenceBox, final Length rx, final boolean rxFarthest, final Length ry,
				final boolean ryFarthest, final Length cx, final Length cy) {
			super(referenceBox);
			this.rx = rx;
			this.rxFarthest = rxFarthest;
			this.ry = ry;
			this.ryFarthest = ryFarthest;
			this.cx = cx;
			this.cy = cy;
		}

		@Override
		public Shape resolve(final double x, final double y, final double w, final double h) {
			final double px = LayoutUtils.computeLength(this.cx, w);
			final double py = LayoutUtils.computeLength(this.cy, h);
			final double rx = this.rx != null ? LayoutUtils.computeLength(this.rx, w)
					: (this.rxFarthest ? Math.max(px, w - px) : Math.min(px, w - px));
			final double ry = this.ry != null ? LayoutUtils.computeLength(this.ry, h)
					: (this.ryFarthest ? Math.max(py, h - py) : Math.min(py, h - py));
			return new Ellipse2D.Double(x + px - rx, y + py - ry, rx * 2, ry * 2);
		}
	}

	/** {@code polygon(fill-rule, x1 y1, x2 y2, ...)}。 */
	public static final class Polygon extends ClipPathShape {
		private final boolean evenOdd;
		/** [x0, y0, x1, y1, ...]。 */
		private final Length[] points;

		public Polygon(final ReferenceBox referenceBox, final boolean evenOdd, final Length[] points) {
			super(referenceBox);
			this.evenOdd = evenOdd;
			this.points = points;
		}

		@Override
		public Shape resolve(final double x, final double y, final double w, final double h) {
			final Path2D.Double p = new Path2D.Double(
					this.evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
			for (int i = 0; i + 1 < this.points.length; i += 2) {
				final double px = x + LayoutUtils.computeLength(this.points[i], w);
				final double py = y + LayoutUtils.computeLength(this.points[i + 1], h);
				if (i == 0) {
					p.moveTo(px, py);
				} else {
					p.lineTo(px, py);
				}
			}
			p.closePath();
			return p;
		}
	}

	/** 形状なし(参照ボックスだけの指定、例: {@code clip-path: content-box})。 */
	public static final class BoxOnly extends ClipPathShape {
		public BoxOnly(final ReferenceBox referenceBox) {
			super(referenceBox);
		}

		@Override
		public Shape resolve(final double x, final double y, final double w, final double h) {
			return new Rectangle2D.Double(x, y, w, h);
		}
	}
}
