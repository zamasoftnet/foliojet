package net.zamasoft.foliojet.xml;

import java.net.URI;

/**
 * An interface for obtaining style sheets associated with a document.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: StyleSheetSelector.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface StyleSheetSelector {
	/**
	 *
	 * @param uri
	 * @param type
	 * @param title
	 * @param media
	 * @param alternate
	 * @return true to apply this style sheet.
	 */
	public boolean stylesheet(URI uri, String type, String title, String media, boolean alternate);
}