package net.zamasoft.foliojet.style.box;

import net.zamasoft.foliojet.style.box.params.InlinePos;

/**
 * 
 * インラインボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IInlineBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IInlineBox extends IBox, IFramedBox {
	/**
	 * インラインボックスの配置パラメータを返します。
	 * 
	 * @return
	 */
	public InlinePos getInlinePos();
}
