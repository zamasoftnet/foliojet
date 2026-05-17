package jp.cssj.homare.css.value;

import java.awt.geom.Rectangle2D;

import net.zamasoft.pdfg2d.gc.paint.Paint;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ColorValue.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface PaintValue extends Value {
	public Paint getPaint(Rectangle2D box);
}