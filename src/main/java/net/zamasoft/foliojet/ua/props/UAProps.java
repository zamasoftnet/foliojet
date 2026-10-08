package net.zamasoft.foliojet.ua.props;

/**
 * Available parameter names.
 *
 * @author MIYABE Tatsuhiko
 */
public final class UAProps {
	private UAProps() {
		// constants
	}

	/**
	 * Whether processing instructions may override properties.
	 */
	public static final BooleanPropManager INPUT_PROPERTY_PI = new BooleanPropManager("input.property-pi", false);

	/**
	 * Filtering for XML/HTML.
	 */
	public static final StringPropManager INPUT_FILTERS = new StringPropManager("input.filters",
			"xslt default-to-xhtml loose-html");

	/**
	 * Support change default namespace.
	 */
	public static final BooleanPropManager INPUT_CHANGE_DEFAULT_NAMESPACE = new BooleanPropManager("input.html.change-default-namespace",
			false);

	/**
	 * The title of the alternate stylesheet to select.
	 */
	public static final StringPropManager INPUT_STYLESHEET_TITLES = new StringPropManager("input.stylesheet.titles",
			null);

	/**
	 * Normalize text by NFC mode.
	 */
	public static final BooleanPropManager INPUT_NORMALIZE_TEXT = new BooleanPropManager("input.normalize-text",
			false);

	/**
	 * The default encoding.
	 */
	public static final StringPropManager INPUT_DEFAULT_ENCODING = new StringPropManager("input.default-encoding",
			"JISUniAutoDetect");

	/**
	 * The default CSS stylesheet.
	 */
	public static final StringPropManager INPUT_DEFAULT_STYLESHEET = new StringPropManager("input.default-stylesheet",
			null);

	/**
	 * A JSON file that lists image dimensions in advance. Layout uses these dimensions instead of loading
	 * the images, which speeds up multi-pass processing and repeated layout of the same book.
	 * Paged SVG outputs this format as <code>metrics.json</code>,
	 * so you can use that file directly for the next conversion.
	 */
	public static final StringPropManager INPUT_IMAGE_METRICS = new StringPropManager("input.image-metrics", null);

	/**
	 * Which EPUB spine items to lay out (2026-09-02).
	 *
	 * <p>
	 * An empty value (the default) selects all items. The value is a sequence separated by whitespace or
	 * {@code ,}. Each entry is an OPF {@code idref}, an item path ({@code OEBPS/ch3.xhtml} or
	 * {@code ch3.xhtml}), a 1-based number, or a range of numbers ({@code 3-5}).
	 * </p>
	 *
	 * <p>
	 * This entry point lets an e-book reader <b>lay out only the chapter currently being read</b> when
	 * the font size changes. Items are laid out independently, so laying out one chapter produces the
	 * same result as that chapter in a full-book conversion. In Paged SVG, item numbers are fixed by
	 * their positions in the spine ({@code items/0003/}), so partial output can directly overlay
	 * the output for the entire book.
	 * </p>
	 */
	public static final StringPropManager INPUT_EPUB_SPINE = new StringPropManager("input.epub.spine", null);

	/**
	 * The default XSLT stylesheet.
	 */
	public static final StringPropManager INPUT_XSLT_DEFAULT_STYLESHEET = new StringPropManager(
			"input.xslt.default-stylesheet", null);

	/**
	 * Whether to send the Referer header.
	 */
	public static final BooleanPropManager INPUT_HTTP_REFERER = new BooleanPropManager("input.http.referer", true);

	/**
	 * The connection timeout. 0 means unlimited.
	 * The default changed from unlimited to 60 seconds (2026-08-08) to prevent one unresponsive server
	 * from blocking the entire conversion forever.
	 */
	public static final IntegerPropManager INPUT_HTTP_CONNECTION_TIMEOUT = new IntegerPropManager(
			"input.http.connection.timeout", 60000);

	/**
	 * The socket timeout (the limit on waiting for a response or a stalled read). 0 means unlimited.
	 * The default changed from unlimited to 60 seconds (2026-08-08) to prevent recurrence of a real bug:
	 * one external resource on kakaku.com stopped delivering data and hung conversion for over 2000 seconds.
	 */
	public static final IntegerPropManager INPUT_HTTP_SOCKET_TIMEOUT = new IntegerPropManager(
			"input.http.socket.timeout", 60000);

	/**
	 * The maximum input size of the main document. A negative value means unlimited.
	 */
	public static final LongPropManager INPUT_SIZE_LIMIT = new LongPropManager("input.size-limit", -1L);

	/**
	 * The maximum cumulative input size of external resources resolved from the main document. A negative value means unlimited.
	 */
	public static final LongPropManager INPUT_RESOURCE_SIZE_LIMIT = new LongPropManager(
			"input.resource-size-limit", -1L);

	/**
	 * The maximum number of distinct external resource URIs resolved from the main document. A negative value means unlimited.
	 */
	public static final IntegerPropManager INPUT_RESOURCE_COUNT_LIMIT = new IntegerPropManager(
			"input.resource-count-limit", -1);

	/**
	 * The maximum pixel count (width × height) of each input image. A negative value means unlimited (2026-10-03).
	 * The dimensions in the header are checked before decoding pixels. An image exceeding the limit is
	 * treated like an unreadable image (message 2811, {@code output.broken-image}).
	 * {@link #INPUT_RESOURCE_SIZE_LIMIT} (bytes read) cannot stop a small image file that expands
	 * into a huge image.
	 */
	public static final LongPropManager INPUT_IMAGE_PIXEL_LIMIT = new LongPropManager("input.image-pixel-limit", -1L);

	/**
	 * An HTTP response cache shared across conversions (2026-08-10). Only GET requests without credentials
	 * or cookies qualify. It respects the response's {@code Cache-Control}
	 * (no-store/no-cache/private/max-age). This eliminates the delay of fetching the same external
	 * resources, such as @import-ed web font CSS, again for every conversion.
	 */
	public static final BooleanPropManager INPUT_HTTP_CACHE = new BooleanPropManager("input.http.cache", true);

	/**
	 * Asynchronous prefetching of external resources (stylesheets and images) found in the main document
	 * (2026-08-27). In streaming layout, the parser-driving thread is the layout thread, and resources
	 * are resolved synchronously in sequence at their points of use. For real websites, sequential HTTP
	 * waits therefore directly add to conversion wall-clock time. Prefetching scans the main document's
	 * read-ahead buffer, fetches only URLs the engine will certainly request in parallel, and stores
	 * them in the HTTP response cache. Requests with credentials or cookies are excluded, and only URLs
	 * that pass the ACL (input.include/exclude) are fetched.
	 * Failures and unsupported cases silently fall back to the existing synchronous path.
	 *
	 * <p>
	 * <b>Enabled by default</b> (2026-08-28, owner's decision). It substantially improves conversion
	 * of real websites (measured: 24.3 seconds → 6.4 seconds for an article with about 110 images)
	 * without changing correctness. Only http(s) resources that pass {@code input.include}/{@code input.exclude}
	 * are fetched; requests that send credentials are excluded. With no ACL configured, {@code permits}
	 * rejects them, so this does not affect use cases that obtain resources through an injected resolver.
	 * </p>
	 */
	public static final BooleanPropManager INPUT_PREFETCH = new BooleanPropManager("input.prefetch", true);

	/**
	 * The HTTP response cache retention period in seconds. 0 disables caching.
	 * If the response has {@code max-age}, the shorter period applies.
	 */
	public static final IntegerPropManager INPUT_HTTP_CACHE_TTL = new IntegerPropManager("input.http.cache.ttl", 600);

	/**
	 * The proxy host name.
	 */
	public static final StringPropManager INPUT_HTTP_PROXY_HOST = new StringPropManager("input.http.proxy.host", null);

	/**
	 * The proxy port number.
	 */
	public static final IntegerPropManager INPUT_HTTP_PROXY_PORT = new IntegerPropManager("input.http.proxy.port",
			8080);

	/**
	 * The proxy user.
	 */
	public static final StringPropManager INPUT_HTTP_PROXY_AUTHENTICATION_USER = new StringPropManager(
			"input.http.proxy.authentication.user", null);

	/**
	 * The proxy password.
	 */
	public static final StringPropManager INPUT_HTTP_PROXY_AUTHENTICATION_PASSWORD = new StringPropManager(
			"input.http.proxy.authentication.password", "");

	/**
	 * Whether to send credentials preemptively for authentication.
	 */
	public static final BooleanPropManager INPUT_HTTP_AUTHENTICATION_PREEMPTIVE = new BooleanPropManager(
			"input.http.authentication.preemptive", false);

	/**
	 * Authentication settings.
	 */
	public static final String INPUT_HTTP_AUTHENTICATION = "input.http.authentication.";

	/**
	 * Cookie settings.
	 */
	public static final String INPUT_HTTP_COOKIE = "input.http.cookie.";

	/**
	 * HTTP header settings.
	 */
	public static final String INPUT_HTTP_HEADER = "input.http.header.";

	/**
	 * Recognizes &lt;meta name="viewport"... as the page size.
	 */
	public static final BooleanPropManager INPUT_VIEWPORT = new BooleanPropManager("input.viewport", false);

	/**
	 * The page width.
	 */
	public static final StringPropManager OUTPUT_PAGE_WIDTH = new StringPropManager("output.page-width", "210mm");

	/**
	 * The page height.
	 */
	public static final StringPropManager OUTPUT_PAGE_HEIGHT = new StringPropManager("output.page-height", "297mm");

	/**
	 * The page margins.
	 */
	public static final StringPropManager OUTPUT_PAGE_MARGINS = new StringPropManager("output.page-margins", "12.7mm");

	/**
	 * The paper width.
	 */
	public static final StringPropManager OUTPUT_PAPER_WIDTH = new StringPropManager("output.paper-width", null);

	/**
	 * The paper height.
	 */
	public static final StringPropManager OUTPUT_PAPER_HEIGHT = new StringPropManager("output.paper-height", null);

	/**
	 * The print mode.
	 */
	public static final CodePropManager<OutputPrintMode> OUTPUT_PRINT_MODE = new CodePropManager<>("output.print-mode", OutputPrintMode.class, OutputPrintMode.DOUBLE_SIDE);

	/**
	 * The number of logical pages imposed on one sheet (N-up). 1 disables imposition.
	 */
	public static final IntegerPropManager OUTPUT_N_UP = new IntegerPropManager("output.n-up", 1, 1, 256);

	/**
	 * The page order for N-up imposition.
	 */
	public static final CodePropManager<OutputNUpOrder> OUTPUT_N_UP_ORDER = new CodePropManager<>("output.n-up.order", OutputNUpOrder.class, OutputNUpOrder.HORIZONTAL);

	/**
	 * The horizontal trim margin width.
	 */
	public static final StringPropManager OUTPUT_HTRIM = new StringPropManager("output.htrim", "1cm");

	/**
	 * The vertical trim margin width.
	 */
	public static final StringPropManager OUTPUT_VTRIM = new StringPropManager("output.vtrim", "1cm");

	/**
	 * The trim margin width.
	 */
	public static final StringPropManager OUTPUT_TRIMS = new StringPropManager("output.trims", null);

	/**
	 * The <b>width of the outer band of the printed area treated as bleed</b>
	 * (the trim line lies this far inside the outer edge of the printed area). This lets you output
	 * existing content that already includes bleed with crop marks, without rewriting its CSS.
	 */
	public static final StringPropManager OUTPUT_TRIM_INSET = new StringPropManager("output.trim-inset", null);

	/**
	 * Enlarges the content to fit the paper.
	 */
	public static final CodePropManager<OutputFitToPaper> OUTPUT_FIT_TO_PAPER = new CodePropManager<>("output.fit-to-paper", OutputFitToPaper.class, OutputFitToPaper.FALSE);

	/**
	 * Automatically rotates the content or paper.
	 */
	public static final CodePropManager<OutputAutoRotate> OUTPUT_AUTO_ROTATE = new CodePropManager<>("output.auto-rotate", OutputAutoRotate.class, OutputAutoRotate.NONE);

	/**
	 * Clips the area inside the crop marks.
	 */
	public static final BooleanPropManager OUTPUT_CLIP = new BooleanPropManager("output.clip", true);

	/**
	 * The default font.
	 */
	public static final StringPropManager OUTPUT_DEFAULT_FONT_FAMILY = new StringPropManager(
			"output.default-font-family", "serif");

	/**
	 * The text scale factor.
	 */
	public static final DoublePropManager OUTPUT_TEXT_SIZE = new DoublePropManager("output.text-size", 1.0, 0.01, 100);

	/**
	 * Automatic height.
	 */
	public static final BooleanPropManager OUTPUT_AUTO_HEIGHT = new BooleanPropManager("output.auto-height", false);

	/**
	 * Automatic height.
	 */
	public static final BooleanPropManager OUTPUT_EXPAND_WITH_CONTENT = new BooleanPropManager("output.expand-with-content", false);

	/**
	 * Disables page breaks.
	 */
	public static final BooleanPropManager OUTPUT_NO_PAGE_BREAK = new BooleanPropManager("output.no-page-break", false);

	/**
	 * The output format.
	 */
	public static final StringPropManager OUTPUT_TYPE = new StringPropManager("output.type", "application/pdf");

	/**
	 * The include pattern.
	 */
	public static final String INPUT_INCLUDE = "input.include";

	/**
	 * The exclude pattern.
	 */
	public static final String INPUT_EXCLUDE = "input.exclude";

	/**
	 * The file size limit.
	 */
	public static final LongPropManager OUTPUT_SIZE_LIMIT = new LongPropManager("output.size-limit", -1L);

	/**
	 * The page count limit.
	 */
	public static final IntegerPropManager OUTPUT_PAGE_LIMIT = new IntegerPropManager("output.page-limit", -1);

	/**
	 * The action to take when the page count limit is reached.
	 */
	public static final CodePropManager<OutputPageLimitAbort> OUTPUT_PAGE_LIMIT_ABORT = new CodePropManager<>("output.page-limit.abort", OutputPageLimitAbort.class, OutputPageLimitAbort.FORCE);

	/**
	 * The crop mark format.
	 */
	public static final CodePropManager<OutputMarks> OUTPUT_MARKS = new CodePropManager<>("output.marks", OutputMarks.class, OutputMarks.NONE);

	/**
	 * The CSS media type to apply.
	 */
	public static final StringPropManager OUTPUT_MEDIA_TYPES = new StringPropManager("output.media_types",
			"all print paged visual bitmap static");

	/**
	 * How to handle images that cannot be displayed.
	 */
	public static final CodePropManager<OutputBrokenImage> OUTPUT_BROKEN_IMAGE = new CodePropManager<>("output.broken-image", OutputBrokenImage.class, OutputBrokenImage.NONE);

	/**
	 * Color output.
	 */
	public static final CodePropManager<OutputColor> OUTPUT_COLOR = new CodePropManager<>("output.color", OutputColor.class, OutputColor.RGB);

	/**
	 * The resolution used to calculate px.
	 */
	public static final DoublePropManager OUTPUT_RESOLUTION = new DoublePropManager("output.resolution", 96.0, 1, 10000);

	/**
	 * The image output resolution.
	 */
	public static final DoublePropManager OUTPUT_IMAGE_RESOLUTION = new DoublePropManager("output.image.resolution",
			96.0, 1, 10000);

	/**
	 * The maximum pixel count (width × height) of each generated raster image. A negative value means
	 * unlimited (2026-10-03). If the type area for image output (page size × {@link #OUTPUT_IMAGE_RESOLUTION})
	 * exceeds the limit, conversion fails (message 3812). Images rasterized for Paged SVG use a lower
	 * scale factor to stay within the limit.
	 */
	public static final LongPropManager OUTPUT_IMAGE_PIXEL_LIMIT = new LongPropManager("output.image-pixel-limit",
			-1L);

	/**
	 * Image antialiasing.
	 */
	public static final BooleanPropManager OUTPUT_IMAGE_ANTIALIAS = new BooleanPropManager("output.image.antialias",
			true);

	/**
	 * Whether the image output background is transparent.
	 *
	 * <p>
	 * With the default {@code false}, drawing starts after filling the background with white.
	 * With {@code true}, <b>drawing starts without a background fill</b>, so areas where nothing is drawn
	 * remain transparent (a background color specified for the page or an element makes that area opaque).
	 * </p>
	 *
	 * <p>
	 * <b>Effective only for formats that support alpha.</b> PNG, GIF, and TIFF support it;
	 * JPEG, BMP, and WBMP do not. For an unsupported format, drawing still uses a white background and
	 * reports {@code 2824}: silently ignoring the setting would leave users wondering why their
	 * transparent output is white. Support is determined by querying the writer ({@code ImageWriter}),
	 * rather than using a hard-coded table of formats.
	 * </p>
	 */
	public static final BooleanPropManager OUTPUT_IMAGE_TRANSPARENT = new BooleanPropManager(
			"output.image.transparent", false);

	/**
	 * Metadata.
	 */
	public static final String OUTPUT_META = "output.meta.";

	/**
	 * Font handling.
	 */
	public static final StringPropManager OUTPUT_PDF_FONTS_POLICY = new StringPropManager("output.pdf.fonts.policy",
			"cid-keyed");

	/**
	 * The overall compression method.
	 */
	public static final CodePropManager<OutputPdfCompression> OUTPUT_PDF_COMPRESSION = new CodePropManager<>("output.pdf.compression", OutputPdfCompression.class, OutputPdfCompression.BINARY);

	/**
	 * The image compression method.
	 */
	public static final CodePropManager<OutputPdfImageCompression> OUTPUT_PDF_IMAGE_COMPRESSION = new CodePropManager<>("output.pdf.image.compression", OutputPdfImageCompression.class, OutputPdfImageCompression.FLATE);

	/**
	 * The image size threshold for applying lossless compression.
	 */
	public static final IntegerPropManager OUTPUT_PDF_IMAGE_COMPRESSION_LOSSLESS = new IntegerPropManager(
			"output.pdf.image.compression.lossless", 200);

	/**
	 * The maximum image width in pixels.
	 */
	public static final IntegerPropManager OUTPUT_PDF_IMAGE_MAX_WIDTH = new IntegerPropManager(
			"output.pdf.image.max-width", 0);

	/**
	 * The maximum image height in pixels.
	 */
	public static final IntegerPropManager OUTPUT_PDF_IMAGE_MAX_HEIGHT = new IntegerPropManager(
			"output.pdf.image.max-height", 0);

	/**
	 * The resolution (dpi) of blurred shadow images generated for PDF.
	 */
	public static final IntegerPropManager OUTPUT_PDF_BLUR_RESOLUTION = new IntegerPropManager(
			"output.pdf.blur-resolution", 150);

	/**
	 * The resolution (dpi) when rasterizing only elements with filters in PDF.
	 */
	public static final IntegerPropManager OUTPUT_PDF_FILTER_RESOLUTION = new IntegerPropManager(
			"output.pdf.filter-resolution", 300);

	/**
	 * Attachment settings.
	 */
	public static final String OUTPUT_PDF_ATTACHMENTS = "output.pdf.attachments.";

	/**
	 * The PDF version.
	 */
	public static final CodePropManager<OutputPdfVersion> OUTPUT_PDF_VERSION = new CodePropManager<>("output.pdf.version", OutputPdfVersion.class, OutputPdfVersion.V1_5);

	/**
	 * The encryption method.
	 */
	public static final CodePropManager<OutputPdfEncryption> OUTPUT_PDF_ENCRYPTION = new CodePropManager<>("output.pdf.encryption", OutputPdfEncryption.class, OutputPdfEncryption.NONE);

	/**
	 * Whether to output tagged PDF (logical structure).
	 */
	public static final BooleanPropManager OUTPUT_PDF_TAGGED = new BooleanPropManager("output.pdf.tagged", false);

	/**
	 * Whether to output the logical text of bidi lines as ActualText.
	 */
	public static final BooleanPropManager OUTPUT_PDF_BIDI_ACTUAL_TEXT = new BooleanPropManager("output.pdf.bidi.actual-text", false);

	/**
	 * The language for tagged PDF / PDF/UA (BCP 47, e.g. "ja").
	 */
	public static final StringPropManager OUTPUT_PDF_TAGGED_LANG = new StringPropManager("output.pdf.tagged.lang", null);

	/**
	 * Whether to output HTML form controls (input/textarea/select) as fillable PDF form fields (AcroForm).
	 * When enabled, form controls take on the appearance of interactive widgets
	 * (output for documents without forms is unchanged).
	 * Forms are not output in PDF/X because it prohibits them.
	 */
	public static final BooleanPropManager OUTPUT_PDF_FORMS = new BooleanPropManager("output.pdf.forms", false);

	/**
	 * The user password for encryption.
	 */
	public static final StringPropManager OUTPUT_PDF_ENCRYPTION_USER_PASSWORD = new StringPropManager(
			"output.pdf.encryption.user-password", "");

	/**
	 * The owner password for encryption.
	 */
	public static final StringPropManager OUTPUT_PDF_ENCRYPTION_OWNER_PASSWORD = new StringPropManager(
			"output.pdf.encryption.owner-password", null);

	// PDF permission settings.
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT = new BooleanPropManager(
			"output.pdf.encryption.permissions.print", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_MODIFY = new BooleanPropManager(
			"output.pdf.encryption.permissions.modify", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_COPY = new BooleanPropManager(
			"output.pdf.encryption.permissions.copy", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ADD = new BooleanPropManager(
			"output.pdf.encryption.permissions.add", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_FILL = new BooleanPropManager(
			"output.pdf.encryption.permissions.fill", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_EXTRACT = new BooleanPropManager(
			"output.pdf.encryption.permissions.extract", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ASSEMBLE = new BooleanPropManager(
			"output.pdf.encryption.permissions.assemble", true);
	public static final BooleanPropManager OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT_HIGH = new BooleanPropManager(
			"output.pdf.encryption.permissions.print-high", true);

	/**
	 * The encryption key length.
	 */
	public static final IntegerPropManager OUTPUT_PDF_ENCRYPTION_LENGTH = new IntegerPropManager(
			"output.pdf.encryption.length", 128);

	/**
	 * Bookmarks (PDF outline). The default is true (2026-10-04). Previously it was false, which made
	 * missing bookmarks in book PDFs easy to overlook. Prince and WeasyPrint also add them by default.
	 */
	public static final BooleanPropManager OUTPUT_PDF_BOOKMARKS = new BooleanPropManager("output.pdf.bookmarks", true);

	/**
	 * Links. The default is true (2026-10-04, as in Chrome, Prince, and WeasyPrint).
	 * PDF/X prohibits links, so the default value silently omits them, and a warning is issued only
	 * for an explicitly specified true (PDFVisitor).
	 */
	public static final BooleanPropManager OUTPUT_PDF_HYPERLINKS = new BooleanPropManager("output.pdf.hyperlinks",
			true);

	/**
	 * The effective value for bookmarks and links (2026-10-04). If not explicitly specified, it uses
	 * the default (true), except in PDF/UA-2, where it is false
	 * ({@link OutputPdfVersion#keepsNavigationOffByDefault}).
	 */
	public static boolean navigation(final BooleanPropManager prop, final net.zamasoft.foliojet.ua.UserAgent ua) {
		if (ua.getProperty(prop.name) == null && OUTPUT_PDF_VERSION.get(ua).keepsNavigationOffByDefault()) {
			return false;
		}
		return prop.getBoolean(ua);
	}

	/**
	 * The link method.
	 */
	public static final CodePropManager<OutputPdfHyperlinksHref> OUTPUT_PDF_HYPERLINKS_HREF = new CodePropManager<>("output.pdf.hyperlinks.href", OutputPdfHyperlinksHref.class, OutputPdfHyperlinksHref.RELATIVE);

	/**
	 * The link base.
	 */
	public static final StringPropManager OUTPUT_PDF_HYPERLINKS_BASE = new StringPropManager(
			"output.pdf.hyperlinks.base", null);

	/**
	 * Links within a page.
	 */
	public static final BooleanPropManager OUTPUT_PDF_HYPERLINKS_FRAGMENT = new BooleanPropManager(
			"output.pdf.hyperlinks.fragment", true);

	/**
	 * The JPEG image compression method.
	 */
	public static final CodePropManager<OutputPdfJpegImage> OUTPUT_PDF_JPEG_IMAGE = new CodePropManager<>("output.pdf.jpeg-image", OutputPdfJpegImage.class, OutputPdfJpegImage.RAW);

	/**
	 * The encoding of name literals inside PDF.
	 */
	public static final StringPropManager OUTPUT_PDF_PLATFORM_ENCODING = new StringPropManager(
			"output.pdf.platform-encoding", "MS932");

	/**
	 * The number of processing passes.
	 */
	public static final IntegerPropManager PROCESSING_PASS_COUNT = new IntegerPropManager("processing.pass-count", 1, 1, 10);

	/**
	 * The text payload limit for each retained element. A value of 0 or less means unlimited. 16 MiB since
	 * 2026-10-08 (8 MiB before): the body of rustdoc's std index, a flex container retained as a whole, went 2 bytes
	 * past 8 MiB.
	 */
	public static final LongPropManager PROCESSING_RETAINED_TEXT_LIMIT = new LongPropManager(
			"processing.retained-text-limit", 16L << 20);

	/** Enables row-by-row emission of retained tables. The default uses the existing path that places completed tables. */
	public static final BooleanPropManager PROCESSING_TABLE_ROW_EMISSION = new BooleanPropManager(
			"processing.table-row-emission", false);

	/**
	 * The maximum elapsed time allowed to convert one document (milliseconds). A value of 0 or less means unlimited.
	 */
	public static final LongPropManager PROCESSING_TIME_LIMIT = new LongPropManager("processing.time-limit", 0L);

	/**
	 * Runs an intermediate pass without actually generating data.
	 */
	public static final BooleanPropManager PROCESSING_MIDDLE_PASS = new BooleanPropManager("processing.middle-pass",
			false);

	/**
	 * The number of independently layable units to lay out concurrently (2026-09-02).
	 *
	 * <p>
	 * Currently effective only when outputting EPUB spine items to {@code MultiDocumentOutput} (Paged SVG).
	 * {@code 0} (the default) selects automatically: the smaller of the CPU core count and 4.
	 * {@code 1} selects sequential processing. <b>Output is identical for any value</b>, because items
	 * are independent and results are released in spine order. Only elapsed time and memory use change
	 * (layout is retained for each item being processed concurrently).
	 * </p>
	 */
	public static final IntegerPropManager PROCESSING_CONCURRENCY = new IntegerPropManager("processing.concurrency",
			0);
	/**
	 * Enables page references.
	 */
	public static final BooleanPropManager PROCESSING_PAGE_REFERENCES = new BooleanPropManager(
			"processing.page-references", false);

	/**
	 * The number of digits reserved for {@code target-counter()} numbers in single-pass PDF (2026-10-04).
	 * Even for a number on a later page, a field of this many digits is laid out in advance, and the value
	 * is written when the document closes. A number that exceeds the field overflows to the left. Range: 1–9.
	 */
	public static final IntegerPropManager PROCESSING_TARGET_COUNTER_DIGITS = new IntegerPropManager(
			"processing.target-counter.digits", 3);

	/**
	 * Forces an abort when an error occurs.
	 */
	public static final BooleanPropManager PROCESSING_FAIL_ON_FATAL_ERROR = new BooleanPropManager(
			"processing.fail-on-fatal-error", true);


		/**
	 * The limit (bytes) for retaining layout source (LayoutSource) text payload inline on the heap
	 * (default 8 MB, introduced 2026-07-24: E-6 increment 3b-2).
	 *
	 * <p>
	 * New text that would push the retained inline bytes over this budget is written to a temporary
	 * file (spill store) and decoded during page-break replay. The decision is deterministic, based
	 * only on the configured value and cumulative bytes, not on available heap space.
	 * Output (display list) is completely identical regardless of the budget; only memory behavior
	 * changes. The default is comfortably above measured corpus requirements (most documents retain
	 * a few thousand events), so ordinary documents do not spill.
	 * </p>
	 */
	public static final LongPropManager PROCESSING_TEXT_SPILL_BUDGET = new LongPropManager(
			"processing.text-spill-budget", 8L * 1024L * 1024L);

	/**
	 * The file ID.
	 */
	public static final StringPropManager OUTPUT_PDF_FILE_ID = new StringPropManager("output.pdf.file-id", null);

	/**
	 * The creation date and time.
	 */
	public static final StringPropManager OUTPUT_PDF_META_CREATION_DATE = new StringPropManager(
			"output.pdf.meta.creation-date", null);

	/**
	 * The modification date and time.
	 */
	public static final StringPropManager OUTPUT_PDF_META_MOD_DATE = new StringPropManager("output.pdf.meta.mod-date",
			null);

	/**
	 * The XMP conformance level for electronic invoices (Factur-X/ZUGFeRD). When set, the fx: extension
	 * schema is output to XMP (attach the invoice XML itself through output.pdf.attachments.*
	 * with relationship=alternative).
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_CONFORMANCE_LEVEL = new StringPropManager(
			"output.pdf.facturx.conformance-level", null);

	/**
	 * The Factur-X document type (default INVOICE).
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_DOCUMENT_TYPE = new StringPropManager(
			"output.pdf.facturx.document-type", "INVOICE");

	/**
	 * The Factur-X invoice XML file name (default factur-x.xml; it must match the attachment name).
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_DOCUMENT_FILE_NAME = new StringPropManager(
			"output.pdf.facturx.document-file-name", "factur-x.xml");

	/**
	 * The Factur-X profile version (default 1.0).
	 */
	public static final StringPropManager OUTPUT_PDF_FACTURX_VERSION = new StringPropManager(
			"output.pdf.facturx.version", "1.0");

	/**
	 * The output condition identifier for the output intent (e.g. JC200103, FOGRA39).
	 * When set, /OutputIntents is output in the catalog (required for PDF/X conformance).
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER = new StringPropManager(
			"output.pdf.output-intent.identifier", null);

	/**
	 * The human-readable name of the output condition for the output intent.
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_CONDITION = new StringPropManager(
			"output.pdf.output-intent.condition", null);

	/**
	 * The registry name for the output intent (the default is the ICC characterization registry).
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_REGISTRY = new StringPropManager(
			"output.pdf.output-intent.registry", "http://www.color.org");

	/**
	 * Additional information for the output intent (recommended in PDF/X for unregistered conditions).
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_INFO = new StringPropManager(
			"output.pdf.output-intent.info", null);

	/**
	 * The URI of the ICC profile to embed in the output intent (DestOutputProfile).
	 */
	public static final StringPropManager OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE = new StringPropManager(
			"output.pdf.output-intent.icc-profile", null);

	/**
	 * The default rendering intent (one of perceptual, relative-colorimetric,
	 * saturation, or absolute-colorimetric).
	 */
	public static final StringPropManager OUTPUT_PDF_RENDERING_INTENT = new StringPropManager(
			"output.pdf.rendering-intent", null);

	/**
	 * The spine width.
	 */
	public static final StringPropManager OUTPUT_MARKS_SPINE_WIDTH = new StringPropManager("output.marks.spine-width",
			null);

	/**
	 * CFM encryption.
	 */
	public static final CodePropManager<OutputPdfEncryptionV4CFM> OUTPUT_PDF_ENCRYPTION_V4_CFM = new CodePropManager<>("output.pdf.encryption.v4.cfm", OutputPdfEncryptionV4CFM.class, OutputPdfEncryptionV4CFM.V2);

	/**
	 * The watermark image.
	 */
	public static final StringPropManager OUTPUT_PDF_WATERMARK_URI = new StringPropManager("output.pdf.watermark.uri",
			null);

	/**
	 * The watermark image placement method.
	 */
	public static final CodePropManager<OutputPdfWatermarkMode> OUTPUT_PDF_WATERMARK_MODE = new CodePropManager<>("output.pdf.watermark.mode", OutputPdfWatermarkMode.class, OutputPdfWatermarkMode.BACK);

	/**
	 * The watermark image opacity.
	 */
	public static final DoublePropManager OUTPUT_PDF_WATERMARK_OPACITY = new DoublePropManager(
			"output.pdf.watermark.opacity", 1);

	/**
	 * Whether to display the watermark image on screen.
	 */
	public static final BooleanPropManager OUTPUT_PDF_WATERMARK_VIEW = new BooleanPropManager(
			"output.pdf.watermark.view", true);

	/**
	 * Whether to print the watermark image.
	 */
	public static final BooleanPropManager OUTPUT_PDF_WATERMARK_PRINT = new BooleanPropManager(
			"output.pdf.watermark.print", true);

	// PDF ViewerPreference settings
	// hide-toolbar was spelled hide-toolber up to 3.2; renamed without an alias on 2026-10-08 (copper4 keeps no
	// compatibility-only names).
	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_TOOLBAR = new BooleanPropManager(
			"output.pdf.viewer-preferences.hide-toolbar", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_MENUBAR = new BooleanPropManager(
			"output.pdf.viewer-preferences.hide-menubar", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_WINDOWUI = new BooleanPropManager(
			"output.pdf.viewer-preferences.hide-windowUI", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_FIT_WINDOW = new BooleanPropManager(
			"output.pdf.viewer-preferences.fit-window", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_CENTER_WINDOW = new BooleanPropManager(
			"output.pdf.viewer-preferences.center-window", false);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_DISPLAY_DOC_TITLE = new BooleanPropManager(
			"output.pdf.viewer-preferences.display-doc-title", false);

	public static final CodePropManager<OutputPdfViewerPreferencesNonFullScreenPageMode> OUTPUT_PDF_VIEWER_PREFERENCES_NON_FULL_SCREEN_PAGE_MODE = new CodePropManager<>("output.pdf.viewer-preferences.non-full-screen-page-mode", OutputPdfViewerPreferencesNonFullScreenPageMode.class, OutputPdfViewerPreferencesNonFullScreenPageMode.USE_NONE);

	public static final CodePropManager<OutputPdfViewerPreferencesPrintScaling> OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING = new CodePropManager<>("output.pdf.viewer-preferences.print-scaling", OutputPdfViewerPreferencesPrintScaling.class, OutputPdfViewerPreferencesPrintScaling.APP_DEFAULT);

	public static final CodePropManager<OutputPdfViewerPreferencesDuplex> OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX = new CodePropManager<>("output.pdf.viewer-preferences.duplex", OutputPdfViewerPreferencesDuplex.class, OutputPdfViewerPreferencesDuplex.NONE);

	public static final BooleanPropManager OUTPUT_PDF_VIEWER_PREFERENCES_PICK_TRAY_BY_PDF_SIZE = new BooleanPropManager(
			"output.pdf.viewer-preferences.pick-tray-by-pdf-size", false);

	public static final StringPropManager OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE = new StringPropManager(
			"output.pdf.viewer-preferences.print-page-range", null);

	public static final IntegerPropManager OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES = new IntegerPropManager(
			"output.pdf.viewer-preferences.num-copies", 0);

	/**
	 * JavaScript to run when the PDF opens.
	 */
	public static final StringPropManager OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT = new StringPropManager(
			"output.pdf.open-action.java-script", null);

	/**
	 * Interprets meta and title tags that set document information.
	 */
	public static final BooleanPropManager OUTPUT_USE_META_INFO = new BooleanPropManager("output.use-meta-info", true);

	/**
	 * How to deliver shared resources (font subsets and images) for Paged SVG.
	 * Choose from referencing, embedding, or omitting them.
	 */
	public static final CodePropManager<PagedSvgResourceMode> OUTPUT_PAGED_SVG_RESOURCES = new CodePropManager<>(
			"output.paged-svg.resources", PagedSvgResourceMode.class, PagedSvgResourceMode.REFERENCE);


	/**
	 * Whether to create <b>one font subset for the entire document or one for each page</b>.
	 *
	 * <p>
	 * The default {@code document} shares subsets across the document, minimizing total size.
	 * However, <b>subsets cannot be output until all pages have been written</b>, so the recipient
	 * cannot draw even one character until conversion finishes (body text uses private-use characters,
	 * whose glyphs exist only in the font).
	 * </p>
	 *
	 * <p>
	 * With {@code page}, <b>each page's subsets are output when that page closes</b>.
	 * Drawing can start as soon as the first page and its fonts arrive. A reader that fetches only
	 * pages near the visible page also downloads less. The tradeoff is total size: measured with
	 * Japanese text, each page uses about 20 KB. In a 350-page book, total font size grows from
	 * 0.25 MB → 7.1 MB, and total output from 11.8 MB → 18.6 MB (1.6 times).
	 * **Heavier for reading cover to cover, lighter for selective reading**
	 * (the break-even point is 12–13 pages). For documents containing only Latin text, the difference is negligible.
	 * </p>
	 */
	public static final CodePropManager<PagedSvgFontScope> OUTPUT_PAGED_SVG_FONT_SCOPE = new CodePropManager<>(
			"output.paged-svg.font-scope", PagedSvgFontScope.class, PagedSvgFontScope.DOCUMENT);

	/**
	 * The prefix used by page SVGs to reference shared resources (font subsets and images).
	 *
	 * <p>
	 * The default is {@code ../}, relative to {@code pages/}.
	 * <b>A reader that embeds multiple page SVGs in a single HTML document cannot resolve this</b>,
	 * because the host document becomes the base. Body text uses private-use code points, so without
	 * the glyphs it appears <b>entirely blank</b> (2026-09-01, reported by the cti.li reader).
	 * </p>
	 *
	 * <p>
	 * An absolute URL prefix ({@code https://example.com/book/}) resolves regardless of the host document.
	 * A trailing {@code /} is added if missing.
	 * The same prefix applies to both font subsets and images.
	 * </p>
	 */
	public static final StringPropManager OUTPUT_PAGED_SVG_BASE_URI = new StringPropManager(
			"output.paged-svg.base-uri", "../");

	/**
	 * Whether to return page SVG and page JSON compressed with gzip.
	 *
	 * <p>
	 * <b>The default is gzip</b> (2026-08-28, owner's decision). Page SVG is plain text with characters
	 * stored directly, so compression works well. File names are {@code pages/NNNN.svgz} and
	 * {@code pages/NNNN.json.gz}; {@code manifest.json} is the entry point and is not compressed.
	 * For static hosting, serve these files with {@code Content-Encoding: gzip}.
	 * </p>
	 */
	public static final CodePropManager<PagedSvgCompression> OUTPUT_PAGED_SVG_COMPRESSION = new CodePropManager<>(
			"output.paged-svg.compression", PagedSvgCompression.class, PagedSvgCompression.GZIP);

	/**
	 * The compression policy for shared images in page-split SVG (2026-09-03, requested by cti.li).
	 * The default leaves them unchanged. {@code jpeg} recompresses large raster images without
	 * transparent areas as JPEG. Specify the threshold and size reduction using keys with the
	 * same meanings as PDF's {@code output.pdf.image.*}.
	 */
	public static final CodePropManager<PagedSvgImageCompression> OUTPUT_PAGED_SVG_IMAGE_COMPRESSION = new CodePropManager<>(
			"output.paged-svg.image.compression", PagedSvgImageCompression.class, PagedSvgImageCompression.NONE);

	/** The image size threshold (width + height in pixels) for applying lossy compression. */
	public static final IntegerPropManager OUTPUT_PAGED_SVG_IMAGE_COMPRESSION_LOSSLESS = new IntegerPropManager(
			"output.paged-svg.image.compression.lossless", 200);

	/** The maximum shared image width in pixels. 0 means unlimited. */
	public static final IntegerPropManager OUTPUT_PAGED_SVG_IMAGE_MAX_WIDTH = new IntegerPropManager(
			"output.paged-svg.image.max-width", 0);

	/** The maximum shared image height in pixels. 0 means unlimited. */
	public static final IntegerPropManager OUTPUT_PAGED_SVG_IMAGE_MAX_HEIGHT = new IntegerPropManager(
			"output.paged-svg.image.max-height", 0);

	/**
	 * Whether to write each page's SHA-256 ({@code svgSha256} and {@code dataSha256})
	 * in {@code pages[]} of {@code manifest.json} (2026-09-03).
	 * If the recipient does not use them, {@code false} reduces the manifest size
	 * (they account for most of its 143 KB for 310 pages). The {@code sha256} of shared resources
	 * (fonts and images) is always written because it is the key for their URI and identity.
	 */
	public static final BooleanPropManager OUTPUT_PAGED_SVG_PAGE_CHECKSUMS = new BooleanPropManager(
			"output.paged-svg.page-checksums", true);

	/**
	 * Whether to also output PDF from the same layout as page-split SVG (2026-09-03, requested by cti.li).
	 * {@code true} adds {@code document.pdf} to the result set (inside the ZIP for ZIP output;
	 * the manifest's {@code pdf}). Layout runs once, and each page's drawing is sent to both page SVG
	 * and PDF. PDF output follows {@code output.pdf.*}. This has no effect for EPUB (per-item bundles).
	 */
	public static final BooleanPropManager OUTPUT_PAGED_SVG_PDF = new BooleanPropManager("output.paged-svg.pdf",
			false);

	/**
	 * How to write text in single SVG output ({@code image/svg+xml})
	 * (B-1, 2026-08-29). The default remains outlines.
	 * With {@code keep}, text stays as {@code <text>}, and subsetted WOFF2 and images are embedded
	 * in the SVG using {@code data:}, making the single file self-contained.
	 */
	public static final CodePropManager<SvgTextMode> OUTPUT_SVG_TEXT = new CodePropManager<>(
			"output.svg.text", SvgTextMode.class, SvgTextMode.OUTLINE);

	/**
	 * All public static {@link PropManager} instances in this class (collected on first use,
	 * since they are not all available midway through declaration).
	 * Until 2026-10-04, a manual list omitted eight entries, including input limits, image pixel limits,
	 * and font subset scope for page-split SVG, so they did not appear among the defaults in the management UI.
	 */
	private static final class All {
		static final java.util.List<PropManager> LIST = collect();

		private static java.util.List<PropManager> collect() {
			final java.util.Set<PropManager> all = java.util.Collections
					.newSetFromMap(new java.util.IdentityHashMap<>());
			final java.util.List<PropManager> list = new java.util.ArrayList<>();
			for (final java.lang.reflect.Field field : UAProps.class.getFields()) {
				if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
						&& PropManager.class.isAssignableFrom(field.getType())) {
					try {
						final PropManager manager = (PropManager) field.get(null);
						if (all.add(manager)) {
							list.add(manager);
						}
					} catch (final IllegalAccessException e) {
						throw new IllegalStateException(e);
					}
				}
			}
			return java.util.List.copyOf(list);
		}
	}

	/**
	 * Returns all defined properties.
	 */
	public static java.util.List<PropManager> all() {
		return All.LIST;
	}
}
