package net.zamasoft.foliojet.xml;

import org.xml.sax.Locator;

/**
 * XML/HTMLソース上の位置情報です。
 * パーサーは {@link org.xml.sax.ContentHandler#setDocumentLocator} を通じてこれを渡します。
 */
public interface SourceLocator extends Locator {
	public int getCharacterOffset();

	public String getEncoding();

	public String getXMLVersion();
}
