package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.params.InlinePos;

/**
 *
 * An inline box.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: IInlineBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IInlineBox extends IBox, IFramedBox {
	/**
	 * Returns the positioning parameters of the inline box.
	 *
	 * @return
	 */
	public InlinePos getInlinePos();
}
