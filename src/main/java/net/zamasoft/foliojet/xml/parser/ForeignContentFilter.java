package net.zamasoft.foliojet.xml.parser;

import org.htmlunit.cyberneko.filters.DefaultFilter;
import org.htmlunit.cyberneko.xerces.xni.Augmentations;
import org.htmlunit.cyberneko.xerces.xni.QName;
import org.htmlunit.cyberneko.xerces.xni.XMLAttributes;
import org.htmlunit.cyberneko.xerces.xni.XNIException;

import net.zamasoft.foliojet.xml.vocab.Foreign;

/**
 * A filter that normalizes element namespaces in HTML (and HTML within Markdown).
 * Until 2026-10-04, HTMLParser and MarkdownParser contained copies of the same anonymous class.
 *
 * <ul>
 * <li><b>HTML5 foreign content</b>: assigns HTML5 namespaces to {@code <math>}/{@code <svg>} and descendants.
 * HTML normally omits {@code xmlns}; HTML5 assigns the correct namespaces during parsing
 * (as all browsers do). NekoHTML does not, so compensate here. Otherwise, MathML becomes flat text,
 * including even raw LaTeX inside {@code <annotation>}. This is exactly how arXiv currently produces
 * HTML (ar5iv/LaTeXML): {@code h_{t}} appeared as "htsubscript … h_{t}"
 * (2026-08-05, real-world corpus wave 11).
 * <b>Simplification</b>: does not handle HTML5 integration points (where contents of {@code <foreignObject>}
 * or {@code <annotation-xml encoding="text/html">} return to HTML).
 * Documents for printing almost never switch back to HTML inside these in practice.</li>
 * <li>If {@code input.html.change-default-namespace} is false, removes non-foreign namespaces
 * from unprefixed elements.</li>
 * </ul>
 * It follows the {@link net.zamasoft.balancer.TagBalancer}, whose end events match its start events one for one,
 * and keeps the namespace of each open element of foreign content (2026-11-26). Before the balancer it counted the end
 * tags as written:
 * {@code <svg><path d="..."></svg>} left it one deep, and every element after the svg went into the SVG namespace,
 * the rest of the document with it (Shopify's theme demo). The switch of element properties at the first
 * {@code <body>} stays before the balancer ({@link StandardsModeFilter}).
 */
class ForeignContentFilter extends DefaultFilter {
	private final boolean changeDefaultNamespace;

	/**
	 * The namespace each open element of foreign content was given, innermost first; empty outside foreign content.
	 * An end event gets the namespace its start event got (2026-11-26): the balancer sends the end event of an element
	 * with the name it kept, whose namespace was NekoHTML's (the XHTML one under {@code <html xmlns="...xhtml">}).
	 */
	private final java.util.ArrayDeque<String> openURIs = new java.util.ArrayDeque<String>();

	ForeignContentFilter(final boolean changeDefaultNamespace) {
		this.changeDefaultNamespace = changeDefaultNamespace;
	}

	/**
	 * Gives the element its namespace by where it is, as HTML does, and tells whether it is in foreign content. An svg
	 * or a math element is an SVG or MathML one by its name; the other unprefixed elements in foreign content take their
	 * parent's. The namespace NekoHTML gave them by the xmlns attributes in force does not count (2026-11-26): it was
	 * the XHTML one for an svg in a document of {@code <html xmlns="http://www.w3.org/1999/xhtml">}, which was not drawn,
	 * and the SVG one for a math element after an svg its namespace binder took as still open. A prefixed element keeps
	 * the namespace its declared prefix gives it.
	 */
	private boolean applyForeign(final QName element) {
		final String byName = Foreign.uriOf(element.getLocalpart());
		final String parent = this.openURIs.peek();
		final String uri;
		if (prefixed(element) && element.getUri() != null && (parent != null || Foreign.is(element.getUri()))) {
			uri = element.getUri();
		} else if (byName != null) {
			uri = byName;
		} else if (parent != null) {
			uri = parent;
		} else {
			this.dropStaleForeign(element);
			return false;
		}
		element.setUri(uri);
		return true;
	}

	private static boolean prefixed(final QName element) {
		return element.getPrefix() != null && element.getPrefix().length() > 0;
	}

	/**
	 * Takes a foreign namespace off an unprefixed element outside foreign content (2026-11-26). HTML gives no element
	 * a namespace by its xmlns attribute; NekoHTML does, and its namespace binder counts the end tags as written: after
	 * {@code <svg xmlns="http://www.w3.org/2000/svg"><symbol><path d="..."></symbol></svg>} the svg's default namespace
	 * stayed in force, and every element after it was an SVG one (Shopify's theme demo).
	 */
	private void dropStaleForeign(final QName element) {
		if (Foreign.is(element.getUri()) && !prefixed(element)) {
			element.setUri(null);
		}
	}

	/** Removes non-foreign default namespaces from unprefixed elements. */
	private void stripDefaultNamespace(final QName element) {
		if (!this.changeDefaultNamespace && !Foreign.is(element.getUri()) && element.getUri() != null
				&& (element.getPrefix() == null || element.getPrefix().length() == 0)) {
			element.setUri(null);
		}
	}

	@Override
	public void startElement(final QName element, final XMLAttributes attributes, final Augmentations augs)
			throws XNIException {
		if (this.applyForeign(element)) {
			this.openURIs.push(element.getUri());
		}
		this.stripDefaultNamespace(element);
		super.startElement(element, attributes, augs);
	}

	@Override
	public void endElement(final QName element, final Augmentations augs) throws XNIException {
		if (!this.openURIs.isEmpty()) {
			element.setUri(this.openURIs.pop());
		} else {
			this.dropStaleForeign(element);
		}
		this.stripDefaultNamespace(element);
		super.endElement(element, augs);
	}

	@Override
	public void emptyElement(final QName element, final XMLAttributes attributes, final Augmentations augs)
			throws XNIException {
		// Empty elements open and close immediately: nothing to keep for an end event.
		this.applyForeign(element);
		this.stripDefaultNamespace(element);
		super.emptyElement(element, attributes, augs);
	}
}
