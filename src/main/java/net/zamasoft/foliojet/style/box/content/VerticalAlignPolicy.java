package net.zamasoft.foliojet.style.box.content;

import net.zamasoft.foliojet.style.box.AbstractLineBox;
import net.zamasoft.foliojet.style.box.AbstractTextBox;

/**
 * vertical-align特性による上下のずれを計算します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: VerticalAlignPolicy.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface VerticalAlignPolicy {
	/**
	 * ベースラインからのずれを返します。正の値が上方向です。
	 * 
	 * @param parent
	 * @param line
	 * @param ascent
	 * @param descent
	 * @param lineHeight
	 * @param lineBase
	 * @return
	 */
	public double getVerticalAlign(AbstractTextBox parent, AbstractLineBox line, double ascent, double descent,
			double lineHeight, double lineBase);
}
