package net.zamasoft.foliojet.ua.impl.image;

import java.awt.image.BufferedImage;

import net.zamasoft.pdfg2d.g2d.image.RasterImageImpl;

/**
 * A raster image that retains the original image for reuse in Paged SVG without recompression.
 * Not created for normal output; used only when emitting the original encoding as a shared asset.
 */
public final class EncodedRasterImage extends RasterImageImpl {
	private final byte[] encoded;
	private final String mediaType;
	private final String extension;

	public EncodedRasterImage(final BufferedImage image, final byte[] encoded, final String mediaType,
			final String extension) {
		super(image);
		this.encoded = encoded;
		this.mediaType = mediaType;
		this.extension = extension;
	}

	public byte[] getEncoded() {
		return this.encoded;
	}

	public String getMediaType() {
		return this.mediaType;
	}

	public String getExtension() {
		return this.extension;
	}
}
