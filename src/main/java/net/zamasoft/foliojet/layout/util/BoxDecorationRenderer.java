package net.zamasoft.foliojet.layout.util;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.function.DoubleFunction;

import net.zamasoft.foliojet.layout.box.params.Border;
import net.zamasoft.foliojet.layout.box.params.BoxShadow;
import net.zamasoft.foliojet.layout.box.params.Outline;
import net.zamasoft.foliojet.layout.box.params.RectBorder;
import net.zamasoft.foliojet.layout.box.params.RectBorder.Radius;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * Rendering for box-shadow and outline (2026-08-29).
 *
 * <p>
 * {@link net.zamasoft.foliojet.layout.part.AbsoluteRectFrame#draw} determines the drawing order:
 * outer shadows → background → inner shadows → border → outline.
 * </p>
 *
 * <p>
 * <b>Blur approximation.</b> If the destination cannot use exact blur, reproduces the shadow edge
 * in steps with {@link #BLUR_STEPS} concentric fills. Places each step at a Gaussian quantile
 * (σ=blur/2, the same conversion as Chrome/Skia), and sets its alpha to
 * {@code 1-(1-α)^(1/N)} so the center, where all steps overlap, matches the specified color's alpha.
 * Edge falloff is smoother than a single translucent band and looks close to Chrome for the
 * common card shadow {@code 0 2px 8px rgba(0,0,0,.15)}
 * (with 12 steps and α=0.15, each step is about 1.3%, invisible to the naked eye).
 * The outer extent is ±1.73σ≒±0.87×blur, slightly tighter than Chrome's visible tail (≒blur).
 * </p>
 *
 * <p>
 * <b>Exact path (updated 2026-09-03).</b> If the destination provides Gaussian blur
 * ({@link GC.Capability#GAUSSIAN_BLUR}: Java2D, browser-rendered SVG, and PDF supporting transparency),
 * fills once with {@link GC#tryFillBlurred} without approximation.
 * PDF rasterizes only the shadow, keeping body text and boxes as vectors.
 * If exact rendering is refused, falls back to the stepped fills above and notifies the user
 * through {@link ApproximationGC#report} (once per document).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class BoxDecorationRenderer {
	/** Approximation detail for 2822: box-shadow blur. */
	static final String BLUR_DETAIL = "2822.blur-rings";
	/**
	 * Edge positions of each step (in σ units) when exact blur is unavailable.
	 * Standard normal quantiles ((k+0.5)/N), N=12, ordered from outside inward. Edges of the
	 * progressively fainter outer steps correspond to equal-probability intervals of Gaussian falloff.
	 * Eight steps were visible for dark shadows (α=.5) at 4× zoom, so twelve were chosen
	 * (about 1.3% per step for α=.15, about 6% even for α=.5).
	 * {@code text-shadow} blur ({@code AbstractTextBox}) uses the same steps.
	 * PDF supporting transparency rasterizes only shadows, so this approximation is a fallback
	 * when the destination refuses exact rendering (2026-09-03).
	 */
	public static final double[] BLUR_STEPS = { 1.7317, 1.1503, 0.8122, 0.5485, 0.3186, 0.1046, -0.1046, -0.3186,
			-0.5485, -0.8122, -1.1503, -1.7317 };

	private BoxDecorationRenderer() {
		// unused
	}

	/**
	 * Draws outer shadows. Call before the background.
	 * Shadows draw only outside the border box (CSS Backgrounds 3 §7.1: they do not show through
	 * under the box even if its background is transparent), so applies an even-odd clip
	 * with the border box cut out.
	 */
	public static void drawOuterShadows(GC gc, RectFrame frame, double x, double y, double w, double h)
			throws GraphicsException {
		final BoxShadow[] shadows = frame.shadows;
		if (shadows == null || w <= 0 || h <= 0) {
			return;
		}
		double extent = 0;
		for (final BoxShadow s : shadows) {
			if (!s.inset) {
				extent = Math.max(extent,
						Math.abs(s.x) + Math.abs(s.y) + Math.max(0, s.spread) + 1.5 * s.blur);
			}
		}
		if (extent <= 0) {
			// No outer shadow, or it is hidden under the box (neither spread nor offset)
			return;
		}
		final Radius[] radii = resolvedRadii(frame.border, w, h);
		final Path2D.Double clip = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		clip.append(new Rectangle2D.Double(x - extent - 1, y - extent - 1, w + extent * 2 + 2, h + extent * 2 + 2),
				false);
		clip.append(roundedShape(x, y, w, h, radii), false);
		try (final var state = gc.begin()) {
			gc.clip(clip);
			// Draw back to front because the first shadow is on top
			for (int i = shadows.length - 1; i >= 0; --i) {
				final BoxShadow s = shadows[i];
				if (s.inset) {
					continue;
				}
				fillLayers(gc, s, "box-shadow", BLUR_DETAIL,
						d -> expandedShape(x + s.x, y + s.y, w, h, radii, s.spread + d));
			}
		}
	}

	/**
	 * Draws a {@code filter: drop-shadow()} shadow using the box's border shape (2026-08-29).
	 * It should shadow the silhouette of the element's opaque parts, but uses the box frame
	 * as the shadow shape (even a transparent-background box casts a full-box shadow;
	 * documented approximation, reported as 2822 for boxes without a background).
	 * Blur is the same as box-shadow (exact or stepped approximation).
	 * When the destination supports {@code GROUP_FILTER} and {@code DROP_SHADOW}, this is not called;
	 * {@code AbstractDrawable} applies the shadow to the whole element.
	 */
	public static void drawDropShadow(GC gc, RectFrame frame, double x, double y, double w, double h, double dx,
			double dy, double blur, net.zamasoft.pdfg2d.gc.paint.Color color) throws GraphicsException {
		if (w <= 0 || h <= 0 || color == null) {
			return;
		}
		final Radius[] radii = resolvedRadii(frame.border, w, h);
		// Convert the drop-shadow blur radius the same way as box-shadow (σ=blur/2,
		// filter-effects-1 §9.2; corrected on 2026-08-29 to match Java2D)
		final BoxShadow s = new BoxShadow(dx, dy, blur, 0, color, false);
		if (!frame.background.isVisible()) {
			ApproximationGC.report(gc, "filter", "2822.drop-shadow-box");
		}
		try (final var state = gc.begin()) {
			fillLayers(gc, s, "filter", "2822.drop-shadow-blur-rings",
					d -> expandedShape(x + s.x, y + s.y, w, h, radii, d));
		}
	}

	/**
	 * Draws inner shadows. Call after the background and before the border.
	 * Clips to the padding box and fills an even-odd band made by cutting a
	 * "hole shrunk by the spread and offset" out of the padding box.
	 */
	public static void drawInsetShadows(GC gc, RectFrame frame, double x, double y, double w, double h)
			throws GraphicsException {
		final BoxShadow[] shadows = frame.shadows;
		if (shadows == null) {
			return;
		}
		boolean any = false;
		for (final BoxShadow s : shadows) {
			any |= s.inset;
		}
		if (!any) {
			return;
		}
		final RectBorder border = frame.border;
		final double bl = border.getLeft().width, bt = border.getTop().width;
		final double br = border.getRight().width, bb = border.getBottom().width;
		final double px = x + bl, py = y + bt, pw = w - bl - br, ph = h - bt - bb;
		if (pw <= 0 || ph <= 0) {
			return;
		}
		// Padding-box corner radii are border-box radii minus border widths
		// (CSS Backgrounds 3 §5.2)
		final Radius[] outer = resolvedRadii(border, w, h);
		final Radius[] radii = { shrink(outer[0], bl, bt), shrink(outer[1], br, bt), shrink(outer[2], bl, bb),
				shrink(outer[3], br, bb) };
		final Shape paddingShape = roundedShape(px, py, pw, ph, radii);
		try (final var state = gc.begin()) {
			gc.clip(paddingShape);
			for (int i = shadows.length - 1; i >= 0; --i) {
				final BoxShadow s = shadows[i];
				if (!s.inset) {
					continue;
				}
				// For exact blur, place the band's outer edge well outside the padding box
				// (if it coincides with the padding box, blur fades that edge.
				// Clipping removes the outer edge, so its shape does not appear in the result)
				final Shape band0;
				if (s.blur > 0 && gc.supports(GC.Capability.GAUSSIAN_BLUR)) {
					final double reach = Math.abs(s.x) + Math.abs(s.y) + Math.abs(s.spread) + 1.5 * s.blur + 1;
					band0 = new Rectangle2D.Double(px - reach, py - reach, pw + reach * 2, ph + reach * 2);
				} else {
					band0 = paddingShape;
				}
				fillLayers(gc, s, "box-shadow", BLUR_DETAIL, d -> {
					final Shape hole = expandedShape(px + s.x, py + s.y, pw, ph, radii, -(s.spread + d));
					if (hole == null) {
						// The hole has collapsed = the entire padding box is shadow
						return paddingShape;
					}
					final Path2D.Double band = new Path2D.Double(Path2D.WIND_EVEN_ODD);
					band.append(band0, false);
					band.append(hole, false);
					return band;
				});
			}
		}
	}

	/**
	 * Draws the outline. Call after the border. Draws a {@link RectBorder} with identical lines
	 * on all four sides of a rectangle expanded outward from the border edges by offset + width
	 * (reuses border rendering for dotted, double, groove, and other line styles).
	 * Adds the same distance to the border corner radii (as Chrome does).
	 */
	public static void drawOutline(GC gc, RectFrame frame, double x, double y, double w, double h)
			throws GraphicsException {
		final Outline outline = frame.outline;
		if (outline == null) {
			return;
		}
		final Border line = outline.border;
		final double d = outline.offset + line.width;
		final double ow = w + d * 2, oh = h + d * 2;
		if (ow <= 0 || oh <= 0) {
			return;
		}
		final Radius[] radii = resolvedRadii(frame.border, w, h);
		final RectBorder rect = RectBorder.create(line, line, line, line, grow(radii[0], d), grow(radii[1], d),
				grow(radii[2], d), grow(radii[3], d));
		try (final var state = gc.begin()) {
			BorderRenderer.INSTANCE.drawRectBorder(gc, rect, x - d, y - d, ow, oh);
		}
	}

	/**
	 * Fills one shadow. {@code shapeAt} maps an edge offset (positive outward) to the fill shape
	 * (null means collapsed; do not fill). Uses exact blur through {@link GC#tryFillBlurred}
	 * if the destination supports it; otherwise approximates with stepped fills and reports
	 * 2822 with {@code property}/{@code blurDetail}.
	 */
	private static void fillLayers(GC gc, BoxShadow s, String property, String blurDetail,
			DoubleFunction<Shape> shapeAt) throws GraphicsException {
		final float alpha = s.color.getAlpha();
		if (alpha <= 0) {
			return;
		}
		try (final var state = gc.begin()) {
			gc.setFillPaint(s.color);
			if (s.blur <= 0) {
				gc.setFillAlpha(alpha);
				final Shape shape = shapeAt.apply(0);
				if (shape != null) {
					gc.fill(shape);
				}
				return;
			}
			final double sigma = s.blur / 2;
			// Exact: fill once with Gaussian blur in the specified color (σ=blur/2). If false,
			// the contract guarantees nothing was drawn, so fall back directly to the existing approximation.
			gc.setFillAlpha(alpha);
			final Shape exact = shapeAt.apply(0);
			if (exact != null && gc.tryFillBlurred(exact, sigma)) {
				return;
			}
			ApproximationGC.report(gc, property, blurDetail);
			final int n = BLUR_STEPS.length;
			// Per-step value yielding composite alpha equal to alpha at the center where all steps overlap.
			// For an opaque shadow (α=1), each step would also be 1, producing a solid mass all the way
			// to the outer edge, so cap at 0.98 as for text-shadow
			gc.setFillAlpha((float) (1 - Math.pow(1 - Math.min(alpha, 0.98), 1.0 / n)));
			for (int k = 0; k < n; ++k) {
				final Shape shape = shapeAt.apply(BLUR_STEPS[k] * sigma);
				if (shape != null) {
					gc.fill(shape);
				}
			}
		}
	}

	private static Radius[] resolvedRadii(RectBorder border, double w, double h) {
		return new Radius[] { border.getTopLeft().resolve(w, h), border.getTopRight().resolve(w, h),
				border.getBottomLeft().resolve(w, h), border.getBottomRight().resolve(w, h) };
	}

	private static Shape roundedShape(double x, double y, double w, double h, Radius[] radii) {
		return BorderRenderer.INSTANCE.getRoundedShape(x, y, w, h, radii[0], radii[1], radii[2], radii[3]);
	}

	/**
	 * Returns the rectangle expanded outward by {@code d} (shrunk if negative), with corner radii
	 * increased or decreased by the same amount. Returns null if shrinking collapses it.
	 */
	private static Shape expandedShape(double x, double y, double w, double h, Radius[] radii, double d) {
		final double nw = w + d * 2, nh = h + d * 2;
		if (nw <= 0 || nh <= 0) {
			return null;
		}
		return BorderRenderer.INSTANCE.getRoundedShape(x - d, y - d, nw, nh, grow(radii[0], d), grow(radii[1], d),
				grow(radii[2], d), grow(radii[3], d));
	}

	/** Increases or decreases radii by {@code d}. Right angles (0) stay right angles. */
	private static Radius grow(Radius r, double d) {
		if (r.hr <= 0 && r.vr <= 0) {
			return Radius.ZERO_RADIUS;
		}
		return Radius.create(Math.max(0, r.hr + d), Math.max(0, r.vr + d));
	}

	/** Reduces radii by separate horizontal and vertical amounts. */
	private static Radius shrink(Radius r, double dh, double dv) {
		if (r.hr <= 0 && r.vr <= 0) {
			return Radius.ZERO_RADIUS;
		}
		return Radius.create(Math.max(0, r.hr - dh), Math.max(0, r.vr - dv));
	}
}
