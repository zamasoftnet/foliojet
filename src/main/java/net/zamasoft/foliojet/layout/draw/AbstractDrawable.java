package net.zamasoft.foliojet.layout.draw;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.util.ApproximationGC;
import net.zamasoft.foliojet.layout.util.FilterGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;

public abstract class AbstractDrawable implements Drawable {
	protected final Shape clip;
	protected final PageBox pageBox;
	protected final float opacity;
	protected final AffineTransform transform;
	/**
	 * {@code mix-blend-mode} (2026-08-29). If the output supports per-layer blending ({@link
	 * GC.Capability#BLEND_GROUP}), blends this drawable as a single layer; otherwise approximates by applying blending
	 * per drawing command (see MixBlendMode). Concrete classes that do not receive params set it immediately after
	 * construction via {@link #withBlendMode}.
	 */
	protected net.zamasoft.pdfg2d.gc.paint.BlendMode blendMode = net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL;
	/**
	 * {@code filter} (2026-08-29). {@link Drawer} groups element-wide effects into a layer. For drawables that cannot
	 * use layers, falls back to per-command approximation through {@link FilterGC}, which substitutes paints and
	 * images. Set through {@link #withFilter}.
	 */
	protected FilterValue filter = FilterValue.NONE;

	public AbstractDrawable(final PageBox pageBox, final Shape clip, final float opacity,
			final AffineTransform transform) {
		this.pageBox = pageBox;
		this.clip = clip;
		this.opacity = opacity;
		this.transform = transform;
	}

	/** Sets the blend mode and returns this instance (call immediately after construction). */
	public final AbstractDrawable withBlendMode(final net.zamasoft.pdfg2d.gc.paint.BlendMode mode) {
		this.blendMode = mode == null ? net.zamasoft.pdfg2d.gc.paint.BlendMode.NORMAL : mode;
		return this;
	}

	/** Sets the filter and returns this instance (call immediately after construction). */
	public final AbstractDrawable withFilter(final FilterValue filter) {
		this.filter = filter == null ? FilterValue.NONE : filter;
		return this;
	}

	/** Whether this drawable's main pass is redirected to its own output stream. */
	final boolean createsOwnGroup(final GC gc, final java.util.Set<FilterValue> groupedFilters) {
		final FilterValue f = this.filter.excluding(groupedFilters);
		final boolean blendGroup = this.blendMode != gc.getBlendMode() && gc.supports(GC.Capability.BLEND_GROUP);
		return blendGroup || this.opacity * f.opacity != 1f;
	}

	/**
	 * Appends nonidentity GC transforms and filters to the {@code describe} string for display-list dumps
	 * (2026-08-08). Dump coordinates are before GC transformation, so transform regressions otherwise leave no trace
	 * in goldens (the gap that let ParamsFields' lost %translate pass for 10 days). Existing goldens without
	 * transforms or filters remain unchanged. Emit filters only on drawables of the declaring element, not descendants
	 * receiving them through inheritance.
	 */
	protected final String describeTransform(final String base) {
		String s = base;
		if (!this.transform.isIdentity()) {
			final double[] m = new double[6];
			this.transform.getMatrix(m);
			s = s + String.format(java.util.Locale.ROOT, " tf=[%.2f %.2f %.2f %.2f %.2f %.2f]", m[0], m[1], m[2],
					m[3], m[4], m[5]);
		}
		if (this.filter.declared != null) {
			s = s + " filter=[" + this.filter.declared + "]";
		}
		return s;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Outputs the clip shape's bounding rectangle (2026-08-09). Existing goldens without clipping remain unchanged.
	 * </p>
	 */
	@Override
	public final String describeClip() {
		if (this.clip == null) {
			return "";
		}
		final java.awt.geom.Rectangle2D b = this.clip.getBounds2D();
		return String.format(java.util.Locale.ROOT, " clip=[%.2f %.2f %.2f %.2f]", b.getX(), b.getY(), b.getWidth(),
				b.getHeight());
	}

	public final void draw(GC gc, double x, double y) throws GraphicsException {
		final FilterValue f = FilterScope.effective(gc, this.filter);
		GC.State state = null;
		if (this.clip != null || !this.transform.isIdentity()) {
			state = gc.begin();
			if (this.clip != null) {
				gc.clip(this.clip);
			}
			if (!this.transform.isIdentity()) {
				gc.transform(this.transform);
			}
		}
		// mix-blend-mode (2026-08-29). If the output supports layer blending, put the entire
		// drawable into a single layer (group image), then blend it
		// (exact). Otherwise set it outside the transparency group and blend per drawing command
		// (approximation; the mode also affects Do for the group image).
		// Restore the original value at the end.
		final net.zamasoft.pdfg2d.gc.paint.BlendMode outerBlend = gc.getBlendMode();
		final boolean blends = this.blendMode != outerBlend;
		final boolean blendGroup = blends && gc.supports(GC.Capability.BLEND_GROUP);
		if (blends && !blendGroup) {
			ApproximationGC.report(gc, "mix-blend-mode", "2822.per-drawable");
			gc.setBlendMode(this.blendMode);
		}

		// filter: opacity() multiplies group opacity (the specified order is filter → opacity,
		// but both multiply the same group, so the result is identical).
		final float opacity = this.opacity * f.opacity;
		if (f.needsGroup()) {
			ApproximationGC.report(gc, "filter", "2822.per-drawable");
		}

		/* NoAndroid begin */
		final GC xgc;
		final GroupImageGC ggc;
		float alpha = gc.getFillAlpha();
		if (blendGroup || opacity != 1f) {
			// Begin transparency (group into a layer).
			xgc = gc;
			ggc = gc.createGroupImage(this.pageBox.getWidth(), this.pageBox.getHeight());
			gc = ggc;
		} else {
			xgc = ggc = null;
			gc.setFillAlpha(opacity);
		}
		/* NoAndroid end */

		this.drawFilterApproximation(gc, x, y, f);

		/* NoAndroid begin */
		if (ggc != null) {
			// End transparency.
			Image gi = ggc.finish();
			if (blendGroup) {
				xgc.setBlendMode(this.blendMode);
			}
			xgc.setFillAlpha(opacity);
			xgc.drawImage(gi);
			xgc.setFillAlpha(alpha);
			gc = xgc;
		} else {
			gc.setFillAlpha(alpha);
		}
		/* NoAndroid end */
		if (blends) {
			gc.setBlendMode(outerBlend);
		}

		if (state != null) {
			state.close();
		}
	}

	/** Existing approximation path that applies filters per drawing command. */
	private void drawFilterApproximation(final GC gc, final double x, final double y, final FilterValue f)
			throws GraphicsException {
		// drop-shadow() goes below content, outside color conversion (preserve the specified shadow color).
		if (f.shadow != null) {
			this.drawFilterShadow(gc, x, y, f.shadow);
		}
		if (f.hasColorOps()) {
			// Apply color matrices and blur to all content through a GC that substitutes paints and images.
			this.innerDraw(new FilterGC(gc, f), x, y);
		} else {
			this.innerDraw(gc, x, y);
		}
	}

	/**
	 * Draws the shadow for {@code filter: drop-shadow()} (called before content; not called when the output supports
	 * layer drop shadows). The default does nothing; concrete classes that know the shape (frames, replaced elements)
	 * override it. Does not affect text drawables (use {@code text-shadow}; documented).
	 */
	protected void drawFilterShadow(final GC gc, final double x, final double y,
			final FilterValue.DropShadow shadow) throws GraphicsException {
		// no-op
	}

	public abstract void innerDraw(GC gc, double x, double y) throws GraphicsException;
}
