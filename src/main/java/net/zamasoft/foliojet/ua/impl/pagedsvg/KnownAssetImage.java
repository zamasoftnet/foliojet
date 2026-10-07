package net.zamasoft.foliojet.ua.impl.pagedsvg;

import net.zamasoft.foliojet.ua.ImageMetricsCache;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * An image that only references a resource written by the previous output (2026-08-28).
 *
 * <p>
 * Paged SVG pages reference images by content-hash names such as
 * {@code assets/images/<sha256>.<ext>}. Thus even reconversion with
 * {@code output.paged-svg.resources=omit} had to reread image bytes to determine the name.
 * Passing the identity ({@link ImageMetricsCache.Asset}) recorded in the previous
 * {@code metrics.json} allows simply creating this image, <b>without opening the resource
 * at all</b> (eliminating the entire fetch round trip for remote resources).
 * </p>
 *
 * <p>
 * Only {@link DirectPagedSVGGC} can draw it. It draws nothing when passed to other GCs,
 * so {@link PagedSVGUserAgent} returns this image
 * <b>only for direct output with omit</b>.
 * </p>
 */
final class KnownAssetImage implements Image {
	/** Logical dimensions used for layout (pt). */
	private final double width, height;

	/** Identity of the resource written by the previous output. */
	final ImageMetricsCache.Asset asset;

	KnownAssetImage(final double width, final double height, final ImageMetricsCache.Asset asset) {
		this.width = width;
		this.height = height;
		this.asset = asset;
	}

	@Override
	public double getWidth() {
		return this.width;
	}

	@Override
	public double getHeight() {
		return this.height;
	}

	@Override
	public void drawTo(final GC gc) {
		// A reference-only image, so it does not draw itself.
		// DirectPagedSVGGC.drawImage recognizes this type and writes the reference.
	}

	@Override
	public String getAltString() {
		return null;
	}
}
