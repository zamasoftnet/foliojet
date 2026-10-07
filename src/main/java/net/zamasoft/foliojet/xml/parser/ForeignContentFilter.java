package net.zamasoft.foliojet.xml.parser;

import org.htmlunit.cyberneko.filters.DefaultFilter;
import org.htmlunit.cyberneko.xerces.xni.Augmentations;
import org.htmlunit.cyberneko.xerces.xni.QName;
import org.htmlunit.cyberneko.xerces.xni.XMLAttributes;
import org.htmlunit.cyberneko.xerces.xni.XNIException;

import net.zamasoft.balancer.ElementProps;
import net.zamasoft.balancer.TagBalancer;
import net.zamasoft.foliojet.ua.CompatibleMode;
import net.zamasoft.foliojet.ua.UserAgent;
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
 * or {@code <annotation-xml encoding="text/html">} return to HTML); tracks depth only.
 * Documents for printing almost never switch back to HTML inside these in practice.</li>
 * <li>If {@code input.html.change-default-namespace} is false, removes non-foreign namespaces
 * from unprefixed elements.</li>
 * <li>At the first {@code <body>}, switches element properties to {@code html4.xml} in standards mode.</li>
 * </ul>
 */
class ForeignContentFilter extends DefaultFilter {
	private final UserAgent ua;
	private final TagBalancer balancer;
	private final boolean changeDefaultNamespace;
	private boolean firstElement = true;

	/** Foreign content namespace and nesting depth (0 means outside). */
	private String foreignURI = null;
	private int foreignDepth = 0;

	ForeignContentFilter(final UserAgent ua, final TagBalancer balancer, final boolean changeDefaultNamespace) {
		this.ua = ua;
		this.balancer = balancer;
		this.changeDefaultNamespace = changeDefaultNamespace;
	}

	private void applyForeign(final QName element) {
		if (this.foreignDepth == 0) {
			if (element.getUri() == null) {
				final String uri = Foreign.uriOf(element.getLocalpart());
				if (uri == null) {
					return;
				}
				this.foreignURI = uri;
				element.setUri(uri);
			} else if (Foreign.is(element.getUri())) {
				// An explicit xmlns is present. NekoHTML has already assigned it.
				this.foreignURI = element.getUri();
			} else {
				return;
			}
		} else if (element.getUri() == null) {
			element.setUri(this.foreignURI);
		}
		++this.foreignDepth;
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
		this.applyForeign(element);
		this.stripDefaultNamespace(element);
		super.startElement(element, attributes, augs);
		if (this.firstElement && element.getLocalpart().equalsIgnoreCase("body")) {
			// Switch to standards mode.
			if (this.ua.getDocumentContext().getCompatibleMode() == CompatibleMode.STRICT) {
				this.balancer.setElementProps(ElementProps.getElementProps("html4.xml"));
			}
			this.firstElement = false;
		}
	}

	@Override
	public void endElement(final QName element, final Augmentations augs) throws XNIException {
		if (this.foreignDepth > 0) {
			if (element.getUri() == null) {
				element.setUri(this.foreignURI);
			}
			if (--this.foreignDepth == 0) {
				this.foreignURI = null;
			}
		}
		this.stripDefaultNamespace(element);
		super.endElement(element, augs);
	}

	@Override
	public void emptyElement(final QName element, final XMLAttributes attributes, final Augmentations augs)
			throws XNIException {
		// Empty elements open and close immediately. Do not change foreign depth.
		final int depth = this.foreignDepth;
		final String uri = this.foreignURI;
		this.applyForeign(element);
		this.foreignDepth = depth;
		this.foreignURI = uri;
		this.stripDefaultNamespace(element);
		super.emptyElement(element, attributes, augs);
	}
}
