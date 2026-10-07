package net.zamasoft.foliojet.ua;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * Cache of image intrinsic dimensions (and applied EXIF orientation), 2026-08-16.
 *
 * <p>
 * Layout needs only width and height, not pixels. Measurement passes and {@code STRUCTURE_SCAN}
 * already read <b>headers only</b> through
 * {@link net.zamasoft.foliojet.ua.impl.image.RasterImageLoader#loadImageForLayout},
 * but did not retain the result anywhere. Each occurrence of the same image and each new pass
 * reopened the resource and reread the header. This is wasteful even locally;
 * <b>for remote resources it adds retrieval round trips</b>.
 * </p>
 *
 * <p>
 * Stores only dimensions keyed by URI strings. Without pixels, storage is negligible
 * ({@code loadImageForLayout} returns a lightweight {@link Image} with only width, height,
 * and a no-op {@code drawTo}).
 * </p>
 *
 * <p>
 * Lifetime matches {@link SelectorFacts}/{@link ContainerFacts}; resets at the start of
 * {@code STRUCTURE_SCAN} (multiple passes) and {@code DOCUMENT} (single pass),
 * since the same URI can refer to different content in another document.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ImageMetricsCache {
	private Map<String, Image> metrics;

	/** Clears at document start. */
	public void reset() {
		this.metrics = null;
		this.assets = null;
	}

	/** Recorded dimensions (null if absent). */
	public Image get(final String uri) {
		if (this.metrics == null || uri == null) {
			return null;
		}
		return this.metrics.get(uri);
	}

	/** Records dimensions. */
	public void put(final String uri, final Image image) {
		if (uri == null || image == null) {
			return;
		}
		if (this.metrics == null) {
			this.metrics = new HashMap<String, Image>();
		}
		this.metrics.put(uri, image);
	}

	/** Number of entries (for diagnostics). */
	public int size() {
		return this.metrics == null ? 0 : this.metrics.size();
	}

	/**
	 * Identity of an emitted resource (2026-08-28, for Paged SVG reconversion).
	 *
	 * <p>
	 * Paged SVG pages reference images by <b>content-hash names</b> such as
	 * {@code assets/images/<sha256>.<ext>}. Thus, even with
	 * {@code output.paged-svg.resources=omit} (reconversion without re-emitting the actual resources),
	 * the image bytes previously had to be reread just to determine the name.
	 * Recording this identity alongside dimensions lets the next conversion write the same reference
	 * <b>without opening the image even once</b>.
	 * </p>
	 *
	 * @param sha256     resource content hash
	 * @param mediaType  resource MIME type
	 * @param extension  resource file extension
	 * @param pixelWidth width in pixels (for the manifest)
	 * @param pixelHeight height in pixels (for the manifest)
	 */
	public record Asset(String sha256, String mediaType, String extension, int pixelWidth, int pixelHeight) {
	}

	private Map<String, Asset> assets;

	/** Records the identity of an emitted resource. */
	public void putAsset(final String uri, final Asset asset) {
		if (uri == null || asset == null) {
			return;
		}
		if (this.assets == null) {
			this.assets = new HashMap<String, Asset>();
		}
		this.assets.put(uri, asset);
	}

	/** Recorded resource identity (null if absent). */
	public Asset getAsset(final String uri) {
		if (this.assets == null || uri == null) {
			return null;
		}
		return this.assets.get(uri);
	}

	/** Recorded resource identities (for export). */
	public Map<String, Asset> assets() {
		return this.assets == null ? Map.of() : Collections.unmodifiableMap(this.assets);
	}

	/** Recorded URIs and dimensions (for export). Never returns {@code null}, even when empty. */
	public Map<String, Image> entries() {
		return this.metrics == null ? Map.of() : Collections.unmodifiableMap(this.metrics);
	}

	/**
	 * Records an {@link Image} containing only width and height.
	 * Used to store dimensions loaded from {@code input.image-metrics}.
	 */
	public void putSize(final String uri, final double width, final double height) {
		this.put(uri, new SizeOnlyImage(width, height));
	}

	/**
	 * Dimension-only image without pixels. {@code drawTo} does nothing:
	 * this value is used only in passes that need dimensions alone, and
	 * {@link net.zamasoft.foliojet.ua.impl.AbstractUserAgent#loadImage} does not consult this cache
	 * in the final pass that actually draws.
	 */
	private static final class SizeOnlyImage implements Image {
		private final double width, height;

		SizeOnlyImage(final double width, final double height) {
			this.width = width;
			this.height = height;
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
		public void drawTo(final net.zamasoft.pdfg2d.gc.GC gc) {
			// Dimension-only image; nothing to draw
		}

		@Override
		public String getAltString() {
			return null;
		}
	}
}
