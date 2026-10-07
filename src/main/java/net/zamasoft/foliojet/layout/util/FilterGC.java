package net.zamasoft.foliojet.layout.util;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.css.value.css3.FilterValue;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.GroupEffects;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * {@link GC} wrapper that applies {@code filter} color matrices and blur to paints and images
 * during drawing commands (added 2026-08-29).
 *
 * <p>
 * Drawables (backgrounds, borders, text, images) set paints through {@link #setFillPaint}/
 * {@link #setStrokePaint} and draw images through {@link #drawImage}.
 * Routing these two points through {@link FilterOps} applies effects without touching drawable
 * implementations. Text color is also paint, so it changes through the same path.
 * {@link #fillBlurred} and {@link #tryFillBlurred} use the already set
 * (= transformed) paint and therefore pass through.
 * </p>
 *
 * <p>
 * Also wraps child GCs created by {@link #createGroupImage} (nested opacity) so effects reach
 * child drawing. All other operations pass through ({@link AbstractDelegatingGC}).
 * When the destination supports {@code GROUP_FILTER}, this wrapper is not used;
 * {@code AbstractDrawable} turns the whole element into a group image and applies
 * {@link GroupEffects}.
 * </p>
 */
public final class FilterGC extends AbstractDelegatingGC {
	private final FilterValue filter;

	public FilterGC(final GC gc, final FilterValue filter) {
		super(gc);
		this.filter = filter;
	}

	/** Pt per pixel under the current transform (for blur conversion). */
	private double pixelScale() {
		final AffineTransform at = this.gc.getTransform();
		if (at == null) {
			return 1;
		}
		final double det = Math.abs(at.getDeterminant());
		return det > 0 ? Math.sqrt(det) : 1;
	}

	@Override
	public void setStrokePaint(final Paint paint) throws GraphicsException {
		this.gc.setStrokePaint(FilterOps.apply(this.filter, paint, this.pixelScale()));
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		this.gc.setFillPaint(FilterOps.apply(this.filter, paint, this.pixelScale()));
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		this.gc.drawImage(FilterOps.apply(this.filter, image, this.pixelScale()));
	}

	@Override
	public void drawImage(final Image image, final GroupEffects effects) throws GraphicsException {
		this.gc.drawImage(FilterOps.apply(this.filter, image, this.pixelScale()), effects);
	}

	@Override
	public GroupEffectsResult drawGroupEffects(final Image image, final GroupEffects effects)
			throws GraphicsException {
		return this.gc.drawGroupEffects(FilterOps.apply(this.filter, image, this.pixelScale()), effects);
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createGroupImage(width, height), this.filter);
	}

	@Override
	public GroupImageGC createFilterGroup(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createFilterGroup(width, height), this.filter);
	}

	/** Wrapper that propagates effects to nested groups. */
	private static final class Group extends AbstractDelegatingGC implements GroupImageGC {
		private final GroupImageGC group;

		Group(final GroupImageGC group, final FilterValue filter) {
			super(new FilterGC(group, filter));
			this.group = group;
		}

		@Override
		public Image finish() throws GraphicsException {
			return this.group.finish();
		}
	}
}
