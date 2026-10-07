package net.zamasoft.foliojet.ua.props;

/**
 * Compression policy for shared images in page-split SVG (2026-09-03, cti.li request).
 *
 * <p>
 * The default, {@code none}, emits fetched images unchanged (JPEG remains JPEG; others use PNG).
 * {@code jpeg} recompresses raster images without transparency as JPEG (quality 0.8).
 * Small images (at or below the {@code output.paged-svg.image.compression.lossless} threshold)
 * and images with transparency remain lossless (PNG).
 * SVG images remain vectors and are excluded.
 * </p>
 */
public enum PagedSvgImageCompression implements PropCode {
	/** Emits images unchanged. */
	NONE,
	/** Recompresses large raster images without transparency as JPEG. */
	JPEG;
}
