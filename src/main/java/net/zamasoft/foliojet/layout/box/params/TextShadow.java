package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

public class TextShadow {
	public final double x, y;
	public final Color color;

	public TextShadow(double x, double y, Color color) {
		this.x = x;
		this.y = y;
		this.color = color;
	}
}
