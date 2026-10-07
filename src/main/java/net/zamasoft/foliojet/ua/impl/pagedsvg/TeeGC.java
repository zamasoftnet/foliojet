package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.awt.Shape;
import java.awt.geom.AffineTransform;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.paint.Paint;
import net.zamasoft.pdfg2d.gc.text.Text;

/**
 * A graphics context that forwards the same drawing operations to two GCs
 * (2026-09-03, simultaneous page-split SVG and PDF output).
 *
 * <p>
 * Layout runs once; page drawing goes to both the primary (page SVG) and secondary (PDF) GCs.
 * State queries and capability checks ({@link #supports}) follow the primary:
 * drawing code chooses its approach from that answer, while the secondary uses the GC's
 * default fallbacks (fill for blur, ignored effects). Text is already shaped by the primary's
 * font manager, but the font store is shared with the secondary
 * ({@code PagedSVGUserAgent.getFontManager}), so PDF can write it directly as text.
 * Create group images in both GCs, combine them in {@link TeeImage},
 * and pass each GC its own image when drawing.
 * </p>
 */
class TeeGC implements GC {
	private final GC primary;
	private final GC secondary;

	TeeGC(final GC primary, final GC secondary) {
		this.primary = primary;
		this.secondary = secondary;
	}

	GC primary() {
		return this.primary;
	}

	GC secondary() {
		return this.secondary;
	}

	@Override
	public FontManager getFontManager() {
		return this.primary.getFontManager();
	}

	@Override
	public State begin() throws GraphicsException {
		final State a = this.primary.begin();
		final State b = this.secondary.begin();
		return () -> {
			try {
				a.close();
			} finally {
				b.close();
			}
		};
	}

	@Override
	public State beginArtifactScope() throws GraphicsException {
		final State a = this.primary.beginArtifactScope();
		final State b = this.secondary.beginArtifactScope();
		return () -> {
			try {
				a.close();
			} finally {
				b.close();
			}
		};
	}

	@Override
	public State beginTextReplacement(final String logicalText) throws GraphicsException {
		final State a = this.primary.beginTextReplacement(logicalText);
		final State b = this.secondary.beginTextReplacement(logicalText);
		return () -> {
			try {
				a.close();
			} finally {
				b.close();
			}
		};
	}

	@Override
	public void resetState() throws GraphicsException {
		this.primary.resetState();
		this.secondary.resetState();
	}

	@Override
	public void setStrokePaint(final Paint paint) throws GraphicsException {
		this.primary.setStrokePaint(paint);
		this.secondary.setStrokePaint(paint);
	}

	@Override
	public Paint getStrokePaint() {
		return this.primary.getStrokePaint();
	}

	@Override
	public void setFillPaint(final Paint paint) throws GraphicsException {
		this.primary.setFillPaint(paint);
		this.secondary.setFillPaint(paint);
	}

	@Override
	public Paint getFillPaint() {
		return this.primary.getFillPaint();
	}

	@Override
	public float getStrokeAlpha() {
		return this.primary.getStrokeAlpha();
	}

	@Override
	public void setStrokeAlpha(final float strokeAlpha) throws GraphicsException {
		this.primary.setStrokeAlpha(strokeAlpha);
		this.secondary.setStrokeAlpha(strokeAlpha);
	}

	@Override
	public float getFillAlpha() {
		return this.primary.getFillAlpha();
	}

	@Override
	public void setFillAlpha(final float fillAlpha) throws GraphicsException {
		this.primary.setFillAlpha(fillAlpha);
		this.secondary.setFillAlpha(fillAlpha);
	}

	@Override
	public void setBlendMode(final net.zamasoft.pdfg2d.gc.paint.BlendMode mode) throws GraphicsException {
		this.primary.setBlendMode(mode);
		this.secondary.setBlendMode(mode);
	}

	@Override
	public boolean supports(final Capability capability) {
		return this.primary.supports(capability);
	}

	@Override
	public net.zamasoft.pdfg2d.gc.paint.BlendMode getBlendMode() {
		return this.primary.getBlendMode();
	}

	@Override
	public void setLineWidth(final double width) throws GraphicsException {
		this.primary.setLineWidth(width);
		this.secondary.setLineWidth(width);
	}

	@Override
	public double getLineWidth() {
		return this.primary.getLineWidth();
	}

	@Override
	public void setLinePattern(final double[] pattern) throws GraphicsException {
		this.primary.setLinePattern(pattern);
		this.secondary.setLinePattern(pattern);
	}

	@Override
	public double[] getLinePattern() {
		return this.primary.getLinePattern();
	}

	@Override
	public void setLineJoin(final LineJoin style) throws GraphicsException {
		this.primary.setLineJoin(style);
		this.secondary.setLineJoin(style);
	}

	@Override
	public LineJoin getLineJoin() {
		return this.primary.getLineJoin();
	}

	@Override
	public void setLineCap(final LineCap style) throws GraphicsException {
		this.primary.setLineCap(style);
		this.secondary.setLineCap(style);
	}

	@Override
	public LineCap getLineCap() {
		return this.primary.getLineCap();
	}

	@Override
	public void setTextMode(final TextMode textMode) throws GraphicsException {
		this.primary.setTextMode(textMode);
		this.secondary.setTextMode(textMode);
	}

	@Override
	public TextMode getTextMode() {
		return this.primary.getTextMode();
	}

	@Override
	public void transform(final AffineTransform at) throws GraphicsException {
		this.primary.transform(at);
		this.secondary.transform(at);
	}

	@Override
	public AffineTransform getTransform() {
		return this.primary.getTransform();
	}

	@Override
	public void clip(final Shape shape) throws GraphicsException {
		this.primary.clip(shape);
		this.secondary.clip(shape);
	}

	@Override
	public void draw(final Shape shape) throws GraphicsException {
		this.primary.draw(shape);
		this.secondary.draw(shape);
	}

	@Override
	public void fill(final Shape shape) throws GraphicsException {
		this.primary.fill(shape);
		this.secondary.fill(shape);
	}

	@Override
	public void fillDraw(final Shape shape) throws GraphicsException {
		this.primary.fillDraw(shape);
		this.secondary.fillDraw(shape);
	}

	@Override
	public void fillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		this.primary.fillBlurred(shape, sigma);
		this.secondary.fillBlurred(shape, sigma);
	}

	/**
	 * Tries the primary and secondary separately. If the secondary (PDF) cannot draw it
	 * (e.g., PDF/A-1 prohibits transparency), use an unblurred fill only for the secondary.
	 * Stepped-fill approximation exists only in foliojet, and this simultaneous-output combination
	 * is rare, so use the designed fallback (pdf-blur-raster-design.md §0-11).
	 * Returns the primary result.
	 */
	@Override
	public boolean tryFillBlurred(final Shape shape, final double sigma) throws GraphicsException {
		final boolean drawn = this.primary.tryFillBlurred(shape, sigma);
		if (!this.secondary.tryFillBlurred(shape, sigma)) {
			this.secondary.fill(shape);
		}
		return drawn;
	}

	@Override
	public void drawImage(final Image image) throws GraphicsException {
		if (image instanceof final TeeImage tee) {
			this.primary.drawImage(tee.primary);
			this.secondary.drawImage(tee.secondary);
			return;
		}
		this.primary.drawImage(image);
		this.secondary.drawImage(forSecondary(image));
	}

	/** Image for the secondary. Replace sourced images with ones the secondary (PDF) created from the same source. */
	private Image forSecondary(final Image image) {
		if (image instanceof final SourcedImage sourced && sourced.companion != null
				&& this.secondary instanceof net.zamasoft.pdfg2d.pdf.gc.PDFGC) {
			// Use the PDF image only when the secondary is an actual PDF GC. In filter capture groups
			// (later replayed to pixels), PDF-only images cannot be drawn, so keep the primary image.
			return sourced.companion;
		}
		return image;
	}

	@Override
	public void drawImage(final Image image, final net.zamasoft.pdfg2d.gc.GroupEffects effects)
			throws GraphicsException {
		if (image instanceof final TeeImage tee) {
			this.primary.drawImage(tee.primary, effects);
			this.secondary.drawImage(tee.secondary, effects);
			return;
		}
		this.primary.drawImage(image, effects);
		this.secondary.drawImage(forSecondary(image), effects);
	}

	@Override
	public void drawText(final Text text, final double x, final double y) throws GraphicsException {
		// The font store is shared (PagedSVGUserAgent.getFontManager),
		// so text shaped by the primary can be drawn unchanged to the secondary.
		this.primary.drawText(text, x, y);
		this.secondary.drawText(text, x, y);
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		final GroupImageGC a = this.primary.createGroupImage(width, height);
		final GroupImageGC b = this.secondary.createGroupImage(width, height);
		return new TeeGroupImageGC(a, b);
	}

	/**
	 * Capture group for filters (2026-09-03). Combines the primary and secondary capture groups.
	 * The secondary (PDF) capture group is later replayed to pixels, so pass the primary image
	 * (with pixels) to the secondary within it as well
	 * ({@link #forSecondary} does not replace images when the secondary is not PDFGC).
	 */
	@Override
	public GroupImageGC createFilterGroup(final double width, final double height) throws GraphicsException {
		final GroupImageGC a = this.primary.createFilterGroup(width, height);
		final GroupImageGC b = this.secondary.createFilterGroup(width, height);
		return new TeeGroupImageGC(a, b);
	}

	/**
	 * Returns the primary result. If the secondary is UNSUPPORTED, draw there without effects
	 * (simultaneous-output fallback).
	 */
	@Override
	public GroupEffectsResult drawGroupEffects(final Image image, final net.zamasoft.pdfg2d.gc.GroupEffects effects)
			throws GraphicsException {
		final Image a = image instanceof final TeeImage tee ? tee.primary : image;
		final Image b = image instanceof final TeeImage tee ? tee.secondary : forSecondary(image);
		final GroupEffectsResult result = this.primary.drawGroupEffects(a, effects);
		if (this.secondary.drawGroupEffects(b, effects) == GroupEffectsResult.UNSUPPORTED) {
			this.secondary.drawImage(b);
		}
		return result;
	}

	@Override
	public boolean rasterizesGroupEffects() {
		return this.primary.rasterizesGroupEffects();
	}

	/** Group image context that draws to both group images and returns {@link TeeImage} when finished. */
	private static final class TeeGroupImageGC extends TeeGC implements GroupImageGC {
		private final GroupImageGC a;
		private final GroupImageGC b;

		TeeGroupImageGC(final GroupImageGC a, final GroupImageGC b) {
			super(a, b);
			this.a = a;
			this.b = b;
		}

		@Override
		public Image finish() throws GraphicsException {
			return new TeeImage(this.a.finish(), this.b.finish());
		}
	}

	/** Pair of primary and secondary group images. Each GC draws its corresponding image. */
	static final class TeeImage implements Image {
		final Image primary;
		final Image secondary;

		TeeImage(final Image primary, final Image secondary) {
			this.primary = primary;
			this.secondary = secondary;
		}

		@Override
		public Intrinsic getIntrinsic() {
			return this.primary.getIntrinsic();
		}

		@Override
		public double getWidth() {
			return this.primary.getWidth();
		}

		@Override
		public double getHeight() {
			return this.primary.getHeight();
		}

		@Override
		public void drawTo(final GC gc) throws GraphicsException {
			if (gc instanceof final TeeGC tee) {
				this.primary.drawTo(tee.primary);
				this.secondary.drawTo(tee.secondary);
			} else {
				this.primary.drawTo(gc);
			}
		}

		@Override
		public String getAltString() {
			return this.primary.getAltString();
		}
	}
}
