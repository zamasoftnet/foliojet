package net.zamasoft.foliojet.css.value.css3;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.LinearGradient;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * @author MIYABE Tatsuhiko
 */
public class LinearGradientValue implements PaintValue {
	protected final double angle;
	protected final Color[] colors;
	protected final double[] fractions;

	public LinearGradientValue(double angle, double[] fractions, Color[] colors) {
		this.angle = angle;
		this.colors = colors;
		this.fractions = fractions;
	}

	public Paint getPaint(Rectangle2D box) {
		double mx = (box.getMinX() + box.getMaxX()) / 2;
		return new LinearGradient(mx, box.getMaxY(), mx, box.getMinY(), this.fractions, this.colors,
				AffineTransform.getRotateInstance(this.angle, mx, (box.getMinY() + box.getMaxY()) / 2));
	}

}