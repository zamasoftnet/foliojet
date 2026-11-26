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

/**
 * Sits before the {@link TagBalancer} in HTML (and HTML within Markdown): at the first {@code <body>} start tag of
 * a document in standards mode, it switches the balancer's element properties to {@code html4.xml}. It reads the tags
 * as written, so a document without a {@code <body>} tag keeps the properties it started with. Until 2026-11-26 this
 * was part of {@link ForeignContentFilter}, which now follows the balancer.
 */
class StandardsModeFilter extends DefaultFilter {
	private final UserAgent ua;
	private final TagBalancer balancer;
	private boolean bodySeen = false;

	StandardsModeFilter(final UserAgent ua, final TagBalancer balancer) {
		this.ua = ua;
		this.balancer = balancer;
	}

	@Override
	public void startElement(final QName element, final XMLAttributes attributes, final Augmentations augs)
			throws XNIException {
		super.startElement(element, attributes, augs);
		if (!this.bodySeen && element.getLocalpart().equalsIgnoreCase("body")) {
			if (this.ua.getDocumentContext().getCompatibleMode() == CompatibleMode.STRICT) {
				this.balancer.setElementProps(ElementProps.getElementProps("html4.xml"));
			}
			this.bodySeen = true;
		}
	}
}
