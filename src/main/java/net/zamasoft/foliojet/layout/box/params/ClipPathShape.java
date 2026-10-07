package net.zamasoft.foliojet.layout.box.params;

import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * Basic {@code clip-path} shapes (css-shapes-1, added 2026-08-22).
 *
 * <p>
 * Stores lengths as {@link Length} (absolute/percentage/mixed), determined during style computation;
 * at draw time, uses {@link #resolve} with the reference box's actual dimensions to obtain a
 * {@link java.awt.Shape}. PDF clipping already supports arbitrary shapes
 * ({@code PDFGC.applyClip} writes a PathIterator using W/W*), so the resulting Shape passes through as is.
 * </p>
 *
 * <p>
 * <b>Initial scope</b>: {@code inset()} (with round, corner radii x=y), {@code circle()},
 * {@code ellipse()}, {@code polygon()}, and four reference box types. {@code path()} was added
 * 2026-08-29 ({@link Path}). {@code url()} references and application to inline elements are
 * unsupported (see manual 5300). Boxes split across pages resolve each fragment against its own
 * reference box (an intentional tradeoff that differs from the specification's one shape for the whole box).
 * </p>
 */
public abstract class ClipPathShape {
	/** Reference box (shape-box among css-shapes-1 &lt;geometry-box&gt; values). */
	public enum ReferenceBox {
		BORDER_BOX, PADDING_BOX, CONTENT_BOX, MARGIN_BOX
	}

	public final ReferenceBox referenceBox;

	protected ClipPathShape(final ReferenceBox referenceBox) {
		this.referenceBox = referenceBox;
	}

	/**
	 * Resolves the shape using the reference box's actual dimensions.
	 *
	 * @param x x coordinate of the reference box's top-left corner (physical coordinates)
	 * @param y y coordinate of the reference box's top-left corner (physical coordinates)
	 * @param w reference box width
	 * @param h reference box height
	 */
	public abstract Shape resolve(double x, double y, double w, double h);

	/** {@code inset(top right bottom left round r1 r2 r3 r4)}。 */
	public static final class Inset extends ClipPathShape {
		private final Length top, right, bottom, left;
		/** Corner radii (TL, TR, BR, BL; x=y). null for no rounding. */
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
			// Only a single radius (TL) uses RoundRectangle; otherwise, construct a path.
			final double[] rr = new double[4];
			for (int i = 0; i < 4; ++i) {
				// Percentage radii refer to the corresponding reference-box axis, but to match
				// the x=y simplification, resolve against the shorter side (a known difference from strict css-shapes).
				rr[i] = Math.max(0, LayoutUtils.computeLength(this.radii[i], Math.min(rw, rh)));
				rr[i] = Math.min(rr[i], Math.min(rw, rh) / 2);
			}
			if (rr[0] == rr[1] && rr[1] == rr[2] && rr[2] == rr[3]) {
				return new RoundRectangle2D.Double(x + l, y + t, rw, rh, rr[0] * 2, rr[0] * 2);
			}
			return roundedRectPath(x + l, y + t, rw, rh, rr);
		}
	}

	/** Rounded-rectangle path with radii at all four corners (x=y). */
	static Shape roundedRectPath(final double x, final double y, final double w, final double h, final double[] rr) {
		final Path2D.Double p = new Path2D.Double();
		final double k = 0.5522847498; // Coefficient for cubic Bezier approximation of an arc
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

	/** {@code circle(r at cx cy)}. r==null represents a radius keyword. */
	public static final class Circle extends ClipPathShape {
		/** null = keyword (distinguished by closestSide). */
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
				// Percentages refer to sqrt(w^2+h^2)/sqrt(2) (css-shapes-1 §3.1.1).
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

	/**
	 * {@code path("...")} (2026-08-29). Stores SVG path data in px coordinates; at draw time,
	 * translates it to the reference box's top-left corner and applies the px → pt scale.
	 */
	public static final class Path extends ClipPathShape {
		private final boolean evenOdd;
		private final Path2D.Double path;
		private final double pxToPt;

		public Path(final ReferenceBox referenceBox, final boolean evenOdd, final Path2D.Double path,
				final double pxToPt) {
			super(referenceBox);
			this.evenOdd = evenOdd;
			this.path = path;
			this.pxToPt = pxToPt;
		}

		@Override
		public Shape resolve(final double x, final double y, final double w, final double h) {
			final java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform(this.pxToPt, 0, 0,
					this.pxToPt, x, y);
			final Path2D.Double p = new Path2D.Double(
					this.evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
			p.append(this.path.getPathIterator(at), false);
			return p;
		}
	}

	/** No shape (reference box only, e.g., {@code clip-path: content-box}). */
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
