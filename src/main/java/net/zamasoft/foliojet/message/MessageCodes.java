package net.zamasoft.foliojet.message;

/**
 * List of message codes.
 *
 * @author MIYABE Tatsuhiko
 */
public interface MessageCodes {
	public static final short INFO_PAGE_NUMBER = 0x1801;
	public static final short INFO_HEADING_TITLE = 0x1802;
	public static final short INFO_PASS_REMAINDER = 0x1803;
	public static final short INFO_ANNOTATION = 0x1804;
	public static final short INFO_TITLE = 0x1805;
	public static final short INFO_PAGE_HEIGHT = 0x1806;
	public static final short INFO_PLUGIN = 0x18FF;

	public static final short WARN_BAD_CSS_SYNTAX = 0x2801;
	public static final short WARN_UNSUPPORTED_CSS_PROPERTY = 0x2802;
	public static final short WARN_MISSING_CSS_STYLESHEET = 0x2803;
	public static final short WARN_BAD_IO_PROPERTY = 0x2804;
	public static final short WARN_BAD_PI_SYNTAX = 0x2805;
	public static final short WARN_DEEP_IMPORT = 0x2806;
	public static final short WARN_LOOP_IMPORT = 0x2807;
	public static final short WARN_BAD_HTML_ATTRIBUTE = 0x2808;
	public static final short WARN_BAD_HEADER = 0x280A;
	public static final short WARN_BAD_URI_PATTERN = 0x280B;
	public static final short WARN_BAD_LINK_URI = 0x280C;
	public static final short WARN_SVG = 0x280D;
	public static final short WARN_MISSING_XSLT_STYLESHEET = 0x280E;
	public static final short WARN_CANNOT_OVERRIDE_PROPERTY = 0x280F;
	public static final short WARN_MISSING_ATTACHMENT = 0x2810;
	/**
	 * Warning that an image cannot be loaded. Arguments: {0} = URI without userinfo,
	 * {1} = failure stage ({@code resolve}, {@code fetch},
	 * {@code fetch: HTTP nnn}, {@code decode}).
	 */
	public static final short WARN_MISSING_IMAGE = 0x2811;
	public static final short WARN_UNSUPPORTED_PDF_CAPABILITY = 0x2812;
	public static final short WARN_BAD_INLINE_OBJECT = 0x2813;
	public static final short WARN_BLOCKED_RESOURCE = 0x2814;
	public static final short WARN_BAD_CSS_ARGMENTS = 0x2816;
	public static final short WARN_BAD_INLINE_CSS = 0x2817;
	public static final short WARN_UNSUPPORTED_IO_PROPERTY = 0x2818;
	public static final short WARN_MISSING_PROFILE = 0x281C;
	public static final short WARN_UNSUPPORTED_ENCODING = 0x281D;
	public static final short WARN_MISSING_FONT_FILE = 0x281E;
	public static final short WARN_MISSING_FONT = 0x281F;
	public static final short WARN_MISSING_FONT_OUTLINE = 0x2820;
	/**
	 * Properties <b>intentionally unsupported</b> because they have no meaning in static layout
	 * (2026-08-28). Separated from {@link #WARN_UNSUPPORTED_CSS_PROPERTY} (unimplemented but
	 * potentially supported later): mixing these two prevents narrowing implementation candidates
	 * when counting warnings from real sites.
	 */
	public static final short WARN_IGNORED_CSS_PROPERTY = 0x2821;
	/**
	 * Feature <b>rendered approximately</b> because the current output format cannot render it exactly
	 * (2026-08-29). Arguments: {0} = CSS property name, {1} = output MIME type, {2} = approximation details.
	 * Emitted once per document per feature, at draw time only when the approximation path actually runs.
	 * Normal PDF supporting transparency rasterizes only shadows for exact blur, but profiles without
	 * transparency such as PDF/A-1, or refusal to rasterize, fall back to approximation.
	 * Warnings, including those for conic gradients, vary by output format/profile even for the same document.
	 */
	public static final short WARN_APPROXIMATED_RENDERING = 0x2822;
	/**
	 * Declaration parsed successfully but <b>ineffective in this combination</b> (2026-08-29).
	 * Arguments: {0} = CSS property name, {1} = reason it has no effect.
	 * Unlike {@link #WARN_UNSUPPORTED_CSS_PROPERTY} (property itself unimplemented) or
	 * {@link #WARN_IGNORED_CSS_PROPERTY} (meaningless in static layout), reports declarations
	 * <b>that work on their own but are dropped due to context</b>.
	 * "I wrote it, but it does nothing" wastes users' time the most, so do not silently discard them.
	 */
	public static final short WARN_INEFFECTIVE_CSS_COMBINATION = 0x2823;
	/**
	 * A transparent image was requested, but the format cannot carry alpha.
	 *
	 * <p>
	 * Silently using white leaves users wondering "why is it white when I requested transparency?"
	 * Explain what happened.
	 * </p>
	 */
	public static final short WARN_NO_ALPHA_IN_IMAGE_FORMAT = 0x2824;
	/** Warning for a setting looser than the operator's limit (uses the limit value; 2026-10-03). */
	public static final short WARN_OPERATOR_LIMIT = 0x2825;
	public static final short WARN_PLUGIN = 0x28FF;

	public static final short ERROR_BAD_XSLT_STYLESHEET = 0x3801;
	public static final short ERROR_BAD_PAGE_SIZE = 0x3802;
	public static final short ERROR_BAD_XML_SYNTAX = 0x3803;
	public static final short ERROR_OUTPUT_FILE_TOO_LARGE = 0x3804;
	public static final short ERROR_OUT_OF_PAGE_LIMIT = 0x3805;
	public static final short ERROR_MISSING_SERVERSIDE_DOCUMENT = 0x3806;
	public static final short ERROR_XSLT_WARN = 0x3808;
	public static final short ERROR_XSLT_ERROR = 0x3809;
	public static final short ERROR_NO_CONTENT = 0x380D;
	/** Error: conversion aborted because the PDF/X output intent specification is invalid. */
	public static final short ERROR_PDFX_OUTPUT_INTENT = 0x380E;
	public static final short ERROR_RETAINED_TEXT_LIMIT = 0x380F;
	/**
	 * Error: server-side main document retrieval is disallowed
	 * (e.g., denying a remote user an internal server destination; 2026-09-14).
	 */
	public static final short ERROR_FORBIDDEN_SERVERSIDE_DOCUMENT = 0x3810;
	/** Error: server-side main document retrieval failed (connection refused, disconnected, etc.; 2026-09-14). */
	public static final short ERROR_UNREACHABLE_SERVERSIDE_DOCUMENT = 0x3811;
	/** Error: generated raster (type area for image output) exceeds output.image-pixel-limit pixels (2026-10-03). */
	public static final short ERROR_OUTPUT_IMAGE_TOO_LARGE = 0x3812;
	/** Error: an unsupported format is specified for {@code output.type} (2026-10-05). */
	public static final short ERROR_UNSUPPORTED_OUTPUT_TYPE = 0x3813;
	/** Error: PDF/UA selected without a document language ({@code output.pdf.tagged.lang}; 2026-10-05). */
	public static final short ERROR_PDFUA_LANG = 0x3814;
	/** Error: an EPUB item cannot be read as XML, typically an encrypted (DRM) book (2026-10-08). */
	public static final short ERROR_EPUB_ITEM_NOT_XML = 0x3815;
	public static final short ERROR_PLUGIN = 0x38FF;

	public static final short FATAL_XSLT_FATAL = 0x4801;
	public static final short FATAL_PLUGIN = 0x48FF;
}
