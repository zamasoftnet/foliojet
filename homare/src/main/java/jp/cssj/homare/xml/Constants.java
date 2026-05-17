package jp.cssj.homare.xml;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Constants.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class Constants {
	private Constants() {
		// unused
	}

	/**
	 * XML Namespaceの名前空間URIです。
	 */
	public static final String NAMESPACE_URI = "http://www.w3.org/2000/xmlns/";

	/**
	 * XML Namespaceの接頭辞です。
	 */
	public static final String NAMESPACE_PREFIX = "xmlns";

	/** リンクの型。(SPEC ASSX1.0) */
	public static final String STYLESHEET_REL = "stylesheet";

	/** 代用スタイルシートとしてのリンクの型。(SPEC ASSX1.0) */
	public static final String ALTERNATE_REL = "alternate";

	/** CSSのMIMEタイプ。(SPEC CSS2 3.4) */
	public static final String CSS_MIME_TYPE = "text/css";

	/** リンクのためのPI。(SPEC ASSX1.0) */
	public static final String LINK_PI = "xml-stylesheet";

	/** XSLTのMIMEタイプ。 */
	public static final String XSLT_MIME_TYPE = "text/xsl";

	/**
	 * XLINKの名前空間URIです。
	 */
	public static final String XLINK_URI = "http://www.w3.org/1999/xlink";

	public static final String XLINK_PREFIX = "xlink";

	public static final AttributeNode XLINK_HREF_ATTR = new AttributeNode(XLINK_URI, XLINK_PREFIX, "href");
}