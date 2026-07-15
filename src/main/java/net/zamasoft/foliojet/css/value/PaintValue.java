package net.zamasoft.foliojet.css.value;

import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * @author MIYABE Tatsuhiko
 */
public interface PaintValue extends Value {
	public Paint getPaint(Rectangle2D box);
}