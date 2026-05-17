package jp.cssj.homare.xml;

/**
 * ContentHandlerに対するフィルタリング処理をします。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: XMLHandlerFilter.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface XMLHandlerFilter extends XMLHandler {
	/**
	 * 出力先のXMLHandlerを設定します。
	 * 
	 * @param xmlHandler
	 */
	public void setXMLHandler(XMLHandler xmlHandler);

	/**
	 * 出力先のXMLHandlerを返します。
	 * 
	 * @return
	 */
	public XMLHandler getXMLHandler();
}
