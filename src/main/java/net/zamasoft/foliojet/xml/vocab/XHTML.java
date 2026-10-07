package net.zamasoft.foliojet.xml.vocab;

import net.zamasoft.foliojet.xml.AttributeNode;
import net.zamasoft.foliojet.xml.ElementNode;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: XHTML.java 1587 2019-06-10 01:42:25Z miyabe $
 */
public final class XHTML {
	private XHTML() {
		// unused
	}

	public static final String PREFIX = "html";

	public static final String URI = "http://www.w3.org/1999/xhtml";

	/** Root. */
	public static final ElementNode HTML_ELEM = new ElementNode(URI, PREFIX, "html");

	/** Document style. (SPEC CSS2 2.1) */
	public static final ElementNode STYLE_ELEM = new ElementNode(URI, PREFIX, "style");

	/** Title. */
	public static final ElementNode TITLE_ELEM = new ElementNode(URI, PREFIX, "title");

	/** Metadata. */
	public static final ElementNode META_ELEM = new ElementNode(URI, PREFIX, "meta");

	/** Document contents. */
	public static final ElementNode BODY_ELEM = new ElementNode(URI, PREFIX, "body");

	/** Element for changing the base URI. */
	public static final ElementNode BASE_ELEM = new ElementNode(URI, PREFIX, "base");

	/** Element for links. (SPEC ASSX1.0) */
	public static final ElementNode LINK_ELEM = new ElementNode(URI, PREFIX, "link");

	/** Anchor element. */
	public static final ElementNode A_ELEM = new ElementNode(URI, PREFIX, "a");

	/** Image element. */
	public static final ElementNode IMG_ELEM = new ElementNode(URI, PREFIX, "img");

	/** Embed element. */
	public static final ElementNode EMBED_ELEM = new ElementNode(URI, PREFIX, "embed");

	/** Object element. */
	public static final ElementNode OBJECT_ELEM = new ElementNode(URI, PREFIX, "object");

	/** INPUT element. */
	public static final ElementNode INPUT_ELEM = new ElementNode(URI, PREFIX, "input");

	/** BUTTON element. */
	public static final ElementNode BUTTON_ELEM = new ElementNode(URI, PREFIX, "button");
	//
	// /** RUBY element. */
	// public static final ElementNode RUBY_ELEM = new ElementNode(URI, PREFIX,
	// "ruby");
	//
	// /** RB element. */
	// public static final ElementNode RB_ELEM = new ElementNode(URI, PREFIX,
	// "rb");

	public static final ElementNode OL_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "ol");

	public static final ElementNode UL_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "ul");

	public static final ElementNode LI_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "li");

	public static final ElementNode SPAN_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "span");

	public static final ElementNode P_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "p");

	public static final ElementNode TD_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "td");

	public static final ElementNode TH_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "th");

	public static final ElementNode H1_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h1");

	public static final ElementNode H2_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h2");

	public static final ElementNode H3_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h3");

	public static final ElementNode H4_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h4");

	public static final ElementNode H5_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h5");

	public static final ElementNode H6_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "h6");

	public static final ElementNode BR_ELEM = new ElementNode(XHTML.URI, XHTML.PREFIX, "br");

	/** Attribute for ID selectors. */
	public static final AttributeNode ID_ATTR = new AttributeNode("id");

	/** Attribute for class selectors. */
	public static final AttributeNode CLASS_ATTR = new AttributeNode("class");

	/** Attribute for inline styles. */
	public static final AttributeNode STYLE_ATTR = new AttributeNode("style");

	/** Attribute for language selectors. */
	public static final AttributeNode LANG_ATTR = new AttributeNode("lang");

	/** Attribute for the :dir() selector. */
	public static final AttributeNode DIR_ATTR = new AttributeNode("dir");

	/** Advisory text attribute. */
	public static final AttributeNode TITLE_ATTR = new AttributeNode("title");

	/** Table column span attribute. */
	public static final AttributeNode SPAN_ATTR = new AttributeNode("span");

	/** Table cell row span attribute. */
	public static final AttributeNode COLSPAN_ATTR = new AttributeNode("colspan");

	/** Table column column span attribute. */
	public static final AttributeNode ROWSPAN_ATTR = new AttributeNode("rowspan");

	public static final AttributeNode HREF_ATTR = new AttributeNode("href");

	public static final AttributeNode NAME_ATTR = new AttributeNode("name");

	/** Image map reference. */
	public static final AttributeNode USEMAP_ATTR = new AttributeNode("usemap");

	/**
	 * Gets attribute values such as {@code class} and {@code style}.
	 *
	 * <p>
	 * <b>Attributes of elements within foreign content such as SVG/MathML are not in the
	 * XHTML namespace</b>: per HTML5 parsing rules, attributes other than {@code xlink:}/{@code xml:}
	 * remain unqualified (discovered on 2026-08-06 while investigating broken inline SVG sizing
	 * in the full-scale corpus). If not found in the XHTML namespace, also try the unqualified
	 * name (qName), preventing {@code class}/{@code style}/{@code width}/{@code height} and similar
	 * SVG element attributes from being missed and thus ignored by CSS.
	 * </p>
	 */
	public static String getAttr(org.xml.sax.Attributes atts, String localName) {
		String value = atts.getValue(URI, localName);
		if (value == null) {
			value = atts.getValue(localName);
		}
		return value;
	}
}