package net.zamasoft.foliojet.ua.props;

/**
 * How to deliver shared resources (font subsets and images) for page-split SVG.
 *
 * <p>
 * Fonts and images both concern delivery to the consumer, so configure them together.
 * The three options are mutually exclusive.
 * </p>
 */
public enum PagedSvgResourceMode implements PropCode {
	/**
	 * Emits separate files, referenced by relative URIs from page SVG.
	 *
	 * <p>
	 * This is advantageous for directory output. An image needs only one copy regardless
	 * of how many pages use it, and relative URIs can be followed directly.
	 * </p>
	 */
	REFERENCE,

	/**
	 * Embeds resources in page SVG as {@code data:}.
	 *
	 * <p>
	 * For delivery methods that <b>cannot preserve relative URIs</b>,
	 * such as sending a single page SVG elsewhere. The same image is duplicated per page,
	 * increasing total size.
	 * </p>
	 *
	 * <p>
	 * Even in embedding mode, fonts remain references to shared WOFF2. Subsets are not final
	 * until the whole document has been laid out, and embedding them per page would run
	 * Brotli compression as many times as there are pages.
	 * </p>
	 */
	EMBED,

	/**
	 * Writes references only, without returning resource data.
	 *
	 * <p>
	 * When relaying out the same book with only font size or screen size changed,
	 * font subsets and images are exactly the same as before. References from page SVG
	 * and entries in {@code manifest.json} remain, so the consumer can reuse previously
	 * saved resources at the same URIs unchanged.
	 * </p>
	 *
	 * <p>
	 * <b>This setting reduces bandwidth and storage, not processing time.</b>
	 * Measurements with a 314-page book in vertical writing (one pass) showed only a 121 ms (6%)
	 * time difference, while output shrank 27%, from 15.0 MB to 10.9 MB.
	 * </p>
	 *
	 * <p>
	 * Receive everything with {@link #REFERENCE} the first time; use this from the second conversion onward.
	 * </p>
	 */
	OMIT,

	/**
	 * References original source URLs for web images without copying them (2026-09-02).
	 *
	 * <p>
	 * For converting web content to SVG and displaying it on the same web.
	 * For images sourced from {@code http:}, {@code https:}, or {@code file:}
	 * (raster images with bytes that can be emitted unchanged), page SVG writes
	 * {@code <image href="source URL">} without emitting the data.
	 * The manifest's {@code images[]} entries include {@code source}.
	 * Images without a source ({@code data:}, generated images, rasterized SVGs) and fonts
	 * are emitted as shared resources, as with {@code reference}.
	 * </p>
	 *
	 * <p>
	 * Note: these reference the original server directly. The reader cannot fetch private URLs
	 * or authenticated resources. Images reselected by Copper ({@code image-set()})
	 * or processed images may look different from the originals.
	 * </p>
	 */
	SOURCE;
}
