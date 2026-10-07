package net.zamasoft.foliojet.css.value;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * @author MIYABE Tatsuhiko
 */
public interface PaintValue extends Value {
	public Paint getPaint(Rectangle2D box);

	/**
	 * Fills a shape (2026-08-29). By default, sets {@link #getPaint} as the fill paint
	 * and simply fills the shape. Paints that pdfg2d's {@code Paint} cannot represent,
	 * such as conic gradients, override this method to draw themselves.
	 *
	 * @param gc    the drawing destination (the fill setting remains in the caller's scope,
	 *              so the caller must enclose the call in begin())
	 * @param shape the shape to fill
	 * @param box   the gradient's reference box (usually the bounding rectangle of {@code shape})
	 */
	public default void fill(final GC gc, final Shape shape, final Rectangle2D box) throws GraphicsException {
		gc.setFillPaint(this.getPaint(box));
		gc.fill(shape);
	}
}
