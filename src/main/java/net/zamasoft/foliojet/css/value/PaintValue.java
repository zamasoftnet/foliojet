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
	 * 形を塗ります(2026-08-29)。既定は{@link #getPaint}を塗りに設定して
	 * 塗りつぶすだけ。円錐グラデーションのようにpdfg2dの{@code Paint}で
	 * 表せない塗りは、これを上書きして自前で描く。
	 *
	 * @param gc    描画先(塗りの設定は呼び出し側のスコープに残るので、
	 *              呼び出し側がbegin()で囲むこと)
	 * @param shape 塗る形
	 * @param box   グラデーションの基準箱(通常は{@code shape}の外接矩形)
	 */
	public default void fill(final GC gc, final Shape shape, final Rectangle2D box) throws GraphicsException {
		gc.setFillPaint(this.getPaint(box));
		gc.fill(shape);
	}
}
