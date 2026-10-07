package net.zamasoft.foliojet.xml;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Constants.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class Constants {
	private Constants() {
		// unused
	}

	/** Link type. (SPEC ASSX1.0) */
	public static final String STYLESHEET_REL = "stylesheet";

	/** Link type for an alternate style sheet. (SPEC ASSX1.0) */
	public static final String ALTERNATE_REL = "alternate";

	/** CSS MIME type. (SPEC CSS2 3.4) */
	public static final String CSS_MIME_TYPE = "text/css";

	/** Processing instruction for links. (SPEC ASSX1.0) */
	public static final String LINK_PI = "xml-stylesheet";

	/** XSLT MIME type. */
	public static final String XSLT_MIME_TYPE = "text/xsl";

	/**
	 * The XLINK namespace URI.
	 */
	public static final String XLINK_URI = "http://www.w3.org/1999/xlink";

	public static final String XLINK_PREFIX = "xlink";

	public static final AttributeNode XLINK_HREF_ATTR = new AttributeNode(XLINK_URI, XLINK_PREFIX, "href");
}