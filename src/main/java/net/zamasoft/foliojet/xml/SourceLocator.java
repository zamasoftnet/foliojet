package net.zamasoft.foliojet.xml;

/**
 * XML/HTMLソース上の位置情報です。
 */
public interface SourceLocator {
	public String getPublicId();

	public String getSystemId();

	public int getLineNumber();

	public int getColumnNumber();

	public int getCharacterOffset();

	public String getEncoding();

	public String getXMLVersion();
}
