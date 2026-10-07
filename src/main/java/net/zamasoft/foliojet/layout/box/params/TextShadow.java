package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

public class TextShadow {
	public final double x, y;

	/**
	 * The blur radius (0 = no blur; 2026-08-29). Drawing uses the same multistep translucent approximation as
	 * {@code box-shadow} ({@code AbstractTextBox.TextSequenceDrawable}).
	 */
	public final double blur;

	public final Color color;

	public TextShadow(double x, double y, Color color) {
		this(x, y, 0, color);
	}

	public TextShadow(double x, double y, double blur, Color color) {
		this.x = x;
		this.y = y;
		this.blur = blur;
		this.color = color;
	}
}
