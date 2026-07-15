package net.zamasoft.foliojet.css.value;

import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * @author MIYABE Tatsuhiko
 */
public class ColorValue implements PaintValue {
	protected final Color color;
	
	public ColorValue(Color color) {
		this.color = color;
	}
	
	public Paint getPaint(Rectangle2D box) {
		return this.color;
	}
	
	public Color getColor() {
		return this.color;
	}

	public Paint.Type getPaintType() {
		return this.color.getPaintType();
	}

	public Color.Type getColorType() {
		return this.color.getColorType();
	}

	public float getRed() {
		return this.color.getRed();
	}

	public float getGreen() {
		return this.color.getGreen();
	}

	public float getBlue() {
		return this.color.getBlue();
	}

	public float getAlpha() {
		return this.color.getAlpha();
	}

	public float getComponent(int i) {
		return this.color.getComponent(i);
	}

	public boolean equals(Object o) {
		if (o instanceof ColorValue) {
			return ((ColorValue)o).getColor().equals(this.color);
		}
		if (o instanceof Paint) {
			return ((Paint)o).equals(this.color);
		}
		return false;
	}
}
