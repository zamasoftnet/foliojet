package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.net.URI;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;

/**
 * An image with its source URI attached (2026-08-28, for Paged SVG reconversion).
 *
 * <p>
 * Used to associate resource identity (content hash), determined during drawing,
 * with <b>the image's source URI</b> in {@code metrics.json}.
 * The GC follows the {@link WrappedImage} chain to its contents,
 * so inserting this wrapper does not change drawing behavior.
 * </p>
 */
final class SourcedImage extends WrappedImage {
	final URI uri;

	/**
	 * Image created by the companion PDF from the same source (2026-09-03, simultaneous PDF output).
	 * PDF deduplicates images by source URI and embeds original JPEG bytes,
	 * so pass this to the secondary GC. If absent, use the primary image unchanged.
	 */
	Image companion;

	SourcedImage(final Image image, final URI uri) {
		super(image);
		this.uri = uri;
	}

	@Override
	public double getWidth() {
		return this.image.getWidth();
	}

	@Override
	public double getHeight() {
		return this.image.getHeight();
	}

	@Override
	public void drawTo(final GC gc) {
		this.image.drawTo(gc);
	}

	@Override
	public String getAltString() {
		return this.image.getAltString();
	}
}
