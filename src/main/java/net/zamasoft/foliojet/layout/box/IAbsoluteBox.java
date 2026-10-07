package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.AbsolutePos;

/**
 * An absolutely positioned box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: IAbsoluteBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IAbsoluteBox extends IBox {
	/**
	 * Returns the positioning parameters of the absolutely positioned box.
	 *
	 * @return
	 */
	public AbsolutePos getAbsolutePos();
}
