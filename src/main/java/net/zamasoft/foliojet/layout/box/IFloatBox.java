package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.FloatPos;

/**
 * A float box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: IFloatBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IFloatBox extends IBox {
	/**
	 * Returns the positioning parameters of the float box.
	 *
	 * @return
	 */
	public FloatPos getFloatPos();
}
