package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.pdfg2d.gc.paint.Color;

public class TextShadow {
	public final double x, y;

	/**
	 * ぼかし半径(0=ぼかしなし。2026-08-29)。描画は{@code box-shadow}と
	 * 同じ多段の半透明近似({@code AbstractTextBox.TextSequenceDrawable})。
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
