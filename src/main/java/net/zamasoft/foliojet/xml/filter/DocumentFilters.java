package net.zamasoft.foliojet.xml.filter;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.StyleSheetSelector;
import net.zamasoft.foliojet.xml.XMLHandlerFilter;

/**
 * input.filters プロパティのフィルタ名から入力フィルタを生成します。
 */
public final class DocumentFilters {
	private DocumentFilters() {
		// utility
	}

	/**
	 * フィルタを生成します。未知の名前の場合はnullを返します。
	 */
	public static XMLHandlerFilter create(String name, UserAgent ua, StyleSheetSelector ssh) {
		switch (name) {
		case "loose-html":
			// html補正
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
			// namespace置き換え
			return new XHTMLNSFilter();
		default:
			return null;
		}
	}
}
