package jp.cssj.homare.message;

/**
 * メッセージコード一覧です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: MessageCodes.java 1631 2022-05-15 05:43:49Z miyabe $
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
	public static final short WARN_MISSING_IMAGE = 0x2811;
	public static final short WARN_UNSUPPORTED_PDF_CAPABILITY = 0x2812;
	public static final short WARN_BAD_INLINE_OBJECT = 0x2813;
	public static final short WARN_BLOCKED_RESOURCE = 0x2814;
	public static final short WARN_LICENSE_CONSTRAINT_CSS = 0x2815;
	public static final short WARN_BAD_CSS_ARGMENTS = 0x2816;
	public static final short WARN_BAD_INLINE_CSS = 0x2817;
	public static final short WARN_UNSUPPORTED_IO_PROPERTY = 0x2818;
	public static final short WARN_LICENSE_CONSTRAINT_IO = 0x281B;
	public static final short WARN_MISSING_PROFILE = 0x281C;
	public static final short WARN_UNSUPPORTED_ENCODING = 0x281D;
	public static final short WARN_MISSING_FONT_FILE = 0x281E;
	public static final short WARN_MISSING_FONT = 0x281F;
	public static final short WARN_MISSING_FONT_OUTLINE = 0x2820;
	public static final short WARN_PLUGIN = 0x28FF;

	public static final short ERROR_BAD_XSLT_STYLESHEET = 0x3801;
	public static final short ERROR_BAD_PAGE_SIZE = 0x3802;
	public static final short ERROR_BAD_XML_SYNTAX = 0x3803;
	public static final short ERROR_OUTPUT_FILE_TOO_LARGE = 0x3804;
	public static final short ERROR_OUT_OF_PAGE_LIMIT = 0x3805;
	public static final short ERROR_MISSING_SERVERSIDE_DOCUMENT = 0x3806;
	public static final short ERROR_INVALID_LICENSE = 0x3807;
	public static final short ERROR_XSLT_WARN = 0x3808;
	public static final short ERROR_XSLT_ERROR = 0x3809;
	public static final short ERROR_EXPIRED_LICENSE = 0x380B;
	public static final short ERROR_UNLICENSED = 0x380C;
	public static final short ERROR_NO_CONTENT = 0x380D;
	public static final short ERROR_PLUGIN = 0x38FF;

	public static final short FATAL_XSLT_FATAL = 0x4801;
	public static final short FATAL_PLUGIN = 0x48FF;
}
