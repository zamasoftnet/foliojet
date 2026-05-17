package jp.cssj.homare.style.box.content;

import jp.cssj.homare.style.box.AbstractLineBox;
import jp.cssj.homare.style.box.AbstractTextBox;

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
