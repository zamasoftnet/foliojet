package net.zamasoft.foliojet.layout.box;

/**
 * 通常のフローに配置されるボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IFlowBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IFlowBox extends IBox {
	/**
	 * 直前で改ページ禁止されていればtrueを返します。
	 * 
	 * @return
	 */
	public boolean avoidBreakBefore();

	/**
	 * 直後で改ページ禁止されていればtrueを返します。
	 * 
	 * @return
	 */
	public boolean avoidBreakAfter();
}
