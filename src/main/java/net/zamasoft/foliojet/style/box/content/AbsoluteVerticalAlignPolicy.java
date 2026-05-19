package net.zamasoft.foliojet.style.box.content;

import net.zamasoft.foliojet.style.box.AbstractLineBox;
import net.zamasoft.foliojet.style.box.AbstractTextBox;

/**
 * 絶対位置指定の垂直位置合わせです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteVerticalAlignPolicy.java 3804 2012-07-10 06:53:45Z
 *          miyabe $
 */
public class AbsoluteVerticalAlignPolicy implements VerticalAlignPolicy {
	protected final double position;

	public AbsoluteVerticalAlignPolicy(double position) {
		this.position = position;
	}

	public double getVerticalAlign(AbstractTextBox parent, AbstractLineBox line, double ascent, double descent,
			double lineHeight, double lineBase) {
		return this.position;
	}

	public String toString() {
		return String.valueOf(this.position);
	}
}
