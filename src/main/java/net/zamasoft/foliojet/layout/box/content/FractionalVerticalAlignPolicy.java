package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.AbstractLineBox;
import net.zamasoft.foliojet.layout.box.AbstractTextBox;

public class FractionalVerticalAlignPolicy implements VerticalAlignPolicy {
	protected final double ratio;

	public FractionalVerticalAlignPolicy(double ratio) {
		this.ratio = ratio;
	}

	public double getVerticalAlign(AbstractTextBox parent, AbstractLineBox line, double ascent, double descent,
			double lineHeight, double lineBase) {
		return (this.ratio * lineHeight);
	}

	public String toString() {
		return this.ratio * 100 + "%";
	}
}
