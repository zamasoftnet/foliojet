package net.zamasoft.foliojet.xml.filter;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.StyleSheetSelector;
import net.zamasoft.foliojet.xml.XMLHandlerFilter;

/**
 * Creates input filters from filter names in the input.filters property.
 */
public final class DocumentFilters {
	private DocumentFilters() {
		// utility
	}

	/**
	 * Creates a filter. Returns null for unknown names.
	 */
	public static XMLHandlerFilter create(String name, UserAgent ua, StyleSheetSelector ssh) {
		switch (name) {
		case "loose-html":
			// HTML correction
			return new XHTMLPreprocessFilter(ua);
		case "xslt": {
			// XSLT
			XSLTProcessorFilter xsltFilter = new XSLTProcessorFilter();
			xsltFilter.setup(ua);
			if (ssh != null) {
				xsltFilter.setStyleSheetSelector(ssh);
			}
			return xsltFilter;
		}
		case "default-to-xhtml":
			// Namespace replacement
			return new XHTMLNSFilter();
		default:
			return null;
		}
	}
}
