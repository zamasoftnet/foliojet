package net.zamasoft.foliojet.ua;

import java.io.IOException;
import java.io.InputStream;

/**
 * Entry point for reading/writing image metrics tables (2026-08-28).
 *
 * <p>
 * <b>Writes JSON</b> ({@link ImageMetricsJSON}), matching the format of other page-split SVG
 * artifacts ({@code manifest.json} and page JSON).
 * </p>
 *
 * <p>
 * Reading XML ({@code metrics.xml}) emitted during 4.0.0 development was removed on 2026-10-04:
 * it existed only for compatibility with a format that no published version ever emitted.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ImageMetricsIO {

	private ImageMetricsIO() {
		// Utility
	}

	/** MIME type of the export format. */
	public static final String MEDIA_TYPE = "application/json";

	/** Name of the metrics table emitted by page-split SVG. */
	public static final String FILE_NAME = "metrics.json";

	/** Writes the metrics table as JSON. */
	public static byte[] write(final ImageMetricsCache cache, final double resolution) throws IOException {
		return ImageMetricsJSON.write(cache, resolution);
	}

	/**
	 * Reads a metrics table (JSON).
	 *
	 * @return number of entries loaded
	 */
	public static int read(final InputStream in, final ImageMetricsCache cache, final double resolution)
			throws IOException {
		return ImageMetricsJSON.read(in, cache, resolution);
	}
}
