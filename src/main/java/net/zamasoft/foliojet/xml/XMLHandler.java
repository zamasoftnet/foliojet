package net.zamasoft.foliojet.xml;

import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

/**
 * SAXのContentHandlerとLexicalHandlerを合わせた、文書イベントの受け口です。
 */
public interface XMLHandler extends ContentHandler, LexicalHandler {
	/**
	 * ContentHandlerとLexicalHandlerの組をXMLHandlerに合成します。
	 * nullを渡した側のイベントは無視されます。
	 */
	public static XMLHandler of(ContentHandler contentHandler, LexicalHandler lexicalHandler) {
		return new CompositeXMLHandler(contentHandler, lexicalHandler);
	}
}
