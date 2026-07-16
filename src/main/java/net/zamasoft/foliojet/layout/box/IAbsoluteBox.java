package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.AbsolutePos;

/**
 * 絶対位置指定されたボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IAbsoluteBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IAbsoluteBox extends IBox {
	/**
	 * 絶対位置指定されたボックスの配置パラメータを返します。
	 * 
	 * @return
	 */
	public AbsolutePos getAbsolutePos();
}
