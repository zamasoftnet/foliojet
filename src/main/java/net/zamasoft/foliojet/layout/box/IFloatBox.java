package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.FloatPos;

/**
 * 浮動ボックスです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IFloatBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IFloatBox extends IBox {
	/**
	 * 浮動ボックスの配置パラメータを返します。
	 * 
	 * @return
	 */
	public FloatPos getFloatPos();
}
