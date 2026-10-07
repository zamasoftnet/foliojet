package net.zamasoft.foliojet.xml.vocab;

import net.zamasoft.foliojet.xml.AttributeNode;
import net.zamasoft.foliojet.xml.ElementNode;

/**
 * CSSJ-specific markup (CSSJML).
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJML.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public final class CSSJML {
	private CSSJML() {
		// unused
	}

	/**
	 * The CSSJML prefix.
	 */
	public static final String PREFIX = "cssj";

	/**
	 * The CSSJML namespace URI.
	 */
	public static final String URI = "http://www.cssj.jp/ns/cssjml";

	/**
	 * Outputs an annotation.
	 */
	public static final AttributeNode ANNOT_ATTR = new AttributeNode(URI, PREFIX, "annot");

	/**
	 * Specifies the heading level.
	 */
	public static final AttributeNode HEADER_ATTR = new AttributeNode(URI, PREFIX, "header");

	/**
	 * Generates a table of contents.
	 */
	public static final ElementNode MAKE_TOC_ELEM = new ElementNode(URI, PREFIX, "make-toc");

	/**
	 * Generates an index.
	 */
	public static final ElementNode MAKE_INDEX_ELEM = new ElementNode(URI, PREFIX, "make-index");

	/**
	 * Marks an index keyword.
	 */
	public static final ElementNode INDEX_ELEM = new ElementNode(URI, PREFIX, "index");

	/**
	 * Stops with an error (for testing).
	 */
	public static final ElementNode FAIL_ELEM = new ElementNode(URI, PREFIX, "fail");

	/**
	 * Embeds a style sheet in an HTML document. The data value is the style sheet itself.
	 */
	public static final String PI_STYLESHEET = "jp.cssj.stylesheet";

	/**
	 * Document information. Pseudo-attributes name and value set the name and value.
	 */
	public static final String PI_DOCUMENT_INFO = "jp.cssj.document-info";

	/**
	 * Default character encoding for style sheets.
	 */
	public static final String PI_DEFAULT_ENCODING = "jp.cssj.default-encoding";

	/**
	 * Default style sheet MIME type.
	 */
	public static final String PI_DEFAULT_STYLE_TYPE = "jp.cssj.default-style-type";

	/**
	 * The document's base URI.
	 */
	public static final String PI_BASE_URI = "jp.cssj.base-uri";

	/** Processing instruction for setting properties. */
	public static final String PI_PROPERTY = "jp.cssj.property";
}