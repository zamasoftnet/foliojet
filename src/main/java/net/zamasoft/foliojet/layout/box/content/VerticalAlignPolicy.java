package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;

/**
 * Calculates vertical offsets for the vertical-align property.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: VerticalAlignPolicy.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface VerticalAlignPolicy {
	/**
	 * Returns the offset from the baseline. Positive values point upward.
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
