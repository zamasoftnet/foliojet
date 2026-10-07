package net.zamasoft.foliojet.xml.vocab;

/**
 * Namespaces for HTML5 <b>foreign content</b> ({@code <math>} and {@code <svg>}).
 *
 * <p>
 * <b>HTML normally omits {@code xmlns}.</b> HTML5 assigns the correct namespaces to these
 * two elements during parsing (as all browsers do), so authors specify nothing.
 * A path that knows only XML rules turns them into XHTML elements,
 * <b>flattening MathML into text and SVG into mere nested elements</b>.
 *
 * <p>
 * Discovered on 2026-08-05 in real-world corpus wave 11. arXiv's current HTML format
 * (ar5iv/LaTeXML) omits {@code xmlns}; {@code h_{t}} appeared as
 * "htsubscript … h_{t}", because even raw LaTeX inside {@code <annotation>}
 * flowed into the output.
 *
 * <p>
 * <b>Protection is needed in two places</b>; either alone is insufficient:
 * <ul>
 * <li>{@code HTMLParser}: <b>assign namespaces</b> to {@code <math>}/{@code <svg>}
 * without {@code xmlns} (NekoHTML does not implement foreign content).</li>
 * <li>{@code XHTMLNSFilter}: <b>do not collapse the assigned namespaces into XHTML</b>.
 * This path maps all elements with no declared prefix and identical local and qualified names
 * to XHTML, so without a guard it also affects {@code <math>}.</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 */
public final class Foreign {
	public static final String MATHML_URI = "http://www.w3.org/1998/Math/MathML";

	public static final String SVG_URI = "http://www.w3.org/2000/svg";

	private Foreign() {
		// Utility
	}

	/** Whether this namespace must be preserved rather than collapsed into XHTML. */
	public static boolean is(String uri) {
		return MATHML_URI.equals(uri) || SVG_URI.equals(uri);
	}

	/** Whether the element name introduces foreign content. Returns {@code null} if it does not. */
	public static String uriOf(String localName) {
		if ("math".equalsIgnoreCase(localName)) {
			return MATHML_URI;
		}
		if ("svg".equalsIgnoreCase(localName)) {
			return SVG_URI;
		}
		return null;
	}
}
