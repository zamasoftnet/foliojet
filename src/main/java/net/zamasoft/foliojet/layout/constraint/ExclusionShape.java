package net.zamasoft.foliojet.layout.constraint;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

/**
 * Resolved float exclusion shape from {@code shape-outside} (css-shapes-1, added 2026-08-29).
 *
 * <p>
 * Coordinates use the same logical axes as {@link FloatExclusion}: u=line direction (lineSpan), v=page direction
 * (pageSpan). The resolver ({@code FloatShapeResolver}) handles the writing-direction-dependent physical-to-logical
 * transform, so queries need not account for vertical writing.
 * </p>
 *
 * <p>
 * There is only one query, {@link #lineSpanAt}: the line-direction range (minimum u to maximum u) occupied by the
 * shape within page-direction band [v0, v1]. Line boxes must avoid the shape over their full height (css-shapes-1
 * §4.1: "line boxes are shortened as necessary to avoid intersections"), so return the <b>maximum</b> protrusion
 * within the band. If the band and shape do not intersect, return null: this float does not narrow lines in that
 * band (lines can enter the empty space above/below a circle, even inside the margin box).
 * </p>
 *
 * <p>
 * Two implementations: a sequence of line segments flattening an arbitrary {@link Shape} ({@link #ofShape}), and
 * per-scanline ranges extracted from an image ({@link #ofProfile}). For line segments, checking vertices inside the
 * band and intersections with its upper/lower edges gives exact extrema (even for nonconvex polygons, extrema of
 * the region clipped to the band must lie at one of these). Curve flattening tolerance is 0.2 pt, finer than {@code
 * LayoutUtils.THRESHOLD} (0.5 pt).
 * </p>
 */
public abstract class ExclusionShape {
	/** Flattening tolerance (pt). Finer than THRESHOLD is sufficient. */
	static final double FLATNESS = 0.2;

	/**
	 * Returns the line-direction range occupied by the shape in band [v0, v1], or null if they do not intersect. If
	 * {@code v1 < v0}, treats the band as the single point {@code v0}.
	 */
	public abstract AxisSpan lineSpanAt(double v0, double v1);

	/**
	 * Creates from a shape in logical coordinates. Clips to {@code bounds} (the float's exclusion rectangle = margin
	 * box), per specification §4.1: the shape is clipped to the margin box and cannot expand the exclusion area.
	 */
	public static ExclusionShape ofShape(final Shape logical, final AxisSpan lineSpan, final AxisSpan pageSpan) {
		final Area area = new Area(logical);
		area.intersect(new Area(new Rectangle2D.Double(lineSpan.start(), pageSpan.start(),
				Math.max(0, lineSpan.end() - lineSpan.start()), Math.max(0, pageSpan.end() - pageSpan.start()))));
		return new Flattened(area);
	}

	/**
	 * Creates from per-scanline ranges (for image shapes).
	 *
	 * @param vStart v of the first scanline
	 * @param vStep  scanline interval (>0)
	 * @param minU   minimum u per scanline (NaN if empty)
	 * @param maxU   maximum u per scanline (NaN if empty)
	 */
	public static ExclusionShape ofProfile(final double vStart, final double vStep, final double[] minU,
			final double[] maxU) {
		return new Profile(vStart, vStep, minU, maxU);
	}

	/**
	 * Expands the shape outward by {@code margin} ({@code shape-margin}). The union of the original area and its
	 * outline stroked at width {@code 2*margin} with round caps and joins equals the Minkowski sum with a disk (exact
	 * offset).
	 */
	public static Shape dilate(final Shape shape, final double margin) {
		if (!(margin > 0)) {
			return shape;
		}
		final Area area = new Area(shape);
		area.add(new Area(new BasicStroke((float) (margin * 2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
				.createStrokedShape(shape)));
		return area;
	}

	/** Flattened line-segment sequence. */
	static final class Flattened extends ExclusionShape {
		/** [u0, v0, u1, v1]×n. */
		private final double[] edges;

		Flattened(final Shape shape) {
			final java.util.ArrayList<double[]> list = new java.util.ArrayList<>();
			final double[] c = new double[6];
			double startU = 0, startV = 0, lastU = 0, lastV = 0;
			boolean open = false;
			for (PathIterator it = shape.getPathIterator(null, FLATNESS); !it.isDone(); it.next()) {
				switch (it.currentSegment(c)) {
				case PathIterator.SEG_MOVETO:
					if (open) {
						list.add(new double[] { lastU, lastV, startU, startV });
					}
					startU = lastU = c[0];
					startV = lastV = c[1];
					open = true;
					break;
				case PathIterator.SEG_LINETO:
					list.add(new double[] { lastU, lastV, c[0], c[1] });
					lastU = c[0];
					lastV = c[1];
					break;
				case PathIterator.SEG_CLOSE:
					if (open) {
						list.add(new double[] { lastU, lastV, startU, startV });
						lastU = startU;
						lastV = startV;
					}
					open = false;
					break;
				default:
					throw new IllegalStateException("flattened iterator must not emit curves");
				}
			}
			if (open) {
				list.add(new double[] { lastU, lastV, startU, startV });
			}
			this.edges = new double[list.size() * 4];
			for (int i = 0; i < list.size(); ++i) {
				System.arraycopy(list.get(i), 0, this.edges, i * 4, 4);
			}
		}

		@Override
		public AxisSpan lineSpanAt(final double v0, final double v1in) {
			final double v1 = Math.max(v0, v1in);
			double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < this.edges.length; i += 4) {
				final double ua = this.edges[i], va = this.edges[i + 1], ub = this.edges[i + 2],
						vb = this.edges[i + 3];
				if (va >= v0 && va <= v1) {
					min = Math.min(min, ua);
					max = Math.max(max, ua);
				}
				if (vb >= v0 && vb <= v1) {
					min = Math.min(min, ub);
					max = Math.max(max, ub);
				}
				// Intersections with segments crossing the band's upper/lower edges (endpoints inside the band were handled above).
				final double lo = Math.min(va, vb), hi = Math.max(va, vb);
				if (hi - lo > 0) {
					for (final double edge : new double[] { v0, v1 }) {
						if (edge > lo && edge < hi) {
							final double u = ua + (ub - ua) * (edge - va) / (vb - va);
							min = Math.min(min, u);
							max = Math.max(max, u);
						}
					}
				}
			}
			if (min > max) {
				return null;
			}
			return new AxisSpan(min, max);
		}
	}

	/** Per-scanline ranges. */
	static final class Profile extends ExclusionShape {
		private final double vStart, vStep;
		private final double[] minU, maxU;

		Profile(final double vStart, final double vStep, final double[] minU, final double[] maxU) {
			if (!(vStep > 0) || minU.length != maxU.length) {
				throw new IllegalArgumentException();
			}
			this.vStart = vStart;
			this.vStep = vStep;
			this.minU = minU;
			this.maxU = maxU;
		}

		@Override
		public AxisSpan lineSpanAt(final double v0, final double v1in) {
			final double v1 = Math.max(v0, v1in);
			// Scanline k occupies [vStart + k*step, vStart + (k+1)*step).
			int from = (int) Math.floor((v0 - this.vStart) / this.vStep);
			int to = (int) Math.ceil((v1 - this.vStart) / this.vStep) - 1;
			if (to < from) {
				to = from;
			}
			from = Math.max(from, 0);
			to = Math.min(to, this.minU.length - 1);
			double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
			for (int k = from; k <= to; ++k) {
				if (Double.isNaN(this.minU[k])) {
					continue;
				}
				min = Math.min(min, this.minU[k]);
				max = Math.max(max, this.maxU[k]);
			}
			if (min > max) {
				return null;
			}
			return new AxisSpan(min, max);
		}
	}
}
