package net.zamasoft.foliojet.ua.impl.pdf;

import java.net.URI;
import java.util.function.Supplier;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.WrappedImage;

/**
 * A wrapper that adds lazily decoded pixels to an image registered directly with PDF
 * ({@code PDFImage}, which has no pixels) only when needed (introduced 2026-08-29).
 *
 * <p>
 * {@code PDFUserAgent} lets PDFWriter read images directly ({@code loadImage}) to skip decoding,
 * so the {@code Image} used during drawing has no pixels. However, {@code filter} operations
 * such as grayscale must transform raster pixels into another image.
 * Read pixels lazily via {@link #getPixels}, leaving ordinary documents without filters
 * on the original path (without extra decoding or memory).
 * </p>
 */
public final class PixelBackedImage extends WrappedImage {
	private final Supplier<Image> pixels;
	private final URI sourceURI;
	private Image loaded;
	private boolean tried;

	public PixelBackedImage(final Image image, final Supplier<Image> pixels) {
		this(image, pixels, null);
	}

	public PixelBackedImage(final Image image, final Supplier<Image> pixels, final URI sourceURI) {
		super(image);
		this.pixels = pixels;
		this.sourceURI = sourceURI;
	}

	/** Retains a reference to the original resource so content remains identifiable even for formats without decoders. */
	public URI getSourceURI() {
		return this.sourceURI;
	}

	/** An image with decoded pixels ({@code RasterImage}). Null if unreadable. */
	public synchronized Image getPixels() {
		if (!this.tried) {
			this.tried = true;
			this.loaded = this.pixels.get();
		}
		return this.loaded;
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
	public void drawTo(final GC gc) throws GraphicsException {
		this.image.drawTo(gc);
	}

	@Override
	public String getAltString() {
		return this.image.getAltString();
	}
}
