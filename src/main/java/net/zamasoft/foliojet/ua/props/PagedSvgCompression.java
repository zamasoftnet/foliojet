package net.zamasoft.foliojet.ua.props;

/**
 * Whether to return page SVG and page JSON compressed with gzip.
 *
 * <p>
 * Only textual results shrink. Shared WOFF2 and PNG/JPEG are already compressed
 * and do not shrink with gzip (measured: WOFF2 grew 0.1%, PNG shrank 1.7%).
 * Therefore, never apply gzip to them.
 * </p>
 *
 * <p>
 * Measurements with a 314-page book in vertical writing showed page SVG shrinking 78.2%
 * and page JSON 79.1%, reducing total output from 14.96 MB to 6.35 MB, <b>a 57.5% reduction</b>.
 * Compression itself took about 0.1 seconds for 628 results.
 * </p>
 *
 * <p>
 * On fast connections, round-trip time barely changes (transfer took about 0.2 seconds over 613 Mbps).
 * This benefits slow connections, metered connections, and storage of results as received.
 * </p>
 */
public enum PagedSvgCompression implements PropCode {
	/**
	 * Returns results unchanged.
	 */
	NONE,

	/**
	 * Returns page SVG as {@code .svgz} and page JSON as {@code .json.gz}, compressed with gzip.
	 * Leaves {@code manifest.json} uncompressed because it is the entry point.
	 */
	GZIP;
}
