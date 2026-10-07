package net.zamasoft.foliojet.xml;

/**
 * Filters ContentHandler events.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: XMLHandlerFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface XMLHandlerFilter extends XMLHandler {
	/**
	 * Sets the destination XMLHandler.
	 *
	 * @param xmlHandler
	 */
	public void setXMLHandler(XMLHandler xmlHandler);

	/**
	 * Returns the destination XMLHandler.
	 *
	 * @return
	 */
	public XMLHandler getXMLHandler();
}
